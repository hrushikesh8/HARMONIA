package org.harmonia.player.domain.model

/**
 * Pure domain model representing an Album.
 */
data class Album(
    val id: String,
    val title: String,
    val artistId: String? = null,
    val artistName: String? = null,
    val year: Int? = null,
    val totalTracks: Int? = null,
    val totalDiscs: Int? = null,
    val mbid: String? = null,
    val artworkHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
