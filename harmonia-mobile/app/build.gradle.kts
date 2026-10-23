import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.legacy.kapt)
}

android {
    namespace = "org.harmonia.player"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.harmonia.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.0"
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    // ── Release Signing Configuration (M-REL.1) ───────────────────────────────
    val envKeystorePath: String? = System.getenv("HARMONIA_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
    val envKeystorePassword: String? = System.getenv("HARMONIA_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
    val envKeyAlias: String? = System.getenv("HARMONIA_KEY_ALIAS")?.takeIf { it.isNotBlank() }
    val envKeyPassword: String? = System.getenv("HARMONIA_KEY_PASSWORD")?.takeIf { it.isNotBlank() }

    // Optional local keystore.properties fallback (gitignored)
    val keystoreProps = Properties()
    val propsFile = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?: project.file("keystore.properties").takeIf { it.exists() }
    if (propsFile != null) {
        FileInputStream(propsFile).use { stream ->
            keystoreProps.load(stream)
        }
    }

    fun getProp(key: String): String? {
        val value: String? = keystoreProps.getProperty(key)
        return if (!value.isNullOrBlank()) value else null
    }

    val resolvedKeystorePath: String? = envKeystorePath
        ?: getProp("HARMONIA_KEYSTORE_PATH")
        ?: getProp("storeFile")

    val resolvedKeystorePassword: String? = envKeystorePassword
        ?: getProp("HARMONIA_KEYSTORE_PASSWORD")
        ?: getProp("storePassword")

    val resolvedKeyAlias: String? = envKeyAlias
        ?: getProp("HARMONIA_KEY_ALIAS")
        ?: getProp("keyAlias")

    val resolvedKeyPassword: String? = envKeyPassword
        ?: getProp("HARMONIA_KEY_PASSWORD")
        ?: getProp("keyPassword")

    val isExplicitSigningRequested = System.getenv("HARMONIA_REQUIRE_SIGNING")?.toBooleanStrictOrNull() == true ||
        (project.hasProperty("requireSigning") && project.property("requireSigning").toString().toBoolean()) ||
        (project.hasProperty("harmoniaRequireSigning") && project.property("harmoniaRequireSigning").toString().toBoolean())

    val hasAnySigningInput = resolvedKeystorePath != null ||
        resolvedKeystorePassword != null ||
        resolvedKeyAlias != null ||
        resolvedKeyPassword != null

    // Validation: when signing is explicitly requested or partial inputs are provided
    if (isExplicitSigningRequested || hasAnySigningInput) {
        val missingInputs = mutableListOf<String>()
        if (resolvedKeystorePath == null) missingInputs.add("HARMONIA_KEYSTORE_PATH")
        if (resolvedKeystorePassword == null) missingInputs.add("HARMONIA_KEYSTORE_PASSWORD")
        if (resolvedKeyAlias == null) missingInputs.add("HARMONIA_KEY_ALIAS")
        if (resolvedKeyPassword == null) missingInputs.add("HARMONIA_KEY_PASSWORD")

        if (missingInputs.isNotEmpty()) {
            throw GradleException(
                "Release signing was requested or partially configured, but the following required inputs are missing: " +
                    missingInputs.joinToString(", ") +
                    ". All four inputs (HARMONIA_KEYSTORE_PATH, HARMONIA_KEYSTORE_PASSWORD, HARMONIA_KEY_ALIAS, HARMONIA_KEY_PASSWORD) are required to produce a signed release build."
            )
        }

        val keystoreFile = file(resolvedKeystorePath!!)
        if (!keystoreFile.exists()) {
            throw GradleException(
                "Release keystore file specified by HARMONIA_KEYSTORE_PATH does not exist: ${keystoreFile.absolutePath}"
            )
        }
    }

    val isSigningConfigured = resolvedKeystorePath != null &&
        resolvedKeystorePassword != null &&
        resolvedKeyAlias != null &&
        resolvedKeyPassword != null &&
        file(resolvedKeystorePath).exists()

    signingConfigs {
        if (isSigningConfigured) {
            create("release") {
                storeFile = file(resolvedKeystorePath!!)
                storePassword = resolvedKeystorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (isSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = null
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    testOptions {
      unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Room Database
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  kapt(libs.androidx.room.compiler)

  // Media3 Playback Engine (ExoPlayer + MediaSession)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.common)
}

tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask>().configureEach {
    val tmpDir = layout.buildDirectory.dir("tmp").get().asFile
    tmpDir.mkdirs()
    kaptProcessJvmArgs.add("-Dorg.sqlite.tmpdir=${tmpDir.absolutePath.replace('\\', '/')}")
    kaptProcessJvmArgs.add("-Djava.io.tmpdir=${tmpDir.absolutePath.replace('\\', '/')}")
}

