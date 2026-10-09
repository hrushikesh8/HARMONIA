package org.harmonia.player.domain.scanner

import org.harmonia.player.domain.model.scanner.DiscoveredMedia
import org.harmonia.player.domain.model.scanner.ReconciliationItemState
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.scanner.ScanProgress
import org.harmonia.player.domain.model.scanner.ScanProgressStatus
import org.harmonia.player.domain.model.storage.ScanCheckpoint
import org.harmonia.player.domain.model.storage.ScanStatus
import org.harmonia.player.domain.model.storage.SourceIdentity
import org.harmonia.player.domain.model.storage.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveredMediaAndIdentityTest {

    @Test
    fun discovered_media_model_holds_correct_identity_and_locators() {
        val identity = SourceIdentity.mediaStorePrimary("Music/Coltrane/GiantSteps.flac")
        val candidate = DiscoveredMedia(
            sourceIdentity = identity,
            runtimeLocator = "content://media/external/audio/media/1001",
            discoveryLocator = "1001",
            displayName = "GiantSteps.flac",
            mimeType = "audio/flac",
            sizeBytes = 25_000_000L,
            lastModifiedEpochSec = 1600000000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY,
            isAccessible = true
        )

        assertEquals("content://media/external/audio/media/1001", candidate.runtimeLocator)
        assertEquals("1001", candidate.discoveryLocator)
        assertEquals(identity, candidate.sourceIdentity)
        assertEquals(SourceType.MEDIA_STORE_PRIMARY, candidate.sourceType)
        assertTrue(candidate.isAccessible)
        assertEquals(25_000_000L, candidate.sizeBytes)
    }

    @Test
    fun discovery_locator_is_not_part_of_source_identity() {
        val id1 = SourceIdentity.mediaStorePrimary("Music/Song.mp3")
        val candidate1 = DiscoveredMedia(
            sourceIdentity = id1,
            runtimeLocator = "content://media/external/audio/media/1",
            discoveryLocator = "1",
            displayName = "Song.mp3",
            sizeBytes = 5000L,
            lastModifiedEpochSec = 100L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY
        )

        // Same physical file re-indexed by MediaStore with different _ID
        val candidate2 = candidate1.copy(
            runtimeLocator = "content://media/external/audio/media/999",
            discoveryLocator = "999"
        )

        assertEquals(candidate1.sourceIdentity, candidate2.sourceIdentity)
        assertNotEquals(candidate1.discoveryLocator, candidate2.discoveryLocator)
    }

    @Test
    fun saf_document_identity_mapping_preserves_tree_and_doc_id() {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        val docUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Ftrack.m4a"

        val safIdentity = SourceIdentity.safDocument(treeUri, docUri)
        val candidate = DiscoveredMedia(
            sourceIdentity = safIdentity,
            runtimeLocator = docUri,
            discoveryLocator = docUri,
            displayName = "track.m4a",
            mimeType = "audio/mp4",
            sizeBytes = 12_000_000L,
            lastModifiedEpochSec = 1700000000L,
            sourceType = SourceType.SAF_DOCUMENT_TREE
        )

        assertEquals(SourceType.SAF_DOCUMENT_TREE, candidate.sourceIdentity.sourceType)
        assertEquals(treeUri, candidate.sourceIdentity.volumeId)
        assertEquals(docUri, candidate.sourceIdentity.relativePathOrDocId)
    }

    @Test
    fun freshness_logic_identifies_matching_size_and_mtime_as_fresh() {
        val existingSize = 8_369L
        val existingVerifiedAt = 1600000000000L

        val freshCandidate = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("track.mp3"),
            runtimeLocator = "content://media/1",
            displayName = "track.mp3",
            sizeBytes = 8_369L,
            lastModifiedEpochSec = 1600000000L, // 1600000000000 ms
            sourceType = SourceType.MEDIA_STORE_PRIMARY
        )

        val isFresh = freshCandidate.sizeBytes == existingSize &&
                existingVerifiedAt >= (freshCandidate.lastModifiedEpochSec * 1000L)
        assertTrue(isFresh)

        // Modified candidate with different size
        val sizeChanged = freshCandidate.copy(sizeBytes = 9_000L)
        assertFalse(sizeChanged.sizeBytes == existingSize && existingVerifiedAt >= (sizeChanged.lastModifiedEpochSec * 1000L))

        // Modified candidate with newer timestamp
        val timeChanged = freshCandidate.copy(lastModifiedEpochSec = 1600000001L)
        assertFalse(timeChanged.sizeBytes == existingSize && existingVerifiedAt >= (timeChanged.lastModifiedEpochSec * 1000L))
    }

    @Test
    fun reconciliation_states_and_scan_modes_behave_deterministically() {
        assertEquals(ScanMode.INCREMENTAL, ScanMode.valueOf("INCREMENTAL"))
        assertEquals(ScanMode.FULL_RECONCILIATION, ScanMode.valueOf("FULL_RECONCILIATION"))

        val states = listOf(
            ReconciliationItemState.NEW,
            ReconciliationItemState.CHANGED,
            ReconciliationItemState.UNCHANGED,
            ReconciliationItemState.MOVED,
            ReconciliationItemState.MISSING,
            ReconciliationItemState.FAILED
        )
        assertEquals(6, states.size)
    }

    @Test
    fun scan_progress_reporting_aggregates_counts_correctly() {
        val initial = ScanProgress(sourceId = "mediastore_primary", status = ScanProgressStatus.IDLE)
        assertEquals(0, initial.discoveredCount)
        assertEquals(ScanProgressStatus.IDLE, initial.status)

        val running = initial.copy(
            status = ScanProgressStatus.PROCESSING,
            discoveredCount = 10,
            processedCount = 5,
            importedCount = 3,
            unchangedCount = 2
        )
        assertEquals(10, running.discoveredCount)
        assertEquals(5, running.processedCount)
        assertEquals(3, running.importedCount)
        assertEquals(2, running.unchangedCount)

        val completed = running.copy(
            status = ScanProgressStatus.COMPLETED,
            processedCount = 10
        )
        assertEquals(ScanProgressStatus.COMPLETED, completed.status)
        assertEquals(10, completed.processedCount)
    }

    @Test
    fun checkpoint_advancement_semantics_preserve_generation_and_status() {
        val checkpoint = ScanCheckpoint(
            sourceId = "mediastore_primary",
            volumeName = "mediastore_primary",
            lastGeneration = 500L,
            lastScanTimestamp = 1600000000000L,
            scannedFileCount = 42,
            status = ScanStatus.COMPLETED
        )

        assertEquals("mediastore_primary", checkpoint.sourceId)
        assertEquals(500L, checkpoint.lastGeneration)
        assertEquals(42, checkpoint.scannedFileCount)
        assertEquals(ScanStatus.COMPLETED, checkpoint.status)
    }

    @Test
    fun discovered_media_distinctly_separates_last_modified_freshness_from_provider_generation() {
        val candidate = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("track.mp3"),
            runtimeLocator = "content://media/external/audio/media/1",
            displayName = "track.mp3",
            sizeBytes = 1024L,
            lastModifiedEpochSec = 1728432000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY,
            generation = 42L
        )

        assertEquals(1728432000L, candidate.lastModifiedEpochSec)
        assertEquals(42L, candidate.generation)
    }
}
