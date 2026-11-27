// Generates the Baseline Profile for :app and measures cold start with and without it (task 3.11).
//   Generate:  ./gradlew :app:generateBaselineProfile        (needs a device or emulator, API 28+)
//   Measure:   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
plugins {
  alias(libs.plugins.android.test)
  alias(libs.plugins.baselineprofile)
}

android {
    namespace = "org.harmonia.player.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Emulator numbers are not reliable for absolute timings; allow running there anyway.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,DEBUGGABLE"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.test.uiautomator)
  implementation(libs.androidx.benchmark.macro.junit4)
}
