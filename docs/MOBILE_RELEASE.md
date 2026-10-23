# Harmonia Mobile — Release Engineering Guide

**Document Version:** 1.0
**Scope:** Android Mobile Application (`harmonia-mobile`)
**Target Milestone:** M-REL.8 Release Gate Closure

---

## 1. Architecture & Offline-First Scope

Harmonia Mobile is an offline-first, local-first audiophile music library, audio analysis, and playback player built with modern Android standards:

- **UI & Interaction:** Single-activity architecture (`MainActivity.kt`) built entirely in Jetpack Compose, implementing Harmonia Design System tokens (pure black Obsidian surfaces, High-contrast signal accents, WCAG AAA contrast ratios, and strict 48dp minimum touch targets).
- **Core Playback Engine:** Media3 / ExoPlayer 1.x pipeline running within a dedicated foreground service (`HarmoniaPlaybackService`) bound to Android `MediaLibraryService`, supporting gapless FLAC/ALAC/MP3/Opus playback, audio focus handling, and becoming-noisy interruption recovery.
- **Persistence:** Android Room Database (`HarmoniaDatabase`) utilizing SQLite with strict migration lifecycle management (V1 through V5).
- **Audio DSP & Equalizer:** Platform `android.media.audiofx.Equalizer` bridge with audio session ID tracking, band persistence, custom and factory presets, and decibel-formatted state descriptions.
- **Automotive Integration:** MediaSession-backed Android Auto browsing and voice-search dispatch via `MediaItemTreeResolver`.
- **Offline Invariant:** Zero network permissions (`android.permission.INTERNET` is absent from `AndroidManifest.xml`). All library scanning, metadata indexing, DSP manipulation, and user personalization operate 100% locally.

---

## 2. Build Variants: Debug vs. Minified Release

| Dimension | Debug Variant (`debug`) | Minified Release Variant (`release`) |
| :--- | :--- | :--- |
| **Gradle Task** | `:app:assembleDebug` | `:app:assembleRelease` / `:app:bundleRelease` |
| **Artifact Output** | `app/build/outputs/apk/debug/app-debug.apk` | `app/build/outputs/apk/release/app-release-unsigned.apk` (or `app-release.apk` if signed) |
| **Typical Size** | ~17.0 MB (unstripped symbols & tooling) | ~2.95 MB (R8 optimized, shrunk, and inlined) |
| **R8 Minification** | Disabled (`isMinifyEnabled = false`) | Enabled (`isMinifyEnabled = true`) |
| **ProGuard Rules** | Inactive | `proguard-android-optimize.txt` + `app/proguard-rules.pro` |
| **Signing** | Android default debug keystore | Strict signing gate (requires production keystore or outputs unsigned) |
| **Manifest Tooling**| Includes Compose test manifest & tooling | Stripped of test harnesses |

---

## 3. R8 / ProGuard Configuration

The production release build enforces R8 shrinking, obfuscation, and dead-code elimination. The following application-specific rules are defined in [`harmonia-mobile/app/proguard-rules.pro`](file:///harmonia-mobile/app/proguard-rules.pro):

```proguard
# Preserve Room entity class names while allowing member optimization.
-keep @androidx.room.Entity class *

# Preserve MediaLibrarySession callback implementations for framework dispatch.
-keepclassmembers class * implements androidx.media3.session.MediaLibraryService$MediaLibrarySession$Callback {
    *;
}
```

### Rationale:
1. **`-keep @androidx.room.Entity class *`**: Room maps SQLite tables to data class structures. Preserving entity class names guarantees schema compatibility across migrations and avoids runtime table reflection failures.
2. **`-keepclassmembers ... MediaLibrarySession$Callback`**: Media3 session events (playback commands, automotive browsing, search queries) are dispatched by framework IPC and internal reflection. Retaining these member signatures prevents R8 from inlining or obfuscating essential callback methods.

---

## 4. Release Signing Architecture & The Strict Signing Gate

Release signing is governed by [`harmonia-mobile/app/build.gradle.kts`](file:///harmonia-mobile/app/build.gradle.kts) and requires four environment variables:

```bash
export HARMONIA_KEYSTORE_PATH="/secure/path/to/harmonia-release.jks"
export HARMONIA_KEYSTORE_PASSWORD="<keystore_password>"
export HARMONIA_KEY_ALIAS="<key_alias>"
export HARMONIA_KEY_PASSWORD="<key_password>"
```

*(Alternatively, a local developer override can be placed in `harmonia-mobile/keystore.properties` or the root project, which is strictly gitignored).*

### The Signing Gate Logic:
- **Missing Keystore File:** If `HARMONIA_KEYSTORE_PATH` is set but points to a non-existent file, Gradle throws an explicit `GradleException`, preventing ambiguous builds.
- **Unconfigured Signing:** If any of the four environment variables are unset, `isSigningConfigured` evaluates to `false`, and `signingConfig` is set to `null`.
- **Guaranteed Safety:** When signing is unconfigured, Gradle builds an `app-release-unsigned.apk`. This guarantees that unsigned artifacts can never be confused with authentic releases and cannot be installed on end-user devices without explicit manual signing.
- **Never Check In Secrets:** Production keystore binaries and passwords must NEVER be committed to Git or stored in workspace scripts.

---

## 5. Standard Verification & Build Commands

All mobile commands must be executed from the `harmonia-mobile` directory.

### 5.1 Unit Tests (Local JVM)
```powershell
# Run the complete mobile unit test suite
.\gradlew.bat :app:testDebugUnitTest

# Run focused accessibility semantics suite
.\gradlew.bat :app:testDebugUnitTest --tests "org.harmonia.player.presentation.AccessibilitySemanticsFormattingTest"

# Run playback & interruption test suite
.\gradlew.bat :app:testDebugUnitTest --tests "org.harmonia.player.playback.*"
```

### 5.2 Build Artifacts
```powershell
# Assemble Debug APK (for development/testing)
.\gradlew.bat :app:assembleDebug

# Assemble Minified Release APK
.\gradlew.bat :app:assembleRelease

# Assemble Minified Release App Bundle (for Google Play distribution)
.\gradlew.bat :app:bundleRelease
```

### 5.3 Signature Verification (`apksigner`)
Using the Android SDK `apksigner` tool:
```powershell
# Locate apksigner in Android SDK build-tools
$APKSIGNER = "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat"

# Verify signature schemes (V1, V2, V3) and certificate validity
& $APKSIGNER verify --verbose "app/build/outputs/apk/release/app-release.apk"
```

---

## 6. Artifact Integrity & SHA-256 Checksums

Every release candidate and final release artifact must have its SHA-256 hash calculated and documented in an artifact manifest.

### Checksum Generation Commands:
- **Windows (PowerShell):**
  ```powershell
  Get-FileHash -Algorithm SHA256 app/build/outputs/apk/release/*.apk
  Get-FileHash -Algorithm SHA256 app/build/outputs/mapping/release/mapping.txt
  ```
- **Linux / macOS (Bash):**
  ```bash
  sha256sum app/build/outputs/apk/release/*.apk
  sha256sum app/build/outputs/mapping/release/mapping.txt
  ```

### Release Manifest Format (`RELEASE_MANIFEST.txt`):
```text
Harmonia Mobile Release Candidate Manifest
Build Date: YYYY-MM-DDTHH:MM:SSZ
Commit HEAD: <40-char-git-hash>
Version Name: 0.1.0-rc1
Version Code: 1
Build Variant: release (minified)

Artifacts:
<SHA-256>  app-release.apk
<SHA-256>  mapping.txt
```

---

## 7. Versioning Policy & Decision

### Current Repository State:
- `versionCode = 1`
- `versionName = "0.1.0"`

### Versioning Recommendation:
Harmonia Mobile adheres to **Semantic Versioning 2.0.0** (`MAJOR.MINOR.PATCH`) paired with a strictly monotonic integer `versionCode`:

1. **Development Baseline (`0.1.0` / `versionCode = 1`):**
   Maintained during pre-release feature qualification and local testing. The project version metadata is intentionally left at `0.1.0` during M-REL.6.1 to avoid superficial status changes without a formal release decision.
2. **Release Candidate (`0.1.0-rc1` / `versionCode = 10001`):**
   Formalized during M-REL.7 when all automated and runtime gates are completed and staged for final sign-off.
3. **First Production Release (`0.1.0` or `1.0.0` / `versionCode = 10002`):**
   Tagged at M-REL.8 upon signing with the production keystore and publication of the release manifest.

---

## 8. Database Migrations & Persistence Lifecycle

Harmonia Mobile tracks database schema migrations via Room in `app/schemas/org.harmonia.player.data.local.HarmoniaDatabase/`:
- **V1 $\to$ V2:** Adds provenance and track metadata indexing.
- **V2 $\to$ V3:** Adds external SD card and SAF document storage source tracking.
- **V3 $\to$ V4:** Introduces source association foreign keys.
- **V4 $\to$ V5:** Adds personalization tables (favorites, 5-star ratings, custom playlists).

Automated verification tests are located in:
- `RoomMigrationAndPlaylistDaoTest.kt`
- `RoomMigrationV2ToV3AndStorageDaoTest.kt`
- `RoomMigrationV3ToV4AndSourceAssociationTest.kt`
- `RoomMigrationV4ToV5AndPersonalizationDaoTest.kt`

---

## 9. Current Test Evidence Baseline

*(As verified and recorded on 2026-10-22)*:

- **Unit Test Suite:** `:app:testDebugUnitTest`
  - **Status:** **PASS**
  - **Suites:** 40 test classes
  - **Tests Run:** **386 total tests, 0 failures, 0 errors, 0 skipped**
- **Accessibility Semantics:** `AccessibilitySemanticsFormattingTest.kt`
  - **Status:** **PASS** (13/13 tests passing covering shuffle state transitions, repeat modes, millibel-to-decibel boundaries, and 48dp touch targets).
- **Minified Release Runtime:**
  - **Status:** **PASS (Emulator)**
  - Qualified on Android 16 (`medium_phone` emulator) in M-REL.3.2 and M-REL.5.1 with successful Room initialization, background playback, and mini-player interactions.

---

## 10. Known Hardware Boundaries & External Disclosures

The following areas require external hardware or interactive tooling and cannot be qualified purely within a headless, offline development environment:

1. **Interactive TalkBack Spoken Output:**
   Compose semantics and state descriptions are 100% verified via automated assertions. However, real-time speech pacing, auditory feedback, and gesture traversal require a connected emulator or physical device with TalkBack enabled.
2. **Real Doze & Deep-Sleep Transitions:**
   The sleep timer's monotonic clock countdown and 30-second volume fade are verified deterministically with virtual time dispatchers. Real-world validation of wake-lock releases during Android Deep Doze requires physical device battery-unplug simulation (`dumpsys deviceidle force-idle`).
3. **Android Auto Desktop Head Unit (DHU):**
   MediaSession service interfaces and media item trees are 100% verified. Live head-unit rendering requires installing the proprietary Google DHU SDK tool (`desktop-head-unit.exe`).
4. **Physical Audio DSP & Bluetooth HAL:**
   Platform equalizer binding and settings persistence are fully tested. Hardware DAC biquad filtering, buffer underruns, and Bluetooth A2DP codec negotiation require physical phone hardware.
5. **Production Signing Credentials:**
   By security design, production signing keys are not checked into source control and must be provided via the CI/CD secret manager or release operator.
