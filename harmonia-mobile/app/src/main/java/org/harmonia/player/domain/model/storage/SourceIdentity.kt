package org.harmonia.player.domain.model.storage

/**
 * Storage category for file location identification.
 */
enum class SourceType {
    MEDIA_STORE_PRIMARY,
    MEDIA_STORE_VOLUME,
    SAF_DOCUMENT_TREE,
    INTERNAL_SYNC
}

/**
 * Location Identity representing where an audio file physically resides on Android storage.
 * Injective and composite: combines source type, volume identity, and relative path / document ID.
 *
 * Guarantees:
 * - Does NOT use MediaStore _ID as durable identity.
 * - Does NOT use platform URIs as content identity.
 * - Does NOT use metadata as identity.
 */
data class SourceIdentity(
    val sourceType: SourceType,
    val volumeId: String,
    val relativePathOrDocId: String
) {
    /**
     * Serialized, injective, delimiter-safe key for comparison and indexing.
     * Uses length-framing to ensure collision-free parsing.
     */
    fun toSerializedKey(): String {
        val cleanVolume = volumeId.trim()
        val cleanPath = relativePathOrDocId.trim()
        return "${sourceType.name}:${cleanVolume.length}:$cleanVolume:${cleanPath.length}:$cleanPath"
    }

    companion object {
        fun fromSerializedKey(key: String): SourceIdentity {
            val firstColon = key.indexOf(':')
            require(firstColon != -1) { "Invalid SourceIdentity key (no type delimiter): $key" }
            val typeStr = key.substring(0, firstColon)
            val type = SourceType.valueOf(typeStr)

            val secondColon = key.indexOf(':', firstColon + 1)
            require(secondColon != -1) { "Invalid SourceIdentity key (no volume length delimiter): $key" }
            val volLen = key.substring(firstColon + 1, secondColon).toInt()

            val volStart = secondColon + 1
            val volEnd = volStart + volLen
            require(volEnd < key.length && key[volEnd] == ':') { "Expected ':' delimiter after volume string" }
            val volume = key.substring(volStart, volEnd)

            val thirdColon = volEnd
            val fourthColon = key.indexOf(':', thirdColon + 1)
            require(fourthColon != -1) { "Invalid SourceIdentity key (no path length delimiter): $key" }
            val pathLen = key.substring(thirdColon + 1, fourthColon).toInt()

            val pathStart = fourthColon + 1
            val pathEnd = pathStart + pathLen
            require(pathEnd == key.length) { "Path length mismatch with key end: expected $pathEnd, got ${key.length}" }
            val path = key.substring(pathStart, pathEnd)

            return SourceIdentity(type, volume, path)
        }

        fun mediaStorePrimary(relativePath: String): SourceIdentity =
            SourceIdentity(SourceType.MEDIA_STORE_PRIMARY, "primary", relativePath)

        fun mediaStoreVolume(volumeUuid: String, relativePath: String): SourceIdentity =
            SourceIdentity(SourceType.MEDIA_STORE_VOLUME, volumeUuid, relativePath)

        fun safDocument(treeUri: String, documentId: String): SourceIdentity =
            SourceIdentity(SourceType.SAF_DOCUMENT_TREE, treeUri, documentId)

        fun internalSync(relativePath: String): SourceIdentity =
            SourceIdentity(SourceType.INTERNAL_SYNC, "internal", relativePath)
    }
}

/**
 * Content Identity representing the immutable cryptographic digest and size of an audio bitstream.
 * Universal, permanent, and platform-agnostic.
 */
data class ContentIdentity(
    val fileHash: String,
    val fileSize: Long
)
