plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.backroom"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.backroom"
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
    }

    buildTypes {
        release {
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

    // WebRTC - using latest version with AudioRecordDataCallback support
    implementation("io.getstream:stream-webrtc-android:1.3.10")

    // WebSocket & Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Firebase (FCM)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    testImplementation(libs.junit)

    // Off-device rendering of the redesign screens. These tests live in
    // com.example.backroom.screenshot and are EXCLUDED from the normal test run
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