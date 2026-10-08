package org.harmonia.player.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation
import org.harmonia.player.domain.model.LocalFile
import org.harmonia.player.domain.model.LocalFileAvailability
import org.harmonia.player.domain.model.StorageType
import org.harmonia.player.domain.model.Track

/**
 * Composite Room relation model combining a track with its artist, album, and local bitstream files.
 */
data class TrackWithDetails(
    @Embedded
    val track: TrackEntity,

    @Relation(
        parentColumn = "artist_id",
        entityColumn = "id"
    )
    val artist: ArtistEntity? = null,

    @Relation(
        parentColumn = "album_id",
        entityColumn = "id"
    )
    val album: AlbumEntity? = null,

    @Relation(
        parentColumn = "id",
        entityColumn = "track_id"
    )
    val localFiles: List<LocalFileEntity> = emptyList()
) {
    fun toDomainTrack(): Track {
        val primaryLocalFile = localFiles.firstOrNull()
        val domainLocalFile = primaryLocalFile?.let {
            LocalFile(
                id = it.id,
                trackId = it.trackId,
                storageType = try {
                    StorageType.valueOf(it.storageType)
                } catch (e: Exception) {
                    StorageType.INTERNAL_SYNC
                },
                fileUri = it.fileUri,
                fileSize = it.fileSize,
                fileHash = it.fileHash,
                format = it.format,
                bitrate = it.bitrate,
                sampleRate = it.sampleRate,
                bitDepth = it.bitDepth,
                channels = it.channels,
                isLossless = it.isLossless,
                isVerified = it.availabilityStatus == "AVAILABLE",
                verifiedAt = it.verifiedAt,
                addedAt = it.addedAt
            )
        }

        val availability = when {
            primaryLocalFile == null -> LocalFileAvailability.UNAVAILABLE
            primaryLocalFile.availabilityStatus == "AVAILABLE" -> LocalFileAvailability.AVAILABLE_VERIFIED
            primaryLocalFile.availabilityStatus == "MISSING" -> LocalFileAvailability.MISSING
            primaryLocalFile.availabilityStatus == "CORRUPT" -> LocalFileAvailability.CORRUPT
            else -> LocalFileAvailability.UNVERIFIED
        }

        return Track(
            id = track.id,
            title = track.title,
            albumId = track.albumId,
            artistId = track.artistId,
            albumTitle = album?.title,
            artistName = artist?.name,
            trackNumber = track.trackNumber,
            discNumber = track.discNumber,
            durationSec = track.durationSec,
            year = track.year,
            genre = track.genre,
            mbid = track.mbid,
            integratedLufs = track.integratedLufs,
            truePeakDbtp = track.truePeakDbtp,
            createdAt = track.createdAt,
            updatedAt = track.updatedAt,
            artworkHash = album?.artworkHash,
            localFile = domainLocalFile,
            availability = availability
        )
    }
}
