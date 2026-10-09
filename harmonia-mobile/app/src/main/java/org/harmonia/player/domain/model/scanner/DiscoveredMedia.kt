package org.harmonia.player.domain.model.scanner

import org.harmonia.player.domain.model.storage.SourceIdentity
import org.harmonia.player.domain.model.storage.SourceType

/**
 * Provider-neutral domain candidate discovered on Android device storage.
 * Completely free of Android framework types (zero MediaStore, zero Uri, zero DocumentFile).
 *
 * Distinctly separates:
 * 1. Source Identity: Logical storage source + relative path or document identity.
 * 2. Runtime Locator: Actual platform URI or accessible file path (content://, file://).
 * 3. Discovery Locator: Ephemeral MediaStore _ID or SAF cursor ID (used only during discovery, NEVER as durable PK).
 * 4. Freshness State: Size and lastModifiedEpochSec used for fast change detection.
 * 5. Generation Watermark: Optional provider-level monotonic counter (e.g. MediaStore GENERATION_MODIFIED).
 */
data class DiscoveredMedia(
    val sourceIdentity: SourceIdentity,
    val runtimeLocator: String,
    val discoveryLocator: String? = null,
    val displayName: String,
    val mimeType: String? = null,
    val sizeBytes: Long,
    val lastModifiedEpochSec: Long,
    val sourceType: SourceType,
    val isAccessible: Boolean = true,
    val generation: Long? = null
)
