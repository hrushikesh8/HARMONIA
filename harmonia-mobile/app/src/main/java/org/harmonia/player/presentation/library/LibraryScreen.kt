package org.harmonia.player.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.AcousticCharcoal
import org.harmonia.player.design.theme.AcousticTeal
import org.harmonia.player.design.theme.BorderSlate
import org.harmonia.player.design.theme.MutedSlate
import org.harmonia.player.design.theme.ObsidianSpace
import org.harmonia.player.design.theme.PureSignalWhite
import org.harmonia.player.design.theme.StatusWarning
import org.harmonia.player.design.theme.StudioSlate
import org.harmonia.player.domain.model.LocalFileAvailability
import org.harmonia.player.domain.model.Track
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianSpace)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = "LIBRARY",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                fontSize = 28.sp
            ),
            color = PureSignalWhite
        )
        Text(
            text = "TWO-TIER METADATA CATALOG",
            style = MaterialTheme.typography.labelSmall,
            color = AcousticTeal
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Search Input
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Search tracks, artists, albums...",
                    color = MutedSlate,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AcousticTeal,
                unfocusedBorderColor = BorderSlate,
                focusedContainerColor = AcousticCharcoal,
                unfocusedContainerColor = AcousticCharcoal,
                cursorColor = AcousticTeal,
                focusedTextColor = PureSignalWhite,
                unfocusedTextColor = PureSignalWhite
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Row (ADR-025 Offline Toggle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !uiState.offlineOnly,
                onClick = { viewModel.setOfflineOnly(false) },
                label = { Text("All Tracks (${uiState.totalCount})") },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = AcousticCharcoal,
                    labelColor = MutedSlate,
                    selectedContainerColor = StudioSlate,
                    selectedLabelColor = PureSignalWhite
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = if (!uiState.offlineOnly) AcousticTeal else BorderSlate,
                    enabled = true,
                    selected = !uiState.offlineOnly
                )
            )

            FilterChip(
                selected = uiState.offlineOnly,
                onClick = { viewModel.setOfflineOnly(true) },
                label = { Text("Offline Only (${uiState.offlineCount})") },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = AcousticCharcoal,
                    labelColor = MutedSlate,
                    selectedContainerColor = AcousticTeal.copy(alpha = 0.2f),
                    selectedLabelColor = AcousticTeal
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = if (uiState.offlineOnly) AcousticTeal else BorderSlate,
                    enabled = true,
                    selected = uiState.offlineOnly
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tracks Content
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AcousticTeal)
            }
        } else if (uiState.tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (uiState.offlineOnly) "No Offline Bitstreams Available" else "No Tracks In Library",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureSignalWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.offlineOnly) "Toggle 'All Tracks' to browse full synced metadata catalog" else "Add tracks via SAF or desktop sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(uiState.tracks, key = { it.id }) { track ->
                    LibraryTrackCard(track = track)
                }
            }
        }
    }
}

@Composable
private fun LibraryTrackCard(track: Track) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track Index / Number Box
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioSlate),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = track.trackNumber?.toString() ?: "#",
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedSlate
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = PureSignalWhite,
                    maxLines = 1
                )
                Text(
                    text = listOfNotNull(track.artistName, track.albumTitle).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Duration & Telemetry Badges
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDuration(track.durationSec),
                    style = MaterialTheme.typography.labelMedium,
                    color = PureSignalWhite
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Availability Status Badge
                    when (track.availability) {
                        LocalFileAvailability.AVAILABLE_VERIFIED -> {
                            BadgeBox(text = "OFFLINE", color = AcousticTeal)
                        }
                        LocalFileAvailability.MISSING -> {
                            BadgeBox(text = "MISSING", color = StatusWarning)
                        }
                        LocalFileAvailability.UNAVAILABLE -> {
                            BadgeBox(text = "REMOTE", color = MutedSlate)
                        }
                        else -> {
                            BadgeBox(text = "UNVERIFIED", color = MutedSlate)
                        }
                    }

                    // ReplayGain LUFS Badge
                    track.integratedLufs?.let { lufs ->
                        BadgeBox(
                            text = String.format(Locale.US, "%.1f LUFS", lufs),
                            color = MutedSlate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeBox(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = color
        )
    }
}

private fun formatDuration(durationSec: Double): String {
    val totalSec = durationSec.toInt()
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
