package org.harmonia.player.data.local.scanner

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Memory-safe cryptographic content hashing engine.
 * Streams audio bitstreams in bounded 16KB blocks to compute SHA-256 digests
 * without loading large media files into memory ($O(1)$ memory consumption).
 */
object ContentHasher {

    private const val BUFFER_SIZE = 16384 // 16 KB

    suspend fun computeFileHash(context: Context, locator: String): String? = withContext(Dispatchers.IO) {
        if (locator.isBlank()) return@withContext null

        val uri = Uri.parse(locator)
        val stream: InputStream? = try {
            when {
                uri.scheme == "content" -> context.contentResolver.openInputStream(uri)
                uri.scheme == "file" -> FileInputStream(uri.path ?: locator)
                else -> {
                    val directFile = File(locator)
                    if (directFile.exists()) FileInputStream(directFile)
                    else context.contentResolver.openInputStream(uri)
                }
            }
        } catch (_: Throwable) {
            null
        }

        stream?.use { input ->
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                val hashBytes = digest.digest()
                hashBytes.joinToString("") { "%02x".format(it) }
            } catch (_: Throwable) {
                null
            }
        }
    }
}
