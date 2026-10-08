package org.harmonia.player.presentation.navigation

import kotlinx.serialization.Serializable

sealed interface HarmoniaNavDestination {
    @Serializable
    data object Home : HarmoniaNavDestination

    @Serializable
    data object Library : HarmoniaNavDestination

    @Serializable
    data object Playlists : HarmoniaNavDestination

    @Serializable
    data object Search : HarmoniaNavDestination

    @Serializable
    data object Settings : HarmoniaNavDestination

    @Serializable
    data class AlbumDetail(val albumId: String) : HarmoniaNavDestination

    @Serializable
    data class ArtistDetail(val artistId: String) : HarmoniaNavDestination

    @Serializable
    data class PlaylistDetail(val playlistId: String) : HarmoniaNavDestination
}
