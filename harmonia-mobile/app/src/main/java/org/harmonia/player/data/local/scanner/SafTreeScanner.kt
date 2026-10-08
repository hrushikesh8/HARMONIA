package org.harmonia.player.data.local.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.harmonia.player.domain.model.scanner.DiscoveredMedia
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.storage.SourceIdentity
import org.harmonia.player.domain.model.storage.SourceType
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker
import org.harmonia.player.domain.scanner.MediaSourceScanner

/**
 * Concrete Storage Access Framework (SAF) document tree scanner.
 * Recursively traverses user-granted directory trees via native DocumentsContract.
 *
 * Guarantees:
 * 1. Bounded recursion depth (max 15 levels) to prevent symlink/virtual loop blowups.
 * 2. Catches SecurityException and IO failures per directory level without aborting the scan.
 * 3. Preserves document identity and generates provider-neutral DiscoveredMedia models.
 */
class SafTreeScanner(
    private val context: Context,
    private val permissionChecker: StoragePermissionChecker
) : MediaSourceScanner {

    override val supportedSourceType: SourceType = SourceType.SAF_DOCUMENT_TREE

    companion object {
        private const val MAX_RECURSION_DEPTH = 15
    }

    override suspend fun discoverMedia(
        source: StorageSource,
        mode: ScanMode,
        lastGeneration: Long
    ): List<DiscoveredMedia> = withContext(Dispatchers.IO) {
        if (!permissionChecker.canScanAudioMedia()) {
            return@withContext emptyList()
        }

        if (source !is StorageSource.SafDocumentTree) {
            return@withContext emptyList()
        }

        val treeUri = try {
            Uri.parse(source.treeUri)
        } catch (_: Throwable) {
            return@withContext emptyList()
        }

        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Throwable) {
            return@withContext emptyList()
        }

        val candidates = mutableListOf<DiscoveredMedia>()
        traverseDirectory(
            treeUri = treeUri,
            documentId = rootDocId,
            source = source,
            depth = 0,
            candidates = candidates
        )

        candidates
    }

    private fun traverseDirectory(
        treeUri: Uri,
        documentId: String,
        source: StorageSource.SafDocumentTree,
        depth: Int,
        candidates: MutableList<DiscoveredMedia>
    ) {
        if (depth > MAX_RECURSION_DEPTH) return

        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        } catch (_: Throwable) {
            return
        }

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val lastModCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val childDocId = if (idCol >= 0) cursor.getString(idCol) ?: continue else continue
                    val displayName = if (nameCol >= 0) cursor.getString(nameCol) ?: "unnamed_audio" else "unnamed_audio"
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val lastModified = if (lastModCol >= 0) cursor.getLong(lastModCol) / 1000L else 0L

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        traverseDirectory(treeUri, childDocId, source, depth + 1, candidates)
                    } else if (AudioFileFilter.isSupportedAudio(displayName, mimeType)) {
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId).toString()
                        val sourceIdentity = SourceIdentity.safDocument(
                            treeUri = source.treeUri,
                            documentId = childDocId
                        )

                        candidates.add(
                            DiscoveredMedia(
                                sourceIdentity = sourceIdentity,
                                runtimeLocator = documentUri,
                                discoveryLocator = childDocId,
                                displayName = displayName,
                                mimeType = mimeType,
                                sizeBytes = size,
                                lastModifiedEpochSec = lastModified,
                                sourceType = SourceType.SAF_DOCUMENT_TREE,
                                isAccessible = true
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {
            // Isolate directory query failure
        }
    }
}
