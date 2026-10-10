package org.harmonia.player

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.ArtworkProvider
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.metadata.AndroidAudioMetadataReader
import org.harmonia.player.data.local.scanner.DefaultMediaReconciliationEngine
import org.harmonia.player.data.local.scanner.SafTreeScanner
import org.harmonia.player.domain.model.scanner.DiscoveredMedia
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.scanner.ScanProgress
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end integration tests proving cross-caller scan coordination through the actual
 * [DefaultMediaReconciliationEngine] and its real internal [SourceScanCoordinator].
 *
 * Verifies:
 * 1. Serialized execution of overlapping reconciliation requests for the same storage source.
 * 2. True independent concurrency of reconciliation requests for distinct storage sources.
 * 3. Lock release and recovery following scan cancellation or unhandled failures.
 */
@RunWith(AndroidJUnit4::class)
class CrossCallerEngineScanCoordinationTest {

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

        testMediaDir = File(context.cacheDir, "e2e_coordination_test_media").apply { mkdirs() }
        copyAssetToFile("test_track.mp3", File(testMediaDir, "test_track.mp3"))
        copyAssetToFile("test_track.flac", File(testMediaDir, "test_track.flac"))
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

    private class ControlledTestScanner(
        override val supportedSourceType: SourceType = SourceType.MEDIA_STORE_PRIMARY,
        private val onDiscover: suspend (StorageSource, ScanMode) -> List<DiscoveredMedia>
    ) : MediaSourceScanner {
        override suspend fun discoverMedia(
            source: StorageSource,
            mode: ScanMode,
            checkpointGeneration: Long
        ): List<DiscoveredMedia> {
            return onDiscover(source, mode)
        }
    }

    private class StubPermissionChecker(
        private val state: StoragePermissionState = StoragePermissionState.GRANTED
    ) : StoragePermissionChecker {
        override fun getPermissionState(): StoragePermissionState = state
        override fun canScanAudioMedia(): Boolean = state == StoragePermissionState.GRANTED
    }

    @Test
    fun engine_serializes_overlapping_scan_requests_for_the_same_source() = runBlocking {
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

        val activeInvocations = AtomicInteger(0)
        val maxConcurrentInvocations = AtomicInteger(0)
        val scan1Started = CompletableDeferred<Unit>()
        val allowScan1ToFinish = CompletableDeferred<Unit>()

        val mp3File = File(testMediaDir, "test_track.mp3")
        val candidate = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("test_track.mp3"),
            runtimeLocator = mp3File.absolutePath,
            discoveryLocator = "1001",
            displayName = "test_track.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = mp3File.length(),
            lastModifiedEpochSec = mp3File.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY
        )

        val controlledScanner = ControlledTestScanner { src, _ ->
            val count = activeInvocations.incrementAndGet()
            var currentMax = maxConcurrentInvocations.get()
            while (count > currentMax) {
                if (maxConcurrentInvocations.compareAndSet(currentMax, count)) break
                currentMax = maxConcurrentInvocations.get()
            }

            if (!scan1Started.isCompleted) {
                scan1Started.complete(Unit)
                allowScan1ToFinish.await()
            }

            activeInvocations.decrementAndGet()
            listOf(candidate)
        }

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = StubPermissionChecker(),
            mediaStoreScanner = controlledScanner,
            safTreeScanner = SafTreeScanner(context, StubPermissionChecker())
        )

        val scan1Results = Collections.synchronizedList(mutableListOf<ScanProgress>())
        val scan2Results = Collections.synchronizedList(mutableListOf<ScanProgress>())

        val job1 = launch(Dispatchers.IO) {
            engine.scanSource(source, ScanMode.FULL_RECONCILIATION).collect {
                scan1Results.add(it)
            }
        }

        // Wait for scan 1 to acquire the lock and enter discovery
        scan1Started.await()

        // Launch scan 2 for the identical source while scan 1 is holding the lock
        val job2 = launch(Dispatchers.IO) {
            engine.scanSource(source, ScanMode.INCREMENTAL).collect {
                scan2Results.add(it)
            }
        }

        // Verify that scan 2 is suspended at withSourceLock and has not entered DISCOVERING
        delay(100)
        assertTrue("Scan 2 must not enter DISCOVERING while scan 1 holds lock",
            scan2Results.none { it.status == ScanProgressStatus.DISCOVERING })
        assertEquals(1, maxConcurrentInvocations.get())

        // Release scan 1
        allowScan1ToFinish.complete(Unit)

        job1.join()
        job2.join()

        // Both scans completed sequentially
        assertEquals(1, maxConcurrentInvocations.get())
        assertEquals(ScanProgressStatus.COMPLETED, scan1Results.last().status)
        assertEquals(ScanProgressStatus.COMPLETED, scan2Results.last().status)
        assertEquals(1, database.trackDao().getTrackCount())
    }

    @Test
    fun engine_allows_concurrent_scans_of_different_storage_sources() = runBlocking {
        val sourceA = StorageSource.MediaStorePrimary()
        val sourceB = StorageSource.SafDocumentTree(
            treeUri = "content://com.android.externalstorage.documents/tree/SD123%3AMusic",
            displayName = "SD Music",
            isPersisted = true,
            isRemovable = true,
            isAvailable = true
        )

        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = sourceA.id,
                type = "MEDIA_STORE_PRIMARY",
                uri = "content://media/external/audio/media",
                displayName = sourceA.displayName,
                isRemovable = false,
                isEnabled = true
            )
        )
        database.storageSourceDao().upsertSource(
            org.harmonia.player.data.local.entity.StorageSourceEntity(
                id = sourceB.id,
                type = "SAF_DOCUMENT_TREE",
                uri = sourceB.treeUri,
                displayName = sourceB.displayName,
                isRemovable = true,
                isEnabled = true
            )
        )

        val activeInvocations = AtomicInteger(0)
        val maxConcurrentInvocations = AtomicInteger(0)
        val bothRunning = CompletableDeferred<Unit>()

        val mp3File = File(testMediaDir, "test_track.mp3")
        val flacFile = File(testMediaDir, "test_track.flac")

        val candidateA = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("test_track.mp3"),
            runtimeLocator = mp3File.absolutePath,
            discoveryLocator = "2001",
            displayName = "test_track.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = mp3File.length(),
            lastModifiedEpochSec = mp3File.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY
        )
        val candidateB = DiscoveredMedia(
            sourceIdentity = SourceIdentity.safDocument(
                treeUri = sourceB.treeUri,
                documentId = "SD123:Music/test_track.flac"
            ),
            runtimeLocator = flacFile.absolutePath,
            discoveryLocator = "2002",
            displayName = "test_track.flac",
            mimeType = "audio/flac",
            sizeBytes = flacFile.length(),
            lastModifiedEpochSec = flacFile.lastModified() / 1000L,
            sourceType = SourceType.SAF_DOCUMENT_TREE
        )

        val scannerA = ControlledTestScanner(SourceType.MEDIA_STORE_PRIMARY) { _, _ ->
            val count = activeInvocations.incrementAndGet()
            var currentMax = maxConcurrentInvocations.get()
            while (count > currentMax) {
                if (maxConcurrentInvocations.compareAndSet(currentMax, count)) break
                currentMax = maxConcurrentInvocations.get()
            }
            if (count == 2) bothRunning.complete(Unit) else bothRunning.await()
            activeInvocations.decrementAndGet()
            listOf(candidateA)
        }

        val scannerB = ControlledTestScanner(SourceType.SAF_DOCUMENT_TREE) { _, _ ->
            val count = activeInvocations.incrementAndGet()
            var currentMax = maxConcurrentInvocations.get()
            while (count > currentMax) {
                if (maxConcurrentInvocations.compareAndSet(currentMax, count)) break
                currentMax = maxConcurrentInvocations.get()
            }
            if (count == 2) bothRunning.complete(Unit) else bothRunning.await()
            activeInvocations.decrementAndGet()
            listOf(candidateB)
        }

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = StubPermissionChecker(),
            mediaStoreScanner = scannerA,
            safTreeScanner = scannerB
        )

        val jobA = launch(Dispatchers.IO) {
            engine.scanSource(sourceA, ScanMode.FULL_RECONCILIATION).toList()
        }
        val jobB = launch(Dispatchers.IO) {
            engine.scanSource(sourceB, ScanMode.FULL_RECONCILIATION).toList()
        }

        jobA.join()
        jobB.join()

        // Must reach concurrency of 2 because sources are distinct
        assertEquals(2, maxConcurrentInvocations.get())
        assertEquals(2, database.trackDao().getTrackCount())
    }

    @Test
    fun engine_releases_lock_on_cancellation_permitting_subsequent_scans() = runBlocking {
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

        val scanStarted = CompletableDeferred<Unit>()
        val mp3File = File(testMediaDir, "test_track.mp3")
        val candidate = DiscoveredMedia(
            sourceIdentity = SourceIdentity.mediaStorePrimary("test_track.mp3"),
            runtimeLocator = mp3File.absolutePath,
            discoveryLocator = "3001",
            displayName = "test_track.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = mp3File.length(),
            lastModifiedEpochSec = mp3File.lastModified() / 1000L,
            sourceType = SourceType.MEDIA_STORE_PRIMARY
        )

        val scanner = ControlledTestScanner { _, _ ->
            scanStarted.complete(Unit)
            // Hang until cancelled
            delay(10_000)
            listOf(candidate)
        }

        val engine = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = StubPermissionChecker(),
            mediaStoreScanner = scanner,
            safTreeScanner = SafTreeScanner(context, StubPermissionChecker())
        )

        val job = launch(Dispatchers.IO) {
            engine.scanSource(source, ScanMode.FULL_RECONCILIATION).collect {}
        }

        scanStarted.await()
        // Cancel first scan while it is holding the lock
        job.cancel()
        job.join()

        // Subsequent scan must acquire the lock immediately without deadlock
        val secondScanner = ControlledTestScanner { _, _ -> listOf(candidate) }
        val engine2 = DefaultMediaReconciliationEngine(
            context = context,
            database = database,
            metadataReader = metadataReader,
            permissionChecker = StubPermissionChecker(),
            mediaStoreScanner = secondScanner,
            safTreeScanner = SafTreeScanner(context, StubPermissionChecker())
        )

        val results = engine2.scanSource(source, ScanMode.FULL_RECONCILIATION).toList()
        assertEquals(ScanProgressStatus.COMPLETED, results.last().status)
        assertEquals(1, database.trackDao().getTrackCount())
    }
}
