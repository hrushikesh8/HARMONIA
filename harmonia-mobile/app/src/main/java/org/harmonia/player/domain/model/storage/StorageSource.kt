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
        override val id: String = ID,
        override val displayName: String = "Internal Audio Storage",
        override val isRemovable: Boolean = false,
        override val isAvailable: Boolean = true
    ) : StorageSource {
        companion object {
            const val ID = "mediastore_primary"
        }
    }

    /**
     * Removable external volume (e.g. SD Card, USB OTG) indexed via MediaStore volume UUID.
     */
    data class MediaStoreVolume(
        val volumeName: String,
        val volumeUuid: String,
        override val displayName: String,
        override val id: String = deriveCanonicalId(volumeUuid),
        override val isRemovable: Boolean = true,
        override val isAvailable: Boolean = true
    ) : StorageSource {
        companion object {
            fun normalizeVolumeUuid(volumeUuid: String): String = volumeUuid.trim().lowercase()

            fun isValidVolumeUuid(uuid: String): Boolean {
                val clean = uuid.trim()
                if (clean.isBlank()) return false
                if (clean.contains("/") || clean.contains("\\") || clean.contains("?") || clean.contains("&") || clean.contains(":")) {
                    return false
                }
                return clean.length in 2..64 && clean.all { it.isLetterOrDigit() || it == '-' || it == '_' }
            }

            fun extractVolumeUuid(uri: String, id: String): String? {
                val rawUri = uri.trim()
                val cleanUri = if (rawUri.startsWith("mediastore_vol_", ignoreCase = true)) {
                    rawUri.substring("mediastore_vol_".length).trim()
                } else {
                    rawUri
                }

                if (cleanUri.isNotBlank() && !cleanUri.startsWith("content:", ignoreCase = true) && !cleanUri.startsWith("file:", ignoreCase = true)) {
                    if (cleanUri.contains(":")) {
                        val candidate = cleanUri.substringAfter(":").trim()
                        if (isValidVolumeUuid(candidate)) {
                            return normalizeVolumeUuid(candidate)
                        }
                    } else if (isValidVolumeUuid(cleanUri)) {
                        return normalizeVolumeUuid(cleanUri)
                    }
                }

                val trimmedId = id.trim()
                if (trimmedId.startsWith("mediastore_vol_", ignoreCase = true)) {
                    val candidate = trimmedId.substring("mediastore_vol_".length).trim()
                    if (isValidVolumeUuid(candidate)) {
                        return normalizeVolumeUuid(candidate)
                    }
                } else if (isValidVolumeUuid(trimmedId)) {
                    return normalizeVolumeUuid(trimmedId)
                }

                return null
            }

            fun deriveCanonicalId(volumeUuid: String): String {
                val clean = normalizeVolumeUuid(volumeUuid)
                require(clean.isNotBlank()) { "Volume UUID cannot be blank for removable media store volume" }
                return "mediastore_vol_$clean"
            }
        }
    }

    /**
     * User-selected directory tree picked via Storage Access Framework (SAF ACTION_OPEN_DOCUMENT_TREE).
     */
    data class SafDocumentTree(
        val treeUri: String,
        override val displayName: String,
        override val id: String = deriveCanonicalId(treeUri),
        val isPersisted: Boolean = true,
        override val isRemovable: Boolean = false,
        override val isAvailable: Boolean = true
    ) : StorageSource {
        companion object {
            /**
             * Normalizes SAF tree URI representation:
             * - Trims whitespace
             * - Normalizes URI scheme to lowercase
             * - Strips trailing slash
             */
            fun normalizeTreeUri(treeUri: String): String {
                val trimmed = treeUri.trim()
                val schemeSep = trimmed.indexOf("://")
                val schemeNormalized = if (schemeSep != -1) {
                    trimmed.substring(0, schemeSep).lowercase() + trimmed.substring(schemeSep)
                } else {
                    trimmed
                }
                return schemeNormalized.trimEnd('/')
            }

            /**
             * Derives deterministic, collision-resistant canonical source ID using SHA-256
             * over normalized tree URI.
             */
            fun deriveCanonicalId(treeUri: String): String {
                val normalized = normalizeTreeUri(treeUri)
                require(normalized.isNotBlank()) { "Tree URI cannot be blank" }
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(normalized.toByteArray(Charsets.UTF_8))
                val hex = digest.joinToString("") { "%02x".format(it) }
                return "saf_tree_$hex"
            }
        }
    }

    /**
     * App-scoped private storage directory reserved for future desktop-to-mobile synchronization.
     */
    data class InternalSyncStorage(
        override val id: String = ID,
        val rootPath: String,
        override val displayName: String = "Harmonia Synced Storage",
        override val isRemovable: Boolean = false,
        override val isAvailable: Boolean = true
    ) : StorageSource {
        companion object {
            const val ID = "internal_sync_store"
        }
    }
}
