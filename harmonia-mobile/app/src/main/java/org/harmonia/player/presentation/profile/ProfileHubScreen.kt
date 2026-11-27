package org.harmonia.player.presentation.profile

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.HarmoniaArtwork
import java.util.Locale

private enum class ProfileDialog { None, Help, About }

/**
 * "Your listening" (the Profile destination): this month's stats, top artists and songs,
 * recently played, your ratings, and shortcuts. Everything comes from data on this phone.
 */
@Composable
fun ProfileHubScreen(
    onBack: () -> Unit,
    onOpenLikes: () -> Unit,
    onOpenCarMode: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(),
    onPlayTracks: (List<Track>, Int) -> Unit = { _, _ -> },
    onArtistClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listening = uiState.listening
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    var dialog by remember { mutableStateOf(ProfileDialog.None) }
    val now = remember(listening) { System.currentTimeMillis() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot),
        contentPadding = PaddingValues(start = spacing.standard, end = spacing.standard, bottom = spacing.bottomScaffold)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = spacing.small)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                    Icon(imageVector = HarmoniaIcons.ArrowBack, contentDescription = null, tint = colors.textPrimary)
                }
                Spacer(modifier = Modifier.width(spacing.xSmall))
                Text(text = "Your listening", style = MaterialTheme.typography.displayMedium, color = colors.textPrimary)
            }
            Text(
                text = "The last 30 days on this phone",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = spacing.small, bottom = spacing.medium)
            )
        }

        // Stats
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
                    StatTile("Minutes listened", formatCount(listening.minutesThisMonth), Modifier.weight(1f), highlight = true)
                    StatTile("Plays", formatCount(listening.playsThisMonth), Modifier.weight(1f), highlight = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
                    StatTile("Songs on this phone", formatCount(uiState.totalTracks), Modifier.weight(1f))
                    StatTile("Favorites", formatCount(uiState.favoritesCount), Modifier.weight(1f))
                }
            }
        }

        if (listening.playsThisMonth == 0) {
            item {
                Text(
                    text = "Play some music and your top artists and songs will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(vertical = spacing.large)
                )
            }
        }

        if (listening.topArtists.isNotEmpty()) {
            item { SectionTitle("Top artists") }
            itemsIndexed(listening.topArtists, key = { _, artist -> "artist_${artist.name}" }) { index, artist ->
                RankedRow(
                    rank = index + 1,
                    title = artist.name,
                    subtitle = playsLabel(artist.plays),
                    icon = HarmoniaIcons.Person,
                    onClick = artist.artistId?.let { id -> { onArtistClick(id) } }
                )
            }
        }

        if (listening.topSongs.isNotEmpty()) {
            item { SectionTitle("Top songs") }
            itemsIndexed(listening.topSongs, key = { _, track -> "top_${track.id}" }) { index, track ->
                SongRow(
                    track = track,
                    leading = "${index + 1}",
                    detail = track.artistName ?: "Unknown artist",
                    onClick = { onPlayTracks(listening.topSongs, index) }
                )
            }
        }

        if (listening.recentlyPlayed.isNotEmpty()) {
            item { SectionTitle("Recently played") }
            val recentTracks = listening.recentlyPlayed.map { it.track }
            itemsIndexed(listening.recentlyPlayed, key = { index, play -> "recent_${play.track.id}_$index" }) { index, play ->
                SongRow(
                    track = play.track,
                    detail = listOfNotNull(play.track.artistName, formatTimeAgo(now, play.playedAt)).joinToString(" · "),
                    onClick = { onPlayTracks(recentTracks, index) }
                )
            }
        }

        item { SectionTitle("Your ratings") }
        if (listening.ratedTracks.isEmpty()) {
            item {
                Text(
                    text = "Rate a song from Now Playing (the star button) and it will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
        } else {
            val ratedTracks = listening.ratedTracks.map { it.track }
            itemsIndexed(listening.ratedTracks, key = { _, rated -> "rated_${rated.track.id}" }) { index, rated ->
                SongRow(
                    track = rated.track,
                    detail = "★".repeat(rated.rating) + "  " + (rated.track.artistName ?: ""),
                    onClick = { onPlayTracks(ratedTracks, index) }
                )
            }
        }

        item { SectionTitle("More") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceElevated)
            ) {
                MenuRow(HarmoniaIcons.Favorite, "Favorites", "${uiState.favoritesCount} songs", onOpenLikes)
                MenuRow(HarmoniaIcons.DirectionsCar, "Car Mode", "Big, simple controls for driving", onOpenCarMode)
                MenuRow(HarmoniaIcons.Settings, "Settings", "Theme, playback, folders and more", onOpenSettings)
                MenuRow(HarmoniaIcons.Info, "Help", "What Harmonia can play", { dialog = ProfileDialog.Help })
                MenuRow(HarmoniaIcons.Person, "About Harmonia", "Version and privacy", { dialog = ProfileDialog.About })
            }
        }
    }

    when (dialog) {
        ProfileDialog.Help -> InfoDialog(
            title = "Help",
            message = "Harmonia plays the music files already on your phone: MP3, FLAC, ALAC, AAC/M4A, Opus, Ogg Vorbis and WAV.\n\n" +
                "Add folders (for example on an SD card) in Settings › Music library. Long-press any song for Play next, Add to queue and more.",
            onDismiss = { dialog = ProfileDialog.None }
        )
        ProfileDialog.About -> InfoDialog(
            title = "About Harmonia",
            message = "Version 0.1.0-alpha\n\nYour songs, playlists, ratings and listening history stay on this phone. No accounts, no tracking, no ads.",
            onDismiss = { dialog = ProfileDialog.None }
        )
        ProfileDialog.None -> Unit
    }
}

internal fun playsLabel(plays: Int): String = if (plays == 1) "1 play" else "$plays plays"

private fun formatCount(value: Int): String = String.format(Locale.US, "%,d", value)

@Composable
private fun SectionTitle(text: String) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = colors.textPrimary,
        modifier = Modifier
            .padding(top = spacing.large, bottom = spacing.small)
            .semantics { heading() }
    )
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val colors = HarmoniaThemeTokens.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) colors.primarySignal.copy(alpha = if (colors.isDark) 0.18f else 0.12f) else colors.surfaceElevated)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = if (highlight) colors.primarySignal else colors.textPrimary
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

@Composable
private fun RankedRow(rank: Int, title: String, subtitle: String, icon: ImageVector, onClick: (() -> Unit)?) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$rank", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary, modifier = Modifier.width(28.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.primarySignal)
        }
        Spacer(modifier = Modifier.width(spacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        if (onClick != null) {
            Icon(imageVector = HarmoniaIcons.ChevronRight, contentDescription = null, tint = colors.textDisabled)
        }
    }
}

@Composable
private fun SongRow(track: Track, detail: String, onClick: () -> Unit, leading: String? = null) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Text(text = leading, style = MaterialTheme.typography.titleMedium, color = colors.textSecondary, modifier = Modifier.width(28.dp))
        }
        HarmoniaArtwork(artworkHash = track.artworkHash, size = 48.dp, cornerRadius = 6.dp, contentDescription = null)
        Spacer(modifier = Modifier.width(spacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = track.title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.standard, vertical = spacing.small)
            .semantics(mergeDescendants = true) { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.primarySignal, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(spacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Icon(imageVector = HarmoniaIcons.ChevronRight, contentDescription = null, tint = colors.textDisabled)
    }
}

@Composable
private fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    val colors = HarmoniaThemeTokens.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Done", color = colors.primarySignal, style = MaterialTheme.typography.labelLarge)
            }
        },
        containerColor = colors.surfaceElevated,
        shape = RoundedCornerShape(16.dp)
    )
}
