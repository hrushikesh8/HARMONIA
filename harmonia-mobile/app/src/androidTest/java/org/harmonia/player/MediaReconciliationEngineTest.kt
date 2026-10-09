package org.harmonia.player

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.ArtworkProvider
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.metadata.AndroidAudioMetadataReader
import org.harmonia.player.data.local.scanner.DefaultMediaReconciliationEngine
import org.harmonia.player.data.local.scanner.MediaStoreScanner
import org.harmonia.player.data.local.scanner.SafTreeScanner
import org.harmonia.player.domain.model.scanner.DiscoveredMedia
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.scanner.ScanProgressStatus
import org.harmonia.player.domain.model.storage.SourceIdentity
import org.harmonia.player.domain.model.storage.SourceType
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker
import org.harmonia.player.domain.model.storage.permission.StoragePermissionState
import org.harmonia.player.domain.scanner.MediaSourceScanner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class MediaReconciliationEngineTest {

    private lateinit var context: Context
    private lateinit var database: HarmoniaDatabase
    private lateinit var artworkProvider: ArtworkProvider
    private lateinit var metadataReader: AndroidAudioMetadataReader
    private lateinit var testMediaDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, HarmoniaDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        artworkProvider = ArtworkProvider.getInstance(context)
        artworkProvider.clearMemoryCache()

        metadataReader = AndroidAudioMetadataReader(context, artworkProvider)

        testMediaDir = File(context.cacheDir, "engine_test_media").apply { mkdirs() }
        copyAssetToFile("test_track.mp3", File(testMediaDir, "test_track.mp3"))
        copyAssetToFile("test_track.flac", File(testMediaDir, "test_track.flac"))
        copyAssetToFile("image.mp3", File(testMediaDir, "image.mp3"))
        copyAssetToFile("empty.mp3", File(testMediaDir, "empty.mp3"))
        copyAssetToFile("corrupt.mp3", File(testMediaDir, "corrupt.mp3"))
    }

    @After
    fun tearDown() {
        database.close()
        artworkProvider.clearMemoryCache()
        val artDir = File(context.filesDir, "artwork")
        if (artDir.exists()) artDir.deleteRecursively()
        if (testMediaDir.exists()) testMediaDir.deleteRecursively()
    }

    private fun copyAssetToFile(assetName: String, destination: File) {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        testContext.assets.open(assetName).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    private class TestMockScanner(
        private val candidatesProvider: () -> List<DiscoveredMedia>
    ) : MediaSourceScanner {
        override val supportedSourceType: SourceType = SourceType.MEDIA_STORE_PRIMARY
        override suspend fun discoverMedia(
            source: StorageSource,
            mode: ScanMode,
            lastGeneration: Long
        ): List<DiscoveredMedia> = candidatesProvider()
    }

    private class TestMockPermissionChecker(
        var state: StoragePermissionState = StoragePermissionState.GRANTED
    ) : StoragePermissionChecker {
        override fun getPermissionState(): StoragePermissionState = state
        override fun canScanAudioMedia(): Boolean = state == StoragePermissionState.GRANTED
    }

    @Test
    fun initial_scan_discovers_and_imports_new_tracks_and_local_files() = runBlocking {
        val mp3File = File(testMediaDir, "test_track.mp3")
        val flacFile = File(testMediaDir, "test_track.flac")

        val candidates = listOf(
            DiscoveredMedia(
                sourceIdentity = SourceIdentity.mediaStorePrimary("test_track.mp3"),
                runtimeLocator = mp3File.absolutePath,
                discoveryLocator = "101",
                displayName = "test_track.mp3",
                mimeType = "audio/mpeg",
                sizeBytes = mp3File.length(),
                lastModifiedEpochSec = mp3File.lastModified() / 1000L,
                sourceType = SourceType.MEDIA_STORE_PRIMARY,
                generation = 10L
            ),
            DiscoveredMedia(
                sourceIdentity = SourceIdentity.mediaStorePrimary("test_track.flac"),
                runtimeLocator = flacFile.absolutePath,
                discoveryLocator = "102",
                displayName = "test_track.flac",
                mimeType = "audio/flac",
                sizeBytes = flacFile.length(),
                lastModifiedEpochSec = flacFile.lastModified() / 1000L,
                sourceType = SourceType.MEDIA_STORE_PRIMARY,
                generation = 20L
            )
        )

        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.GRANTED)
        val mockScanner = TestMockScanner { candidates }

        // Seed storage source in DB
        val source = StorageSource.MediaStorePrimary()
        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = source.id,
                type = "MEDIA_STORE_PRIMARY",
                uri = "content://media/external/audio/media",
                displayName = source.displayName,
                isRemovable = false,
                isEnabled = true
            )
        )

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = permissionChecker,
            mediaStoreScanner = mockScanner,
            safTreeScanner = SafTreeScanner(context, permissionChecker)
        )

        val events = engine.scanSource(source, ScanMode.FULL_RECONCILIATION).toList()
        val finalProgress = events.last()

        assertEquals(ScanProgressStatus.COMPLETED, finalProgress.status)
        assertEquals(2, finalProgress.discoveredCount)
        assertEquals(2, finalProgress.importedCount)
        assertEquals(0, finalProgress.failedCount)

        // Verify database persistence
        assertEquals(2, database.trackDao().getTrackCount())
        assertEquals(2, database.localFileDao().getAvailableFileCount())

        // Verify checkpoint advancement
        val checkpoint = database.scanCheckpointDao().getCheckpoint(source.id)
        assertNotNull(checkpoint)
        assertEquals("COMPLETED", checkpoint?.status)
        assertEquals(2, checkpoint?.scannedFileCount)
        assertEquals(20L, checkpoint?.lastGeneration)
    }

    @Test
    fun incremental_scan_recognizes_unchanged_files_and_skips_reprocessing() = runBlocking {
        val mp3File = File(testMediaDir, "test_track.mp3")
        val candidate = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("test_track.mp3"),
            runtimeLocator = mp3File.absolutePath,
            discoveryLocator = "101",
            displayName = "test_track.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = mp3File.length(),
            lastModifiedEpochSec = mp3File.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY
        )

        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.GRANTED)
        val source = StorageSource.MediaStorePrimary()
        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = source.id,
                type = "MEDIA_STORE_PRIMARY",
                uri = "content://media/external/audio/media",
                displayName = source.displayName,
                isRemovable = false,
                isEnabled = true
            )
        )

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = permissionChecker,
            mediaStoreScanner = TestMockScanner { listOf(candidate) },
            safTreeScanner = SafTreeScanner(context, permissionChecker)
        )

        // 1. Initial scan: imports 1 file
        val initialEvents = engine.scanSource(source, ScanMode.INCREMENTAL).toList()
        assertEquals(1, initialEvents.last().importedCount)

        // 2. Second scan: identical candidate -> unchanged
        val secondEvents = engine.scanSource(source, ScanMode.INCREMENTAL).toList()
        val secondResult = secondEvents.last()

        assertEquals(ScanProgressStatus.COMPLETED, secondResult.status)
        assertEquals(0, secondResult.importedCount)
        assertEquals(1, secondResult.unchangedCount)
        assertEquals(0, secondResult.updatedCount)
    }

    @Test
    fun scan_with_embedded_artwork_stores_artwork_in_m3_artwork_provider() = runBlocking {
        val imageFile = File(testMediaDir, "image.mp3")
        val candidate = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("image.mp3"),
            runtimeLocator = imageFile.absolutePath,
            discoveryLocator = "105",
            displayName = "image.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = imageFile.length(),
            lastModifiedEpochSec = imageFile.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY
        )

        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.GRANTED)
        val source = StorageSource.MediaStorePrimary()
        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = source.id,
                type = "MEDIA_STORE_PRIMARY",
                uri = "content://media/external/audio/media",
                displayName = source.displayName,
                isRemovable = false,
                isEnabled = true
            )
        )

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = permissionChecker,
            mediaStoreScanner = TestMockScanner { listOf(candidate) },
            safTreeScanner = SafTreeScanner(context, permissionChecker)
        )

        val events = engine.scanSource(source, ScanMode.FULL_RECONCILIATION).toList()
        assertEquals(1, events.last().importedCount)

        // Verify that artwork is registered in AlbumEntity and ArtworkProvider
        val localFile = database.localFileDao().getLocalFileByUri(imageFile.absolutePath)
        assertNotNull(localFile)
        val track = database.trackDao().getTrackById(localFile!!.trackId)
        assertNotNull(track)
        val album = track?.track?.albumId?.let { database.albumDao().getAlbumById(it) }
        val artHash = album?.artworkHash

        assertNotNull(artHash)
        assertTrue(artworkProvider.isArtworkAvailable(artHash!!))
    }

    @Test
    fun full_reconciliation_marks_missing_file_without_deleting_database_records() = runBlocking {
        val mp3File = File(testMediaDir, "test_track.mp3")
        val candidate = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("test_track.mp3"),
            runtimeLocator = mp3File.absolutePath,
            discoveryLocator = "101",
            displayName = "test_track.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = mp3File.length(),
            lastModifiedEpochSec = mp3File.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY
        )

        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.GRANTED)
        val source = StorageSource.MediaStorePrimary()
        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = source.id,
                type = "MEDIA_STORE_PRIMARY",
                uri = "content://media/external/audio/media",
                displayName = source.displayName,
                isRemovable = false,
                isEnabled = true
            )
        )

        var currentCandidates = listOf(candidate)

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = permissionChecker,
            mediaStoreScanner = TestMockScanner { currentCandidates },
            safTreeScanner = SafTreeScanner(context, permissionChecker)
        )

        // 1. Initial scan imports file
        engine.scanSource(source, ScanMode.FULL_RECONCILIATION).toList()
        assertEquals(1, database.trackDao().getTrackCount())

        // 2. Candidate disappears from storage
        currentCandidates = emptyList()
        val secondEvents = engine.scanSource(source, ScanMode.FULL_RECONCILIATION).toList()
        val secondResult = secondEvents.last()

        assertEquals(1, secondResult.unavailableCount)
        // Record must NOT be deleted from Room!
        assertEquals(1, database.trackDao().getTrackCount())

        val localFile = database.localFileDao().getLocalFileByUri(mp3File.absolutePath)
        assertNotNull(localFile)
        assertEquals("MISSING", localFile?.availabilityStatus)
    }

    @Test
    fun permission_denied_aborts_without_modifying_library_or_checkpoint() = runBlocking {
        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.DENIED)
        val source = StorageSource.MediaStorePrimary()

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = permissionChecker,
            mediaStoreScanner = MediaStoreScanner(context, permissionChecker),
            safTreeScanner = SafTreeScanner(context, permissionChecker)
        )

        val events = engine.scanSource(source, ScanMode.INCREMENTAL).toList()
        val result = events.last()

        assertEquals(ScanProgressStatus.PERMISSION_REQUIRED, result.status)
        assertEquals(0, database.trackDao().getTrackCount())
        assertEquals(null, database.scanCheckpointDao().getCheckpoint(source.id))
    }

    @Test
    fun moved_or_renamed_file_reconciles_by_content_hash_without_creating_duplicate_track() = runBlocking {
        val originalFile = File(testMediaDir, "moving_origin.mp3")
        File(testMediaDir, "test_track.mp3").copyTo(originalFile, overwrite = true)
        val renamedDestination = File(testMediaDir, "moved_dest.mp3")
        if (renamedDestination.exists()) renamedDestination.delete()

        val initialCandidate = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("moving_origin.mp3"),
            runtimeLocator = originalFile.absolutePath,
            discoveryLocator = "101",
            displayName = "moving_origin.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = originalFile.length(),
            lastModifiedEpochSec = originalFile.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY
        )

        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.GRANTED)
        val source = StorageSource.MediaStorePrimary()
        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = source.id,
                type = "MEDIA_STORE_PRIMARY",
                uri = "content://media/external/audio/media",
                displayName = source.displayName,
                isRemovable = false,
                isEnabled = true
            )
        )

        var currentCandidates = listOf(initialCandidate)
        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = permissionChecker,
            mediaStoreScanner = TestMockScanner { currentCandidates },
            safTreeScanner = SafTreeScanner(context, permissionChecker)
        )

        // 1. Initial scan: imports original file
        val initialEvents = engine.scanSource(source, ScanMode.FULL_RECONCILIATION).toList()
        assertEquals(1, initialEvents.last().importedCount)
        assertEquals(1, database.trackDao().getTrackCount())

        val initialLocalFile = database.localFileDao().getLocalFileByUri(originalFile.absolutePath)
        assertNotNull(initialLocalFile)
        val initialTrackId = initialLocalFile!!.trackId
        val initialFileId = initialLocalFile.id
        val initialHash = initialLocalFile.fileHash

        // 2. Perform a REAL filesystem move: renameTo moves the file and removes the old location
        assertTrue(originalFile.renameTo(renamedDestination))
        assertFalse(originalFile.exists())
        assertTrue(renamedDestination.exists())

        // 3. Rescan with moved candidate
        val movedCandidate = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("moved_dest.mp3"),
            runtimeLocator = renamedDestination.absolutePath,
            discoveryLocator = "102",
            displayName = "moved_dest.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = renamedDestination.length(),
            lastModifiedEpochSec = renamedDestination.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY
        )

        currentCandidates = listOf(movedCandidate)
        val secondEvents = engine.scanSource(source, ScanMode.FULL_RECONCILIATION).toList()
        val secondResult = secondEvents.last()

        assertEquals(ScanProgressStatus.COMPLETED, secondResult.status)
        assertEquals(0, secondResult.importedCount)
        assertEquals(1, secondResult.movedCount)

        // Verify: Same Track, no duplicate Track
        assertEquals(1, database.trackDao().getTrackCount())

        // Verify: Same physical LocalFile reconciled in-place, no duplicate LocalFile
        assertEquals(1, database.localFileDao().getAllLocalFiles().size)
        val reconciledFile = database.localFileDao().getLocalFileByUri(renamedDestination.absolutePath)
        assertNotNull(reconciledFile)
        assertEquals(initialFileId, reconciledFile?.id)
        assertEquals(initialTrackId, reconciledFile?.trackId)
        assertEquals(initialHash, reconciledFile?.fileHash)
        assertEquals("AVAILABLE", reconciledFile?.availabilityStatus)

        // Verify: Old URI is no longer considered the active location
        assertNull(database.localFileDao().getLocalFileByUri(originalFile.absolutePath))
    }

    @Test
    fun duplicate_byte_identical_files_at_distinct_locations_preserve_both_physical_instances() = runBlocking {
        val fileA = File(testMediaDir, "dup_a.mp3")
        val fileB = File(testMediaDir, "dup_b.mp3")
        File(testMediaDir, "test_track.mp3").copyTo(fileA, overwrite = true)
        File(testMediaDir, "test_track.mp3").copyTo(fileB, overwrite = true)

        assertTrue(fileA.exists())
        assertTrue(fileB.exists())

        val candidateA = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("dup_a.mp3"),
            runtimeLocator = fileA.absolutePath,
            discoveryLocator = "201",
            displayName = "dup_a.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = fileA.length(),
            lastModifiedEpochSec = fileA.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY
        )
        val candidateB = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("dup_b.mp3"),
            runtimeLocator = fileB.absolutePath,
            discoveryLocator = "202",
            displayName = "dup_b.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = fileB.length(),
            lastModifiedEpochSec = fileB.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY
        )

        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.GRANTED)
        val source = StorageSource.MediaStorePrimary()
        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = source.id,
                type = "MEDIA_STORE_PRIMARY",
                uri = "content://media/external/audio/media",
                displayName = source.displayName,
                isRemovable = false,
                isEnabled = true
            )
        )

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = permissionChecker,
            mediaStoreScanner = TestMockScanner { listOf(candidateA, candidateB) },
            safTreeScanner = SafTreeScanner(context, permissionChecker)
        )

        val events = engine.scanSource(source, ScanMode.FULL_RECONCILIATION).toList()
        val result = events.last()

        assertEquals(ScanProgressStatus.COMPLETED, result.status)
        assertEquals(2, result.discoveredCount)
        assertEquals(2, result.importedCount)
        assertEquals(0, result.movedCount)

        // Logical Track: Exactly 1 Track represents the musical work (no duplicate track!)
        assertEquals(1, database.trackDao().getTrackCount())

        // Physical LocalFiles: BOTH instances are preserved (2 physical rows)
        val allLocalFiles = database.localFileDao().getAllLocalFiles()
        assertEquals(2, allLocalFiles.size)

        val localFileA = database.localFileDao().getLocalFileByUri(fileA.absolutePath)
        val localFileB = database.localFileDao().getLocalFileByUri(fileB.absolutePath)
        assertNotNull(localFileA)
        assertNotNull(localFileB)

        // Distinct physical file IDs, but same logical track and identical content hash
        assertTrue(localFileA!!.id != localFileB!!.id)
        assertEquals(localFileA.trackId, localFileB.trackId)
        assertEquals(localFileA.fileHash, localFileB.fileHash)
        assertEquals("AVAILABLE", localFileA.availabilityStatus)
        assertEquals("AVAILABLE", localFileB.availabilityStatus)
    }

    @Test
    fun fatal_media_store_query_failure_aborts_without_advancing_checkpoint() = runBlocking {
        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.GRANTED)
        val source = StorageSource.MediaStorePrimary()
        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = source.id,
                type = "MEDIA_STORE_PRIMARY",
                uri = "content://media/external/audio/media",
                displayName = source.displayName,
                isRemovable = false,
                isEnabled = true
            )
        )

        // Seed prior checkpoint at generation 80L
        database.scanCheckpointDao().upsertCheckpoint(
            org.harmonia.player.data.local.entity.ScanCheckpointEntity(
                sourceId = source.id,
                volumeName = source.id,
                lastGeneration = 80L,
                lastScanTimestamp = 1600000000000L,
                scannedFileCount = 3,
                status = "COMPLETED"
            )
        )

        val failingScanner = object : MediaSourceScanner {
            override val supportedSourceType: SourceType = SourceType.MEDIA_STORE_PRIMARY
            override suspend fun discoverMedia(source: StorageSource, mode: ScanMode, lastGeneration: Long): List<DiscoveredMedia> {
                throw RuntimeException("Fatal MediaStore SQLite query failure")
            }
        }

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = permissionChecker,
            mediaStoreScanner = failingScanner,
            safTreeScanner = SafTreeScanner(context, permissionChecker)
        )

        val events = engine.scanSource(source, ScanMode.FULL_RECONCILIATION).toList()
        val result = events.last()

        assertEquals(ScanProgressStatus.FAILED, result.status)
        assertEquals("Fatal MediaStore SQLite query failure", result.errorMessage)
        assertEquals(0, database.trackDao().getTrackCount())

        // Previous checkpoint remains completely unchanged!
        val checkpoint = database.scanCheckpointDao().getCheckpoint(source.id)
        assertNotNull(checkpoint)
        assertEquals(80L, checkpoint?.lastGeneration)
        assertEquals("COMPLETED", checkpoint?.status)
    }

    @Test
    fun successful_mediastore_incremental_scan_advances_generation_watermark_using_correct_value() = runBlocking {
        val file = File(testMediaDir, "test_track.mp3")
        val candidate = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("test_track.mp3"),
            runtimeLocator = file.absolutePath,
            discoveryLocator = "301",
            displayName = "test_track.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = file.length(),
            lastModifiedEpochSec = file.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY,
            generation = 150L
        )

        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.GRANTED)
        val source = StorageSource.MediaStorePrimary()
        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = source.id,
                type = "MEDIA_STORE_PRIMARY",
                uri = "content://media/external/audio/media",
                displayName = source.displayName,
                isRemovable = false,
                isEnabled = true
            )
        )

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = permissionChecker,
            mediaStoreScanner = TestMockScanner { listOf(candidate) },
            safTreeScanner = SafTreeScanner(context, permissionChecker)
        )

        val events = engine.scanSource(source, ScanMode.FULL_RECONCILIATION).toList()
        assertEquals(ScanProgressStatus.COMPLETED, events.last().status)

        val checkpoint = database.scanCheckpointDao().getCheckpoint(source.id)
        assertNotNull(checkpoint)
        assertEquals("COMPLETED", checkpoint?.status)
        assertEquals(150L, checkpoint?.lastGeneration)
    }

    @Test
    fun later_mediastore_scan_discovers_newly_inserted_item_with_greater_provider_generation() = runBlocking {
        val file1 = File(testMediaDir, "test_track.mp3")
        val file2 = File(testMediaDir, "test_track.flac")

        val candidate1 = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("test_track.mp3"),
            runtimeLocator = file1.absolutePath,
            discoveryLocator = "301",
            displayName = "test_track.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = file1.length(),
            lastModifiedEpochSec = file1.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY,
            generation = 150L
        )

        val candidate2 = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("test_track.flac"),
            runtimeLocator = file2.absolutePath,
            discoveryLocator = "302",
            displayName = "test_track.flac",
            mimeType = "audio/flac",
            sizeBytes = file2.length(),
            lastModifiedEpochSec = file2.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY,
            generation = 175L
        )

        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.GRANTED)
        val source = StorageSource.MediaStorePrimary()
        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = source.id,
                type = "MEDIA_STORE_PRIMARY",
                uri = "content://media/external/audio/media",
                displayName = source.displayName,
                isRemovable = false,
                isEnabled = true
            )
        )

        // Seed initial checkpoint at generation 150L
        database.scanCheckpointDao().upsertCheckpoint(
            org.harmonia.player.data.local.entity.ScanCheckpointEntity(
                sourceId = source.id,
                volumeName = source.id,
                lastGeneration = 150L,
                lastScanTimestamp = System.currentTimeMillis() - 10000L,
                scannedFileCount = 1,
                status = "COMPLETED"
            )
        )

        var requestedGeneration = -1L
        val mockScanner = object : MediaSourceScanner {
            override val supportedSourceType: SourceType = SourceType.MEDIA_STORE_PRIMARY
            override suspend fun discoverMedia(source: StorageSource, mode: ScanMode, lastGeneration: Long): List<DiscoveredMedia> {
                requestedGeneration = lastGeneration
                // Only return items with generation > lastGeneration
                return listOf(candidate1, candidate2).filter { (it.generation ?: 0L) > lastGeneration }
            }
        }

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = permissionChecker,
            mediaStoreScanner = mockScanner,
            safTreeScanner = SafTreeScanner(context, permissionChecker)
        )

        val events = engine.scanSource(source, ScanMode.INCREMENTAL).toList()
        val result = events.last()

        assertEquals(ScanProgressStatus.COMPLETED, result.status)
        assertEquals(150L, requestedGeneration)
        assertEquals(1, result.discoveredCount)
        assertEquals(1, result.importedCount)

        val updatedCheckpoint = database.scanCheckpointDao().getCheckpoint(source.id)
        assertNotNull(updatedCheckpoint)
        assertEquals("COMPLETED", updatedCheckpoint?.status)
        assertEquals(175L, updatedCheckpoint?.lastGeneration)
    }

    @Test
    fun partial_scan_does_not_advance_checkpoint_past_unprocessed_candidates() = runBlocking {
        val validFile = File(testMediaDir, "test_track.flac")
        val corruptFile = File(testMediaDir, "corrupt.mp3")

        val validCandidate = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("test_track.flac"),
            runtimeLocator = validFile.absolutePath,
            discoveryLocator = "302",
            displayName = "test_track.flac",
            mimeType = "audio/flac",
            sizeBytes = validFile.length(),
            lastModifiedEpochSec = validFile.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY,
            generation = 200L
        )

        val corruptCandidate = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("corrupt.mp3"),
            runtimeLocator = corruptFile.absolutePath,
            discoveryLocator = "303",
            displayName = "corrupt.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = corruptFile.length(),
            lastModifiedEpochSec = corruptFile.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY,
            isAccessible = false,
            generation = 250L
        )

        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.GRANTED)
        val source = StorageSource.MediaStorePrimary()
        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = source.id,
                type = "MEDIA_STORE_PRIMARY",
                uri = "content://media/external/audio/media",
                displayName = source.displayName,
                isRemovable = false,
                isEnabled = true
            )
        )

        // Seed initial checkpoint at generation 100L
        database.scanCheckpointDao().upsertCheckpoint(
            org.harmonia.player.data.local.entity.ScanCheckpointEntity(
                sourceId = source.id,
                volumeName = source.id,
                lastGeneration = 100L,
                lastScanTimestamp = System.currentTimeMillis() - 5000L,
                scannedFileCount = 5,
                status = "COMPLETED"
            )
        )

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = permissionChecker,
            mediaStoreScanner = TestMockScanner { listOf(validCandidate, corruptCandidate) },
            safTreeScanner = SafTreeScanner(context, permissionChecker)
        )

        val events = engine.scanSource(source, ScanMode.INCREMENTAL).toList()
        val result = events.last()

        assertEquals(ScanProgressStatus.PARTIAL, result.status)
        assertEquals(1, result.failedCount)

        val checkpoint = database.scanCheckpointDao().getCheckpoint(source.id)
        assertNotNull(checkpoint)
        assertEquals("PARTIAL", checkpoint?.status)
        // Must NOT advance past unprocessed candidates!
        assertEquals(100L, checkpoint?.lastGeneration)
    }

    @Test
    fun saf_checkpoint_handling_does_not_confuse_timestamps_with_mediastore_generations() = runBlocking {
        val file = File(testMediaDir, "test_track.mp3")
        val safCandidate = DiscoveredMedia(
            sourceIdentity = SourceIdentity.safDocument(
                treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
                documentId = "primary:Music/test_track.mp3"
            ),
            runtimeLocator = file.absolutePath,
            discoveryLocator = "primary:Music/test_track.mp3",
            displayName = "test_track.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = file.length(),
            lastModifiedEpochSec = 1728432000L,
            sourceType = SourceType.SAF_DOCUMENT_TREE,
            generation = null
        )

        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.GRANTED)
        val source = StorageSource.SafDocumentTree(
            id = "saf_tree_1",
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            displayName = "Music Folder"
        )
        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = source.id,
                type = "SAF_DOCUMENT_TREE",
                uri = source.treeUri,
                displayName = source.displayName,
                isRemovable = false,
                isEnabled = true
            )
        )

        val safMockScanner = object : MediaSourceScanner {
            override val supportedSourceType: SourceType = SourceType.SAF_DOCUMENT_TREE
            override suspend fun discoverMedia(source: StorageSource, mode: ScanMode, lastGeneration: Long): List<DiscoveredMedia> {
                return listOf(safCandidate)
            }
        }

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = permissionChecker,
            mediaStoreScanner = TestMockScanner { emptyList() },
            safTreeScanner = safMockScanner
        )

        val events = engine.scanSource(source, ScanMode.FULL_RECONCILIATION).toList()
        val result = events.last()

        assertEquals(ScanProgressStatus.COMPLETED, result.status)
        assertEquals(1, result.importedCount)

        val checkpoint = database.scanCheckpointDao().getCheckpoint(source.id)
        assertNotNull(checkpoint)
        assertEquals("COMPLETED", checkpoint?.status)
        // SAF checkpoint must NOT substitute timestamps into lastGeneration!
        assertEquals(0L, checkpoint?.lastGeneration)
        assertTrue((checkpoint?.lastScanTimestamp ?: 0L) > 0L)
    }

    @Test
    fun corrupt_or_unreadable_file_is_isolated_and_scan_continues() = runBlocking {
        val corruptFile = File(testMediaDir, "corrupt.mp3")
        val validFile = File(testMediaDir, "test_track.flac")

        val candidates = listOf(
            DiscoveredMedia(
                sourceIdentity = SourceIdentity.mediaStorePrimary("corrupt.mp3"),
                runtimeLocator = corruptFile.absolutePath,
                discoveryLocator = "999",
                displayName = "corrupt.mp3",
                mimeType = "audio/mpeg",
                sizeBytes = corruptFile.length(),
                lastModifiedEpochSec = corruptFile.lastModified() / 1000L,
                sourceType = SourceType.MEDIA_STORE_PRIMARY,
                isAccessible = false
            ),
            DiscoveredMedia(
                sourceIdentity = SourceIdentity.mediaStorePrimary("test_track.flac"),
                runtimeLocator = validFile.absolutePath,
                discoveryLocator = "102",
                displayName = "test_track.flac",
                mimeType = "audio/flac",
                sizeBytes = validFile.length(),
                lastModifiedEpochSec = validFile.lastModified() / 1000L,
                sourceType = SourceType.MEDIA_STORE_PRIMARY,
                isAccessible = true
            )
        )

        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.GRANTED)
        val source = StorageSource.MediaStorePrimary()
        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = source.id,
                type = "MEDIA_STORE_PRIMARY",
                uri = "content://media/external/audio/media",
                displayName = source.displayName,
                isRemovable = false,
                isEnabled = true
            )
        )

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = permissionChecker,
            mediaStoreScanner = TestMockScanner { candidates },
            safTreeScanner = SafTreeScanner(context, permissionChecker)
        )

        val events = engine.scanSource(source, ScanMode.FULL_RECONCILIATION).toList()
        val finalProgress = events.last()

        assertEquals(ScanProgressStatus.PARTIAL, finalProgress.status)
        assertEquals(2, finalProgress.discoveredCount)
        assertEquals(1, finalProgress.failedCount)
        assertEquals(1, finalProgress.importedCount)
        assertEquals(1, database.trackDao().getTrackCount())

        // Checkpoint must NOT advance beyond failed boundary!
        val checkpoint = database.scanCheckpointDao().getCheckpoint(source.id)
        assertNotNull(checkpoint)
        assertEquals("PARTIAL", checkpoint?.status)
        assertEquals(0L, checkpoint?.lastGeneration)
    }

    @Test
    fun content_hasher_computes_deterministic_sha256_hash() = runBlocking {
        val mp3File = File(testMediaDir, "test_track.mp3")
        val flacFile = File(testMediaDir, "test_track.flac")

        val hash1 = org.harmonia.player.data.local.scanner.ContentHasher.computeFileHash(context, mp3File.absolutePath)
        val hash2 = org.harmonia.player.data.local.scanner.ContentHasher.computeFileHash(context, mp3File.absolutePath)
        val flacHash = org.harmonia.player.data.local.scanner.ContentHasher.computeFileHash(context, flacFile.absolutePath)

        assertNotNull(hash1)
        assertEquals(64, hash1?.length)
        assertEquals(hash1, hash2)
        assertNotNull(flacHash)
        assertTrue(hash1 != flacHash)
    }

    @Test
    fun real_media_store_runtime_discovery_and_reconciliation() = runBlocking {
        val permissionChecker = TestMockPermissionChecker(StoragePermissionState.GRANTED)
        val realScanner = MediaStoreScanner(context, permissionChecker)
        val source = StorageSource.MediaStorePrimary()

        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, "real_test_track.mp3")
            put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(android.provider.MediaStore.Audio.Media.IS_MUSIC, 1)
        }

        val insertedUri = try {
            context.contentResolver.insert(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values
            )
        } catch (_: Throwable) {
            null
        }

        if (insertedUri != null) {
            try {
                // Write valid mp3 audio bytes to the real MediaStore URI
                val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
                testContext.assets.open("test_track.mp3").use { input ->
                    context.contentResolver.openOutputStream(insertedUri)?.use { output ->
                        input.copyTo(output)
                    }
                }

                // Query MediaStore using the actual MediaStoreScanner
                val candidates = realScanner.discoverMedia(source, ScanMode.FULL_RECONCILIATION, 0L)
                val matchingCandidate = candidates.find { it.displayName == "real_test_track.mp3" }

                assertNotNull(matchingCandidate)
                assertTrue(matchingCandidate!!.runtimeLocator.startsWith("content://"))
                assertEquals(SourceType.MEDIA_STORE_PRIMARY, matchingCandidate.sourceType)

                // Reconcile via DefaultMediaReconciliationEngine using real scanner
                database.storageSourceDao().upsertSource(
                    org.harmonia.player.data.local.entity.StorageSourceEntity(
                        id = source.id,
                        type = "MEDIA_STORE_PRIMARY",
                        uri = "content://media/external/audio/media",
                        displayName = source.displayName,
                        isRemovable = false,
                        isEnabled = true
                    )
                )

                val engine = DefaultMediaReconciliationEngine(
                    context = context,
                    database = database,
                    metadataReader = metadataReader,
                    permissionChecker = permissionChecker,
                    mediaStoreScanner = realScanner,
                    safTreeScanner = SafTreeScanner(context, permissionChecker)
                )

                val events = engine.scanSource(source, ScanMode.FULL_RECONCILIATION).toList()
                val finalResult = events.last()

                assertTrue(finalResult.importedCount >= 1)
                val localFile = database.localFileDao().getLocalFileByUri(matchingCandidate.runtimeLocator)
                assertNotNull(localFile)
                assertEquals("AVAILABLE", localFile?.availabilityStatus)

                val checkpoint = database.scanCheckpointDao().getCheckpoint(source.id)
                assertNotNull(checkpoint)
                assertEquals("COMPLETED", checkpoint?.status)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    assertNotNull(matchingCandidate.generation)
                    assertTrue((checkpoint?.lastGeneration ?: 0L) >= (matchingCandidate.generation ?: 0L))
                }
            } finally {
                try {
                    context.contentResolver.delete(insertedUri, null, null)
                } catch (_: Throwable) {}
            }
        }
    }
}
