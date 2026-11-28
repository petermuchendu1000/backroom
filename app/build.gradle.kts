import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

// ─────────────────────────────────────────────────────────────────────────────
// Release signing
// ─────────────────────────────────────────────────────────────────────────────
// Values come from keystore.properties at the repository root, or from the matching
// environment variables so CI can sign without a file on disk. Both are gitignored /
// absent from the repo: a keystore password in version control is the key itself.
//
// Losing the keystore is unrecoverable. Android identifies an app by its signature, so
// a replacement key cannot update an existing install — users would have to uninstall,
// losing their data, and the Play Store would refuse the upload outright. Back it up
// somewhere that is not this laptop.
//
//     keystore.properties
//     ───────────────────
//     storeFile=/absolute/path/to/ongero-release.jks
//     storePassword=...
//     keyAlias=ongero
//     keyPassword=...
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(key: String, env: String): String? =
    (keystoreProperties.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val releaseKeystore = signingValue("storeFile", "ONGERO_KEYSTORE")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }

// Without a keystore, `assembleRelease` still succeeds and emits an APK no device will
// install — a green build with a useless artifact. Say so, but only when a release task
// was actually asked for, so debug builds stay quiet.
if (releaseKeystore == null &&
    gradle.startParameter.taskNames.any { it.contains("elease") }
) {
    logger.warn(
        "WARNING: no release keystore configured — this build will be UNSIGNED and " +
            "cannot be installed. See docs/11_RELEASE_AND_DISTRIBUTION.md.",
    )
}

android {
    namespace = "com.ongero.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.ongero.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Redesigned UI, off by default. The nav graph keeps the same routes either way,
        // so only the composable behind a route changes. Build the new flow with:
        //     ./gradlew installDebug -PredesignUi=true
        buildConfigField(
            "boolean",
            "REDESIGN_UI",
            (project.findProperty("redesignUi")?.toString()?.toBoolean() ?: false).toString(),
        )

        // Point a DEBUG build at the production backend:
        //     ./gradlew installDebug -PprodBackend=true
        //
        // NetworkConfig picks its URLs from BuildConfig.DEBUG, so a debug build otherwise
        // talks to DEV_SERVER_IP — a LAN address that is right for a laptop on the same
        // WiFi and useless for testing against api.ongero.com. A release build is always
        // production, but `release` here has no signingConfig, so assembleRelease yields
        // an unsigned APK that cannot be installed. This flag is the only way to get a
        // installable build that reaches production, and it keeps logcat, which is what a
        // push-notification test is read through.
        buildConfigField(
            "boolean",
            "USE_PROD_BACKEND",
            (project.findProperty("prodBackend")?.toString()?.toBoolean() ?: false).toString(),
        )
    }

    signingConfigs {
        // Created only when a keystore is actually present. Declaring it unconditionally
        // with a null storeFile fails the build for everyone who just wants installDebug.
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingValue("storePassword", "ONGERO_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "ONGERO_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "ONGERO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // null when no keystore is configured, which leaves the previous behaviour
            // (an unsigned APK) rather than breaking the build. The warning above is
            // what tells you about it.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // ProductionVoiceAnonymizer is pure DSP whose only Android dependency is
        // android.util.Log. Returning default values for the android.jar stubs lets its
        // tests run as fast JVM unit tests instead of requiring a device/emulator.
        unitTests {
            isReturnDefaultValues = true
            // Robolectric renders the redesign screens off-device; it needs real resources.
            isIncludeAndroidResources = true
        }
    }

    // Enable 16 KB page size support for Android 15+
    packaging {
        jniLibs {
            // Use the first occurrence of native libs (needed for 16KB alignment)
            pickFirsts += listOf(
                "lib/arm64-v8a/libjingle_peerconnection_so.so",
                "lib/x86_64/libjingle_peerconnection_so.so",
                "lib/armeabi-v7a/libjingle_peerconnection_so.so",
                "lib/x86/libjingle_peerconnection_so.so"
            )
            // Keep native libs uncompressed for better 16KB compatibility
            useLegacyPackaging = false
        }
    }
}

// The screenshot tests render every redesigned screen to a PNG. They are an authoring
// tool, not a regression gate: they are slow and Robolectric fetches an android-all jar
// on first run. The normal `testDebugUnitTest` therefore skips the package entirely —
// filtered out, not @Ignore'd, so they are never reported as skipped tests.
// Run them with:  ./gradlew :app:testDebugUnitTest -Pscreenshots
tasks.withType<Test>().configureEach {
    if (!project.hasProperty("screenshots")) {
        exclude("**/screenshot/**")
    } else {
        systemProperty("roborazzi.test.record", "true")
    }
    // Lets a caller point Robolectric at a Maven mirror when the default is rate-limited.
    System.getProperty("robolectric.dependency.repo.url")?.let {
        systemProperty("robolectric.dependency.repo.url", it)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // ViewModel & Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // WebRTC.
    //
    // This was io.getstream:stream-webrtc-android:1.3.10, chosen — per the comment that
    // used to be here — for "AudioRecordDataCallback support". That support does not
    // exist in it. JavaAudioDeviceModule.Builder declares the field and the setter, so
    // the app compiles, but build() constructs WebRtcAudioRecord without passing it and
    // WebRtcAudioRecord contains no reference to the type at all. Verified three ways:
    // the source at tag 1.3.10, the source at main, and `javap` over the shipped AAR,
    // whose constructor signature ends (..., SamplesReadyCallback, boolean, boolean).
    //
    // The consequence was not a missing feature. ProductionVoiceAnonymizer never ran on
    // live audio, so every call transmitted the speaker's real, unmodified voice — in an
    // app whose central promise is that it does not.
    //
    // This build invokes JavaAudioDeviceModule.AudioBufferCallback.onBuffer() on the real
    // capture buffer, before the data reaches native: in WebRtcAudioRecord$AudioRecordThread
    // the mute check sits at bytecode offset 537, onBuffer at 741 and nativeDataIsRecorded
    // at 787. In-place modification there is what actually reaches the far end.
    implementation("io.github.webrtc-sdk:android:150.7871.09")

    // WebSocket & Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Firebase (FCM)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // A real HTTP server in-process, so the API client is tested against sockets and
    // bytes rather than against a mock of OkHttp. Version pinned to the okhttp already
    // in the build: mockwebserver ships from the same release train and mixing them
    // pulls a second copy of okhttp onto the test classpath.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // Off-device rendering of the redesign screens. These tests live in
    // com.ongero.app.screenshot and are EXCLUDED from the normal test run
    // (see the Test task config below) because they are slow and only produce images.
    // Pinned to the last Roborazzi line built against Kotlin 1.9.x: newer releases ship
    // Kotlin 2.3 metadata, which this project's Kotlin 2.0.21 compiler refuses to read.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.40.1")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}