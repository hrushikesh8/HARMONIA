package org.harmonia.player.presentation.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.AcousticCharcoal
import org.harmonia.player.design.theme.AcousticTeal
import org.harmonia.player.design.theme.BorderSlate
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.MutedSlate
import org.harmonia.player.design.theme.ObsidianSpace
import org.harmonia.player.design.theme.PureSignalWhite
import org.harmonia.player.design.theme.StudioSlate
import java.util.Locale

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
    onAddFolder: (() -> Unit)? = null,
    onScanLibrary: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val docId = try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Throwable) {
                uri.lastPathSegment ?: "Folder"
            }
            val displayName = docId.substringAfterLast(':').substringAfterLast('/')
                .ifBlank { "Folder Tree" }
            viewModel.registerSafFolder(uri.toString(), displayName)
        }
    }

    val actualAddFolder = onAddFolder ?: { openDocumentTreeLauncher.launch(null) }
    val actualScanLibrary = onScanLibrary ?: { viewModel.scanLibrary() }

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

        // Storage Sources Card
        SettingsCard(title = "STORAGE SOURCES") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = actualAddFolder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AcousticTeal,
                        contentColor = ObsidianSpace
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Add storage folder" }
                ) {
                    Text("Add Folder", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = actualScanLibrary,
                    enabled = !uiState.isScanning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioSlate,
                        contentColor = PureSignalWhite,
                        disabledContainerColor = AcousticCharcoal,
                        disabledContentColor = MutedSlate
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Scan music library" }
                ) {
                    Text("Scan Library", fontWeight = FontWeight.Bold)
                }
            }

            if (uiState.isScanning) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSpace, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    CircularProgressIndicator(
                        color = AcousticTeal,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = uiState.scanStatusMessage ?: "Scanning storage sources...",
                        style = MaterialTheme.typography.bodySmall,
                        color = PureSignalWhite
                    )
                }
            } else if (uiState.scanErrorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = uiState.scanErrorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (uiState.scanStatusMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = uiState.scanStatusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = AcousticTeal
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (uiState.storageSources.isEmpty()) {
                Text(
                    text = "No storage sources registered. Add a folder to index audio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.storageSources.forEach { source ->
                        StorageSourceRow(
                            source = source,
                            onToggle = { isEnabled ->
                                viewModel.toggleSource(source.id, isEnabled)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            SettingsRow(label = "Milestone", value = "Milestone M4-F — Storage & UI Hardening")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
internal fun StorageSourceRow(
    source: StorageSourceItemUiState,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSpace, RoundedCornerShape(10.dp))
            .border(1.dp, BorderSlate, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = PureSignalWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Runtime status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (source.isRuntimeAvailable) AcousticTeal.copy(alpha = 0.2f) else StudioSlate)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = source.runtimeStatusDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (source.isRuntimeAvailable) AcousticTeal else MutedSlate
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            val trackCountText = when (source.indexedTrackCount) {
                1 -> "1 track"
                else -> "${source.indexedTrackCount} tracks"
            }
            Text(
                text = "${source.typeDescription} • $trackCountText",
                style = MaterialTheme.typography.labelSmall,
                color = MutedSlate
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = source.isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianSpace,
                checkedTrackColor = AcousticTeal,
                uncheckedThumbColor = MutedSlate,
                uncheckedTrackColor = AcousticCharcoal,
                uncheckedBorderColor = BorderSlate
            ),
            modifier = Modifier.semantics {
                contentDescription = "Toggle ${source.displayName}"
            }
        )
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
