package org.harmonia.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.OfflineListeningEventRepository
import org.harmonia.player.data.local.OfflineLocalFileRepository
import org.harmonia.player.data.local.OfflinePlaybackStateRepository
import org.harmonia.player.data.local.OfflineTrackRepository
import org.harmonia.player.data.playback.HarmoniaPlayerController
import org.harmonia.player.domain.model.LocalFile
import org.harmonia.player.domain.model.PlaybackStatus
import org.harmonia.player.domain.model.StorageType
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.model.storage.lifecycle.SourceRuntimeState
import org.harmonia.player.domain.model.storage.lifecycle.StorageVolumeLifecycleCoordinator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Instrumented integration test for storage-disconnection playback handling.
 * Exercises live Media3 ExoPlayer, Room database, and HarmoniaPlayerController
 * across storage lifecycle events on Android.
 *
 * Verifies:
 * 1. Storage disconnection pauses active playback promptly.
 * 2. Truthful error indication "Storage volume disconnected" is exposed.
 * 3. Playback context (track ID, file ID, position, queue) is preserved.
 * 4. Remounting storage does NOT auto-resume playback.
 * 5. Explicit user Play action resumes playback and clears error.
 * 6. Disconnecting an unrelated storage source does not interrupt active playback.
 */
@RunWith(AndroidJUnit4::class)
class StorageVolumeLifecycleIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: HarmoniaDatabase
    private lateinit var musicDir: File
    private var player: ExoPlayer? = null
    private var controller: HarmoniaPlayerController? = null
    private var testScope: CoroutineScope? = null

    private lateinit var coordinator: SimulatedStorageVolumeLifecycleCoordinator

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = HarmoniaDatabase.buildInMemory(context)
        musicDir = File(context.filesDir, "Music").apply { mkdirs() }

        extractAssetIfNeeded("test_track.mp3")

        coordinator = SimulatedStorageVolumeLifecycleCoordinator()
    }

    private fun extractAssetIfNeeded(filename: String) {
        val targetFile = File(musicDir, filename)
        if (!targetFile.exists() || targetFile.length() == 0L) {
            val testContext = InstrumentationRegistry.getInstrumentation().context
            testContext.assets.open(filename).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }

    @After
    fun tearDown() = runBlocking(Dispatchers.Main) {
        controller?.release()
        controller = null
        player?.release()
        player = null
        testScope?.cancel()
        testScope = null
        db.close()
    }

    private fun createAudioConfiguredPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    @Test
    fun testStorageDisconnectionDuringPlaybackPausesAndPreventsAutoResume() = runBlocking(Dispatchers.Main) {
        val mp3File = File(musicDir, "test_track.mp3")
        assertTrue("MP3 test file must exist", mp3File.exists())

        val sourceId = "sdcard_vol_test"
        coordinator.setSourceState(sourceId, isAvailable = true)

        val track = Track(
            id = "track-sd-1",
            title = "SD Card Track",
            artistName = "Harmonia Artist",
            albumTitle = "Removable Album",
            durationSec = 2.0
        )
        val localFile = LocalFile(
            id = "file-sd-1",
            trackId = track.id,
            sourceId = sourceId,
            storageType = StorageType.EXTERNAL_SD,
            fileUri = mp3File.absolutePath,
            fileSize = mp3File.length(),
            fileHash = "hash-sd-1",
            format = "mp3"
        )

        val trackRepo = OfflineTrackRepository(db.trackDao())
        val fileRepo = OfflineLocalFileRepository(db.localFileDao())
        val stateRepo = OfflinePlaybackStateRepository(db.playbackStateDao())
        val eventRepo = OfflineListeningEventRepository(db.listeningEventDao())

        trackRepo.upsertTracks(listOf(track))
        fileRepo.upsertLocalFile(localFile)

        val exoPlayer = createAudioConfiguredPlayer()
        player = exoPlayer

        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        testScope = scope

        val playerController = HarmoniaPlayerController(
            context = context,
            playbackStateRepository = stateRepo,
            listeningEventRepository = eventRepo,
            trackRepository = trackRepo,
            localFileRepository = fileRepo,
            storageVolumeLifecycleCoordinator = coordinator,
            scope = scope,
            playerOverride = exoPlayer,
            enablePositionPolling = true
        )
        controller = playerController

        // Load queue and start playback
        playerController.setQueue(listOf(track), 0)
        playerController.play()

        // Wait until ExoPlayer enters PLAYING state
        val started = withTimeoutOrNull(5000L) {
            while (!playerController.playbackState.value.isPlaying) {
                delay(50L)
            }
            true
        }
        assertTrue("Playback must start and enter isPlaying", started == true)

        // Allow playback to advance slightly
        delay(300L)
        assertTrue(playerController.playbackState.value.currentPositionMs > 0)
        val positionBeforeDisconnect = playerController.playbackState.value.currentPositionMs

        // Step 1: Simulate storage volume disconnection
        coordinator.setSourceState(sourceId, isAvailable = false)

        // Wait until playerController pauses and exposes "Storage volume disconnected"
        val paused = withTimeoutOrNull(5000L) {
            while (playerController.playbackState.value.isPlaying ||
                playerController.playbackState.value.errorMessage != HarmoniaPlayerController.STORAGE_DISCONNECTED_MESSAGE) {
                delay(50L)
            }
            true
        }
        assertTrue("Controller must pause and expose storage disconnected error", paused == true)

        // Verify context preserved
        assertEquals("track-sd-1", playerController.playbackState.value.currentTrackId)
        assertEquals("file-sd-1", playerController.playbackState.value.currentLocalFileId)
        assertTrue(playerController.playbackState.value.currentPositionMs >= positionBeforeDisconnect)
        assertEquals(PlaybackStatus.PAUSED, playerController.playbackState.value.status)
        assertEquals(HarmoniaPlayerController.STORAGE_DISCONNECTED_MESSAGE, playerController.playbackState.value.errorMessage)
        assertFalse(exoPlayer.playWhenReady)

        // Step 2: Simulate remount (volume returns to available)
        coordinator.setSourceState(sourceId, isAvailable = true)
        delay(500L)

        // Strict auto-resume invariant: player must remain paused
        assertFalse("Player must NOT auto-resume when volume remounts", playerController.playbackState.value.isPlaying)
        assertFalse("playWhenReady must remain false", exoPlayer.playWhenReady)
        assertEquals(PlaybackStatus.PAUSED, playerController.playbackState.value.status)
        assertEquals(HarmoniaPlayerController.STORAGE_DISCONNECTED_MESSAGE, playerController.playbackState.value.errorMessage)

        // Step 3: Explicit user Play action resumes playback
        playerController.play()

        val resumed = withTimeoutOrNull(5000L) {
            while (!playerController.playbackState.value.isPlaying) {
                delay(50L)
            }
            true
        }
        assertTrue("Explicit Play must resume playback", resumed == true)
        assertNull("Error message must be cleared upon explicit play", playerController.playbackState.value.errorMessage)
    }

    @Test
    fun testUnrelatedStorageDisconnectionDoesNotInterruptActivePlayback() = runBlocking(Dispatchers.Main) {
        val mp3File = File(musicDir, "test_track.mp3")
        val activeSourceId = "primary_internal_source"
        val unrelatedSourceId = "unrelated_usb_source"

        coordinator.setSourceState(activeSourceId, isAvailable = true)
        coordinator.setSourceState(unrelatedSourceId, isAvailable = true)

        val track = Track(
            id = "track-internal-1",
            title = "Internal Track",
            artistName = "Harmonia Artist",
            albumTitle = "Internal Album",
            durationSec = 2.0
        )
        val localFile = LocalFile(
            id = "file-internal-1",
            trackId = track.id,
            sourceId = activeSourceId,
            storageType = StorageType.INTERNAL_SYNC,
            fileUri = mp3File.absolutePath,
            fileSize = mp3File.length(),
            fileHash = "hash-internal-1",
            format = "mp3"
        )

        val trackRepo = OfflineTrackRepository(db.trackDao())
        val fileRepo = OfflineLocalFileRepository(db.localFileDao())
        val stateRepo = OfflinePlaybackStateRepository(db.playbackStateDao())
        val eventRepo = OfflineListeningEventRepository(db.listeningEventDao())

        trackRepo.upsertTracks(listOf(track))
        fileRepo.upsertLocalFile(localFile)

        val exoPlayer = createAudioConfiguredPlayer()
        player = exoPlayer

        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        testScope = scope

        val playerController = HarmoniaPlayerController(
            context = context,
            playbackStateRepository = stateRepo,
            listeningEventRepository = eventRepo,
            trackRepository = trackRepo,
            localFileRepository = fileRepo,
            storageVolumeLifecycleCoordinator = coordinator,
            scope = scope,
            playerOverride = exoPlayer,
            enablePositionPolling = true
        )
        controller = playerController

        playerController.setQueue(listOf(track), 0)
        playerController.play()

        val started = withTimeoutOrNull(5000L) {
            while (!playerController.playbackState.value.isPlaying) {
                delay(50L)
            }
            true
        }
        assertTrue("Playback must start", started == true)

        // Disconnect UNRELATED source
        coordinator.setSourceState(unrelatedSourceId, isAvailable = false)
        delay(400L)

        // Verify active playback continues without interruption
        assertTrue("Playback must continue uninterrupted when unrelated source disconnects", playerController.playbackState.value.isPlaying)
        assertNull("Error message must remain null", playerController.playbackState.value.errorMessage)
    }

    class SimulatedStorageVolumeLifecycleCoordinator : StorageVolumeLifecycleCoordinator {
        private val _states = MutableStateFlow<Map<String, SourceRuntimeState>>(emptyMap())
        override val runtimeStates: StateFlow<Map<String, SourceRuntimeState>> = _states

        fun setSourceState(sourceId: String, isAvailable: Boolean) {
            val current = _states.value.toMutableMap()
            current[sourceId] = SourceRuntimeState(
                sourceId = sourceId,
                isAvailable = isAvailable,
                isMounted = isAvailable,
                hasPermission = true
            )
            _states.value = current
        }

        override fun isSourceAvailable(sourceId: String): Boolean =
            _states.value[sourceId]?.isAvailable ?: true

        override fun onVolumeMounted(volumeId: String) {}
        override fun onVolumeUnmounted(volumeId: String) {}
        override fun onSafPermissionRevoked(treeUri: String) {}
        override fun onSafPermissionGranted(treeUri: String) {}
        override suspend fun revalidateAllSources(): Map<String, SourceRuntimeState> = _states.value
        override suspend fun revalidateSource(source: StorageSource): SourceRuntimeState =
            _states.value[source.id] ?: SourceRuntimeState(source.id, true, true, true)
        override suspend fun awaitInitialReadiness(): Boolean = true
        override fun startMonitoring() {}
        override fun stopMonitoring() {}
    }
}
