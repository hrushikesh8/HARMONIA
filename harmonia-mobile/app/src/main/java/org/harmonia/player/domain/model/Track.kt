package org.harmonia.player.domain.model

/**
 * Pure domain model representing an audio track's canonical metadata.
 * Free of any Android platform or framework dependencies.
 */
data class Track(
    val id: String,
    val title: String,
    val albumId: String? = null,
    val artistId: String? = null,
    val albumTitle: String? = null,
    val artistName: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int = 1,
    val durationSec: Double,
    val year: Int? = null,
    val genre: String? = null,
    val mbid: String? = null,
    val integratedLufs: Double? = null,
    val truePeakDbtp: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Decoupled bitstream presence per ADR-025
    val localFile: LocalFile? = null,
    val availability: LocalFileAvailability = localFile?.let {
        if (it.isVerified) LocalFileAvailability.AVAILABLE_VERIFIED
        else LocalFileAvailability.UNVERIFIED
    } ?: LocalFileAvailability.UNAVAILABLE
) {
    val isAvailableOffline: Boolean
        get() = availability == LocalFileAvailability.AVAILABLE_VERIFIED ||
                availability == LocalFileAvailability.UNVERIFIED
}
