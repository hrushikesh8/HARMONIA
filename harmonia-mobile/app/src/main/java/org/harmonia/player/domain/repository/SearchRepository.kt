package org.harmonia.player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.Track

/**
 * Pure domain repository contract defining the search interface foundation for M3-B.
 * Searches across tracks, albums, artists, and playlists.
 */
interface SearchRepository {
    fun searchTracks(query: String): Flow<List<Track>>
    fun searchAlbums(query: String): Flow<List<Album>>
    fun searchArtists(query: String): Flow<List<Artist>>
    fun searchPlaylists(query: String): Flow<List<Playlist>>
}
