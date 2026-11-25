package com.example.backroom.data.audio

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unit tests for [ProductionVoiceAnonymizer].
 *
 * These verify that processed audio **actually differs** from the input at the
 * byte/sample level, and that a silent input stays silent.
 *
 * These were previously in `androidTest` and so required a device or emulator,
 * which meant they never ran in CI and never ran here. The class under test is
 * pure DSP whose only Android dependency is `android.util.Log`, so with
 * `testOptions.unitTests.isReturnDefaultValues = true` they run on the JVM.
 */
class VoiceAnonymizationTest {

    companion object {
        private const val SAMPLE_RATE = 48000
        private const val FRAME_SIZE = 480 // 10 ms
        private const val FULL_SCALE = 32767.0
    }

    @Before
    fun setUp() {
        ProductionVoiceAnonymizer.initialize(SAMPLE_RATE)
        ProductionVoiceAnonymizer.setEnabled(true)
        ProductionVoiceAnonymizer.setAnonymizationLevel(100) // max
    }

    @After
    fun tearDown() {
        ProductionVoiceAnonymizer.release()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun sineWave(freq: Float, samples: Int): ShortArray {
        val omega = 2.0 * Math.PI * freq / SAMPLE_RATE
        return ShortArray(samples) { i -> (sin(omega * i) * 16000).toInt().toShort() }
    }

    private fun countDifferent(a: ShortArray, b: ShortArray): Int {
        val len = minOf(a.size, b.size)
        var count = 0
        for (i in 0 until len) if (a[i] != b[i]) count++
        return count
    }

    private fun rms(a: ShortArray): Double {
        if (a.isEmpty()) return 0.0
        var s = 0.0
        for (v in a) s += v.toDouble() * v.toDouble()
        return sqrt(s / a.size)
    }

    /** |Pearson r| between input and output. 1.0 = identical shape, 0 = decorrelated. */
    private fun absCorrelation(a: ShortArray, b: ShortArray): Double {
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i].toDouble(); val y = b[i].toDouble()
            sxy += x * y; sxx += x * x; syy += y * y
        }
        return if (sxx > 0 && syy > 0) abs(sxy / sqrt(sxx * syy)) else 0.0
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun processedAudioDiffersFromInput_sineWave150Hz() {
        val input = sineWave(150f, FRAME_SIZE * 10)
        ProductionVoiceAnonymizer.reset()

        val output = ProductionVoiceAnonymizer.process(input)

        val diff = countDifferent(input, output)
        val ratio = diff.toFloat() / input.size

        assertTrue(
            "Expected >10% samples to differ but got ${ratio * 100}%",
            ratio > 0.10f
        )
    }

    @Test
    fun processedAudioDiffersFromInput_sineWave250Hz() {
        val input = sineWave(250f, FRAME_SIZE * 10)
        ProductionVoiceAnonymizer.reset()

        val output = ProductionVoiceAnonymizer.process(input)

        val diff = countDifferent(input, output)
        assertTrue("Zero samples changed at 250 Hz", diff > 0)
    }

    @Test
    fun processedBytesDifferFromInput() {
        val input = sineWave(150f, FRAME_SIZE * 5)
        val bytes = ByteArray(input.size * 2)
        for (i in input.indices) {
            bytes[i * 2] = (input[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (input[i].toInt() shr 8).toByte()
        }

        ProductionVoiceAnonymizer.reset()
        val processed = ProductionVoiceAnonymizer.processBytes(bytes)

        var diffBytes = 0
        for (i in bytes.indices) {
            if (i < processed.size && bytes[i] != processed[i]) diffBytes++
        }

        assertTrue(
            "Expected byte-level differences but got $diffBytes different bytes",
            diffBytes > 0
        )
    }

    @Test
    fun disabledAnonymizationReturnsIdenticalOutput() {
        val input = sineWave(150f, FRAME_SIZE * 5)

        ProductionVoiceAnonymizer.setEnabled(false)
        ProductionVoiceAnonymizer.reset()

        val output = ProductionVoiceAnonymizer.process(input)

        val diff = countDifferent(input, output)
        assertEquals("Disabled anonymizer should return identical audio", 0, diff)
    }

    @Test
    fun silenceRemainsQuiet() {
        val input = ShortArray(FRAME_SIZE * 10) { 0 }
        ProductionVoiceAnonymizer.reset()

        val output = ProductionVoiceAnonymizer.process(input)

        val rms = rms(output)

        assertTrue("Silence output RMS=$rms should be < 100", rms < 100)
    }

    /**
     * A silent input must stay silent at **every** anonymisation level, not just the
     * default. Guards the noise gate in `injectNoise` against regression at any level.
     */
    @Test
    fun silenceRemainsQuiet_atEveryLevel() {
        val input = ShortArray(FRAME_SIZE * 10) { 0 }
        for (level in intArrayOf(0, 25, 50, 75, 100)) {
            ProductionVoiceAnonymizer.setAnonymizationLevel(level)
            ProductionVoiceAnonymizer.reset()

            val rms = rms(ProductionVoiceAnonymizer.process(input))

            assertTrue("Silence at level $level produced RMS=$rms, expected < 100", rms < 100)
        }
    }

    /**
     * Replaces an earlier `higherLevelProducesMoreDifference` test, which asserted that
     * sum|input-output| grows with the anonymisation level. That expectation was invalid:
     * a pure tone is already fully decorrelated from its pitch-shifted self at level 25
     * (|r| ~ 0.018), so the metric saturates immediately, after which the raw sum is
     * dominated by output amplitude -- which *falls* as the level rises. The old test
     * therefore failed on correct, monotonic code.
     *
     * What that test meant to check is asserted here in two parts that are actually
     * measurable: the level -> strength mapping is monotonic, and every active level
     * substantially disguises the input.
     */
    @Test
    fun higherLevelProducesStrongerSettings() {
        val factors = intArrayOf(0, 25, 50, 75, 100).map { level ->
            ProductionVoiceAnonymizer.setAnonymizationLevel(level)
            level to ProductionVoiceAnonymizer.getPitchFactor()
        }

        // A lower pitch factor = a bigger shift away from the speaker's real pitch.
        for (i in 0 until factors.size - 1) {
            val (lowLevel, lowFactor) = factors[i]
            val (highLevel, highFactor) = factors[i + 1]
            assertTrue(
                "Level $highLevel pitch factor ($highFactor) should be a stronger shift " +
                    "than level $lowLevel ($lowFactor)",
                highFactor < lowFactor
            )
        }
    }

    @Test
    fun everyActiveLevelDisguisesTheInput() {
        val input = sineWave(150f, FRAME_SIZE * 10)
        for (level in intArrayOf(25, 50, 75, 100)) {
            ProductionVoiceAnonymizer.setAnonymizationLevel(level)
            ProductionVoiceAnonymizer.reset()

            val r = absCorrelation(input, ProductionVoiceAnonymizer.process(input))

            assertTrue(
                "Level $level left the output correlated with the input (|r|=$r)",
                r < 0.5
            )
        }
    }

    /**
     * The injected noise must stay a noise *floor*. Mutation testing showed that removing
     * the upper cap on the noise scale -- letting it track the frame level without bound --
     * went undetected: noise then rises to signal level and destroys intelligibility while
     * every other assertion still passed. Anonymising must never make the caller louder.
     */
    @Test
    fun anonymisationDoesNotAmplifyTheSignal() {
        val input = sineWave(150f, FRAME_SIZE * 10)
        val inputRms = rms(input)

        for (level in intArrayOf(25, 50, 75, 100)) {
            ProductionVoiceAnonymizer.setAnonymizationLevel(level)
            ProductionVoiceAnonymizer.reset()

            val outputRms = rms(ProductionVoiceAnonymizer.process(input))

            assertTrue(
                "Level $level raised output RMS to $outputRms from an input RMS of $inputRms; " +
                    "the injected noise is no longer a floor",
                outputRms <= inputRms * 1.2
            )
        }
    }

    @Test
    fun levelZeroIsPassThrough() {
        val input = sineWave(150f, FRAME_SIZE * 5)
        ProductionVoiceAnonymizer.setAnonymizationLevel(0)
        ProductionVoiceAnonymizer.reset()

        val output = ProductionVoiceAnonymizer.process(input)

        assertEquals("Level 0 must be pass-through", 0, countDifferent(input, output))
    }
}
