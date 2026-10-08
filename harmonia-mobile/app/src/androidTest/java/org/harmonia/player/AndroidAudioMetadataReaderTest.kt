package org.harmonia.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.ArtworkProvider
import org.harmonia.player.data.local.metadata.AndroidAudioMetadataReader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class AndroidAudioMetadataReaderTest {

    private lateinit var context: Context
    private lateinit var artworkProvider: ArtworkProvider
    private lateinit var reader: AndroidAudioMetadataReader
    private lateinit var fixtureDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        artworkProvider = ArtworkProvider.getInstance(context)
        artworkProvider.clearMemoryCache()
        reader = AndroidAudioMetadataReader(context, artworkProvider)

        fixtureDir = File(context.cacheDir, "audio_fixtures").apply {
            mkdirs()
        }

        copyAssetToFile("test_track.mp3", File(fixtureDir, "test_track.mp3"))
        copyAssetToFile("test_track.flac", File(fixtureDir, "test_track.flac"))
        copyAssetToFile("test_track.m4a", File(fixtureDir, "test_track.m4a"))
        copyAssetToFile("test_track.wav", File(fixtureDir, "test_track.wav"))
        copyAssetToFile("image.mp3", File(fixtureDir, "image.mp3"))
        copyAssetToFile("empty.mp3", File(fixtureDir, "empty.mp3"))
        copyAssetToFile("corrupt.mp3", File(fixtureDir, "corrupt.mp3"))
    }

    @After
    fun tearDown() {
        artworkProvider.clearMemoryCache()
        val artDir = File(context.filesDir, "artwork")
        if (artDir.exists()) {
            artDir.deleteRecursively()
        }
        if (fixtureDir.exists()) {
            fixtureDir.deleteRecursively()
        }
    }

    private fun copyAssetToFile(assetName: String, destination: File) {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        testContext.assets.open(assetName).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    @Test
    fun extract_metadata_from_mp3_returns_factual_fields() = runBlocking {
        val file = File(fixtureDir, "test_track.mp3")
        val metadata = reader.readMetadata(file.absolutePath)

        assertEquals("test", metadata.title)
        assertEquals("test", metadata.artist)
        assertEquals("test", metadata.album)
        assertEquals(1, metadata.trackNumber)
        if (metadata.year != null) {
            assertEquals(2000, metadata.year)
        }
        assertNotNull(metadata.durationSec)
        assertTrue((metadata.durationSec ?: 0.0) > 0.0)
    }

    @Test
    fun extract_metadata_from_flac_returns_factual_fields() = runBlocking {
        val file = File(fixtureDir, "test_track.flac")
        val metadata = reader.readMetadata(file.absolutePath)

        assertEquals("test", metadata.title)
        assertEquals("test", metadata.artist)
        assertEquals("test", metadata.album)
        assertEquals(1, metadata.trackNumber)
        assertEquals(2000, metadata.year)
        assertNotNull(metadata.durationSec)
        assertTrue((metadata.durationSec ?: 0.0) > 0.0)
    }

    @Test
    fun extract_metadata_from_m4a_returns_factual_fields() = runBlocking {
        val file = File(fixtureDir, "test_track.m4a")
        val metadata = reader.readMetadata(file.absolutePath)

        assertEquals("test", metadata.title)
        assertEquals("test", metadata.artist)
        assertEquals("test", metadata.album)
        assertEquals(1, metadata.trackNumber)
        if (metadata.year != null) {
            assertEquals(2000, metadata.year)
        }
        assertNotNull(metadata.durationSec)
        assertTrue((metadata.durationSec ?: 0.0) > 0.0)
    }

    @Test
    fun extract_metadata_from_wav_returns_factual_fields() = runBlocking {
        val file = File(fixtureDir, "test_track.wav")
        val metadata = reader.readMetadata(file.absolutePath)

        // WAV metadata extraction where supported by Android runtime
        assertNotNull(metadata.durationSec)
        assertTrue((metadata.durationSec ?: 0.0) > 0.0)
        // If Android WAV extractor populates ID3 chunks:
        if (metadata.title != null) {
            assertEquals("test", metadata.title)
        }
    }

    @Test
    fun extract_embedded_artwork_integrates_with_m3_artwork_provider() = runBlocking {
        val file = File(fixtureDir, "image.mp3")
        val metadata = reader.readMetadata(file.absolutePath, extractArtwork = true, includeRawArtworkBytes = true)

        assertNotNull(metadata.artworkHash)
        val hash = metadata.artworkHash!!
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("^[a-f0-9]{64}$")))

        // Verify raw artwork bytes present when requested
        assertNotNull(metadata.rawArtworkBytes)
        assertTrue((metadata.rawArtworkBytes?.size ?: 0) > 0)

        // Verify stored in existing M3 ArtworkProvider
        assertTrue(artworkProvider.isArtworkAvailable(hash))

        // Verify loadArtwork retrieves valid Bitmap from cache/disk
        val bitmap = artworkProvider.loadArtwork(hash)
        assertNotNull(bitmap)
        assertTrue((bitmap?.width ?: 0) > 0)
        assertTrue((bitmap?.height ?: 0) > 0)
    }

    @Test
    fun extract_metadata_from_empty_tags_file_leaves_fields_null() = runBlocking {
        val file = File(fixtureDir, "empty.mp3")
        val metadata = reader.readMetadata(file.absolutePath)

        // Title, artist, album must remain null, NOT fabricated
        assertNull(metadata.title)
        assertNull(metadata.artist)
        assertNull(metadata.album)
        assertNull(metadata.artworkHash)
        assertNull(metadata.rawArtworkBytes)

        // Stream duration is still discovered from bitstream
        assertNotNull(metadata.durationSec)
        assertTrue((metadata.durationSec ?: 0.0) > 0.0)
    }

    @Test
    fun corrupt_and_missing_and_blank_sources_return_safe_fallback_without_throwing() = runBlocking {
        // Corrupt file
        val corruptFile = File(fixtureDir, "corrupt.mp3")
        val corruptMeta = reader.readMetadata(corruptFile.absolutePath)
        assertNull(corruptMeta.title)
        assertNull(corruptMeta.artist)

        // Nonexistent file
        val missingMeta = reader.readMetadata("file:///invalid/path/missing.mp3")
        assertNull(missingMeta.title)

        // Blank locator
        val blankMeta = reader.readMetadata("   ")
        assertNull(blankMeta.title)
    }
}
