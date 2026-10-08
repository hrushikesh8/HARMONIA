package org.harmonia.player.domain.model

/**
 * Pure domain model representing a Playlist.
 */
data class Playlist(
    val id: String,
    val title: String,
    val description: String? = null,
    val artworkHash: String? = null,
    val isSmart: Boolean = false,
    val criteriaJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val versionVector: String = "1"
)

data class PlaylistItem(
    val id: String,
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)
