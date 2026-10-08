package org.harmonia.player.data.local.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.harmonia.player.domain.model.scanner.DiscoveredMedia
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.storage.SourceIdentity
import org.harmonia.player.domain.model.storage.SourceType
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker
import org.harmonia.player.domain.model.storage.permission.StoragePermissionState
import org.harmonia.player.domain.scanner.MediaSourceScanner

/**
 * Concrete Android MediaStore scanner implementation.
 * Queries Android's shared audio media provider within Scoped Storage guidelines.
 *
 * Guarantees:
 * 1. Never uses MediaStore _ID as durable Harmonia identity.
 * 2. Uses bounded projections querying only necessary metadata columns.
 * 3. Gracefully yields to StoragePermissionChecker if audio permissions are revoked.
 * 4. Filters non-music media and unsupported bitstreams.
 */
class MediaStoreScanner(
    private val context: Context,
    private val permissionChecker: StoragePermissionChecker
) : MediaSourceScanner {

    override val supportedSourceType: SourceType = SourceType.MEDIA_STORE_PRIMARY

    override suspend fun discoverMedia(
        source: StorageSource,
        mode: ScanMode,
        lastGeneration: Long
    ): List<DiscoveredMedia> = withContext(Dispatchers.IO) {
        if (!permissionChecker.canScanAudioMedia()) {
            return@withContext emptyList()
        }

        val collectionUri = resolveCollectionUri(source)
        val projection = buildProjection()
        val (selection, selectionArgs) = buildSelection(mode, lastGeneration)
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        val candidates = mutableListOf<DiscoveredMedia>()

        try {
            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val dateModCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                    val displayName = if (nameCol >= 0) cursor.getString(nameCol) ?: "unknown_audio" else "unknown_audio"
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val dateModified = if (dateModCol >= 0) cursor.getLong(dateModCol) else 0L
                    val relativePath = if (relPathCol >= 0) cursor.getString(relPathCol) else null

                    // Filter to supported audio types
                    if (!AudioFileFilter.isSupportedAudio(displayName, mimeType)) {
                        continue
                    }

                    val runtimeLocator = ContentUris.withAppendedId(collectionUri, id).toString()
                    val discoveryLocator = id.toString()

                    val sourceIdentity = when (source) {
                        is StorageSource.MediaStoreVolume -> {
                            val path = relativePath?.let { "$it$displayName" } ?: displayName
                            SourceIdentity.mediaStoreVolume(source.volumeUuid, path)
                        }
                        else -> {
                            val path = relativePath?.let { "$it$displayName" } ?: displayName
                            SourceIdentity.mediaStorePrimary(path)
                        }
                    }

                    val sourceType = when (source) {
                        is StorageSource.MediaStoreVolume -> SourceType.MEDIA_STORE_VOLUME
                        else -> SourceType.MEDIA_STORE_PRIMARY
                    }

                    candidates.add(
                        DiscoveredMedia(
                            sourceIdentity = sourceIdentity,
                            runtimeLocator = runtimeLocator,
                            discoveryLocator = discoveryLocator,
                            displayName = displayName,
                            mimeType = mimeType,
                            sizeBytes = sizeBytes,
                            lastModifiedEpochSec = dateModified,
                            sourceType = sourceType,
                            isAccessible = true
                        )
                    )
                }
            }
        } catch (_: Throwable) {
            // Safe swallow on provider query exception; return whatever was collected
        }

        candidates
    }

    private fun resolveCollectionUri(source: StorageSource): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && source is StorageSource.MediaStoreVolume) {
            try {
                MediaStore.Audio.Media.getContentUri(source.volumeName)
            } catch (_: Throwable) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun buildProjection(): Array<String> {
        val list = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.IS_MUSIC
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            list.add(MediaStore.Audio.Media.GENERATION_MODIFIED)
        }

        return list.toTypedArray()
    }

    private fun buildSelection(mode: ScanMode, lastGeneration: Long): Pair<String, Array<String>?> {
        val baseSelection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%')"

        if (mode == ScanMode.INCREMENTAL && lastGeneration > 0) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Pair("$baseSelection AND ${MediaStore.Audio.Media.GENERATION_MODIFIED} > ?", arrayOf(lastGeneration.toString()))
            } else {
                Pair("$baseSelection AND ${MediaStore.Audio.Media.DATE_MODIFIED} > ?", arrayOf(lastGeneration.toString()))
            }
        }

        return Pair(baseSelection, null)
    }
}
