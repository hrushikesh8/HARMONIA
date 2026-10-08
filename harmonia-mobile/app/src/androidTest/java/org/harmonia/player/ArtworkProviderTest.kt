package org.harmonia.player

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.ArtworkProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class ArtworkProviderTest {

    private lateinit var context: Context
    private lateinit var artworkProvider: ArtworkProvider
    private val testHashValid = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        artworkProvider = ArtworkProvider.getInstance(context)
        artworkProvider.clearMemoryCache()
    }

    @After
    fun tearDown() {
        artworkProvider.clearMemoryCache()
        val artDir = File(context.filesDir, "artwork")
        if (artDir.exists()) {
            artDir.deleteRecursively()
        }
    }

    @Test
    fun store_and_load_valid_artwork_image_succeeds_and_populates_lru_cache() = runBlocking {
        // Generate valid 4x4 PNG bytes
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, android.graphics.Color.RED)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val pngBytes = stream.toByteArray()

        // 1. Store artwork in sandbox
        val stored = artworkProvider.storeArtwork(testHashValid, pngBytes)
        assertTrue(stored)

        // 2. Load artwork (disk cache miss -> memory populate)
        val loaded1 = artworkProvider.loadArtwork(testHashValid)
        assertNotNull(loaded1)
        assertEquals(4, loaded1?.width)
        assertEquals(4, loaded1?.height)

        // 3. Load artwork again (LRU memory cache hit)
        val loaded2 = artworkProvider.loadArtwork(testHashValid)
        assertNotNull(loaded2)
        assertEquals(loaded1, loaded2)

        // 4. Clear memory cache, reload from disk
        artworkProvider.clearMemoryCache()
        val loaded3 = artworkProvider.loadArtwork(testHashValid)
        assertNotNull(loaded3)
        assertEquals(4, loaded3?.width)
    }

    @Test
    fun store_and_load_corrupt_artwork_safely_returns_null_without_crashing() = runBlocking {
        val corruptBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val stored = artworkProvider.storeArtwork(testHashValid, corruptBytes)
        assertTrue(stored)

        // Loading corrupt bytes must safely return null instead of throwing uncaught exception
        val loaded = artworkProvider.loadArtwork(testHashValid)
        assertNull(loaded)
    }

    @Test
    fun path_traversal_and_invalid_artwork_hashes_are_safely_rejected() = runBlocking {
        val dummyBytes = byteArrayOf(0x10, 0x20)

        // Path traversal attempts must be rejected
        assertFalse(artworkProvider.storeArtwork("../../../bad_path", dummyBytes))
        assertNull(artworkProvider.loadArtwork("../../../bad_path"))

        // Special characters or whitespace must be rejected
        assertFalse(artworkProvider.storeArtwork("bad hash with spaces", dummyBytes))
        assertNull(artworkProvider.loadArtwork("bad hash with spaces"))

        // Empty hash or null must be rejected
        assertFalse(artworkProvider.storeArtwork("", dummyBytes))
        assertNull(artworkProvider.loadArtwork(""))
        assertNull(artworkProvider.loadArtwork(null))
    }

    @Test
    fun nonexistent_artwork_returns_null() = runBlocking {
        val missingHash = "0000000000000000000000000000000000000000000000000000000000000000"
        val loaded = artworkProvider.loadArtwork(missingHash)
        assertNull(loaded)
    }
}
