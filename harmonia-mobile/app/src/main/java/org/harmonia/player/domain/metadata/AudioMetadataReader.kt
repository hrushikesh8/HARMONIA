package org.harmonia.player.domain.metadata

import org.harmonia.player.domain.model.metadata.ExtractedAudioMetadata

/**
 * Domain-neutral boundary for extracting audio metadata and embedded artwork
 * from an accessible local media bitstream.
 *
 * Guarantees:
 * 1. Pure domain contract free of Android platform dependencies.
 * 2. Never throws uncaught exceptions on corrupt, missing, or unsupported media.
 * 3. Returns factual nullable metadata representation.
 */
interface AudioMetadataReader {

    /**
     * Extracts and normalizes metadata from a local source locator (e.g. content URI or file path).
     *
     * @param sourceLocator Platform URI string (content:// or file://) or accessible locator.
     * @param extractArtwork When true, extracts embedded picture bytes and integrates with ArtworkProvider.
     * @param includeRawArtworkBytes When true, retains raw byte array in ExtractedAudioMetadata.
     * @return Factual ExtractedAudioMetadata with nullable fields.
     */
    suspend fun readMetadata(
        sourceLocator: String,
        extractArtwork: Boolean = true,
        includeRawArtworkBytes: Boolean = false
    ): ExtractedAudioMetadata
}
