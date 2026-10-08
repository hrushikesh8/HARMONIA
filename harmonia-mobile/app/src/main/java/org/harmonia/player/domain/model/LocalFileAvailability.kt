package org.harmonia.player.domain.model

/**
 * Physical bitstream availability of a track on the local mobile device.
 * Adheres to ADR-025 (Two-Tier Identity Model).
 */
enum class LocalFileAvailability {
    /**
     * File exists locally, verified by hash or recent stat check. Ready for instant offline playback.
     */
    AVAILABLE_VERIFIED,

    /**
     * Track metadata exists in library catalog, but no local file is downloaded.
     */
    UNAVAILABLE,

    /**
     * File was registered but is currently missing (e.g. SD card removed or deleted via external file manager).
     */
    MISSING,

    /**
     * File path exists but has not been verified for bitstream integrity or readability.
     */
    UNVERIFIED,

    /**
     * File exists but is corrupt or failed integrity validation.
     */
    CORRUPT
}
