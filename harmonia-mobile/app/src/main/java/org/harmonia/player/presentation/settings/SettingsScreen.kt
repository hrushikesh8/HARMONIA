package org.harmonia.player.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.util.Locale

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianSpace)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SETTINGS",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                fontSize = 28.sp
            ),
            color = PureSignalWhite
        )
        Text(
            text = "SYSTEM ARCHITECTURE & PERSISTENCE",
            style = MaterialTheme.typography.labelSmall,
            color = AcousticTeal
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Persistence Information Card
        SettingsCard(title = "PERSISTENCE SUBSYSTEM") {
            SettingsRow(label = "Database Engine", value = "Android Room (SQLite 3)")
            SettingsRow(label = "Journaling Mode", value = if (uiState.walModeEnabled) "WAL (Write-Ahead Logging)" else "Standard")
            SettingsRow(label = "Identity Model", value = "ADR-025 Two-Tier Decoupled")
            SettingsRow(label = "Total Tracks in Catalog", value = "${uiState.totalTracks}")
            SettingsRow(label = "Verified Offline Files", value = "${uiState.offlineTracks}")
            SettingsRow(
                label = "Local Storage Usage",
                value = String.format(Locale.US, "%.1f MB", uiState.storageUsageEstimateMb)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Audio Engine Specification Card
        SettingsCard(title = "AUDIO ENGINE SPECIFICATION") {
            SettingsRow(label = "Playback Service", value = "AndroidX Media3 MediaLibraryService")
            SettingsRow(label = "Audio Output Path", value = "AudioTrack direct (Hi-Res ready)")
            SettingsRow(label = "Tag Extraction Parser", value = "Platform MediaMetadataRetriever (ADR-023)")
            SettingsRow(label = "Network Streaming", value = "Zero network in playback path (Auxio pattern)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About Harmonia Card
        SettingsCard(title = "BUILD & FOUNDATION") {
            SettingsRow(label = "Application Version", value = uiState.appVersion)
            SettingsRow(label = "Target SDK", value = "Android 36 (Vanilla)")
            SettingsRow(label = "Minimum SDK", value = "Android 26 (Oreo)")
            SettingsRow(label = "Milestone", value = "Phase M1 — Native Foundation")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = AcousticTeal
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedSlate
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = PureSignalWhite
        )
    }
}
