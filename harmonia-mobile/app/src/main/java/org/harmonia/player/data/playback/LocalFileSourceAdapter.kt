package org.harmonia.player.data.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.harmonia.player.domain.model.LocalFile
import org.harmonia.player.domain.model.Track

/**
 * Adapter that maps Harmonia pure domain models ([Track] and [LocalFile])
 * to AndroidX Media3 [MediaItem] instances.
 *
 * Preserves the domain separation: the domain knows nothing about MediaItem,
 * Uri, or MediaMetadata.
 */
object LocalFileSourceAdapter {

    const val EXTRA_TRACK_ID = "org.harmonia.extra.TRACK_ID"
    const val EXTRA_LOCAL_FILE_ID = "org.harmonia.extra.LOCAL_FILE_ID"
    const val EXTRA_DURATION_SEC = "org.harmonia.extra.DURATION_SEC"

    /**
     * Converts a domain [Track] and its corresponding [LocalFile] into a playable Media3 [MediaItem].
     * The [LocalFile.fileUri] string may be a `content://` SAF Uri or `file://` / absolute file path.
     */
    fun toMediaItem(track: Track, localFile: LocalFile? = null): MediaItem {
        val fileToUse = localFile ?: track.localFile
        val uri = if (fileToUse != null && fileToUse.fileUri.isNotBlank()) {
            parseUri(fileToUse.fileUri)
        } else {
            Uri.EMPTY
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumTitle)
            .setDisplayTitle(track.title)

        track.trackNumber?.let { metadataBuilder.setTrackNumber(it) }
        track.discNumber.let { metadataBuilder.setDiscNumber(it) }

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun parseUri(fileUri: String): Uri {
        return if (fileUri.startsWith("content://") || fileUri.startsWith("file://") || fileUri.startsWith("http://") || fileUri.startsWith("https://")) {
            Uri.parse(fileUri)
        } else {
            // Raw filesystem path
            Uri.fromFile(java.io.File(fileUri))
        }
    }
}
