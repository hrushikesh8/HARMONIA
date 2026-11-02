package org.harmonia.player.domain.enrichment

/**
 * Metadata provenance levels ensuring local-first invariants per ADR-023.
 * Higher priority values strictly take precedence over lower priority values.
 */
enum class MetadataProvenance(val priority: Int) {
    USER_EDITED(100),
    LOCAL_BITSTREAM_TAGS(80),
    ONLINE_ENRICHMENT(50),
    DEFAULT_FALLBACK(10)
}

/**
 * Result of an optional online enrichment lookup.
 */
data class EnrichedMetadata(
    val mbid: String? = null,
    val releaseYear: Int? = null,
    val genre: String? = null,
    val albumArtist: String? = null,
    val soundtrackTitle: String? = null,
    val provenance: MetadataProvenance = MetadataProvenance.ONLINE_ENRICHMENT
)
