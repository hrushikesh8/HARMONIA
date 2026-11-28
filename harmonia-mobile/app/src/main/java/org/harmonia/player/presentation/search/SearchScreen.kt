package org.harmonia.player.presentation.search

import org.harmonia.player.presentation.components.AlbumListRow
import org.harmonia.player.presentation.components.ArtistListRow
import org.harmonia.player.presentation.components.CollectionListRow
import org.harmonia.player.presentation.components.SongListRow
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.hairlineBorder
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.components.TrackMenuButton
import org.harmonia.player.presentation.util.TrackDisplayUtils
import java.util.Locale

enum class SearchFilter(val label: String) {
    ALL("All"),
    TRACKS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists")
}

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(),
    onTrackClick: (List<Track>, Int) -> Unit = { _, _ -> },
    onTrackMenu: ((Track) -> Unit)? = null,
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
    onBrowse: (SearchBrowseTarget) -> Unit = {}
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val keyboard = LocalSoftwareKeyboardController.current

    var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }

    // Opening a result also remembers the search, like Apple Music's "Recently Searched".
    val openTrack: (List<Track>, Int) -> Unit = { tracks, index -> viewModel.commitSearch(); onTrackClick(tracks, index) }
    val openAlbum: (Album) -> Unit = { viewModel.commitSearch(); onAlbumClick(it) }
    val openArtist: (Artist) -> Unit = { viewModel.commitSearch(); onArtistClick(it) }
    val openPlaylist: (Playlist) -> Unit = { viewModel.commitSearch(); onPlaylistClick(it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
            .padding(horizontal = spacing.standard)
    ) {
        Spacer(modifier = Modifier.height(spacing.standard))
        Text(
            text = "Search",
            style = MaterialTheme.typography.displayMedium,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(spacing.medium))

        TextField(
            value = query,
            onValueChange = { viewModel.onQueryChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Search input field" },
            placeholder = { Text("Songs, artists, albums, playlists", color = colors.textSecondary) },
            leadingIcon = {
                Icon(imageVector = HarmoniaIcons.Search, contentDescription = null, tint = colors.textSecondary)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearQuery() },
                        modifier = Modifier.semantics { contentDescription = "Clear search query" }
                    ) {
                        Icon(imageVector = HarmoniaIcons.Close, contentDescription = null, tint = colors.textSecondary)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                viewModel.commitSearch()
                keyboard?.hide()
            }),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainer,
                unfocusedContainerColor = colors.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = colors.primarySignal,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )

        if (query.isNotBlank()) {
            Spacer(modifier = Modifier.height(spacing.medium))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                SearchFilter.entries.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            !isSelected -> colors.textPrimary
                            colors.isDark -> colors.backgroundRoot
                            else -> colors.surfaceElevated
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) colors.primarySignal else colors.surfaceContainer)
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        when {
            query.isBlank() -> SearchStartState(
                recentSearches = recentSearches,
                onRecentClick = { viewModel.useRecentSearch(it) },
                onRemoveRecent = { viewModel.removeRecentSearch(it) },
                onClearRecent = { viewModel.clearRecentSearches() },
                onBrowse = onBrowse
            )
            uiState.isEmpty && uiState.isSearching -> Text(
                text = "Searching…",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = spacing.standard)
            )
            uiState.isEmpty -> NoResultsState(query = query)
            else -> SearchResultsList(
                uiState = uiState,
                filter = selectedFilter,
                onTrackClick = openTrack,
                onTrackMenu = onTrackMenu,
                onAlbumClick = openAlbum,
                onArtistClick = openArtist,
                onPlaylistClick = openPlaylist
            )
        }
    }
}

/** Library shortcuts shown on the empty Search screen. */
enum class SearchBrowseTarget(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites"),
    GENRES("Genres"),
    YEARS("Years")
}

/** What Search shows before you type: recent searches, then Browse tiles (Apple Music style). */
@Composable
private fun SearchStartState(
    recentSearches: List<String>,
    onRecentClick: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onBrowse: (SearchBrowseTarget) -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.bottomScaffold)
    ) {
        if (recentSearches.isNotEmpty()) {
            item(key = "recent_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primarySignal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClearRecent)
                            .padding(horizontal = spacing.small, vertical = spacing.small)
                            .semantics { contentDescription = "Clear recent searches" }
                    )
                }
            }
            items(recentSearches, key = { "recent_$it" }) { recent ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRecentClick(recent) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.Timer,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.medium))
                    Text(
                        text = recent,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRemoveRecent(recent) },
                        modifier = Modifier.semantics { contentDescription = "Remove $recent from recent searches" }
                    ) {
                        Icon(
                            imageVector = HarmoniaIcons.Close,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            item(key = "recent_spacer") { Spacer(modifier = Modifier.height(spacing.large)) }
        }

        item(key = "browse_header") {
            Text(
                text = "Browse your library",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = spacing.medium)
            )
        }
        item(key = "browse_tiles") {
            val targets = SearchBrowseTarget.entries
            Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
                targets.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
                        pair.forEach { target ->
                            BrowseTile(target = target, onClick = { onBrowse(target) }, modifier = Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseTile(target: SearchBrowseTarget, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HarmoniaThemeTokens.colors
    val icon = when (target) {
        SearchBrowseTarget.SONGS -> HarmoniaIcons.MusicNote
        SearchBrowseTarget.ALBUMS -> HarmoniaIcons.Album
        SearchBrowseTarget.ARTISTS -> HarmoniaIcons.Person
        SearchBrowseTarget.PLAYLISTS -> HarmoniaIcons.Playlists
        SearchBrowseTarget.FAVORITES -> HarmoniaIcons.Favorite
        SearchBrowseTarget.GENRES -> HarmoniaIcons.Genre
        SearchBrowseTarget.YEARS -> HarmoniaIcons.Calendar
    }
    Box(
        modifier = modifier
            .height(88.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.primarySignal.copy(alpha = if (colors.isDark) 0.18f else 0.12f))
            .clickable(onClick = onClick)
            .padding(14.dp)
            .semantics { contentDescription = "Browse ${target.label}" }
    ) {
        Text(
            text = target.label,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primarySignal,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(30.dp)
        )
    }
}

@Composable
private fun NoResultsState(query: String) {
    val colors = HarmoniaThemeTokens.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = HarmoniaIcons.MusicNote,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No matches for \"$query\"",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Check the spelling or try another word",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    uiState: SearchUiState,
    filter: SearchFilter,
    onTrackClick: (List<Track>, Int) -> Unit,
    onTrackMenu: ((Track) -> Unit)?,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit
) {
    val spacing = HarmoniaThemeTokens.spacing

    val showTracks = filter == SearchFilter.ALL || filter == SearchFilter.TRACKS
    val showAlbums = filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS
    val showArtists = filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS
    val showPlaylists = filter == SearchFilter.ALL || filter == SearchFilter.PLAYLISTS

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.bottomScaffold),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Tracks Category
        if (showTracks && uiState.matchingTracks.isNotEmpty()) {
            item(key = "header_tracks") {
                CategoryHeader(title = "Songs", count = uiState.matchingTracks.size)
            }
            itemsIndexed(uiState.matchingTracks, key = { _, track -> "trk_${track.id}" }) { index, track ->
                SongListRow(
                    track = track,
                    onClick = { onTrackClick(uiState.matchingTracks, index) },
                    onMenuClick = onTrackMenu?.let { menu -> { menu(track) } }
                )
            }
        }

        // Albums Category
        if (showAlbums && uiState.matchingAlbums.isNotEmpty()) {
            item(key = "header_albums") {
                CategoryHeader(title = "Albums", count = uiState.matchingAlbums.size)
            }
            items(uiState.matchingAlbums, key = { "alb_${it.id}" }) { album ->
                AlbumListRow(album = album, onClick = { onAlbumClick(album) })
            }
        }

        // Artists Category
        if (showArtists && uiState.matchingArtists.isNotEmpty()) {
            item(key = "header_artists") {
                CategoryHeader(title = "Artists", count = uiState.matchingArtists.size)
            }
            items(uiState.matchingArtists, key = { "art_${it.id}" }) { artist ->
                ArtistListRow(artist = artist, onClick = { onArtistClick(artist) }, subtitle = "Artist")
            }
        }

        // Playlists Category
        if (showPlaylists && uiState.matchingPlaylists.isNotEmpty()) {
            item(key = "header_playlists") {
                CategoryHeader(title = "Playlists", count = uiState.matchingPlaylists.size)
            }
            items(uiState.matchingPlaylists, key = { "pl_${it.id}" }) { playlist ->
                CollectionListRow(
                    title = playlist.name,
                    subtitle = "Playlist · ${playlist.trackCount} ${if (playlist.trackCount == 1) "song" else "songs"}",
                    artworkHash = playlist.artworkHash,
                    placeholderIcon = HarmoniaIcons.Playlists,
                    description = "Playlist ${playlist.name}",
                    onClick = { onPlaylistClick(playlist) }
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String, count: Int) {
    val colors = HarmoniaThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = colors.primarySignal
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary
        )
    }
}
