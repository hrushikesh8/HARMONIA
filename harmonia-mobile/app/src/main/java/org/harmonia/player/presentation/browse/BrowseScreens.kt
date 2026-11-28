package org.harmonia.player.presentation.browse

import org.harmonia.player.presentation.components.SongSwipeActions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.AlbumGridItem
import org.harmonia.player.presentation.components.AlbumGridMinCellWidth
import org.harmonia.player.presentation.components.AlphabetScroller
import org.harmonia.player.presentation.components.CollectionListRow
import org.harmonia.player.presentation.components.SongListRow
import org.harmonia.player.presentation.components.alphabetPositions
import org.harmonia.player.presentation.components.shouldShowAlphabetBar
import org.harmonia.player.presentation.library.LibraryViewModel

/*
 * Genres and Years pages (task 4.2). They read the library already loaded by LibraryViewModel, so
 * they open instantly and stay in step with scans.
 */

private val BottomInset = 120.dp

@Composable
private fun BrowseTopBar(title: String, subtitle: String?, onBack: () -> Unit) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.standard, bottom = spacing.small)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
            Icon(HarmoniaIcons.ArrowBack, contentDescription = null, tint = colors.textPrimary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun BrowseEmpty(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = HarmoniaThemeTokens.colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** Play and Shuffle, side by side (same look as the album page). */
@Composable
fun PlayShuffleRow(tracks: List<Track>, what: String, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val spacing = HarmoniaThemeTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        BrowseActionButton("Play", HarmoniaIcons.Play, filled = true, enabled = tracks.isNotEmpty(), description = "Play $what", onClick = onPlay, modifier = Modifier.weight(1f))
        BrowseActionButton("Shuffle", HarmoniaIcons.Shuffle, filled = false, enabled = tracks.isNotEmpty(), description = "Shuffle $what", onClick = onShuffle, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BrowseActionButton(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HarmoniaThemeTokens.colors
    val onFilled = if (colors.isDark) colors.backgroundRoot else colors.surfaceElevated
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(48.dp)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) colors.primarySignal else colors.surfaceContainer,
            contentColor = if (filled) onFilled else colors.primarySignal
        )
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

// ── Genres ────────────────────────────────────────────────────────────────

@Composable
fun GenresScreen(
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    onGenreClick: (String) -> Unit
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    val genres = remember(uiState.tracks) { groupByGenre(uiState.tracks) }
    val positions = remember(genres) { alphabetPositions(genres.map { it.name }) }
    val showBar = shouldShowAlphabetBar(positions)
    val listState = rememberLazyListState()
    val spacing = HarmoniaThemeTokens.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HarmoniaThemeTokens.colors.backgroundRoot)
            .padding(horizontal = spacing.standard)
    ) {
        BrowseTopBar(title = "Genres", subtitle = if (genres.isEmpty()) null else "${genres.size} genres", onBack = onBack)
        if (genres.isEmpty()) {
            BrowseEmpty("None of your songs have a genre in their tags yet.")
            return@Column
        }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = if (showBar) 22.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = BottomInset)
            ) {
                items(genres, key = { it.name.lowercase() }) { genre ->
                    CollectionListRow(
                        title = genre.name,
                        subtitle = genreSubtitle(genre),
                        artworkHash = genre.artworkHash,
                        placeholderIcon = HarmoniaIcons.MusicNote,
                        description = "Genre ${genre.name}, ${genreSubtitle(genre)}",
                        onClick = { onGenreClick(genre.name) }
                    )
                }
            }
            if (showBar) {
                AlphabetScroller(
                    positions = positions,
                    onJumpTo = { listState.scrollToItem(it) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(bottom = BottomInset)
                )
            }
        }
    }
}

@Composable
fun GenreDetailScreen(
    genreName: String,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onTrackMenu: ((Track) -> Unit)?,
    onAlbumClick: (Album) -> Unit,
    onPlayNext: ((Track) -> Unit)? = null
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    val genre = remember(uiState.tracks, genreName) {
        groupByGenre(uiState.tracks).firstOrNull { it.name.equals(genreName, ignoreCase = true) }
    }
    val tracks = genre?.tracks.orEmpty()
    val albums = remember(tracks, uiState.albums) { albumsFor(tracks, uiState.albums) }
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
            .padding(horizontal = spacing.standard),
        contentPadding = PaddingValues(bottom = BottomInset)
    ) {
        item { BrowseTopBar(title = genre?.name ?: genreName, subtitle = genre?.let { genreSubtitle(it) }, onBack = onBack) }
        if (genre == null) {
            item { BrowseEmpty("No songs in this genre right now.") }
            return@LazyColumn
        }
        item {
            PlayShuffleRow(tracks, what = "${genre.name} songs", onPlay = { onPlay(tracks, 0) }, onShuffle = { onShuffle(tracks) })
        }
        if (albums.isNotEmpty()) {
            item { SectionTitle("Albums") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(albums, key = { it.id }) { album ->
                        AlbumGridItem(album = album, onClick = { onAlbumClick(album) }, modifier = Modifier.width(150.dp))
                    }
                }
            }
        }
        item { SectionTitle("Songs") }
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            SongSwipeActions(onPlayNext = onPlayNext?.let { action -> { action(track) } }) {
                SongListRow(
                    track = track,
                    onClick = { onPlay(tracks, index) },
                    onMenuClick = onTrackMenu?.let { menu -> { menu(track) } }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val spacing = HarmoniaThemeTokens.spacing
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = HarmoniaThemeTokens.colors.textPrimary,
        modifier = Modifier
            .padding(top = spacing.large, bottom = spacing.small)
            .semantics { heading() }
    )
}

// ── Years ─────────────────────────────────────────────────────────────────

@Composable
fun YearsScreen(
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    onDecadeClick: (Int) -> Unit
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    val decades = remember(uiState.tracks, uiState.albums) { groupByDecade(uiState.tracks, uiState.albums) }
    val undated = uiState.tracks.size - decades.sumOf { it.trackCount }
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = AlbumGridMinCellWidth),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
            .padding(horizontal = spacing.standard),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(bottom = BottomInset)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            BrowseTopBar(title = "Years", subtitle = if (decades.isEmpty()) null else "By decade", onBack = onBack)
        }
        if (decades.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { BrowseEmpty("None of your songs have a year in their tags yet.") }
        } else {
            items(decades, key = { it.decade }) { decade ->
                DecadeTile(decade = decade, onClick = { onDecadeClick(decade.decade) })
            }
            if (undated > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "${songCount(undated)} without a year aren't shown here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = spacing.small)
                    )
                }
            }
        }
    }
}

@Composable
private fun DecadeTile(decade: DecadeGroup, onClick: () -> Unit) {
    val colors = HarmoniaThemeTokens.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(colors.primarySignal.copy(alpha = 0.35f), colors.surfaceContainer)
                )
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "${decade.label}, ${songCount(decade.trackCount)}"
            }
            .padding(14.dp)
    ) {
        Text(
            text = decade.label,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Text(
            text = songCount(decade.trackCount),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
fun DecadeScreen(
    decade: Int,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onTrackMenu: ((Track) -> Unit)?,
    onPlayNext: ((Track) -> Unit)? = null
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    val group = remember(uiState.tracks, uiState.albums, decade) {
        groupByDecade(uiState.tracks, uiState.albums).firstOrNull { it.decade == decade }
    }
    val allTracks = group?.tracks.orEmpty()
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
            .padding(horizontal = spacing.standard),
        contentPadding = PaddingValues(bottom = BottomInset)
    ) {
        item { BrowseTopBar(title = decadeLabel(decade), subtitle = group?.let { songCount(it.trackCount) }, onBack = onBack) }
        if (group == null) {
            item { BrowseEmpty("No songs from the ${decadeLabel(decade)} right now.") }
            return@LazyColumn
        }
        item {
            PlayShuffleRow(allTracks, what = "songs from the ${group.label}", onPlay = { onPlay(allTracks, 0) }, onShuffle = { onShuffle(allTracks) })
        }
        group.years.forEach { yearGroup ->
            item(key = "year_${yearGroup.year}") {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = spacing.large, bottom = spacing.small)) {
                    Text(
                        text = yearGroup.year.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text(text = songCount(yearGroup.tracks.size), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
            items(yearGroup.tracks, key = { "y${yearGroup.year}_${it.id}" }) { track ->
                SongSwipeActions(onPlayNext = onPlayNext?.let { action -> { action(track) } }) {
                    SongListRow(
                        track = track,
                        onClick = { onPlay(allTracks, allTracks.indexOf(track).coerceAtLeast(0)) },
                        onMenuClick = onTrackMenu?.let { menu -> { menu(track) } }
                    )
                }
            }
        }
    }
}
