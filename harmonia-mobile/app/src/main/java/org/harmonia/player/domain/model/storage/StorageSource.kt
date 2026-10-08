package org.harmonia.player.domain.model.storage

/**
 * Pure domain representation of a local media storage root on Android.
 * Completely free of any Android framework, Room, or UI dependencies.
 */
sealed interface StorageSource {
    val id: String
    val displayName: String
    val isRemovable: Boolean
    val isAvailable: Boolean

    /**
     * Primary internal storage discovered via Android MediaStore.
     */
    data class MediaStorePrimary(
        override val id: String = "mediastore_primary",
        override val displayName: String = "Internal Audio Storage",
        override val isRemovable: Boolean = false,
        override val isAvailable: Boolean = true
    ) : StorageSource

    /**
     * Removable external volume (e.g. SD Card, USB OTG) indexed via MediaStore volume UUID.
     */
    data class MediaStoreVolume(
        override val id: String,
        val volumeName: String,
        val volumeUuid: String,
        override val displayName: String,
        override val isRemovable: Boolean = true,
        override val isAvailable: Boolean = true
    ) : StorageSource

    /**
     * User-selected directory tree picked via Storage Access Framework (SAF ACTION_OPEN_DOCUMENT_TREE).
     */
    data class SafDocumentTree(
        override val id: String,
        val treeUri: String,
        override val displayName: String,
        val isPersisted: Boolean = true,
        override val isRemovable: Boolean = false,
        override val isAvailable: Boolean = true
    ) : StorageSource

    /**
     * App-scoped private storage directory reserved for future desktop-to-mobile synchronization.
     */
    data class InternalSyncStorage(
        override val id: String = "internal_sync_store",
        val rootPath: String,
        override val displayName: String = "Harmonia Synced Storage",
        override val isRemovable: Boolean = false,
        override val isAvailable: Boolean = true
    ) : StorageSource
}
