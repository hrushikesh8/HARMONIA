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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.hairlineBorder
import org.harmonia.player.design.theme.touchTarget48
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.audio.SleepTimerMode
import org.harmonia.player.domain.model.audio.SleepTimerPreset
import org.harmonia.player.domain.model.equalizer.EqualizerBand
import java.util.Locale

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
    onAddFolder: (() -> Unit)? = null,
    onScanLibrary: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onOpenCarMode: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    var showResetCheckpointDialog by remember { mutableStateOf(false) }

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
        Spacer(modifier = Modifier.height(spacing.standard))

        // Top Navigation Bar
        if (onBack != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.small)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .touchTarget48()
                        .semantics { contentDescription = "Back to previous screen" }
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(spacing.small))
                Column {
                    Text(
                        text = "SETTINGS",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Text(
                        text = "PREFERENCES & SYSTEM ARCHITECTURE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primarySignal
                    )
                }
            }
        } else {
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontSize = 26.sp
                ),
                color = colors.textPrimary
            )
            Text(
                text = "PREFERENCES & SYSTEM ARCHITECTURE",
                style = MaterialTheme.typography.labelSmall,
                color = colors.primarySignal
            )
        }

        Spacer(modifier = Modifier.height(spacing.standard))

        // ==========================================
        // SECTION 1: APPEARANCE & VISUAL IDENTITY
        // ==========================================
        SettingsCard(
            title = "APPEARANCE & VISUAL IDENTITY",
            subtitle = "Theme styling and active display preferences"
        ) {
            // Theme Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Harmonia Blue Edition",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (uiState.isDarkTheme) {
                            "Dark Mode: Near-black and deep navy (#070A0F) with electric-blue signals"
                        } else {
                            "Daylight Mode: Slate day (#F3F6FA) with deep-blue signals"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.width(spacing.medium))

                Switch(
                    checked = uiState.isDarkTheme,
                    onCheckedChange = { viewModel.setIsDarkTheme(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.backgroundRoot,
                        checkedTrackColor = colors.primarySignal,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surfaceContainer,
                        uncheckedBorderColor = colors.borderStructural
                    ),
                    modifier = Modifier
                        .touchTarget48()
                        .semantics { contentDescription = "Toggle Dark Mode" }
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Keep Screen On Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Keep Screen On",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Prevents display from sleeping during active listening sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.width(spacing.medium))

                Switch(
                    checked = uiState.keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOn(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.backgroundRoot,
                        checkedTrackColor = colors.primarySignal,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surfaceContainer,
                        uncheckedBorderColor = colors.borderStructural
                    ),
                    modifier = Modifier
                        .touchTarget48()
                        .semantics { contentDescription = "Toggle Keep Screen Awake" }
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            SettingsRow(
                label = "Design Standard",
                value = "Harmonia Resonant Tokens (WCAG AAA >= 7.0:1)",
                isHighlight = true
            )
        }

        Spacer(modifier = Modifier.height(spacing.standard))

        // ==========================================
        // SECTION 2: STORAGE & MEDIA SCANNER
        // ==========================================
        SettingsCard(
            title = "STORAGE SOURCES & MEDIA SCANNER",
            subtitle = "Indexed storage directories and local reconciliation"
        ) {
            // Storage Statistics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                MetricChip(label = "CATALOGED", value = "${uiState.totalTracks}", modifier = Modifier.weight(1f))
                MetricChip(label = "OFFLINE", value = "${uiState.offlineTracks}", modifier = Modifier.weight(1f))
                MetricChip(
                    label = "STORAGE",
                    value = String.format(Locale.US, "%.1f MB", uiState.storageUsageEstimateMb),
                    modifier = Modifier.weight(1.2f)
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Action Buttons Row
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
                        .height(44.dp)
                        .touchTarget48()
                        .semantics { contentDescription = "Add storage folder via Storage Access Framework" }
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
                        .height(44.dp)
                        .touchTarget48()
                        .semantics { contentDescription = "Trigger incremental library scan" }
                ) {
                    Text("Scan Library", fontWeight = FontWeight.Bold)
                }
            }

            // Scanner Status / Progress
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

            // Storage Source Rows
            if (uiState.storageSources.isEmpty()) {
                Text(
                    text = "No storage sources registered. Tap 'Add Folder' to select an audio directory.",
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

            Spacer(modifier = Modifier.height(spacing.medium))

            // Plain-language Storage and Permissions Explainer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceContainer.copy(alpha = 0.5f))
                    .padding(spacing.medium)
            ) {
                Text(
                    text = "Harmonia reads audio files directly from the Android MediaStore and user-authorized folders via the Storage Access Framework (SAF). Files remain in their original directories on your device and are never uploaded or synced to external servers.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.standard))

        // ==========================================
        // SECTION 3: PLAYBACK PREFERENCES & EQUALIZER
        // ==========================================
        SettingsCard(
            title = "PLAYBACK & EQUALIZER DSP",
            subtitle = "Audio processing and playback configuration"
        ) {
            // Equalizer Master & Bands
            EqualizerSection(
                equalizer = uiState.equalizer,
                onToggle = { viewModel.setEqualizerEnabled(it) },
                onBandChange = { band, levelMb -> viewModel.setEqualizerBandLevel(band, levelMb) },
                onPresetChange = { presetIndex -> viewModel.useEqualizerPreset(presetIndex) }
            )

            Spacer(modifier = Modifier.height(spacing.large))

            // Playback Defaults: Shuffle Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Default Shuffle",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Automatically shuffle new playback queues on start",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.width(spacing.medium))

                Switch(
                    checked = uiState.defaultShuffle,
                    onCheckedChange = { viewModel.setDefaultShuffle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.backgroundRoot,
                        checkedTrackColor = colors.primarySignal,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surfaceContainer,
                        uncheckedBorderColor = colors.borderStructural
                    ),
                    modifier = Modifier
                        .touchTarget48()
                        .semantics { contentDescription = "Toggle default shuffle" }
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Playback Defaults: Repeat Mode Selector
            Column {
                Text(
                    text = "Default Repeat Mode",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    RepeatMode.entries.forEach { mode ->
                        val isSelected = uiState.defaultRepeatMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
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
                                .clickable { viewModel.setDefaultRepeatMode(mode) }
                                .padding(vertical = 10.dp)
                                .touchTarget48()
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Set default repeat mode to ${mode.name}"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) colors.primarySignal else colors.textPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.large))

            // Sleep Timer Status / Control Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (uiState.isSleepTimerActive) {
                            "Sleep timer active, ${(uiState.sleepTimerRemainingMs / 60000)} minutes remaining"
                        } else {
                            "Sleep timer inactive"
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sleep Timer",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (uiState.isSleepTimerActive) {
                            val mins = (uiState.sleepTimerRemainingMs / 60000).toInt()
                            val secs = ((uiState.sleepTimerRemainingMs % 60000) / 1000).toInt()
                            String.format(Locale.US, "Active • %02d:%02d remaining (fades smoothly)", mins, secs)
                        } else {
                            "Inactive • Select preset below"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.isSleepTimerActive) colors.primarySignal else colors.textSecondary
                    )
                }

                if (uiState.isSleepTimerActive) {
                    Spacer(modifier = Modifier.width(spacing.small))
                    TextButton(
                        onClick = { viewModel.cancelSleepTimer() },
                        modifier = Modifier.touchTarget48()
                    ) {
                        Text("Cancel", color = colors.statusError, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.small))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                SleepTimerPreset.entries.forEach { preset ->
                    val isSelected = uiState.isSleepTimerActive && (
                        (preset == SleepTimerPreset.END_OF_TRACK && uiState.sleepTimerMode == SleepTimerMode.END_OF_TRACK) ||
                        (preset != SleepTimerPreset.END_OF_TRACK && uiState.sleepTimerMode == SleepTimerMode.DURATION && uiState.sleepTimerTargetDurationMs == preset.durationMs)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) colors.surfaceContainer else colors.surfaceElevated)
                            .border(1.dp, if (isSelected) colors.primarySignal else colors.borderStructural, RoundedCornerShape(8.dp))
                            .clickable {
                                if (isSelected) {
                                    viewModel.cancelSleepTimer()
                                } else {
                                    viewModel.startSleepTimer(preset)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .touchTarget48()
                            .semantics {
                                role = Role.Button
                                contentDescription = "Sleep timer ${preset.label}"
                                stateDescription = if (isSelected) "Active" else "Inactive"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) colors.primarySignal else colors.textPrimary
                        )
                    }
                }
            }

            if (onOpenCarMode != null) {
                Spacer(modifier = Modifier.height(spacing.medium))
                Button(
                    onClick = onOpenCarMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceContainer,
                        contentColor = colors.primarySignal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .hairlineBorder(colors.borderStructural, RoundedCornerShape(10.dp))
                        .touchTarget48()
                        .semantics { contentDescription = "Launch Car Mode" }
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text("Launch Car Mode", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.standard))

        // ==========================================
        // SECTION 4: METADATA & OPEN CATALOG ENRICHMENT
        // ==========================================
        SettingsCard(
            title = "METADATA & OPEN CATALOG ENRICHMENT",
            subtitle = "Optional MusicBrainz integration (ADR-023)"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable MusicBrainz Enrichment",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fills in missing release years, genre tags, and soundtrack metadata from the MusicBrainz open catalog. Operates strictly offline-first; local file tags always take precedence and are never overwritten.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.width(spacing.medium))

                Switch(
                    checked = uiState.isOnlineEnrichmentEnabled,
                    onCheckedChange = { viewModel.setOnlineEnrichmentEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.backgroundRoot,
                        checkedTrackColor = colors.primarySignal,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surfaceContainer,
                        uncheckedBorderColor = colors.borderStructural
                    ),
                    modifier = Modifier
                        .touchTarget48()
                        .semantics { contentDescription = "Toggle online metadata enrichment" }
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            SettingsRow(
                label = "Network Etiquette",
                value = "1 req/sec rate-limited, local in-memory cache"
            )
            SettingsRow(
                label = "Offline Fallback",
                value = "Instant silent fallback, zero playback block",
                isHighlight = true
            )
        }

        Spacer(modifier = Modifier.height(spacing.standard))

        // ==========================================
        // SECTION 5: DATA PRIVACY, DIAGNOSTICS & ABOUT
        // ==========================================
        SettingsCard(
            title = "DATA PRIVACY, DIAGNOSTICS & ABOUT",
            subtitle = "Sovereign local-first guarantees and system specifications"
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primarySignal.copy(alpha = 0.1f))
                    .padding(spacing.medium)
            ) {
                Column {
                    Text(
                        text = "LOCAL-FIRST SOVEREIGN ARCHITECTURE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.primarySignal
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Harmonia is an independent audio player. Your playback events, playlists, star ratings, and audio files never leave this device. No remote accounts, analytics tracking, or advertising.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            SettingsRow(label = "Application Version", value = uiState.appVersion, isHighlight = true)
            SettingsRow(label = "Database Engine", value = "Android Room SQLite 3 (WAL Mode)")
            SettingsRow(label = "Playback Service", value = "MediaLibraryService (AndroidX Media3)")
            SettingsRow(label = "Audio Output Route", value = "AudioTrack Direct (Hi-Res AudioFX)")
            SettingsRow(label = "Target Android SDK", value = "API 36 (Android 16)")
            SettingsRow(label = "Minimum Android SDK", value = "API 26 (Android 8.0)")

            Spacer(modifier = Modifier.height(spacing.medium))

            // Destructive / Maintenance Action: Reset Checkpoints
            Button(
                onClick = { showResetCheckpointDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surfaceContainer,
                    contentColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .hairlineBorder(colors.borderStructural, RoundedCornerShape(10.dp))
                    .touchTarget48()
                    .semantics { contentDescription = "Reset reconciliation scan checkpoints" }
            ) {
                Text("Reset Scan Checkpoints", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(spacing.bottomScaffold))
    }

    // Confirmation Dialog for Resetting Checkpoints
    if (showResetCheckpointDialog) {
        AlertDialog(
            onDismissRequest = { showResetCheckpointDialog = false },
            title = {
                Text(
                    text = "Reset Scan Checkpoints?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "This will clear stored reconciliation timestamps. The next manual scan will fully re-examine every audio file across all enabled sources.\n\nYour library tracks, playlists, and ratings will not be deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetCheckpointDialog = false
                        viewModel.resetScanCheckpoints()
                    }
                ) {
                    Text("Reset", color = colors.statusError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetCheckpointDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun MetricChip(
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(spacing.small))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = source.runtimeStatusDescription,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = badgeTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${source.indexedTrackCount} tracks • ${source.typeDescription}",
                style = MaterialTheme.typography.bodySmall,
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
                    contentDescription = "Toggle ${source.displayName} storage source"
                }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(colors.borderStructural, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Column(modifier = Modifier.padding(spacing.standard)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = colors.primarySignal
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(spacing.medium))
            content()
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String, isHighlight: Boolean = false) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1.1f, fill = false)
        )
        Spacer(modifier = Modifier.width(spacing.small))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isHighlight) colors.primarySignal else colors.textPrimary,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.3f, fill = false)
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
