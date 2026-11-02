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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.hairlineBorder
import org.harmonia.player.design.theme.touchTarget48
import java.util.Locale

sealed interface ProfileDialogDestination {
    object None : ProfileDialogDestination
    object ProfileSummary : ProfileDialogDestination
    object ListeningActivity : ProfileDialogDestination
    object RatingsSummary : ProfileDialogDestination
    object AccountDetails : ProfileDialogDestination
    object HelpFeedback : ProfileDialogDestination
}

@Composable
fun ProfileHubScreen(
    onBack: () -> Unit,
    onOpenLikes: () -> Unit,
    onOpenCarMode: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    var activeDialog by remember { mutableStateOf<ProfileDialogDestination>(ProfileDialogDestination.None) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.standard),
            contentPadding = PaddingValues(bottom = spacing.bottomScaffold)
        ) {
            // Header
            item {
                Spacer(modifier = Modifier.height(spacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .touchTarget48()
                            .semantics { contentDescription = "Close Profile Menu" }
                    ) {
                        Icon(
                            imageVector = HarmoniaIcons.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text(
                        text = "PROFILE & HUB",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primarySignal
                    )
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                // Profile Hero Card with Grounded Statistics
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hairlineBorder(colors.borderStructural, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.large)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(colors.primarySignal.copy(alpha = 0.2f))
                                    .hairlineBorder(colors.primarySignal, CircleShape, 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = HarmoniaIcons.Person,
                                    contentDescription = null,
                                    tint = colors.primarySignal,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(spacing.medium))

                            Column {
                                Text(
                                    text = "Harmonia Listener",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Sovereign Offline Audio Vault",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondarySignal
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.primarySignal.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "BLUE EDITION • LOCAL-FIRST",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = colors.primarySignal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.medium))

                        // Grounded Live Metric Badges Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            ProfileStatPill(
                                label = "TRACKS",
                                value = "${uiState.totalTracks}",
                                modifier = Modifier.weight(1f)
                            )
                            ProfileStatPill(
                                label = "LIKES",
                                value = "${uiState.favoritesCount}",
                                modifier = Modifier.weight(1f)
                            )
                            ProfileStatPill(
                                label = "RATED",
                                value = "${uiState.ratedTracksCount}",
                                modifier = Modifier.weight(1f)
                            )
                            ProfileStatPill(
                                label = "STORAGE",
                                value = String.format(Locale.US, "%.1f MB", uiState.storageUsageEstimateMb),
                                modifier = Modifier.weight(1.2f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.large))
            }

            // Section: My Library
            item {
                Text(
                    text = "MY LIBRARY",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primarySignal,
                    modifier = Modifier.padding(bottom = spacing.small)
                )

                ProfileMenuItem(
                    icon = HarmoniaIcons.Favorite,
                    title = "My Favorites",
                    subtitle = "${uiState.favoritesCount} favorite tracks • Jump to library",
                    onClick = onOpenLikes
                )

                ProfileMenuItem(
                    icon = HarmoniaIcons.Album,
                    title = "Storage & Scanner",
                    subtitle = "${uiState.storageSourcesCount} registered sources • Local media scanner",
                    onClick = onOpenSettings
                )

                Spacer(modifier = Modifier.height(spacing.large))

                Text(
                    text = "LISTENING EXPERIENCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primarySignal,
                    modifier = Modifier.padding(bottom = spacing.small)
                )

                ProfileMenuItem(
                    icon = HarmoniaIcons.DirectionsCar,
                    title = "Car Mode",
                    subtitle = "Distraction-free high-visibility interface for driving",
                    onClick = onOpenCarMode
                )

                ProfileMenuItem(
                    icon = HarmoniaIcons.Playlists,
                    title = "Listening Activity",
                    subtitle = "${uiState.totalListeningEvents} telemetry events logged locally",
                    onClick = { activeDialog = ProfileDialogDestination.ListeningActivity }
                )

                Spacer(modifier = Modifier.height(spacing.large))

                Text(
                    text = "PERSONALIZATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primarySignal,
                    modifier = Modifier.padding(bottom = spacing.small)
                )

                ProfileMenuItem(
                    icon = HarmoniaIcons.Star,
                    title = "Rated Music",
                    subtitle = if (uiState.averageRating != null) {
                        String.format(Locale.US, "%.1f ★ average across %d scored tracks", uiState.averageRating, uiState.ratedTracksCount)
                    } else {
                        "No scored tracks yet • Rate tracks from Now Playing"
                    },
                    onClick = { activeDialog = ProfileDialogDestination.RatingsSummary }
                )

                ProfileMenuItem(
                    icon = HarmoniaIcons.MusicNote,
                    title = "Audio Equalizer DSP",
                    subtitle = "5-Band hardware equalizer and decibel presets",
                    onClick = onOpenSettings
                )

                Spacer(modifier = Modifier.height(spacing.large))

                Text(
                    text = "APPLICATION & SYSTEM",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primarySignal,
                    modifier = Modifier.padding(bottom = spacing.small)
                )

                ProfileMenuItem(
                    icon = HarmoniaIcons.Settings,
                    title = "Settings",
                    subtitle = "Preferences, theme, playback defaults, storage & diagnostics",
                    onClick = onOpenSettings
                )

                ProfileMenuItem(
                    icon = HarmoniaIcons.Info,
                    title = "Help & Audio Specifications",
                    subtitle = "Media3 engine, supported codecs, and AudioFX architecture",
                    onClick = { activeDialog = ProfileDialogDestination.HelpFeedback }
                )

                ProfileMenuItem(
                    icon = HarmoniaIcons.Person,
                    title = "Sovereign Architecture & About",
                    subtitle = "Local-first storage identity and Room v5 SQLite guarantees",
                    onClick = { activeDialog = ProfileDialogDestination.AccountDetails }
                )
            }
        }

        // Informational Dialogs for Destinations
        when (activeDialog) {
            ProfileDialogDestination.ListeningActivity -> {
                ListeningActivityDialog(
                    totalEvents = uiState.totalListeningEvents,
                    events = uiState.recentListeningEvents,
                    onDismiss = { activeDialog = ProfileDialogDestination.None }
                )
            }
            ProfileDialogDestination.RatingsSummary -> {
                ProfileInfoDialog(
                    title = "Personal Music Ratings",
                    message = if (uiState.ratedTracksCount > 0) {
                        "Cataloged Ratings: ${uiState.ratedTracksCount} tracks scored\n" +
                                "Average Rating: ${String.format(Locale.US, "%.1f", uiState.averageRating ?: 0.0)} ★\n\n" +
                                "Ratings are stored 100% locally in Harmonia's SQLite database decoupled from external tags per ADR-021."
                    } else {
                        "No tracks rated yet.\n\nOpen Now Playing for any track and tap the star icon to record your 1-5 star rating."
                    },
                    onDismiss = { activeDialog = ProfileDialogDestination.None }
                )
            }
            ProfileDialogDestination.ProfileSummary -> {
                ProfileInfoDialog(
                    title = "My Profile",
                    message = "Harmonia is an independent, sovereign audio player. Your listening profile is stored 100% locally on this device without tracking, remote telemetry, or mandatory cloud accounts.",
                    onDismiss = { activeDialog = ProfileDialogDestination.None }
                )
            }
            ProfileDialogDestination.AccountDetails -> {
                ProfileInfoDialog(
                    title = "Sovereign Account Details",
                    message = "Account Architecture: Local-First Sovereign\nDatabase: SQLite Room v5 (WAL Mode)\nApplication Version: 0.1.0-alpha\nTarget SDK: Android 16 (API 36)\nData Directory: App-scoped private storage\nExternal Network Tracking: Disabled by design (MRD-001)",
                    onDismiss = { activeDialog = ProfileDialogDestination.None }
                )
            }
            ProfileDialogDestination.HelpFeedback -> {
                ProfileInfoDialog(
                    title = "Help & Audio Specifications",
                    message = "Audio Pipeline: AndroidX Media3 / ExoPlayer\nSupported Codecs: FLAC, ALAC, MP3, Opus, AAC, Vorbis, WAV\nAudio DSP: 5-Band Hardware-backed AudioFX Equalizer with decibel presets\nStorage: MediaStore and Storage Access Framework (SAF)",
                    onDismiss = { activeDialog = ProfileDialogDestination.None }
                )
            }
            ProfileDialogDestination.None -> {}
        }
    }
}

@Composable
private fun ProfileStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = HarmoniaThemeTokens.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainer)
            .hairlineBorder(colors.borderStructural, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.primarySignal,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colors.textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = spacing.small, horizontal = spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primarySignal,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(spacing.medium))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }

        Icon(
            imageVector = HarmoniaIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ProfileInfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Close", color = colors.primarySignal, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = colors.surfaceElevated,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun ListeningActivityDialog(
    totalEvents: Int,
    events: List<org.harmonia.player.domain.model.ListeningEvent>,
    onDismiss: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Listening Activity",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
                Text(
                    text = "$totalEvents total events recorded locally",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primarySignal
                )
            }
        },
        text = {
            if (events.isEmpty()) {
                Text(
                    text = "No listening telemetry recorded yet. Play any track to begin factual session logging.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.xSmall)
                ) {
                    items(events.take(30)) { event ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.surfaceContainer)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.eventType.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primarySignal
                                )
                                Text(
                                    text = "Track: ${event.trackId.take(12)}...",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = colors.textSecondary
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "%.1fs", event.durationPlayedSec),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Close", color = colors.primarySignal, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = colors.surfaceElevated,
        shape = RoundedCornerShape(16.dp)
    )
}
