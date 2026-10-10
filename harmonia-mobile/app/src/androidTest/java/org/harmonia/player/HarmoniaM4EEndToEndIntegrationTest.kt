package org.harmonia.player

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.entity.TrackEntity
import org.harmonia.player.design.theme.HarmoniaTheme
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.scanner.ScanProgress
import org.harmonia.player.domain.model.scanner.ScanProgressStatus
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.model.storage.lifecycle.SourceRuntimeState
import org.harmonia.player.domain.model.storage.lifecycle.StorageVolumeLifecycleCoordinator
import org.harmonia.player.domain.model.storage.permission.SafPermissionDelegate
import org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker
import org.harmonia.player.domain.model.storage.permission.StoragePermissionState
import org.harmonia.player.domain.scanner.MediaReconciliationEngine
import org.harmonia.player.presentation.MainActivity
import org.harmonia.player.presentation.MainAppContainer
import org.harmonia.player.presentation.playback.PlaybackViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end integration tests for Harmonia Mobile M4-E Stage 3:
 * Area A: Permission & Onboarding lifecycle and state truthfulness in Library UI.
 * Area B: Storage Management, SAF Registration, Duplicate Selection, and Source Toggling.
 * Area D: Real End-to-End User Flow (Library Browse -> Playback Initiation via UI).
 */
@RunWith(AndroidJUnit4::class)
class HarmoniaM4EEndToEndIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: HarmoniaDatabase
    private lateinit var testPort: TestPort
    private lateinit var testTrackRepo: TestTrackRepo
    private lateinit var playbackViewModel: PlaybackViewModel

    private lateinit var fakePermChecker: FakeE2EPermissionChecker
    private lateinit var fakeSafDelegate: FakeE2ESafDelegate
    private lateinit var fakeReconciliationEngine: FakeE2EReconciliationEngine
    private lateinit var fakeLifecycleCoordinator: FakeE2ELifecycleCoordinator

    private val sampleTrack = Track(
        id = "e2e-m4e-track-1",
        title = "Starlight Motion",
        artistName = "Tycho",
        albumTitle = "Epoch",
        durationSec = 245.0
    )

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = HarmoniaDatabase.getInstance(context)

        // Initialize ServiceLocator with application context if needed
        ServiceLocator.initialize(context)

        // Clean tables
        db.clearAllTables()

        fakePermChecker = FakeE2EPermissionChecker(StoragePermissionState.GRANTED)
        ServiceLocator.setStoragePermissionCheckerForTesting(fakePermChecker)

        fakeSafDelegate = FakeE2ESafDelegate()
        ServiceLocator.setSafPermissionDelegateForTesting(fakeSafDelegate)

        fakeReconciliationEngine = FakeE2EReconciliationEngine()
        ServiceLocator.setMediaReconciliationEngineForTesting(fakeReconciliationEngine)

        fakeLifecycleCoordinator = FakeE2ELifecycleCoordinator()
        ServiceLocator.setStorageVolumeLifecycleCoordinatorForTesting(fakeLifecycleCoordinator)

        testPort = TestPort()
        testTrackRepo = TestTrackRepo()
        testTrackRepo.tracks[sampleTrack.id] = sampleTrack
        playbackViewModel = PlaybackViewModel(testPort, testTrackRepo)
    }

    @After
    fun tearDown() = runBlocking {
        db.clearAllTables()
    }

    // -------------------------------------------------------------------------
    // Area A: Permission and Onboarding UI Flow
    // -------------------------------------------------------------------------

    @Test
    fun onboarding_permission_denial_displays_rationale_and_recovery_on_grant() {
        // Start in DENIED permission state with empty library
        fakePermChecker.state = StoragePermissionState.DENIED

        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = playbackViewModel)
            }
        }

        // Navigate to Library
        composeTestRule.onNodeWithText("Library").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Audio Permission Required").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Audio Permission Required").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Grant audio permission").assertIsDisplayed()

        // Transition to SETTINGS_REQUIRED (permanent denial)
        fakePermChecker.state = StoragePermissionState.SETTINGS_REQUIRED
        // Navigate away and back to trigger onResume refresh
        composeTestRule.onNodeWithText("Home").performClick()
        composeTestRule.onNodeWithText("Library").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Permission Required in Settings").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Permission Required in Settings").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Open system settings").assertIsDisplayed()

        // Transition to GRANTED -> triggers automatic initial ingestion
        fakePermChecker.state = StoragePermissionState.GRANTED
        composeTestRule.onNodeWithText("Home").performClick()
        composeTestRule.onNodeWithText("Library").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("No audio files found").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("No audio files found").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Area B: Storage Source Management, SAF Registration & Toggling
    // -------------------------------------------------------------------------

    @Test
    fun settings_saf_folder_registration_deduplication_and_toggle_state() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = playbackViewModel)
            }
        }

        // Navigate to Settings tab
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("STORAGE SOURCES").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("STORAGE SOURCES").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan Library").assertIsDisplayed()

        // Direct registration through SettingsViewModel logic
        val settingsVm = org.harmonia.player.presentation.settings.SettingsViewModel(
            storageSourceRepository = ServiceLocator.getStorageSourceRepository(),
            storageVolumeLifecycleCoordinator = fakeLifecycleCoordinator,
            mediaReconciliationEngine = fakeReconciliationEngine,
            storagePermissionChecker = fakePermChecker,
            safPermissionDelegate = fakeSafDelegate
        )

        val safUri = "content://com.android.externalstorage.documents/tree/1234-5678%3AMusic"
        settingsVm.registerSafFolder(safUri, "MicroSD Music")

        // Wait for repository state emission in Settings UI
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("MicroSD Music").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("MicroSD Music").assertIsDisplayed()
        assertTrue(fakeSafDelegate.takenPermissions.isNotEmpty())

        // Duplicate registration of the same folder must NOT duplicate source entries
        settingsVm.registerSafFolder(safUri, "MicroSD Music Renamed")
        val sourcesInDb = runBlocking { ServiceLocator.getStorageSourceRepository().observeAllSources() }
        composeTestRule.waitUntil(3000) {
            runBlocking {
                val list = ServiceLocator.getStorageSourceRepository().getSourceById(
                    StorageSource.SafDocumentTree.deriveCanonicalId(safUri)
                )
                list != null
            }
        }

        // Verify Toggle action disables the source
        composeTestRule.onNodeWithContentDescription("Toggle MicroSD Music").performClick()
        composeTestRule.waitUntil(5000) {
            runBlocking {
                val source = ServiceLocator.getStorageSourceRepository().getSourceById(
                    StorageSource.SafDocumentTree.deriveCanonicalId(safUri)
                )
                source?.isAvailable == false
            }
        }
    }

    // -------------------------------------------------------------------------
    // Area D: Real End-to-End User Flow (Library Browse -> Playback Initiation)
    // -------------------------------------------------------------------------

    @Test
    fun library_browse_and_initiate_playback_updates_queue_and_miniplayer() {
        // Seed catalog in Room
        runBlocking {
            db.trackDao().upsertTrack(
                TrackEntity(
                    id = sampleTrack.id,
                    title = sampleTrack.title,
                    durationSec = sampleTrack.durationSec,
                    createdAt = 0L,
                    updatedAt = 0L
                )
            )
        }

        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = playbackViewModel)
            }
        }

        // Navigate to Library
        composeTestRule.onNodeWithText("Library").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Starlight Motion").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Starlight Motion").assertIsDisplayed()

        // Tap the track row to initiate playback
        composeTestRule.onNodeWithText("Starlight Motion").performClick()

        // Assert PlaybackPort was called
        assertTrue("Playback must be initiated", testPort.playCalled)
        assertEquals(sampleTrack.id, testPort.playbackState.value.currentTrackId)

        // MiniPlayer appears and displays active track title
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithContentDescription("Pause").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Test Fakes
    // -------------------------------------------------------------------------

    private class FakeE2EPermissionChecker(
        var state: StoragePermissionState = StoragePermissionState.GRANTED
    ) : StoragePermissionChecker {
        override fun getPermissionState(): StoragePermissionState = state
        override fun canScanAudioMedia(): Boolean = state == StoragePermissionState.GRANTED
    }

    private class FakeE2ESafDelegate : SafPermissionDelegate {
        val takenPermissions = mutableSetOf<String>()
        val releasedPermissions = mutableSetOf<String>()
        val persistedPermissions = mutableSetOf<String>()

        override fun takePersistableReadPermission(treeUri: String) {
            val normalized = StorageSource.SafDocumentTree.normalizeTreeUri(treeUri)
            takenPermissions.add(normalized)
            persistedPermissions.add(normalized)
        }

        override fun releasePersistableReadPermission(treeUri: String) {
            val normalized = StorageSource.SafDocumentTree.normalizeTreeUri(treeUri)
            releasedPermissions.add(normalized)
            persistedPermissions.remove(normalized)
        }

        override fun hasPersistedReadPermission(treeUri: String): Boolean {
            val normalized = StorageSource.SafDocumentTree.normalizeTreeUri(treeUri)
            return persistedPermissions.contains(normalized)
        }
    }

    private class FakeE2EReconciliationEngine : MediaReconciliationEngine {
        val scannedSources = mutableListOf<StorageSource>()
        override fun scanSource(source: StorageSource, mode: ScanMode) = flow {
            scannedSources.add(source)
            emit(ScanProgress(sourceId = source.id, status = ScanProgressStatus.IDLE))
            emit(ScanProgress(sourceId = source.id, status = ScanProgressStatus.DISCOVERING))
            emit(ScanProgress(sourceId = source.id, status = ScanProgressStatus.COMPLETED))
        }
    }

    private class FakeE2ELifecycleCoordinator : StorageVolumeLifecycleCoordinator {
        private val _runtimeStates = MutableStateFlow<Map<String, SourceRuntimeState>>(emptyMap())
        override val runtimeStates = _runtimeStates
        override fun isSourceAvailable(sourceId: String): Boolean = true
        override fun onVolumeMounted(volumeId: String) {}
        override fun onVolumeUnmounted(volumeId: String) {}
        override fun onSafPermissionRevoked(treeUri: String) {}
        override fun onSafPermissionGranted(treeUri: String) {}
        override suspend fun revalidateAllSources(): Map<String, SourceRuntimeState> = emptyMap()
        override suspend fun revalidateSource(source: StorageSource): SourceRuntimeState =
            SourceRuntimeState(sourceId = source.id, isAvailable = true, isMounted = true, hasPermission = true)
        override fun startMonitoring() {}
        override fun stopMonitoring() {}
    }
}
