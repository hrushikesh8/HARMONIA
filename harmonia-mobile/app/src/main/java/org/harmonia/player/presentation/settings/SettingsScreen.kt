package org.harmonia.player.presentation.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.hairlineBorder
import org.harmonia.player.design.theme.touchTarget48
import org.harmonia.player.domain.model.equalizer.EqualizerBand
import org.harmonia.player.domain.model.equalizer.EqualizerPreset
import java.util.Locale

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
    onAddFolder: (() -> Unit)? = null,
    onScanLibrary: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

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
            .background(colors.backgroundRoot)
            .padding(horizontal = spacing.standard)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(spacing.large))

        Text(
            text = "SETTINGS",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                fontSize = 28.sp
            ),
            color = colors.textPrimary
        )
        Text(
            text = "SYSTEM ARCHITECTURE & PERSISTENCE",
            style = MaterialTheme.typography.labelSmall,
            color = colors.primarySignal
        )

        Spacer(modifier = Modifier.height(spacing.standard))

        // Storage Sources Card
        SettingsCard(title = "STORAGE SOURCES") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Button(
                    onClick = actualAddFolder,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primarySignal,
                        contentColor = colors.backgroundRoot
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .touchTarget48()
                        .semantics { contentDescription = "Add storage folder" }
                ) {
                    Text("Add Folder", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = actualScanLibrary,
                    enabled = !uiState.isScanning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceContainer,
                        contentColor = colors.textPrimary,
                        disabledContainerColor = colors.surfaceElevated,
                        disabledContentColor = colors.textDisabled
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .touchTarget48()
                        .semantics { contentDescription = "Scan music library" }
                ) {
                    Text("Scan Library", fontWeight = FontWeight.Bold)
                }
            }

            if (uiState.isScanning) {
                Spacer(modifier = Modifier.height(spacing.medium))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceContainer)
                        .hairlineBorder(colors.borderStructural, RoundedCornerShape(8.dp))
                        .padding(spacing.medium)
                ) {
                    CircularProgressIndicator(
                        color = colors.primarySignal,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(spacing.medium))
                    Text(
                        text = uiState.scanStatusMessage ?: "Scanning storage sources...",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textPrimary
                    )
                }
            } else if (uiState.scanErrorMessage != null) {
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = uiState.scanErrorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.statusError
                )
            } else if (uiState.scanStatusMessage != null) {
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = uiState.scanStatusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primarySignal
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            if (uiState.storageSources.isEmpty()) {
                Text(
                    text = "No storage sources registered. Add a folder to index audio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
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

        Spacer(modifier = Modifier.height(spacing.standard))

        // Audio Equalizer Card
        SettingsCard(title = "AUDIO EQUALIZER") {
            EqualizerSection(
                equalizer = uiState.equalizer,
                onToggle = { viewModel.setEqualizerEnabled(it) },
                onBandChange = { band, levelMb -> viewModel.setEqualizerBandLevel(band, levelMb) },
                onPresetChange = { presetIndex -> viewModel.useEqualizerPreset(presetIndex) }
            )
        }

        Spacer(modifier = Modifier.height(spacing.standard))

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

        Spacer(modifier = Modifier.height(spacing.standard))

        // Audio Engine Specification Card
        SettingsCard(title = "AUDIO ENGINE SPECIFICATION") {
            SettingsRow(label = "Playback Service", value = "AndroidX Media3 MediaLibraryService")
            SettingsRow(label = "Audio Output Path", value = "AudioTrack direct (Hi-Res ready)")
            SettingsRow(label = "Tag Extraction Parser", value = "Platform MediaMetadataRetriever (ADR-023)")
            SettingsRow(label = "Network Streaming", value = "Zero network in playback path (Auxio pattern)")
        }

        Spacer(modifier = Modifier.height(spacing.standard))

        // Build & Foundation Card
        SettingsCard(title = "BUILD & FOUNDATION") {
            SettingsRow(label = "Application Version", value = uiState.appVersion)
            SettingsRow(label = "Target SDK", value = "Android 36 (Vanilla)")
            SettingsRow(label = "Minimum SDK", value = "Android 26 (Oreo)")
            SettingsRow(label = "Milestone", value = "Milestone M4-F — Storage & UI Hardening")
        }

        Spacer(modifier = Modifier.height(spacing.bottomScaffold))
    }
}

@Composable
internal fun StorageSourceRow(
    source: StorageSourceItemUiState,
    onToggle: (Boolean) -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    val (badgeBg, badgeTextColor) = when {
        source.isRuntimeAvailable -> Pair(colors.primarySignal.copy(alpha = 0.2f), colors.primarySignal)
        source.runtimeStatusDescription.contains("Permission", ignoreCase = true) ||
        source.runtimeStatusDescription.contains("revoked", ignoreCase = true) ->
            Pair(colors.statusError.copy(alpha = 0.2f), colors.statusError)
        source.runtimeStatusDescription.contains("Disconnected", ignoreCase = true) ||
        source.runtimeStatusDescription.contains("Unmounted", ignoreCase = true) ->
            Pair(colors.statusWarning.copy(alpha = 0.2f), colors.statusWarning)
        else -> Pair(colors.surfaceContainer, colors.textSecondary)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceElevated)
            .hairlineBorder(colors.borderStructural, RoundedCornerShape(10.dp))
            .padding(horizontal = spacing.medium, vertical = spacing.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(spacing.small))
                // Runtime status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = source.runtimeStatusDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeTextColor
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
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.width(spacing.medium))

        Switch(
            checked = source.isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.backgroundRoot,
                checkedTrackColor = colors.primarySignal,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceContainer,
                uncheckedBorderColor = colors.borderStructural
            ),
            modifier = Modifier
                .touchTarget48()
                .semantics {
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
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(colors.borderStructural, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Column(modifier = Modifier.padding(spacing.standard)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = colors.primarySignal
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            content()
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.textPrimary
        )
    }
}

@Composable
internal fun EqualizerSection(
    equalizer: EqualizerUiState,
    onToggle: (Boolean) -> Unit,
    onBandChange: (Short, Short) -> Unit,
    onPresetChange: (Short) -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    val (badgeBg, badgeTextColor, badgeLabel) = when (equalizer.status) {
        EqualizerUiStatus.UNSUPPORTED -> Triple(colors.statusError.copy(alpha = 0.2f), colors.statusError, "UNSUPPORTED")
        EqualizerUiStatus.CONTROL_LOST -> Triple(colors.statusWarning.copy(alpha = 0.2f), colors.statusWarning, "CONTROL PREEMPTED")
        EqualizerUiStatus.ENABLED -> Triple(colors.primarySignal.copy(alpha = 0.2f), colors.primarySignal, "ACTIVE")
        EqualizerUiStatus.DISABLED -> Triple(colors.surfaceContainer, colors.textSecondary, "DISABLED")
        EqualizerUiStatus.UNAVAILABLE_NO_SESSION -> Triple(colors.surfaceContainer, colors.textSecondary, "STANDBY")
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
        // Master switch row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Equalizer Master",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeTextColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = equalizer.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (equalizer.status) {
                        EqualizerUiStatus.UNSUPPORTED -> colors.statusError
                        EqualizerUiStatus.CONTROL_LOST -> colors.statusWarning
                        EqualizerUiStatus.ENABLED -> colors.primarySignal
                        else -> colors.textSecondary
                    }
                )
            }

            Spacer(modifier = Modifier.width(spacing.medium))

            Switch(
                checked = equalizer.isUserEnabled,
                onCheckedChange = onToggle,
                enabled = equalizer.status != EqualizerUiStatus.UNSUPPORTED,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.backgroundRoot,
                    checkedTrackColor = colors.primarySignal,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedTrackColor = colors.surfaceContainer,
                    uncheckedBorderColor = colors.borderStructural,
                    disabledCheckedThumbColor = colors.backgroundRoot.copy(alpha = 0.5f),
                    disabledCheckedTrackColor = colors.primarySignal.copy(alpha = 0.5f),
                    disabledUncheckedThumbColor = colors.textDisabled,
                    disabledUncheckedTrackColor = colors.surfaceContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .touchTarget48()
                    .semantics {
                        contentDescription = "Toggle Audio Equalizer"
                    }
            )
        }

        // Presets selector
        if (equalizer.presets.isNotEmpty()) {
            Column {
                Text(
                    text = "PRESETS",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    equalizer.presets.forEach { preset ->
                        val isSelected = preset.index == equalizer.currentPresetIndex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) colors.primarySignal.copy(alpha = 0.25f)
                                    else colors.surfaceContainer
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) colors.primarySignal else colors.borderStructural,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = equalizer.isInteractive) {
                                    onPresetChange(preset.index)
                                }
                                .padding(horizontal = spacing.medium, vertical = spacing.small)
                                .touchTarget48()
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Equalizer preset ${preset.name}"
                                }
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) colors.primarySignal else colors.textPrimary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }

        // Frequency bands
        if (equalizer.bands.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                Text(
                    text = "FREQUENCY BANDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
                equalizer.bands.forEach { band ->
                    EqualizerBandSlider(
                        band = band,
                        range = equalizer.bandLevelRange,
                        isInteractive = equalizer.isInteractive,
                        onLevelChanged = { onBandChange(band.index, it) }
                    )
                }
            }
        } else if (equalizer.status == EqualizerUiStatus.UNAVAILABLE_NO_SESSION) {
            Text(
                text = "Device-reported bands and presets will appear when an active playback session is established.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        } else if (equalizer.status == EqualizerUiStatus.UNSUPPORTED) {
            Text(
                text = "Hardware equalizer is unsupported on this device or active output route.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun EqualizerBandSlider(
    band: EqualizerBand,
    range: IntRange,
    isInteractive: Boolean,
    onLevelChanged: (Short) -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatFrequency(band.centerFreqHz),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary
            )
            Text(
                text = formatDb(band.currentLevelMb),
                style = MaterialTheme.typography.bodySmall,
                color = if (isInteractive) colors.primarySignal else colors.textSecondary
            )
        }
        Slider(
            value = band.currentLevelMb.toFloat(),
            onValueChange = { onLevelChanged(it.toInt().toShort()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = isInteractive,
            colors = SliderDefaults.colors(
                thumbColor = colors.primarySignal,
                activeTrackColor = colors.primarySignal,
                inactiveTrackColor = colors.surfaceContainer,
                disabledThumbColor = colors.textDisabled,
                disabledActiveTrackColor = colors.surfaceContainer,
                disabledInactiveTrackColor = colors.surfaceElevated
            ),
            modifier = Modifier
                .fillMaxWidth()
                .touchTarget48()
                .semantics {
                    contentDescription = "Equalizer band ${formatFrequency(band.centerFreqHz)}"
                    stateDescription = formatDb(band.currentLevelMb)
                }
        )
    }
}

internal fun formatFrequency(freqHz: Int): String {
    return if (freqHz >= 1000) {
        val kHz = freqHz / 1000.0
        if (freqHz % 1000 == 0) "${freqHz / 1000} kHz" else String.format(Locale.US, "%.1f kHz", kHz)
    } else {
        "$freqHz Hz"
    }
}

internal fun formatDb(levelMb: Short): String {
    val db = levelMb.toDouble() / 100.0
    val sign = if (db > 0) "+" else ""
    return "$sign${String.format(Locale.US, "%.1f dB", db)}"
}
