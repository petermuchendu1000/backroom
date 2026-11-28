package com.ongero.app.data.audio

import android.content.Context
import android.util.Log
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 * PRODUCTION AUDIO DEVICE MODULE  (v4 — AudioBufferCallback)
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * Instead of creating a second AudioRecord (which conflicts with the one inside
 * JavaAudioDeviceModule), we build a standard JavaAudioDeviceModule and register an
 * [JavaAudioDeviceModule.AudioBufferCallback]. It fires **before** the recorded PCM is
 * handed to native via nativeDataIsRecorded, so modifying the ByteBuffer in place is
 * what the far end actually hears.
 *
 * v3 did the same thing with AudioRecordDataCallback on stream-webrtc-android, where
 * that callback is never invoked — the Builder stores it and build() drops it. The
 * anonymiser therefore never ran and real voices were transmitted. Nothing in the code
 * said so, because the callback simply was not called. If this file is ever ported to
 * another WebRTC build, verify in that build's bytecode that the hook is reached before
 * nativeDataIsRecorded, and do not trust the method merely existing.
 *
 * ARCHITECTURE:
 * ┌──────────┐   ┌─────────────────────────────────┐   ┌──────────┐
 * │ Hardware │──▶│ JavaAudioDeviceModule            │──▶│ WebRTC   │
 * │   Mic    │   │  └─ AudioBufferCallback ──────▶  │   │  Native  │
 * │          │   │      (in-place anonymisation)     │   │  Encoder │
 * └──────────┘   └─────────────────────────────────┘   └──────────┘
 *
 * KEY BENEFITS:
 * - Single AudioRecord instance (no dual-record conflict)
 * - Audio is processed BEFORE nativeDataIsRecorded — guaranteed interception
 * - Hardware AEC / NS still work (owned by JavaAudioDeviceModule)
 * - All playback is delegated unchanged
 */
class ProductionAudioDeviceModule private constructor(
    private val inner: JavaAudioDeviceModule
) : AudioDeviceModule {

    companion object {
        private const val TAG = "ProductionAudioModule"
        private const val SAMPLE_RATE = 48000

        /**
         * Create a NEW instance each time.
         *
         * IMPORTANT: Do NOT use a singleton here. Each PeerConnectionFactory must
         * get its own AudioDeviceModule. Reusing a stale ADM after the previous
         * PeerConnectionFactory was disposed corrupts native audio routing, causing
         * the sharer to not hear the receiver and the receiver to hear their own
         * voice looped back.
         */
        fun create(context: Context): ProductionAudioDeviceModule {
            // Always (re-)initialise the voice anonymizer
            ProductionVoiceAnonymizer.initialize(SAMPLE_RATE)

            // micMuted lives on the companion, so it outlives any one module. Clear it
            // here or a call that ended while muted would start the next one silent.
            micMuted.set(false)

            val statsTracker = StatsTracker()

            val javaModule = JavaAudioDeviceModule.builder(context.applicationContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                // ── THE CRITICAL CALLBACK ──
                // The return value becomes the capture timestamp passed to
                // nativeDataIsRecorded, so hand back the one we were given. The library
                // recomputes bytesRead from the buffer's capacity after this returns.
                .setAudioBufferCallback(object : JavaAudioDeviceModule.AudioBufferCallback {
                    override fun onBuffer(
                        buffer: ByteBuffer,
                        audioFormat: Int,
                        channelCount: Int,
                        sampleRate: Int,
                        bytesRead: Int,
                        captureTimeNs: Long,
                    ): Long {
                        processBufferInPlace(buffer, statsTracker)
                        return captureTimeNs
                    }
                })
                .setAudioRecordStateCallback(object : JavaAudioDeviceModule.AudioRecordStateCallback {
                    override fun onWebRtcAudioRecordStart() {
                        Log.d(TAG, "🎤 AudioRecord STARTED")
                        ProductionVoiceAnonymizer.reset()
                    }
                    override fun onWebRtcAudioRecordStop() {
                        Log.d(TAG, "🎤 AudioRecord STOPPED")
                    }
                })
                .setAudioTrackStateCallback(object : JavaAudioDeviceModule.AudioTrackStateCallback {
                    override fun onWebRtcAudioTrackStart() {
                        Log.d(TAG, "🔊 AudioTrack STARTED")
                    }
                    override fun onWebRtcAudioTrackStop() {
                        Log.d(TAG, "🔊 AudioTrack STOPPED")
                    }
                })
                .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                    override fun onWebRtcAudioRecordInitError(msg: String) {
                        Log.e(TAG, "❌ AudioRecord init error: $msg")
                    }
                    override fun onWebRtcAudioRecordStartError(
                        code: JavaAudioDeviceModule.AudioRecordStartErrorCode,
                        msg: String
                    ) {
                        Log.e(TAG, "❌ AudioRecord start error [$code]: $msg")
                    }
                    override fun onWebRtcAudioRecordError(msg: String) {
                        Log.e(TAG, "❌ AudioRecord error: $msg")
                    }
                })
                .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                    override fun onWebRtcAudioTrackInitError(msg: String) {
                        Log.e(TAG, "❌ AudioTrack init error: $msg")
                    }
                    override fun onWebRtcAudioTrackStartError(
                        code: JavaAudioDeviceModule.AudioTrackStartErrorCode,
                        msg: String
                    ) {
                        Log.e(TAG, "❌ AudioTrack start error [$code]: $msg")
                    }
                    override fun onWebRtcAudioTrackError(msg: String) {
                        Log.e(TAG, "❌ AudioTrack error: $msg")
                    }
                })
                .createAudioDeviceModule()

            return ProductionAudioDeviceModule(javaModule).also {
                Log.d(TAG, "✅ ProductionAudioDeviceModule created (fresh instance)")
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // In-place audio processing (called on WebRTC's audio thread)
        // ─────────────────────────────────────────────────────────────────────

        /** Set by [setMicrophoneMute] so muted frames skip the anonymiser entirely. */
        private val micMuted = AtomicBoolean(false)

        private fun processBufferInPlace(buffer: ByteBuffer, stats: StatsTracker) {
            try {
                val frameNum = stats.framesProcessed.incrementAndGet()

                // WebRtcAudioRecord zeroes the buffer when muted, and it does so BEFORE
                // this callback (bytecode offsets 537 and 741). Running the anonymiser
                // over that silence would be worse than pointless: the pipeline adds ring
                // modulation and a noise component, so a muted microphone would transmit
                // noise instead of nothing. Leave the zeros alone.
                if (micMuted.get()) {
                    if (frameNum <= 5) {
                        Log.d(TAG, "🔇 Frame #$frameNum | muted — passing silence through untouched")
                    }
                    return
                }

                // Ensure little-endian (PCM 16-bit LE is the WebRTC standard)
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                val sampleCount = buffer.remaining() / 2

                // Read samples into a ShortArray
                val shorts = ShortArray(sampleCount)
                val startPos = buffer.position()
                buffer.asShortBuffer().get(shorts)

                if (frameNum <= 5) {
                    Log.d(TAG, "🎭 Frame #$frameNum | samples=$sampleCount | enabled=${ProductionVoiceAnonymizer.isEnabled()}")
                }

                // Process through voice anonymizer
                val processed = ProductionVoiceAnonymizer.process(shorts)

                // Write processed samples BACK into the same ByteBuffer (in-place)
                buffer.position(startPos)
                for (s in processed) {
                    buffer.putShort(s)
                }
                buffer.position(startPos) // Reset for WebRTC to read

                if (frameNum <= 5) {
                    // Quick verification: first sample changed?
                    val changed = shorts[0] != processed[0]
                    Log.d(TAG, "   ✅ Buffer modified in-place (firstSampleChanged=$changed)")
                }

                // Periodic stats
                val now = System.currentTimeMillis()
                val last = stats.lastLogTime.get()
                if (now - last > 5000 && stats.lastLogTime.compareAndSet(last, now)) {
                    Log.d(TAG, "📊 Frames processed: ${stats.framesProcessed.get()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ processBufferInPlace error: ${e.message}", e)
            }
        }
    }

    private class StatsTracker {
        val framesProcessed = AtomicLong(0)
        val lastLogTime = AtomicLong(0)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AudioDeviceModule delegation — everything goes straight to JavaAudioDeviceModule
    // ═══════════════════════════════════════════════════════════════════════════

    // Renamed in webrtc-sdk: getNativeAudioDeviceModulePointer() -> getNative(long).
    // The argument is the WebRTC environment reference the factory passes down.
    override fun getNative(webrtcEnvRef: Long): Long = inner.getNative(webrtcEnvRef)

    override fun release() {
        Log.d(TAG, "🛑 Releasing ProductionAudioDeviceModule")
        ProductionVoiceAnonymizer.release()
        inner.release()
    }

    override fun setSpeakerMute(mute: Boolean) = inner.setSpeakerMute(mute)

    // Delegates so WebRtcAudioRecord zeroes the capture buffer, and records the state so
    // processBufferInPlace leaves those zeros alone instead of anonymising silence into
    // noise. WebRTCManager.setMuted is what calls this.
    override fun setMicrophoneMute(mute: Boolean) {
        micMuted.set(mute)
        inner.setMicrophoneMute(mute)
        Log.d(TAG, "🔇 Microphone mute set to $mute (anonymiser will ${if (mute) "skip" else "process"} frames)")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════════

    fun setAnonymizationLevel(level: Int) {
        ProductionVoiceAnonymizer.setAnonymizationLevel(level)
    }

    fun setAnonymizationEnabled(enabled: Boolean) {
        ProductionVoiceAnonymizer.setEnabled(enabled)
    }

    fun getDebugInfo(): String = """
        ProductionAudioDeviceModule (v4 — AudioBufferCallback)
        ${ProductionVoiceAnonymizer.getDebugInfo()}
    """.trimIndent()
}


// ═══════════════════════════════════════════════════════════════════════════════════════
// HELPER: Audio Device Module Factory
// ═══════════════════════════════════════════════════════════════════════════════════════

object AudioDeviceModuleFactory {

    private const val TAG = "AudioModuleFactory"

    fun create(
        context: Context,
        enableAnonymization: Boolean = true,
        anonymizationLevel: Int = 50
    ): AudioDeviceModule {
        Log.d(TAG, "🏭 Creating AudioDeviceModule (anonymization=$enableAnonymization, level=$anonymizationLevel)")

        return if (enableAnonymization) {
            val module = ProductionAudioDeviceModule.create(context)
            module.setAnonymizationLevel(anonymizationLevel)
            module.setAnonymizationEnabled(true)
            Log.d(TAG, "   ✅ ProductionAudioDeviceModule (AudioBufferCallback) created")
            module
        } else {
            val module = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()
            Log.d(TAG, "   ✅ Plain JavaAudioDeviceModule created")
            module
        }
    }
}

