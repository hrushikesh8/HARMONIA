package org.harmonia.player.presentation.navigation

import kotlinx.serialization.Serializable

sealed interface HarmoniaNavDestination {
    @Serializable
    data object Home : HarmoniaNavDestination

    @Serializable
    data object Library : HarmoniaNavDestination

    @Serializable
    data object Settings : HarmoniaNavDestination
}
