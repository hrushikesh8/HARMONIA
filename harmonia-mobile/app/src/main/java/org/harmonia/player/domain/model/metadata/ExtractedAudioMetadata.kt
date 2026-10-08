package org.harmonia.player.domain.model.metadata

/**
 * Pure domain representation of factual audio metadata extracted from a local bitstream.
 * Strictly decoupled from Android platform types (MediaMetadataRetriever, Uri, Bitmap, Room).
 *
 * All fields are explicitly nullable to reflect real-world audio files where metadata tags
 * may be omitted, partial, or malformed.
 */
data class ExtractedAudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val durationSec: Double? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val mimeType: String? = null,
    val artworkHash: String? = null,
    val rawArtworkBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExtractedAudioMetadata

        if (title != other.title) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (albumArtist != other.albumArtist) return false
        if (genre != other.genre) return false
        if (trackNumber != other.trackNumber) return false
        if (discNumber != other.discNumber) return false
        if (year != other.year) return false
        if (durationSec != other.durationSec) return false
        if (bitrateKbps != other.bitrateKbps) return false
        if (sampleRateHz != other.sampleRateHz) return false
        if (mimeType != other.mimeType) return false
        if (artworkHash != other.artworkHash) return false
        if (rawArtworkBytes != null) {
            if (other.rawArtworkBytes == null) return false
            if (!rawArtworkBytes.contentEquals(other.rawArtworkBytes)) return false
        } else if (other.rawArtworkBytes != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (albumArtist?.hashCode() ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (trackNumber ?: 0)
        result = 31 * result + (discNumber ?: 0)
        result = 31 * result + (year ?: 0)
        result = 31 * result + (durationSec?.hashCode() ?: 0)
        result = 31 * result + (bitrateKbps ?: 0)
        result = 31 * result + (sampleRateHz ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + (artworkHash?.hashCode() ?: 0)
        result = 31 * result + (rawArtworkBytes?.contentHashCode() ?: 0)
        return result
    }
}
