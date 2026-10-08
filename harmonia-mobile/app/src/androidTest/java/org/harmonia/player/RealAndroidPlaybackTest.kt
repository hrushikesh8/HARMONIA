package org.harmonia.player

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Real device/emulator Android instrumented playback test.
 * Executes native ExoPlayer against real audio bitstreams in the app sandbox on Android 16 device.
 * Verifies MP3, FLAC, M4A, WAV decoding, playback state transitions, seek, next/previous, and error handling.
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
    }

    @After
    fun tearDown() = runBlocking(Dispatchers.Main) {
        player?.release()
        player = null
    }

    @Test
    fun testRealMp3PlaybackOnAndroid() = runBlocking(Dispatchers.Main) {
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
        val exoPlayer = ExoPlayer.Builder(context).build()
        player = exoPlayer

        val playStartedLatch = CountDownLatch(1)
        var playerError: PlaybackException? = null

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    playStartedLatch.countDown()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = error
                playStartedLatch.countDown()
            }
        })

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        val ready = withContext(Dispatchers.IO) {
            playStartedLatch.await(5, TimeUnit.SECONDS)
        }

        assertTrue("ExoPlayer must transition to STATE_READY", ready)
        assertNull("Playback must not fail with error", playerError)
        assertEquals(Player.STATE_READY, exoPlayer.playbackState)
        assertTrue("ExoPlayer must be playing", exoPlayer.isPlaying)

        // Verify seek
        exoPlayer.seekTo(500L)
        assertEquals(500L, exoPlayer.currentPosition.coerceAtLeast(0L))

        // Verify pause
        exoPlayer.pause()
        assertTrue("ExoPlayer must pause", !exoPlayer.isPlaying)
    }

    @Test
    fun testRealFlacPlaybackOnAndroid() = runBlocking(Dispatchers.Main) {
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
        val exoPlayer = ExoPlayer.Builder(context).build()
        player = exoPlayer

        val readyLatch = CountDownLatch(1)
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) readyLatch.countDown()
            }
        })

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        val ready = withContext(Dispatchers.IO) { readyLatch.await(5, TimeUnit.SECONDS) }
        assertTrue("FLAC bitstream must be decoded to STATE_READY", ready)
        assertEquals(Player.STATE_READY, exoPlayer.playbackState)
    }

    @Test
    fun testRealM4aAacPlaybackOnAndroid() = runBlocking(Dispatchers.Main) {
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
        val exoPlayer = ExoPlayer.Builder(context).build()
        player = exoPlayer

        val readyLatch = CountDownLatch(1)
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) readyLatch.countDown()
            }
        })

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        val ready = withContext(Dispatchers.IO) { readyLatch.await(5, TimeUnit.SECONDS) }
        assertTrue("M4A bitstream must be decoded to STATE_READY", ready)
        assertEquals(Player.STATE_READY, exoPlayer.playbackState)
    }

    @Test
    fun testRealWavPlaybackOnAndroid() = runBlocking(Dispatchers.Main) {
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
        val exoPlayer = ExoPlayer.Builder(context).build()
        player = exoPlayer

        val readyLatch = CountDownLatch(1)
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) readyLatch.countDown()
            }
        })

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        val ready = withContext(Dispatchers.IO) { readyLatch.await(5, TimeUnit.SECONDS) }
        assertTrue("WAV bitstream must be decoded to STATE_READY", ready)
        assertEquals(Player.STATE_READY, exoPlayer.playbackState)
    }

    @Test
    fun testMultiTrackQueueNavigationOnDevice() = runBlocking(Dispatchers.Main) {
        val mp3File = File(musicDir, "test_track.mp3")
        val flacFile = File(musicDir, "test_track.flac")

        val mp3Item = LocalFileSourceAdapter.toMediaItem(
            Track(id = "trk-1", title = "Track 1", durationSec = 2.0),
            LocalFile(id = "f-1", trackId = "trk-1", storageType = StorageType.INTERNAL_SYNC, fileUri = mp3File.absolutePath, fileSize = mp3File.length(), fileHash = "h1", format = "mp3")
        )
        val flacItem = LocalFileSourceAdapter.toMediaItem(
            Track(id = "trk-2", title = "Track 2", durationSec = 2.0),
            LocalFile(id = "f-2", trackId = "trk-2", storageType = StorageType.INTERNAL_SYNC, fileUri = flacFile.absolutePath, fileSize = flacFile.length(), fileHash = "h2", format = "flac")
        )

        val exoPlayer = ExoPlayer.Builder(context).build()
        player = exoPlayer

        exoPlayer.setMediaItems(listOf(mp3Item, flacItem))
        exoPlayer.prepare()

        assertEquals(2, exoPlayer.mediaItemCount)
        assertEquals(0, exoPlayer.currentMediaItemIndex)
        assertTrue(exoPlayer.hasNextMediaItem())

        // Navigate next
        exoPlayer.seekToNextMediaItem()
        assertEquals(1, exoPlayer.currentMediaItemIndex)
        assertTrue(exoPlayer.hasPreviousMediaItem())

        // Navigate previous
        exoPlayer.seekToPreviousMediaItem()
        assertEquals(0, exoPlayer.currentMediaItemIndex)
    }

    @Test
    fun testMissingFileErrorHandlingDeterministic() = runBlocking(Dispatchers.Main) {
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
        val exoPlayer = ExoPlayer.Builder(context).build()
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
    }
}
