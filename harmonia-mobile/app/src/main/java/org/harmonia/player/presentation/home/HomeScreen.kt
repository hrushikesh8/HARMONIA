package org.harmonia.player.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.AnalogWarmGold
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.SonicAmber
import org.harmonia.player.design.theme.touchTarget48
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.util.TrackDisplayUtils
import java.util.Calendar
import java.util.Locale

/**
 * Harmonia Mobile — Phase 2: Home Screen (Acoustic Sanctuary)
 *
 * Design Pillars:
 * - Obsidian background canvas with tonal elevation surfaces (zero harsh 1dp outline clutter).
 * - Sonic Amber (#F5A623) reserved exclusively for active playback actions.
 * - Cover art treated as the primary visual hero throughout all cards and carousels.
 * - Editorial typography with natural human hierarchy, warm whites, and muted slate.
 * - Monospace strictly limited to bitrates, audio formats, and technical track parameters.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    playbackState: PlaybackState? = null,
    currentTrack: Track? = null,
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onTrackClick: (Track) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onResumeClick: () -> Unit = {},
    onShuffleAllClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    // Authoritative playback state and resume track synchronized with the parent container
    val activePlaybackState = playbackState ?: uiState.playbackState
    val activeResumeTrack = currentTrack ?: uiState.resumeTrack

    if (uiState.isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.backgroundRoot),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = colors.primarySignal)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot),
        contentPadding = PaddingValues(bottom = 104.dp)
    ) {
        // ── 1. Brand Bar & Ambient Greeting ──────────────────────────────
        item {
            Spacer(modifier = Modifier.height(spacing.medium))
            HeaderSection(
                totalTracks = uiState.totalTrackCount,
                onProfileClick = onProfileClick,
                onSearchClick = onSearchClick,
                modifier = Modifier.padding(horizontal = spacing.standard)
            )
            Spacer(modifier = Modifier.height(spacing.medium))
        }

        // ── 2. Artwork-Led Hero Card ("Jump Back In") ─────────────────────
        if (uiState.totalTrackCount > 0) {
            item {
                Box(modifier = Modifier.padding(horizontal = spacing.standard)) {
                    if (activeResumeTrack != null) {
                        HeroListeningCard(
                            track = activeResumeTrack,
                            playbackState = activePlaybackState,
                            onResume = onResumeClick,
                            onCardClick = { onTrackClick(activeResumeTrack) }
                        )
                    } else {
                        NoResumeListeningCard(
                            totalTracks = uiState.totalTrackCount,
                            onShuffleAll = onShuffleAllClick
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.medium))
            }
        }

        // ── 3. Quick Action Pill Bar ───────────────────────────────────────
        if (uiState.totalTrackCount > 0) {
            item {
                Box(modifier = Modifier.padding(horizontal = spacing.standard)) {
                    QuickActionsBar(
                        onShuffleAll = onShuffleAllClick,
                        onNavigateToLibrary = onNavigateToLibrary,
                        onNavigateToPlaylists = onNavigateToPlaylists
                    )
                }
                Spacer(modifier = Modifier.height(spacing.large))
            }
        }

        // ── 4. Recently Played Carousel (Factual Listening History) ───────
        if (uiState.recentlyPlayed.isNotEmpty()) {
            item {
                Box(modifier = Modifier.padding(horizontal = spacing.standard)) {
                    EditorialSectionHeader(
                        title = "Recently Played",
                        subtitle = "Local listening history"
                    )
                }
                Spacer(modifier = Modifier.height(spacing.small))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = spacing.standard),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.recentlyPlayed, key = { "recent_${it.id}" }) { track ->
                        val isPlaying = activePlaybackState?.currentTrackId == track.id &&
                                activePlaybackState.isPlaying
                        ArtworkTrackCard(
                            track = track,
                            isPlaying = isPlaying,
                            onClick = { onTrackClick(track) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ── 5. Recently Added Carousel (New Local Tracks) ─────────────────
        if (uiState.recentlyAdded.isNotEmpty()) {
            item {
                Box(modifier = Modifier.padding(horizontal = spacing.standard)) {
                    EditorialSectionHeader(
                        title = "Recently Added",
                        subtitle = "Newest local acquisitions",
                        actionLabel = "View Library",
                        onAction = onNavigateToLibrary
                    )
                }
                Spacer(modifier = Modifier.height(spacing.small))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = spacing.standard),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.recentlyAdded, key = { "added_${it.id}" }) { track ->
                        val isPlaying = activePlaybackState?.currentTrackId == track.id &&
                                activePlaybackState.isPlaying
                        ArtworkTrackCard(
                            track = track,
                            isPlaying = isPlaying,
                            onClick = { onTrackClick(track) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ── 6. Albums Carousel (Artwork Focus) ────────────────────────────
        if (uiState.albums.isNotEmpty()) {
            item {
                Box(modifier = Modifier.padding(horizontal = spacing.standard)) {
                    EditorialSectionHeader(
                        title = "Albums",
                        subtitle = "${uiState.albums.size} albums in collection",
                        actionLabel = "View All",
                        onAction = onNavigateToLibrary
                    )
                }
                Spacer(modifier = Modifier.height(spacing.small))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = spacing.standard),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.albums.take(15), key = { "album_${it.id}" }) { album ->
                        ArtworkAlbumCard(album = album, onClick = { onAlbumClick(album) })
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ── 7. Artists Carousel (Portrait Focus) ──────────────────────────
        if (uiState.artists.isNotEmpty()) {
            item {
                Box(modifier = Modifier.padding(horizontal = spacing.standard)) {
                    EditorialSectionHeader(
                        title = "Artists",
                        subtitle = "${uiState.artists.size} artists in collection",
                        actionLabel = "View All",
                        onAction = onNavigateToLibrary
                    )
                }
                Spacer(modifier = Modifier.height(spacing.small))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = spacing.standard),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.artists.take(15), key = { "artist_${it.id}" }) { artist ->
                        ArtworkArtistCard(artist = artist, onClick = { onArtistClick(artist) })
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ── 8. Playlists Carousel (Curated Collections) ───────────────────
        if (uiState.playlists.isNotEmpty()) {
            item {
                Box(modifier = Modifier.padding(horizontal = spacing.standard)) {
                    EditorialSectionHeader(
                        title = "Playlists",
                        subtitle = "${uiState.playlists.size} curated playlists",
                        actionLabel = "View All",
                        onAction = onNavigateToPlaylists
                    )
                }
                Spacer(modifier = Modifier.height(spacing.small))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = spacing.standard),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.playlists, key = { "pl_${it.id}" }) { playlist ->
                        ArtworkPlaylistCard(playlist = playlist, onClick = onNavigateToPlaylists)
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ── 9. Empty Catalog State ────────────────────────────────────────
        if (uiState.totalTrackCount == 0) {
            item {
                Box(modifier = Modifier.padding(horizontal = spacing.standard)) {
                    EmptyHomeCard(onNavigateToLibrary = onNavigateToLibrary)
                }
            }
        }
    }
}

/**
 * Refined Brand Header with Ambient Greeting and Quick Utility Actions
 */
@Composable
private fun HeaderSection(
    totalTracks: Int,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..22 -> "Good evening"
            else -> "Good night"
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Harmonia Monogram + Wordmark
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = HarmoniaIcons.Logo,
                    contentDescription = "Harmonia Monogram",
                    tint = SonicAmber,
                    modifier = Modifier
                        .size(30.dp)
                        .padding(end = spacing.small)
                )
                Text(
                    text = "HARMONIA",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        fontSize = 20.sp
                    ),
                    color = colors.textPrimary
                )
            }

            // Tactile Circular Action Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                // Search
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainer)
                        .clickable(onClick = onSearchClick)
                        .touchTarget48(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.Search,
                        contentDescription = "Search Catalog",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Profile / Settings Hub
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainer)
                        .clickable(onClick = onProfileClick)
                        .touchTarget48(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.Person,
                        contentDescription = "My Profile and Settings",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        // Ambient Greeting Hierarchy
        Text(
            text = greeting,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            ),
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (totalTracks > 0) "$totalTracks offline tracks ready" else "Local audio sanctuary",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
    }
}

/**
 * Visually Prominent Artwork-Led Hero Card ("Jump Back In")
 */
@Composable
private fun HeroListeningCard(
    track: Track,
    playbackState: PlaybackState?,
    onResume: () -> Unit,
    onCardClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(16.dp)
    val displayTitle = TrackDisplayUtils.formatDisplayTitle(track)
    val isPlaying = playbackState?.currentTrackId == track.id && playbackState.isPlaying
    val formatStr = track.localFile?.format?.uppercase(Locale.ROOT)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onCardClick)
            .semantics { contentDescription = "${if (isPlaying) "Now Playing" else "Resume"} $displayTitle" },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hero Album Art (72dp) with Smooth 12dp Radius
            Box(contentAlignment = Alignment.Center) {
                HarmoniaArtwork(
                    artworkHash = track.artworkHash ?: track.localFile?.fileHash,
                    size = 72.dp,
                    cornerRadius = 12.dp,
                    contentDescription = "$displayTitle artwork"
                )
            }

            Spacer(modifier = Modifier.width(spacing.medium))

            // Metadata Block
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isPlaying) "NOW PLAYING" else "JUMP BACK IN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = SonicAmber
                    )
                    if (formatStr != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.surfaceContainer)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = formatStr,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = AnalogWarmGold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = displayTitle,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                val artistSubtitle = track.artistName?.trim()?.ifEmpty { null }
                    ?: track.albumTitle?.trim()?.ifEmpty { null }
                    ?: "Unknown Artist"

                Text(
                    text = artistSubtitle,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(spacing.small))

            // Luminous Sonic Amber Play/Pause Action Button (48dp target)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SonicAmber)
                    .clickable(onClick = {
                        if (playbackState?.currentTrackId == track.id) {
                            onResume()
                        } else {
                            onCardClick()
                        }
                    })
                    .touchTarget48()
                    .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) HarmoniaIcons.Pause else HarmoniaIcons.Play,
                    contentDescription = null,
                    tint = colors.backgroundRoot,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Empty/Unstarted Hero Card providing immediate access to shuffle the local library
 */
@Composable
private fun NoResumeListeningCard(
    totalTracks: Int,
    onShuffleAll: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onShuffleAll),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = HarmoniaIcons.MusicNote,
                    contentDescription = null,
                    tint = SonicAmber,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(spacing.medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Acoustic Library Ready",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$totalTracks tracks available • Tap to shuffle",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SonicAmber)
                    .touchTarget48(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = HarmoniaIcons.Shuffle,
                    contentDescription = "Shuffle",
                    tint = colors.backgroundRoot,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Quick Action Shortcut Pills (Shuffle All, Tracks, Playlists)
 */
@Composable
private fun QuickActionsBar(
    onShuffleAll: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToPlaylists: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        // Shuffle All (Primary Sonic Amber Pill)
        Button(
            onClick = onShuffleAll,
            modifier = Modifier
                .weight(1.2f)
                .height(44.dp)
                .touchTarget48()
                .semantics { contentDescription = "Shuffle all library tracks" },
            colors = ButtonDefaults.buttonColors(
                containerColor = SonicAmber,
                contentColor = colors.backgroundRoot
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = HarmoniaIcons.Shuffle,
                contentDescription = null,
                tint = colors.backgroundRoot,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(spacing.xSmall))
            Text("Shuffle All", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        // Tracks Shortcut (Tonal Container Pill)
        Button(
            onClick = onNavigateToLibrary,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .touchTarget48()
                .semantics { contentDescription = "Open library tracks" },
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.surfaceContainer,
                contentColor = colors.textPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = HarmoniaIcons.Library,
                contentDescription = null,
                tint = colors.secondarySignal,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(spacing.xSmall))
            Text("Tracks", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }

        // Playlists Shortcut (Tonal Container Pill)
        Button(
            onClick = onNavigateToPlaylists,
            modifier = Modifier
                .weight(1.1f)
                .height(44.dp)
                .touchTarget48()
                .semantics { contentDescription = "Open playlists" },
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.surfaceContainer,
                contentColor = colors.textPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = HarmoniaIcons.Playlists,
                contentDescription = null,
                tint = colors.secondarySignal,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(spacing.xSmall))
            Text("Playlists", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

/**
 * Editorial Section Header with Clean Hierarchy and Subtle Action Link
 */
@Composable
private fun EditorialSectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = HarmoniaThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = colors.textPrimary
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = SonicAmber,
                    fontSize = 13.sp
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onAction)
                    .padding(vertical = 4.dp, horizontal = 6.dp)
                    .touchTarget48()
            )
        }
    }
}

/**
 * Artwork-Led Horizontal Track Card
 */
@Composable
private fun ArtworkTrackCard(
    track: Track,
    isPlaying: Boolean = false,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(14.dp)
    val displayTitle = TrackDisplayUtils.formatDisplayTitle(track)
    val artistSubtitle = track.artistName?.trim()?.ifEmpty { null }
        ?: track.albumTitle?.trim()?.ifEmpty { null }
        ?: "Unknown Artist"

    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(cardShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Play $displayTitle by $artistSubtitle" }
    ) {
        // Artwork Presentation
        Box {
            HarmoniaArtwork(
                artworkHash = track.artworkHash ?: track.localFile?.fileHash,
                size = 136.dp,
                cornerRadius = 14.dp,
                contentDescription = "$displayTitle artwork"
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SonicAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.Play,
                        contentDescription = "Playing",
                        tint = colors.backgroundRoot,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(spacing.small))
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = if (isPlaying) SonicAmber else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = artistSubtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Artwork-Led Album Card
 */
@Composable
private fun ArtworkAlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(14.dp)
    val artistName = album.artistName?.trim()?.ifEmpty { null } ?: "Unknown Artist"
    val yearStr = album.year?.takeIf { it > 0 }?.toString()
    val albumMeta = listOfNotNull(artistName, yearStr).joinToString(" • ")

    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(cardShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Album ${album.title} by $artistName" }
    ) {
        HarmoniaArtwork(
            artworkHash = album.artworkHash,
            placeholderIcon = HarmoniaIcons.Album,
            size = 136.dp,
            cornerRadius = 14.dp,
            contentDescription = "${album.title} artwork"
        )
        Spacer(modifier = Modifier.height(spacing.small))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            ),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = albumMeta.ifEmpty { "Album" },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Artwork-Led Artist Portrait Card
 */
@Composable
private fun ArtworkArtistCard(
    artist: Artist,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val artistName = artist.name.trim().ifEmpty { "Unknown Artist" }

    Column(
        modifier = Modifier
            .width(104.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Artist $artistName" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            HarmoniaArtwork(
                artworkHash = artist.artworkHash,
                size = 92.dp,
                cornerRadius = 46.dp,
                placeholderIcon = HarmoniaIcons.Person,
                contentDescription = "$artistName portrait"
            )
        }
        Spacer(modifier = Modifier.height(spacing.xSmall))
        Text(
            text = artistName,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = colors.textSecondary
        )
    }
}

/**
 * Artwork-Led Playlist Card
 */
@Composable
private fun ArtworkPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(cardShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Playlist ${playlist.name}" }
    ) {
        HarmoniaArtwork(
            artworkHash = playlist.artworkHash,
            placeholderIcon = HarmoniaIcons.Playlists,
            size = 136.dp,
            cornerRadius = 14.dp,
            contentDescription = "${playlist.name} artwork"
        )
        Spacer(modifier = Modifier.height(spacing.small))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            ),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${playlist.trackCount} ${if (playlist.trackCount == 1) "track" else "tracks"}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = colors.secondarySignal
        )
    }
}

/**
 * Clean Empty Library State
 */
@Composable
private fun EmptyHomeCard(onNavigateToLibrary: () -> Unit) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = HarmoniaIcons.MusicNote,
                    contentDescription = null,
                    tint = SonicAmber,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(spacing.medium))
            Text(
                text = "Welcome to Harmonia",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(spacing.xSmall))
            Text(
                text = "Your local music catalog is empty. Scan an offline music directory to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing.standard))
            Button(
                onClick = onNavigateToLibrary,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SonicAmber,
                    contentColor = colors.backgroundRoot
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .touchTarget48()
            ) {
                Text("Open Library", fontWeight = FontWeight.Bold)
            }
        }
    }
}

