package org.harmonia.player.data.local.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.harmonia.player.data.local.ArtworkProvider
import org.harmonia.player.domain.metadata.AudioMetadataReader
import org.harmonia.player.domain.metadata.MetadataNormalizer
import org.harmonia.player.domain.model.metadata.ExtractedAudioMetadata
import java.io.File

/**
 * Android platform implementation of AudioMetadataReader using MediaMetadataRetriever.
 * Encapsulates all Android-specific media inspection behind the data layer boundary.
 *
 * Guarantees:
 * 1. Safe MediaMetadataRetriever lifecycle management with guaranteed release.
 * 2. Seamless integration with existing M3 ArtworkProvider (content-addressed SHA-256 storage).
 * 3. Memory safety: bounds checking on raw artwork bytes (max 15MB) and selective retention.
 * 4. Exception isolation: corrupt, missing, or unsupported audio files return safe fallback.
 * 5. Composite artist strings preserved without premature splitting.
 */
class AndroidAudioMetadataReader(
    private val context: Context,
    private val artworkProvider: ArtworkProvider = ArtworkProvider.getInstance(context)
) : AudioMetadataReader {

    companion object {
        // Protect against unbounded memory allocation from corrupt or malformed picture payloads
        private const val MAX_ARTWORK_PAYLOAD_BYTES = 15 * 1024 * 1024
    }

    override suspend fun readMetadata(
        sourceLocator: String,
        extractArtwork: Boolean,
        includeRawArtworkBytes: Boolean
    ): ExtractedAudioMetadata = withContext(Dispatchers.IO) {
        if (sourceLocator.isBlank()) {
            return@withContext ExtractedAudioMetadata()
        }

        val retriever = MediaMetadataRetriever()
        try {
            setRetrieverDataSource(retriever, sourceLocator)

            val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val rawAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val rawAlbumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val rawGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val rawTrackNum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val rawDiscNum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            val rawDate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            val rawDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val rawBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val rawSampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            } else null
            val rawMimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            var artworkHash: String? = null
            var artworkBytes: ByteArray? = null

            if (extractArtwork) {
                try {
                    val pic = retriever.embeddedPicture
                    if (pic != null && pic.isNotEmpty() && pic.size <= MAX_ARTWORK_PAYLOAD_BYTES) {
                        val computedHash = MetadataNormalizer.computeArtworkHash(pic)
                        // Store in existing M3 ArtworkProvider if not already present
                        if (!artworkProvider.isArtworkAvailable(computedHash)) {
                            artworkProvider.storeArtwork(computedHash, pic)
                        }
                        artworkHash = computedHash
                        if (includeRawArtworkBytes) {
                            artworkBytes = pic
                        }
                    }
                } catch (e: Throwable) {
                    // Safe swallow: corrupted embedded picture must never crash extraction
                }
            }

            ExtractedAudioMetadata(
                title = MetadataNormalizer.normalizeText(rawTitle),
                artist = MetadataNormalizer.normalizeText(rawArtist), // Multi-artist composite preserved
                album = MetadataNormalizer.normalizeText(rawAlbum),
                albumArtist = MetadataNormalizer.normalizeText(rawAlbumArtist),
                genre = MetadataNormalizer.normalizeText(rawGenre),
                trackNumber = MetadataNormalizer.parseTrackNumber(rawTrackNum),
                discNumber = MetadataNormalizer.parseDiscNumber(rawDiscNum),
                year = MetadataNormalizer.parseYear(rawDate),
                durationSec = MetadataNormalizer.parseDurationSec(rawDuration),
                bitrateKbps = MetadataNormalizer.parseBitrateKbps(rawBitrate),
                sampleRateHz = MetadataNormalizer.parseSampleRateHz(rawSampleRate),
                mimeType = MetadataNormalizer.normalizeText(rawMimeType),
                artworkHash = artworkHash,
                rawArtworkBytes = artworkBytes
            )
        } catch (t: Throwable) {
            // Missing file, inaccessible URI, corrupt media, or unsupported codec
            ExtractedAudioMetadata()
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
                // Ignore release errors
            }
        }
    }

    private fun setRetrieverDataSource(retriever: MediaMetadataRetriever, sourceLocator: String) {
        val uri = Uri.parse(sourceLocator)
        val scheme = uri.scheme

        when {
            scheme == "content" -> {
                // Try ContentResolver ParcelFileDescriptor first for maximum provider compatibility
                val pfd = try {
                    context.contentResolver.openFileDescriptor(uri, "r")
                } catch (_: Throwable) {
                    null
                }

                if (pfd != null) {
                    pfd.use {
                        retriever.setDataSource(it.fileDescriptor)
                    }
                } else {
                    retriever.setDataSource(context, uri)
                }
            }
            scheme == "file" -> {
                val filePath = uri.path ?: sourceLocator
                retriever.setDataSource(filePath)
            }
            else -> {
                val directFile = File(sourceLocator)
                if (directFile.exists()) {
                    retriever.setDataSource(directFile.absolutePath)
                } else {
                    retriever.setDataSource(context, uri)
                }
            }
        }
    }
}
