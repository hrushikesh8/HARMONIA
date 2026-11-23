package org.harmonia.player.data.local

import android.content.Context
import android.os.Build
import android.provider.MediaStore

/**
 * Resolves the on-device folder (e.g. "Music/Album") of MediaStore audio entries.
 *
 * Library tracks scanned through MediaStore are stored by opaque content URI
 * (content://media/external/audio/media/42), which carries no folder information,
 * so the folder is looked up from MediaStore at display time with one query per volume.
 */
class MediaFolderResolver(private val context: Context) {

    /**
     * Returns a map of content URI -> folder path for every resolvable MediaStore URI in [fileUris].
     * URIs from other providers (SAF, file://) are skipped; failures yield a partial map.
     */
    fun resolveFolders(fileUris: Collection<String>): Map<String, String> {
        val byVolume = fileUris
            .mapNotNull { uri -> parseMediaStoreAudioUri(uri)?.let { (volume, id) -> Triple(uri, volume, id) } }
            .groupBy { it.second }

        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.DATA
        }

        val folders = HashMap<String, String>()
        for ((volume, entries) in byVolume) {
            val uriById = entries.associate { it.third to it.first }
            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.getContentUri(volume),
                    arrayOf(MediaStore.Audio.Media._ID, pathColumn),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val pathCol = cursor.getColumnIndex(pathColumn)
                    if (idCol < 0 || pathCol < 0) return@use
                    while (cursor.moveToNext()) {
                        val uri = uriById[cursor.getLong(idCol)] ?: continue
                        val rawPath = cursor.getString(pathCol) ?: continue
                        val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            rawPath.trimEnd('/')
                        } else {
                            rawPath.substringBeforeLast('/', "")
                        }
                        if (folder.isNotBlank()) folders[uri] = folder
                    }
                }
            } catch (_: Throwable) {
                // Volume unavailable or provider error: leave these URIs unresolved
            }
        }
        return folders
    }

    companion object {
        private val MEDIA_STORE_AUDIO_URI = Regex("""^content://media/([^/]+)/audio/media/(\d+)$""")

        /**
         * Parses content://media/<volume>/audio/media/<id> into (volume, id), or null for other URIs.
         */
        fun parseMediaStoreAudioUri(uri: String): Pair<String, Long>? {
            val match = MEDIA_STORE_AUDIO_URI.find(uri) ?: return null
            val id = match.groupValues[2].toLongOrNull() ?: return null
            return match.groupValues[1] to id
        }
    }
}
