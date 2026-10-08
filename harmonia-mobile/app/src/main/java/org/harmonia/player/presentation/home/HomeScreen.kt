package org.harmonia.player.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import org.harmonia.player.design.theme.HarmonicViolet
import org.harmonia.player.design.theme.MutedSlate
import org.harmonia.player.design.theme.ObsidianSpace
import org.harmonia.player.design.theme.PureSignalWhite
import org.harmonia.player.design.theme.StudioSlate
import org.harmonia.player.domain.model.Track

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onNavigateToLibrary: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianSpace)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Harmonia Brand Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "HARMONIA",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        fontSize = 28.sp
                    ),
                    color = PureSignalWhite
                )
                Text(
                    text = "NATIVE OFFLINE-FIRST PLAYER",
                    style = MaterialTheme.typography.labelSmall,
                    color = AcousticTeal
                )
            }

            // Status Indicator Orb
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(AcousticTeal)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // System Readiness & Storage Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "LOCAL PERSISTENCE ENGINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSlate
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "${uiState.offlineTrackCount}",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = AcousticTeal
                            )
                        )
                        Text(
                            text = "Offline Bitstreams",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column {
                        Text(
                            text = "${uiState.totalTrackCount}",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = PureSignalWhite
                            )
                        )
                        Text(
                            text = "Catalog Metadata",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "SQLite 3 WAL persistence initialized with two-tier metadata decoupling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Catalog Highlights Section
        Text(
            text = "CATALOG OVERVIEW",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            color = PureSignalWhite
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AcousticTeal)
            }
        } else if (uiState.recentTracks.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSlate, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = StudioSlate)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Tracks In Local Catalog",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureSignalWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Import local media files or sync from Harmonia Desktop.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSlate
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(uiState.recentTracks) { track ->
                    HomeTrackRow(track = track)
                }
            }
        }
    }
}

@Composable
private fun HomeTrackRow(track: Track) {
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
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioSlate),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = track.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = AcousticTeal
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = PureSignalWhite,
                    maxLines = 1
                )
                Text(
                    text = track.artistName ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (track.isAvailableOffline) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AcousticTeal.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "OFFLINE",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = AcousticTeal
                    )
                }
            }
        }
    }
}
