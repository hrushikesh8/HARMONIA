package org.harmonia.player.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream

/**
 * Local-only, offline artwork provider managing cached artwork bitmaps.
 * Adheres strictly to Harmonia Core SHA-256 artwork hash identity.
 * 
 * Guarantees:
 * 1. Sandboxed storage (filesDir/artwork) with path-traversal protection.
 * 2. In-memory LRU cache to avoid unnecessary disk I/O and bitmap decoding.
 * 3. Graceful handling of:
 *    - Artwork exists (decoded and cached)
 *    - Artwork missing (returns null for visual fallback)
 *    - Artwork invalid/corrupt (caught safely, returns null)
 *    - Cache hit vs cache miss
 * 4. Never exposes arbitrary filesystem paths to presentation or UI layer.
 */
class ArtworkProvider(
    private val context: Context
) {
    private val artworkDir: File = File(context.filesDir, "artwork").apply { mkdirs() }

    // Cache up to 60 bitmaps in memory (approximately 10-15MB depending on dimensions)
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(60) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return 1
        }
    }

    /**
     * Sanitizes and validates SHA-256 hex string to strictly prevent path traversal.
     */
    private fun sanitizeHash(hash: String?): String? {
        if (hash.isNullOrBlank()) return null
        val clean = hash.trim()
        return if (clean.matches(Regex("^[a-fA-F0-9]{1,64}$"))) clean else null
    }

    /**
     * Resolves the sandboxed File object if valid and existing.
     * Note: Kept package-private/internal; not exposed to UI.
     */
    internal fun getArtworkFile(artworkHash: String?): File? {
        val validHash = sanitizeHash(artworkHash) ?: return null
        val file = File(artworkDir, "$validHash.jpg")
        return if (file.exists() && file.length() > 0L) file else null
    }

    /**
     * Fast in-memory cache lookup. Returns Bitmap if present, null on cache miss.
     */
    fun getCachedBitmap(artworkHash: String?): Bitmap? {
        val validHash = sanitizeHash(artworkHash) ?: return null
        return memoryCache.get(validHash)
    }

    /**
     * Synchronous or IO-callable bitmap resolver with memory caching.
     * Handles missing files, corrupt files, and invalid formats safely without throwing.
     */
    fun loadBitmap(artworkHash: String?): Bitmap? {
        val validHash = sanitizeHash(artworkHash) ?: return null

        // 1. Check memory cache (Cache hit)
        val cached = memoryCache.get(validHash)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        // 2. Resolve on disk (Cache miss)
        val file = getArtworkFile(validHash) ?: return null

        // 3. Decode bitmap with failure protection (corrupt / invalid / OOM)
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                memoryCache.put(validHash, bitmap)
            }
            bitmap
        } catch (e: Throwable) {
            // Corrupt file or decoding failure
            null
        }
    }

    /**
     * Checks if the artwork file is available in the local application sandbox.
     */
    fun isArtworkAvailable(artworkHash: String?): Boolean {
        val validHash = sanitizeHash(artworkHash) ?: return false
        return getArtworkFile(validHash) != null
    }

    /**
     * Safely stores artwork bytes into the local application sandbox under the SHA-256 hash.
     */
    fun storeArtwork(artworkHash: String, bytes: ByteArray): Boolean {
        val validHash = sanitizeHash(artworkHash) ?: return false
        artworkDir.mkdirs()
        val tempFile = File(artworkDir, "${validHash}_tmp")
        val targetFile = File(artworkDir, "$validHash.jpg")
        return try {
            FileOutputStream(tempFile).use { out ->
                out.write(bytes)
                out.flush()
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (tempFile.renameTo(targetFile)) {
                // Decode and warm the cache
                loadBitmap(validHash)
                true
            } else {
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            tempFile.delete()
            false
        }
    }

    /**
     * Convenience alias for loadBitmap.
     */
    fun loadArtwork(artworkHash: String?): Bitmap? = loadBitmap(artworkHash)

    /**
     * Evicts all entries from the in-memory bitmap cache.
     */
    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    companion object {
        @Volatile
        private var INSTANCE: ArtworkProvider? = null

        fun getInstance(context: Context): ArtworkProvider {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArtworkProvider(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
