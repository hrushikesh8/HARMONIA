package org.harmonia.player.presentation.library

import java.net.URLDecoder

internal const val UNKNOWN_FOLDER = "Other"
internal const val STORAGE_ROOT_FOLDER = "Storage root"

/**
 * Folder a track's file lives in, for the Library "Folders" tab.
 *
 * - MediaStore URIs use the folder looked up from MediaStore ([resolvedFolders]).
 * - SAF document URIs decode their document id ("primary:Music/Album/song.mp3" -> "Music/Album").
 * - Plain paths and file:// URIs use their parent directory.
 */
internal fun folderPathFor(fileUri: String?, resolvedFolders: Map<String, String>): String {
    if (fileUri.isNullOrBlank()) return UNKNOWN_FOLDER
    resolvedFolders[fileUri]?.let { return it }

    val documentMarker = "/document/"
    if (fileUri.startsWith("content://")) {
        if (!fileUri.contains(documentMarker)) return UNKNOWN_FOLDER
        val documentId = decodeUriComponent(fileUri.substringAfterLast(documentMarker))
        val path = documentId.substringAfter(':', documentId)
        return path.substringBeforeLast('/', "").ifBlank { STORAGE_ROOT_FOLDER }
    }

    val path = decodeUriComponent(fileUri.removePrefix("file://"))
    return path.substringBeforeLast('/', "").ifBlank { STORAGE_ROOT_FOLDER }
}

/**
 * Last path segment used as the folder's display name.
 */
internal fun folderDisplayName(folderPath: String): String =
    folderPath.trimEnd('/').substringAfterLast('/').ifBlank { folderPath }

private fun decodeUriComponent(value: String): String =
    try {
        // URLDecoder treats '+' as a space; file names may legitimately contain '+'.
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        value
    }
