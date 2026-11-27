package org.harmonia.player.presentation.settings

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import org.harmonia.player.domain.model.ThemeMode
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Color
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

// MusicBrainz enrichment is not wired into the scan pipeline yet and the app has no
// INTERNET permission, so the toggle would do nothing. Flip this once enrichment ships.
private const val SHOW_ONLINE_ENRICHMENT_SETTINGS = false

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

    var showTechnicalDetails by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.standard)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.small, bottom = spacing.small)
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "Back to previous screen" }
                ) {
                    Icon(imageVector = HarmoniaIcons.ArrowBack, contentDescription = null, tint = colors.textPrimary)
                }
                Spacer(modifier = Modifier.width(spacing.xSmall))
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displayMedium,
                color = colors.textPrimary
            )
        }

        // ── Appearance ─────────────────────────────────────────────
        SettingsGroup(title = "Appearance") {
            Column(modifier = Modifier.padding(spacing.standard)) {
                Text(text = "Theme", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                Text(
                    text = when (uiState.themeMode) {
                        ThemeMode.SYSTEM -> "Matches your phone's dark mode setting"
                        ThemeMode.DARK -> "Always dark"
                        ThemeMode.LIGHT -> "Always light"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(spacing.small))
                ThemeModeSelector(selected = uiState.themeMode, onSelect = viewModel::setThemeMode)
            }
            SettingsDivider()
            SettingsSwitchRow(
                title = "Keep screen on",
                subtitle = "Stop the screen turning off while music plays",
                checked = uiState.keepScreenOn,
                onCheckedChange = viewModel::setKeepScreenOn,
                contentDescription = "Toggle Keep Screen Awake"
            )
        }

        // ── Playback ───────────────────────────────────────────────
        SettingsGroup(title = "Playback") {
            SettingsSwitchRow(
                title = "Shuffle by default",
                subtitle = "Shuffle when you start an album or playlist",
                checked = uiState.defaultShuffle,
                onCheckedChange = viewModel::setDefaultShuffle,
                contentDescription = "Toggle default shuffle"
            )
            SettingsDivider()
            Column(modifier = Modifier.padding(spacing.standard)) {
                Text(text = "Repeat by default", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                Spacer(modifier = Modifier.height(spacing.small))
                SegmentedChoice(
                    options = RepeatMode.entries.map { it to repeatModeLabel(it) },
                    selected = uiState.defaultRepeatMode,
                    onSelect = viewModel::setDefaultRepeatMode,
                    describe = { "Set default repeat mode to ${it.name}" }
                )
            }
            SettingsDivider()
            Column(
                modifier = Modifier
                    .padding(spacing.standard)
                    .semantics(mergeDescendants = false) {
                        contentDescription = if (uiState.isSleepTimerActive) {
                            "Sleep timer active, ${(uiState.sleepTimerRemainingMs / 60000)} minutes remaining"
                        } else {
                            "Sleep timer inactive"
                        }
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Sleep timer", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                        Text(
                            text = if (uiState.isSleepTimerActive) {
                                val mins = (uiState.sleepTimerRemainingMs / 60000).toInt()
                                val secs = ((uiState.sleepTimerRemainingMs % 60000) / 1000).toInt()
                                String.format(Locale.US, "Stops in %d:%02d, fading out gently", mins, secs)
                            } else {
                                "Off"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isSleepTimerActive) colors.primarySignal else colors.textSecondary
                        )
                    }
                    if (uiState.isSleepTimerActive) {
                        TextButton(onClick = { viewModel.cancelSleepTimer() }) {
                            Text("Turn off", color = colors.statusError, style = MaterialTheme.typography.labelLarge)
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
                                (preset != SleepTimerPreset.END_OF_TRACK && uiState.sleepTimerMode == SleepTimerMode.DURATION &&
                                    uiState.sleepTimerTargetDurationMs == preset.durationMs)
                            )
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                !isSelected -> colors.textPrimary
                                colors.isDark -> colors.backgroundRoot
                                else -> colors.surfaceElevated
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) colors.primarySignal else colors.surfaceContainer)
                                .clickable {
                                    if (isSelected) viewModel.cancelSleepTimer() else viewModel.startSleepTimer(preset)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Sleep timer ${preset.label}"
                                    stateDescription = if (isSelected) "Active" else "Inactive"
                                }
                        )
                    }
                }
            }
            if (onOpenCarMode != null) {
                SettingsDivider()
                SettingsNavRow(
                    icon = HarmoniaIcons.DirectionsCar,
                    title = "Car Mode",
                    subtitle = "Big, simple controls for driving",
                    onClick = onOpenCarMode,
                    contentDescription = "Launch Car Mode"
                )
            }
        }

        // ── Sound ──────────────────────────────────────────────────
        SettingsGroup(title = "Sound") {
            Column(modifier = Modifier.padding(spacing.standard)) {
                EqualizerSection(
                    equalizer = uiState.equalizer,
                    onToggle = { viewModel.setEqualizerEnabled(it) },
                    onBandChange = { band, levelMb -> viewModel.setEqualizerBandLevel(band, levelMb) },
                    onPresetChange = { presetIndex -> viewModel.useEqualizerPreset(presetIndex) }
                )
            }
        }

        // ── Music library ──────────────────────────────────────────
        SettingsGroup(
            title = "Music library",
            footer = "Harmonia plays the music files already on your phone, from your music library and any folders you add. Your files stay where they are and are never uploaded anywhere."
        ) {
            SettingsValueRow(
                title = "Songs on this phone",
                value = "${uiState.totalTracks} · " + String.format(Locale.US, "%.1f MB", uiState.storageUsageEstimateMb)
            )
            SettingsDivider()
            SettingsNavRow(
                icon = HarmoniaIcons.PlaylistAdd,
                title = "Add a music folder",
                subtitle = "Pick a folder, for example on an SD card",
                onClick = actualAddFolder,
                contentDescription = "Add storage folder via Storage Access Framework"
            )
            SettingsDivider()
            SettingsNavRow(
                icon = HarmoniaIcons.Search,
                title = if (uiState.isScanning) "Scanning…" else "Scan for new music",
                subtitle = when {
                    uiState.isScanning -> uiState.scanStatusMessage ?: "Looking for new and changed files"
                    uiState.scanErrorMessage != null -> uiState.scanErrorMessage
                    else -> uiState.scanStatusMessage ?: "Finds songs you've added or changed"
                },
                subtitleColor = if (!uiState.isScanning && uiState.scanErrorMessage != null) colors.statusError else null,
                onClick = actualScanLibrary,
                enabled = !uiState.isScanning,
                trailing = if (uiState.isScanning) {
                    { CircularProgressIndicator(color = colors.primarySignal, modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
                contentDescription = "Trigger incremental library scan"
            )
            if (uiState.storageSources.isEmpty()) {
                SettingsDivider()
                Text(
                    text = "No folders added yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(spacing.standard)
                )
            } else {
                uiState.storageSources.forEach { source ->
                    SettingsDivider()
                    StorageSourceRow(source = source, onToggle = { isEnabled -> viewModel.toggleSource(source.id, isEnabled) })
                }
            }
            SettingsDivider()
            SettingsNavRow(
                icon = HarmoniaIcons.Settings,
                title = "Rescan everything",
                subtitle = "Check every file again from scratch",
                onClick = { showResetCheckpointDialog = true },
                contentDescription = "Reset reconciliation scan checkpoints"
            )
        }

        if (SHOW_ONLINE_ENRICHMENT_SETTINGS) {
            SettingsGroup(
                title = "Song info",
                footer = "Fills in missing years and genres from the free MusicBrainz catalog. Your own tags always win and are never overwritten."
            ) {
                SettingsSwitchRow(
                    title = "Look up missing song info",
                    subtitle = "Uses the internet, about one request a second",
                    checked = uiState.isOnlineEnrichmentEnabled,
                    onCheckedChange = viewModel::setOnlineEnrichmentEnabled,
                    contentDescription = "Toggle online metadata enrichment"
                )
            }
        }

        // ── About ──────────────────────────────────────────────────
        SettingsGroup(
            title = "About",
            footer = "Your songs, playlists, ratings and listening history never leave this phone. No accounts, no tracking, no ads."
        ) {
            SettingsValueRow(title = "Version", value = uiState.appVersion)
            SettingsDivider()
            SettingsNavRow(
                icon = HarmoniaIcons.Info,
                title = "Technical details",
                onClick = { showTechnicalDetails = !showTechnicalDetails },
                trailing = {
                    Icon(
                        imageVector = if (showTechnicalDetails) HarmoniaIcons.KeyboardArrowDown else HarmoniaIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.textDisabled
                    )
                },
                contentDescription = if (showTechnicalDetails) "Hide technical details" else "Show technical details"
            )
            if (showTechnicalDetails) {
                listOf(
                    "Database" to "Room / SQLite (WAL)",
                    "Playback" to "AndroidX Media3",
                    "Audio output" to "AudioTrack + AudioFX",
                    "Built for" to "Android 16 (API 36)",
                    "Works on" to "Android 8.0+ (API 26)"
                ).forEach { (label, value) ->
                    SettingsDivider()
                    SettingsValueRow(title = label, value = value, compact = true)
                }
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
                    text = "Rescan everything?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "The next scan will check every music file again from scratch.\n\nYour songs, playlists and ratings won't be deleted.",
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
            .padding(horizontal = spacing.standard, vertical = spacing.small),
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
                text = "${source.indexedTrackCount} songs · ${source.typeDescription}",
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

/** A titled block of rows on a rounded card, Apple Settings style, with optional footnote. */
@Composable
private fun SettingsGroup(
    title: String,
    footer: String? = null,
    content: @Composable () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = colors.textSecondary,
        modifier = Modifier
            .padding(start = spacing.small, top = spacing.large, bottom = spacing.small)
            .semantics { heading() }
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
    ) {
        content()
    }
    if (footer != null) {
        Text(
            text = footer,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = spacing.small, end = spacing.small, top = spacing.small)
        )
    }
}

@Composable
private fun SettingsDivider() {
    val colors = HarmoniaThemeTokens.colors
    Box(
        modifier = Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.borderStructural)
    )
}

private val SettingsRowMinHeight = 56.dp

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SettingsRowMinHeight)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = spacing.standard, vertical = spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        Spacer(modifier = Modifier.width(spacing.medium))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = settingsSwitchColors(),
            modifier = Modifier.semantics { this.contentDescription = contentDescription }
        )
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SettingsRowMinHeight)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.standard, vertical = spacing.small)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.primarySignal, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(spacing.medium))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor ?: colors.textSecondary
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(imageVector = HarmoniaIcons.ChevronRight, contentDescription = null, tint = colors.textDisabled)
        }
    }
}

@Composable
private fun SettingsValueRow(title: String, value: String, compact: Boolean = false) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 44.dp else SettingsRowMinHeight)
            .padding(horizontal = spacing.standard, vertical = spacing.small)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = if (compact) colors.textSecondary else colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.End
        )
    }
}

/** Pill segmented control, e.g. Off / All / One. */
@Composable
private fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    describe: (T) -> String
) {
    val colors = HarmoniaThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceContainer)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) colors.primarySignal else Color.Transparent)
                    .selectable(selected = isSelected, onClick = { onSelect(value) }, role = Role.RadioButton)
                    .semantics { contentDescription = describe(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        !isSelected -> colors.textPrimary
                        colors.isDark -> colors.backgroundRoot
                        else -> colors.surfaceElevated
                    }
                )
            }
        }
    }
}

internal fun repeatModeLabel(mode: RepeatMode): String = when (mode) {
    RepeatMode.OFF -> "Off"
    RepeatMode.ALL -> "All"
    RepeatMode.ONE -> "One song"
}

@Composable
private fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = if (HarmoniaThemeTokens.colors.isDark) HarmoniaThemeTokens.colors.backgroundRoot else HarmoniaThemeTokens.colors.surfaceElevated,
    checkedTrackColor = HarmoniaThemeTokens.colors.primarySignal,
    uncheckedThumbColor = HarmoniaThemeTokens.colors.textSecondary,
    uncheckedTrackColor = HarmoniaThemeTokens.colors.surfaceContainer,
    uncheckedBorderColor = HarmoniaThemeTokens.colors.borderStructural
)

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
                        text = "Equalizer",
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
                    text = "Presets",
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
                    text = "Bands",
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
                text = "Play a song to see the equalizer bands and presets.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        } else if (equalizer.status == EqualizerUiStatus.UNSUPPORTED) {
            Text(
                text = "This phone or audio output doesn't support the equalizer.",
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

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val options = listOf(
        ThemeMode.SYSTEM to "System",
        ThemeMode.DARK to "Dark",
        ThemeMode.LIGHT to "Light"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceContainer)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (mode, label) ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) colors.primarySignal else Color.Transparent)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(mode) },
                        role = Role.RadioButton
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        !isSelected -> colors.textPrimary
                        colors.isDark -> colors.backgroundRoot
                        else -> colors.surfaceElevated
                    }
                )
            }
        }
    }
}
