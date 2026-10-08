package org.harmonia.player.domain.model

/**
 * Pure domain model representing an Artist.
 */
data class Artist(
    val id: String,
    val name: String,
    val sortName: String = name,
    val mbid: String? = null,
    val artworkHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
