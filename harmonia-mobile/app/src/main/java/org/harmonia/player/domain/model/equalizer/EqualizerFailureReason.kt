package org.harmonia.player.domain.model.equalizer

/**
 * Diagnostically distinct reasons why an equalizer effect could not be established or operated.
 */
sealed interface EqualizerFailureReason {
    /** Output route or hardware does not support the effect (e.g. offload, bit-perfect DAC, or ROM lacks EQ). */
    data class UnsupportedHardwareOrRoute(val message: String? = null) : EqualizerFailureReason

    /** Audio session ID was rejected by the audio server, or an invalid parameter was provided. */
    data class InvalidSessionOrArgument(val message: String? = null) : EqualizerFailureReason

    /** Native effect state was invalid or accessed when dead. */
    data class NativeIllegalState(val message: String? = null) : EqualizerFailureReason

    /** Unexpected audio server binder or JNI runtime communication failure. */
    data class UnexpectedNativeError(val message: String? = null, val causeName: String? = null) : EqualizerFailureReason
}
