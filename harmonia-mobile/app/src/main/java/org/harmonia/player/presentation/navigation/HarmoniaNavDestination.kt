package org.harmonia.player.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe routes for the app's Navigation Compose graph (see MainActivity).
 *
 * Each bottom tab is a nested graph ([HomeTab], [SearchTab], [LibraryTab]) whose
 * start screen is the matching top-level destination. Detail screens (album, artist, playlist,
 * profile, settings, car mode) are pushed onto whichever tab is open, and every tab keeps its own
 * back stack and scroll position when you switch away and back.
 */
sealed interface HarmoniaNavDestination {
    // Tab graphs
    @Serializable
    data object HomeTab : HarmoniaNavDestination

    @Serializable
    data object SearchTab : HarmoniaNavDestination

    @Serializable
    data object LibraryTab : HarmoniaNavDestination

    // Tab start screens
    @Serializable
    data object Home : HarmoniaNavDestination

    @Serializable
    data object Library : HarmoniaNavDestination

    @Serializable
    data object Playlists : HarmoniaNavDestination

    @Serializable
    data object Search : HarmoniaNavDestination

    // Pushed screens
    @Serializable
    data object Settings : HarmoniaNavDestination

    @Serializable
    data object Profile : HarmoniaNavDestination

    @Serializable
    data object CarMode : HarmoniaNavDestination

    /** One Library category (songs, albums, artists, folders, favorites) opened from the Library list. */
    @Serializable
    data class LibraryCategory(val tab: String) : HarmoniaNavDestination

    @Serializable
    data class AlbumDetail(val albumId: String) : HarmoniaNavDestination

    @Serializable
    data class ArtistDetail(val artistId: String) : HarmoniaNavDestination

    @Serializable
    data class PlaylistDetail(val playlistId: String) : HarmoniaNavDestination

    /** Browse by genre and year (task 4.2). */
    @Serializable
    data object Genres : HarmoniaNavDestination

    @Serializable
    data class GenreDetail(val name: String) : HarmoniaNavDestination

    @Serializable
    data object Years : HarmoniaNavDestination

    @Serializable
    data class DecadeDetail(val decade: Int) : HarmoniaNavDestination
}
