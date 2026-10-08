package org.harmonia.player.data.local

import android.content.Context
import java.io.File

/**
 * Local-only artwork provider managing cached album/artist artwork files.
 * Adheres strictly to Harmonia Core SHA-256 artwork hash identity.
 * Separates artwork identity from on-disk availability.
 */
class ArtworkProvider(
    private val context: Context
) {
    private val artworkDir: File = File(context.filesDir, "artwork").apply { mkdirs() }

    /**
     * Resolves local File if the artwork bytes exist in the sandbox.
     */
    fun getArtworkFile(artworkHash: String?): File? {
        if (artworkHash.isNullOrBlank()) return null
        val file = File(artworkDir, "$artworkHash.jpg")
        return if (file.exists() && file.length() > 0L) file else null
    }

    /**
     * Indicates whether the artwork is present on disk locally for offline display.
     */
    fun isArtworkAvailable(artworkHash: String?): Boolean {
        return getArtworkFile(artworkHash) != null
    }
}
