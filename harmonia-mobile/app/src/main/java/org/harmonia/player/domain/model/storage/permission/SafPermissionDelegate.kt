package org.harmonia.player.domain.model.storage.permission

/**
 * Domain port for managing Storage Access Framework (SAF) persistable URI permissions.
 * Isolates Android ContentResolver interactions behind a testable abstraction.
 */
interface SafPermissionDelegate {

    /**
     * Acquires and persists read permission for the given tree URI.
     */
    fun takePersistableReadPermission(treeUri: String)

    /**
     * Releases a previously persisted read permission for the given tree URI.
     */
    fun releasePersistableReadPermission(treeUri: String)

    /**
     * Checks if a persistable read permission is currently held for the given tree URI.
     */
    fun hasPersistedReadPermission(treeUri: String): Boolean
}
