package org.harmonia.player.data.local.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.model.storage.permission.SafPermissionDelegate

/**
 * Concrete Android ContentResolver implementation of SafPermissionDelegate.
 */
class AndroidSafPermissionDelegate(
    private val context: Context
) : SafPermissionDelegate {

    override fun takePersistableReadPermission(treeUri: String) {
        val uri = Uri.parse(treeUri)
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    override fun releasePersistableReadPermission(treeUri: String) {
        try {
            val uri = Uri.parse(treeUri)
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Best effort release
        }
    }

    override fun hasPersistedReadPermission(treeUri: String): Boolean {
        return try {
            val normalizedTarget = StorageSource.SafDocumentTree.normalizeTreeUri(treeUri)
            context.contentResolver.persistedUriPermissions.any {
                it.isReadPermission &&
                    StorageSource.SafDocumentTree.normalizeTreeUri(it.uri.toString()) == normalizedTarget
            }
        } catch (_: Exception) {
            false
        }
    }
}
