package org.harmonia.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.harmonia.player.design.theme.HarmoniaTheme
import org.harmonia.player.domain.model.LocalFile
import org.harmonia.player.domain.model.LocalFileAvailability
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.StorageType
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.library.TracksTabContent
import org.harmonia.player.presentation.playback.NowPlayingSheet
import org.harmonia.player.presentation.settings.StorageSourceItemUiState
import org.harmonia.player.presentation.settings.StorageSourceRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HarmoniaDisconnectedStorageUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun nowPlaying_shows_storage_disconnected_banner_when_storage_disconnected_error_active() {
        val playbackState = PlaybackState(
            isPlaying = false,
            currentTrackId = "track-1",
            durationMs = 240000L,
            currentPositionMs = 45000L,
            errorMessage = PlaybackState.STORAGE_DISCONNECTED_MESSAGE
        )
        val track = Track(
            id = "track-1",
            title = "Pyramid Song",
            artistName = "Radiohead",
            albumTitle = "Amnesiac",
            durationSec = 240.0
        )

        composeTestRule.setContent {
            HarmoniaTheme {
                NowPlayingSheet(
                    playbackState = playbackState,
                    currentTrack = track,
                    queue = emptyList(),
                    onPlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onSeekTo = {},
                    onQueueItemClick = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Storage Disconnected").assertIsDisplayed()
        composeTestRule.onNodeWithText("The storage volume for this track is unavailable. Reconnect storage to resume playback.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(PlaybackState.STORAGE_DISCONNECTED_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun nowPlaying_shows_generic_playback_error_banner_for_other_errors() {
        val playbackState = PlaybackState(
            isPlaying = false,
            currentTrackId = "track-1",
            durationMs = 240000L,
            currentPositionMs = 0L,
            errorMessage = "Corrupt media stream"
        )
        val track = Track(
            id = "track-1",
            title = "Everything In Its Right Place",
            artistName = "Radiohead",
            durationSec = 240.0
        )

        composeTestRule.setContent {
            HarmoniaTheme {
                NowPlayingSheet(
                    playbackState = playbackState,
                    currentTrack = track,
                    queue = emptyList(),
                    onPlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onSeekTo = {},
                    onQueueItemClick = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Playback Error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Corrupt media stream").assertIsDisplayed()
    }

    @Test
    fun nowPlaying_hides_error_banner_when_no_playback_error() {
        val playbackState = PlaybackState(
            isPlaying = true,
            currentTrackId = "track-1",
            durationMs = 240000L,
            currentPositionMs = 30000L,
            errorMessage = null
        )
        val track = Track(
            id = "track-1",
            title = "Everything In Its Right Place",
            artistName = "Radiohead",
            durationSec = 240.0
        )

        composeTestRule.setContent {
            HarmoniaTheme {
                NowPlayingSheet(
                    playbackState = playbackState,
                    currentTrack = track,
                    queue = emptyList(),
                    onPlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onSeekTo = {},
                    onQueueItemClick = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Storage Disconnected").assertDoesNotExist()
        composeTestRule.onNodeWithText("Playback Error").assertDoesNotExist()
    }

    @Test
    fun library_track_row_displays_storage_unmounted_badge_when_source_is_physically_unmounted() {
        val unmountedTrack = Track(
            id = "track-unmounted",
            title = "National Anthem",
            artistName = "Radiohead",
            durationSec = 300.0,
            availability = LocalFileAvailability.AVAILABLE_VERIFIED,
            localFile = LocalFile(
                id = "file-1",
                trackId = "track-unmounted",
                sourceId = "vol-ext-sdcard",
                storageType = StorageType.EXTERNAL_SD,
                fileUri = "content://media/external/audio/media/1",
                fileSize = 1000L,
                fileHash = "hash1",
                format = "flac"
            )
        )
        val availableTrack = Track(
            id = "track-internal",
            title = "Idioteque",
            artistName = "Radiohead",
            durationSec = 309.0,
            availability = LocalFileAvailability.AVAILABLE_VERIFIED,
            localFile = LocalFile(
                id = "file-2",
                trackId = "track-internal",
                sourceId = "mediastore-primary",
                storageType = StorageType.MEDIA_STORE_PRIMARY,
                fileUri = "content://media/external/audio/media/2",
                fileSize = 2000L,
                fileHash = "hash2",
                format = "flac"
            )
        )

        composeTestRule.setContent {
            HarmoniaTheme {
                TracksTabContent(
                    tracks = listOf(unmountedTrack, availableTrack),
                    unmountedSourceIds = setOf("vol-ext-sdcard"),
                    disabledSourceIds = emptySet(),
                    onTrackClick = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("Storage Unmounted").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play National Anthem by Radiohead (Storage Unmounted)").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play Idioteque by Radiohead").assertIsDisplayed()
    }

    @Test
    fun library_track_row_displays_source_disabled_badge_when_source_is_user_disabled() {
        val disabledTrack = Track(
            id = "track-disabled",
            title = "Optimistic",
            artistName = "Radiohead",
            durationSec = 315.0,
            availability = LocalFileAvailability.AVAILABLE_VERIFIED,
            localFile = LocalFile(
                id = "file-3",
                trackId = "track-disabled",
                sourceId = "saf-user-disabled",
                storageType = StorageType.SAF_IMPORTED,
                fileUri = "content://saf/tree/music/3",
                fileSize = 1500L,
                fileHash = "hash3",
                format = "flac"
            )
        )

        composeTestRule.setContent {
            HarmoniaTheme {
                TracksTabContent(
                    tracks = listOf(disabledTrack),
                    unmountedSourceIds = emptySet(),
                    disabledSourceIds = setOf("saf-user-disabled"),
                    onTrackClick = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("Source Disabled").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play Optimistic by Radiohead (Source Disabled)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Storage Unmounted").assertDoesNotExist()
    }

    @Test
    fun library_track_row_displays_file_unavailable_badge_when_track_availability_is_unavailable() {
        val unavailableTrack = Track(
            id = "track-status-unmounted",
            title = "Morning Bell",
            artistName = "Radiohead",
            durationSec = 250.0,
            availability = LocalFileAvailability.UNAVAILABLE
        )

        composeTestRule.setContent {
            HarmoniaTheme {
                TracksTabContent(
                    tracks = listOf(unavailableTrack),
                    unmountedSourceIds = emptySet(),
                    disabledSourceIds = emptySet(),
                    onTrackClick = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("File Unavailable").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play Morning Bell by Radiohead (File Unavailable)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Storage Unmounted").assertDoesNotExist()
    }

    @Test
    fun library_track_row_displays_source_disabled_when_both_disabled_and_stale_unmounted_states_present() {
        val track = Track(
            id = "track-dual-state",
            title = "Knives Out",
            artistName = "Radiohead",
            durationSec = 255.0,
            availability = LocalFileAvailability.AVAILABLE_VERIFIED,
            localFile = LocalFile(
                id = "file-dual",
                trackId = "track-dual-state",
                sourceId = "sdcard-dual-state",
                storageType = StorageType.EXTERNAL_SD,
                fileUri = "content://media/external/audio/media/dual",
                fileSize = 3000L,
                fileHash = "hashdual",
                format = "flac"
            )
        )

        composeTestRule.setContent {
            HarmoniaTheme {
                TracksTabContent(
                    tracks = listOf(track),
                    unmountedSourceIds = setOf("sdcard-dual-state"),
                    disabledSourceIds = setOf("sdcard-dual-state"),
                    onTrackClick = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("Source Disabled").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play Knives Out by Radiohead (Source Disabled)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Storage Unmounted").assertDoesNotExist()
    }

    @Test
    fun settings_storage_source_row_displays_indexed_track_count_plural() {
        val sourceItem = StorageSourceItemUiState(
            id = "mediastore_primary",
            displayName = "Internal Storage",
            typeDescription = "Internal MediaStore",
            isEnabled = true,
            runtimeStatusDescription = "Ready",
            isRuntimeAvailable = true,
            isRemovable = false,
            indexedTrackCount = 42
        )

        composeTestRule.setContent {
            HarmoniaTheme {
                StorageSourceRow(source = sourceItem, onToggle = {})
            }
        }

        composeTestRule.onNodeWithText("Internal Storage").assertIsDisplayed()
        composeTestRule.onNodeWithText("Internal MediaStore • 42 tracks").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready").assertIsDisplayed()
    }

    @Test
    fun settings_storage_source_row_displays_indexed_track_count_singular() {
        val sourceItem = StorageSourceItemUiState(
            id = "saf_tree_1",
            displayName = "Music Folder",
            typeDescription = "SAF Folder Tree",
            isEnabled = true,
            runtimeStatusDescription = "Accessible",
            isRuntimeAvailable = true,
            isRemovable = false,
            indexedTrackCount = 1
        )

        composeTestRule.setContent {
            HarmoniaTheme {
                StorageSourceRow(source = sourceItem, onToggle = {})
            }
        }

        composeTestRule.onNodeWithText("Music Folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("SAF Folder Tree • 1 track").assertIsDisplayed()
        composeTestRule.onNodeWithText("Accessible").assertIsDisplayed()
    }

    @Test
    fun settings_storage_source_row_displays_zero_track_count() {
        val sourceItem = StorageSourceItemUiState(
            id = "vol_sdcard",
            displayName = "SanDisk SD",
            typeDescription = "Removable Volume",
            isEnabled = true,
            runtimeStatusDescription = "Disconnected",
            isRuntimeAvailable = false,
            isRemovable = true,
            indexedTrackCount = 0
        )

        composeTestRule.setContent {
            HarmoniaTheme {
                StorageSourceRow(source = sourceItem, onToggle = {})
            }
        }

        composeTestRule.onNodeWithText("SanDisk SD").assertIsDisplayed()
        composeTestRule.onNodeWithText("Removable Volume • 0 tracks").assertIsDisplayed()
        composeTestRule.onNodeWithText("Disconnected").assertIsDisplayed()
    }
}
