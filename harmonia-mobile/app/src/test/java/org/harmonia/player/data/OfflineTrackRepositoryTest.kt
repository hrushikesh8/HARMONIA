package org.harmonia.player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.OfflineTrackRepository
import org.harmonia.player.data.local.dao.TrackDao
import org.harmonia.player.data.local.entity.AlbumEntity
import org.harmonia.player.data.local.entity.ArtistEntity
import org.harmonia.player.data.local.entity.LocalFileEntity
import org.harmonia.player.data.local.entity.TrackEntity
import org.harmonia.player.data.local.entity.TrackWithDetails
import org.harmonia.player.domain.model.LocalFileAvailability
import org.harmonia.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FakeTrackDao : TrackDao {
    private val tracksFlow = MutableStateFlow<List<TrackWithDetails>>(emptyList())

    override fun observeAllTracks(): Flow<List<TrackWithDetails>> = tracksFlow

    override fun observeOfflineTracks(): Flow<List<TrackWithDetails>> {
        return tracksFlow.map { list ->
            list.filter { item ->
                item.localFiles.any { it.availabilityStatus == "AVAILABLE" }
            }
        }
    }

    override suspend fun getTrackById(id: String): TrackWithDetails? {
        return tracksFlow.value.find { it.track.id == id }
    }

    override fun searchTracks(query: String): Flow<List<TrackWithDetails>> {
        return tracksFlow.map { list ->
            list.filter { it.track.title.contains(query, ignoreCase = true) }
        }
    }

    override suspend fun upsertTracks(tracks: List<TrackEntity>) {
        val current = tracksFlow.value.toMutableList()
        tracks.forEach { entity ->
            val index = current.indexOfFirst { it.track.id == entity.id }
            val newDetail = TrackWithDetails(track = entity)
            if (index >= 0) {
                current[index] = newDetail
            } else {
                current.add(newDetail)
            }
        }
        tracksFlow.value = current
    }

    override suspend fun upsertTrack(track: TrackEntity) {
        upsertTracks(listOf(track))
    }

    override suspend fun deleteTrackById(id: String) {
        tracksFlow.value = tracksFlow.value.filter { it.track.id != id }
    }

    override suspend fun deleteAllTracks() {
        tracksFlow.value = emptyList()
    }

    override suspend fun getTrackCount(): Int = tracksFlow.value.size

    fun seedTrackWithDetails(detail: TrackWithDetails) {
        tracksFlow.value = tracksFlow.value + detail
    }
}

class OfflineTrackRepositoryTest {

    private lateinit var fakeDao: FakeTrackDao
    private lateinit var repository: OfflineTrackRepository

    @Before
    fun setup() {
        fakeDao = FakeTrackDao()
        repository = OfflineTrackRepository(fakeDao)
    }

    @Test
    fun `upsertTracks stores tracks and can be retrieved`() = runBlocking {
        val track = Track(
            id = "t-1",
            title = "Teardrop",
            durationSec = 330.0,
            integratedLufs = -14.2
        )

        repository.upsertTracks(listOf(track))

        val result = repository.getTrackById("t-1")
        assertNotNull(result)
        assertEquals("Teardrop", result?.title)
        assertEquals(-14.2, result?.integratedLufs ?: 0.0, 0.01)
    }

    @Test
    fun `observeOfflineTracks filters out tracks without local bitstream`() = runBlocking {
        val remoteTrack = TrackWithDetails(
            track = TrackEntity(
                id = "t-remote",
                title = "Remote Track",
                durationSec = 200.0,
                createdAt = 0L,
                updatedAt = 0L
            ),
            localFiles = emptyList()
        )

        val offlineTrack = TrackWithDetails(
            track = TrackEntity(
                id = "t-offline",
                title = "Offline Track",
                durationSec = 180.0,
                createdAt = 0L,
                updatedAt = 0L
            ),
            localFiles = listOf(
                LocalFileEntity(
                    id = "f-1",
                    trackId = "t-offline",
                    storageType = "INTERNAL_SYNC",
                    fileUri = "/path/to/file.flac",
                    fileSize = 25000000L,
                    fileHash = "hash1",
                    format = "FLAC",
                    availabilityStatus = "AVAILABLE",
                    addedAt = 0L
                )
            )
        )

        fakeDao.seedTrackWithDetails(remoteTrack)
        fakeDao.seedTrackWithDetails(offlineTrack)

        val allTracks = repository.observeAllTracks().first()
        assertEquals(2, allTracks.size)

        val offlineOnly = repository.observeOfflineTracks().first()
        assertEquals(1, offlineOnly.size)
        assertEquals("Offline Track", offlineOnly[0].title)
        assertEquals(LocalFileAvailability.AVAILABLE_VERIFIED, offlineOnly[0].availability)
    }

    @Test
    fun `deleteTrack removes the track`() = runBlocking {
        val track = Track(
            id = "t-del",
            title = "To Delete",
            durationSec = 100.0
        )
        repository.upsertTracks(listOf(track))
        assertNotNull(repository.getTrackById("t-del"))

        repository.deleteTrack("t-del")
        assertNull(repository.getTrackById("t-del"))
    }
}
