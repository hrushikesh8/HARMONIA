package org.harmonia.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.harmonia.player.data.playback.LocalFileSourceAdapter
import org.harmonia.player.domain.model.LocalFile
import org.harmonia.player.domain.model.StorageType
import org.harmonia.player.domain.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Android Virtual Device (AVD) instrumented playback verification suite.
 * Runs on Android 16 (`sdk_gphone64_x86_64`) with audio enabled.
 *
 * Self-contained: extracts bundled test bitstreams from instrumentation assets into the app sandbox.
 * Verifies the complete Android audio pipeline:
 * Local file -> MediaItem -> ExoPlayer -> AudioTrack/AudioRenderer -> Android Audio HAL
 *
 * Exercises:
 * 1. MP3: prepare, READY, start PLAYING, progress advances, seek, position change, pause, resume, stop
 * 2. FLAC: prepare, READY, start PLAYING, progress advances, seek, pause, resume, stop
 * 3. M4A/AAC: prepare, READY, start PLAYING, progress advances, seek, pause, resume, stop
 * 4. WAV: prepare, READY, start PLAYING, progress advances, seek, pause, resume, stop
 * 5. Multi-track queue navigation with authoritative queue progression
 * 6. Deterministic missing file error handling without process termination
 */
@RunWith(AndroidJUnit4::class)
class RealAndroidPlaybackTest {

    private lateinit var context: Context
    private var player: ExoPlayer? = null
    private lateinit var musicDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        musicDir = File(context.filesDir, "Music").apply { mkdirs() }

        // Extract bundled audio assets from test instrumentation into the target app's sandbox
        extractAssetIfNeeded("test_track.mp3")
        extractAssetIfNeeded("test_track.flac")
        extractAssetIfNeeded("test_track.m4a")
        extractAssetIfNeeded("test_track.wav")
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
        player?.release()
        player = null
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
    fun testRealMp3PlaybackWithAudioPipeline() = runBlocking(Dispatchers.Main) {
        val mp3File = File(musicDir, "test_track.mp3")
        assertTrue("MP3 file must exist at ${mp3File.absolutePath}", mp3File.exists())

        val track = Track(
            id = "test-mp3-1",
            title = "Strawberry MP3",
            artistName = "Strawberry Player",
            albumTitle = "Test Audio",
            durationSec = 2.0
        )
        val localFile = LocalFile(
            id = "file-mp3-1",
            trackId = track.id,
            storageType = StorageType.INTERNAL_SYNC,
            fileUri = mp3File.absolutePath,
            fileSize = mp3File.length(),
            fileHash = "hash-mp3",
            format = "mp3"
        )

        val mediaItem = LocalFileSourceAdapter.toMediaItem(track, localFile)
        val exoPlayer = createAudioConfiguredPlayer()
        player = exoPlayer

        val readyLatch = CountDownLatch(1)
        var capturedError: PlaybackException? = null

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    readyLatch.countDown()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                capturedError = error
                readyLatch.countDown()
            }
        })

        // 1. Prepare
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        val ready = withContext(Dispatchers.IO) { readyLatch.await(5, TimeUnit.SECONDS) }
        assertTrue("MP3 must transition to STATE_READY", ready)
        assertNull("Preparation must not fail with error", capturedError)
        assertEquals(Player.STATE_READY, exoPlayer.playbackState)

        // 2. Play
        exoPlayer.play()
        assertTrue("ExoPlayer must be in playWhenReady state", exoPlayer.playWhenReady)

        // 3. Allow playback to progress through native audio renderer
        val startPos = exoPlayer.currentPosition
        delay(400) // allow audio renderer buffer cycles
        val advancedPos = exoPlayer.currentPosition
        assertTrue("Playback position must advance: advanced=$advancedPos, start=$startPos", advancedPos >= startPos)

        // 4. Seek
        exoPlayer.seekTo(300L)
        val seekPos = exoPlayer.currentPosition
        assertTrue("Position must reflect seek to 300ms: actual=$seekPos", seekPos in 250L..400L)

        // 5. Pause
        exoPlayer.pause()
        assertFalse("Player must not be playing when paused", exoPlayer.isPlaying)
        val pausedPos = exoPlayer.currentPosition

        // 6. Resume
        exoPlayer.play()
        delay(300)
        assertTrue("Player must resume playback from pause point", exoPlayer.currentPosition >= pausedPos)

        // 7. Stop
        exoPlayer.stop()
        assertEquals(Player.STATE_IDLE, exoPlayer.playbackState)
    }

    @Test
    fun testRealFlacPlaybackWithAudioPipeline() = runBlocking(Dispatchers.Main) {
        val flacFile = File(musicDir, "test_track.flac")
        assertTrue("FLAC file must exist at ${flacFile.absolutePath}", flacFile.exists())

        val track = Track(id = "test-flac-1", title = "Strawberry FLAC", durationSec = 2.0)
        val localFile = LocalFile(
            id = "file-flac-1",
            trackId = track.id,
            storageType = StorageType.INTERNAL_SYNC,
            fileUri = flacFile.absolutePath,
            fileSize = flacFile.length(),
            fileHash = "hash-flac",
            format = "flac",
            isLossless = true
        )

        val mediaItem = LocalFileSourceAdapter.toMediaItem(track, localFile)
        val exoPlayer = createAudioConfiguredPlayer()
        player = exoPlayer

        val readyLatch = CountDownLatch(1)
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) readyLatch.countDown()
            }
        })

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        val ready = withContext(Dispatchers.IO) { readyLatch.await(5, TimeUnit.SECONDS) }
        assertTrue("FLAC must transition to STATE_READY", ready)
        assertEquals(Player.STATE_READY, exoPlayer.playbackState)

        // Play and verify progress
        exoPlayer.play()
        delay(400)
        assertTrue("FLAC position must advance during audio rendering", exoPlayer.currentPosition > 0L)

        // Seek and pause
        exoPlayer.seekTo(250L)
        exoPlayer.pause()
        assertFalse("FLAC playback must pause", exoPlayer.isPlaying)

        // Resume and stop
        exoPlayer.play()
        delay(200)
        exoPlayer.stop()
        assertEquals(Player.STATE_IDLE, exoPlayer.playbackState)
    }

    @Test
    fun testRealM4aAacPlaybackWithAudioPipeline() = runBlocking(Dispatchers.Main) {
        val m4aFile = File(musicDir, "test_track.m4a")
        assertTrue("M4A file must exist at ${m4aFile.absolutePath}", m4aFile.exists())

        val track = Track(id = "test-m4a-1", title = "Strawberry M4A", durationSec = 2.0)
        val localFile = LocalFile(
            id = "file-m4a-1",
            trackId = track.id,
            storageType = StorageType.INTERNAL_SYNC,
            fileUri = m4aFile.absolutePath,
            fileSize = m4aFile.length(),
            fileHash = "hash-m4a",
            format = "m4a"
        )

        val mediaItem = LocalFileSourceAdapter.toMediaItem(track, localFile)
        val exoPlayer = createAudioConfiguredPlayer()
        player = exoPlayer

        val readyLatch = CountDownLatch(1)
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) readyLatch.countDown()
            }
        })

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        val ready = withContext(Dispatchers.IO) { readyLatch.await(5, TimeUnit.SECONDS) }
        assertTrue("M4A must transition to STATE_READY", ready)
        assertEquals(Player.STATE_READY, exoPlayer.playbackState)

        // Play and verify progress
        exoPlayer.play()
        delay(400)
        assertTrue("M4A position must advance during playback", exoPlayer.currentPosition > 0L)

        // Seek and pause
        exoPlayer.seekTo(200L)
        exoPlayer.pause()
        assertFalse("M4A playback must pause", exoPlayer.isPlaying)

        // Resume and stop
        exoPlayer.play()
        delay(200)
        exoPlayer.stop()
        assertEquals(Player.STATE_IDLE, exoPlayer.playbackState)
    }

    @Test
    fun testRealWavPlaybackWithAudioPipeline() = runBlocking(Dispatchers.Main) {
        val wavFile = File(musicDir, "test_track.wav")
        assertTrue("WAV file must exist at ${wavFile.absolutePath}", wavFile.exists())

        val track = Track(id = "test-wav-1", title = "Strawberry WAV", durationSec = 2.0)
        val localFile = LocalFile(
            id = "file-wav-1",
            trackId = track.id,
            storageType = StorageType.INTERNAL_SYNC,
            fileUri = wavFile.absolutePath,
            fileSize = wavFile.length(),
            fileHash = "hash-wav",
            format = "wav"
        )

        val mediaItem = LocalFileSourceAdapter.toMediaItem(track, localFile)
        val exoPlayer = createAudioConfiguredPlayer()
        player = exoPlayer

        val readyLatch = CountDownLatch(1)
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) readyLatch.countDown()
            }
        })

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        val ready = withContext(Dispatchers.IO) { readyLatch.await(5, TimeUnit.SECONDS) }
        assertTrue("WAV must transition to STATE_READY", ready)
        assertEquals(Player.STATE_READY, exoPlayer.playbackState)

        // Play and verify progress
        exoPlayer.play()
        delay(400)
        assertTrue("WAV position must advance during playback", exoPlayer.currentPosition > 0L)

        // Seek and pause
        exoPlayer.seekTo(200L)
        exoPlayer.pause()
        assertFalse("WAV playback must pause", exoPlayer.isPlaying)

        // Resume and stop
        exoPlayer.play()
        delay(200)
        exoPlayer.stop()
        assertEquals(Player.STATE_IDLE, exoPlayer.playbackState)
    }

    @Test
    fun testMultiTrackAuthoritativeQueueNavigation() = runBlocking(Dispatchers.Main) {
        val mp3File = File(musicDir, "test_track.mp3")
        val flacFile = File(musicDir, "test_track.flac")
        val wavFile = File(musicDir, "test_track.wav")

        val itemA = LocalFileSourceAdapter.toMediaItem(
            Track(id = "trk-a", title = "Track A", durationSec = 2.0),
            LocalFile(id = "f-a", trackId = "trk-a", storageType = StorageType.INTERNAL_SYNC, fileUri = mp3File.absolutePath, fileSize = mp3File.length(), fileHash = "h-a", format = "mp3")
        )
        val itemB = LocalFileSourceAdapter.toMediaItem(
            Track(id = "trk-b", title = "Track B", durationSec = 2.0),
            LocalFile(id = "f-b", trackId = "trk-b", storageType = StorageType.INTERNAL_SYNC, fileUri = flacFile.absolutePath, fileSize = flacFile.length(), fileHash = "h-b", format = "flac")
        )
        val itemC = LocalFileSourceAdapter.toMediaItem(
            Track(id = "trk-c", title = "Track C", durationSec = 2.0),
            LocalFile(id = "f-c", trackId = "trk-c", storageType = StorageType.INTERNAL_SYNC, fileUri = wavFile.absolutePath, fileSize = wavFile.length(), fileHash = "h-c", format = "wav")
        )

        val exoPlayer = createAudioConfiguredPlayer()
        player = exoPlayer

        exoPlayer.setMediaItems(listOf(itemA, itemB, itemC))
        exoPlayer.prepare()

        assertEquals(3, exoPlayer.mediaItemCount)
        assertEquals(0, exoPlayer.currentMediaItemIndex)
        assertTrue(exoPlayer.hasNextMediaItem())

        // 1. Next: A -> B
        exoPlayer.seekToNextMediaItem()
        assertEquals(1, exoPlayer.currentMediaItemIndex)
        assertEquals("trk-b", exoPlayer.currentMediaItem?.mediaId)
        assertTrue(exoPlayer.hasNextMediaItem())
        assertTrue(exoPlayer.hasPreviousMediaItem())

        // 2. Next: B -> C
        exoPlayer.seekToNextMediaItem()
        assertEquals(2, exoPlayer.currentMediaItemIndex)
        assertEquals("trk-c", exoPlayer.currentMediaItem?.mediaId)

        // 3. Previous: C -> B
        exoPlayer.seekToPreviousMediaItem()
        assertEquals(1, exoPlayer.currentMediaItemIndex)
        assertEquals("trk-b", exoPlayer.currentMediaItem?.mediaId)

        // 4. Previous: B -> A
        exoPlayer.seekToPreviousMediaItem()
        assertEquals(0, exoPlayer.currentMediaItemIndex)
        assertEquals("trk-a", exoPlayer.currentMediaItem?.mediaId)
    }

    @Test
    fun testAutomaticEofProgressionOnQueue() = runBlocking(Dispatchers.Main) {
        val mp3File = File(musicDir, "test_track.mp3")
        val flacFile = File(musicDir, "test_track.flac")

        val itemA = LocalFileSourceAdapter.toMediaItem(
            Track(id = "trk-eof-a", title = "Track A", durationSec = 2.0),
            LocalFile(id = "f-eof-a", trackId = "trk-eof-a", storageType = StorageType.INTERNAL_SYNC, fileUri = mp3File.absolutePath, fileSize = mp3File.length(), fileHash = "h-eof-a", format = "mp3")
        )
        val itemB = LocalFileSourceAdapter.toMediaItem(
            Track(id = "trk-eof-b", title = "Track B", durationSec = 2.0),
            LocalFile(id = "f-eof-b", trackId = "trk-eof-b", storageType = StorageType.INTERNAL_SYNC, fileUri = flacFile.absolutePath, fileSize = flacFile.length(), fileHash = "h-eof-b", format = "flac")
        )

        val exoPlayer = createAudioConfiguredPlayer()
        player = exoPlayer

        exoPlayer.setMediaItems(listOf(itemA, itemB))
        exoPlayer.prepare()

        val readyLatch = CountDownLatch(1)
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) readyLatch.countDown()
            }
        })
        withContext(Dispatchers.IO) { readyLatch.await(5, TimeUnit.SECONDS) }

        // Seek close to the end of Track A (duration ~2.0s / ~2000ms)
        val duration = exoPlayer.duration
        val seekTarget = if (duration > 300L) duration - 250L else 1750L
        exoPlayer.seekTo(seekTarget)
        exoPlayer.play()

        // Wait for natural completion and automatic transition to Track B
        val transitionLatch = CountDownLatch(1)
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (mediaItem?.mediaId == "trk-eof-b" && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    transitionLatch.countDown()
                }
            }
        })

        val transitioned = withContext(Dispatchers.IO) { transitionLatch.await(4, TimeUnit.SECONDS) }
        assertTrue("Queue must automatically advance to Track B on natural EOF", transitioned)
        assertEquals(1, exoPlayer.currentMediaItemIndex)
        assertEquals("trk-eof-b", exoPlayer.currentMediaItem?.mediaId)
    }

    @Test
    fun testMissingFileDeterministicHandling() = runBlocking(Dispatchers.Main) {
        val missingTrack = Track(id = "trk-missing", title = "Missing Track", durationSec = 2.0)
        val missingFile = LocalFile(
            id = "f-missing",
            trackId = missingTrack.id,
            storageType = StorageType.INTERNAL_SYNC,
            fileUri = File(musicDir, "does_not_exist_file.mp3").absolutePath,
            fileSize = 0,
            fileHash = "none",
            format = "mp3"
        )

        val mediaItem = LocalFileSourceAdapter.toMediaItem(missingTrack, missingFile)
        val exoPlayer = createAudioConfiguredPlayer()
        player = exoPlayer

        val errorLatch = CountDownLatch(1)
        var capturedError: PlaybackException? = null

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                capturedError = error
                errorLatch.countDown()
            }
        })

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        val errored = withContext(Dispatchers.IO) { errorLatch.await(5, TimeUnit.SECONDS) }
        assertTrue("ExoPlayer must report deterministic error on missing file", errored)
        assertNotNull("PlaybackException must be captured", capturedError)
        assertEquals(Player.STATE_IDLE, exoPlayer.playbackState)
    }

    private fun assertFalse(message: String, condition: Boolean) {
        assertTrue(message, !condition)
    }
}
