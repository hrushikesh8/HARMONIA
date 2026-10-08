package org.harmonia.player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.OfflinePlaybackStateRepository
import org.harmonia.player.data.local.dao.PlaybackStateDao
import org.harmonia.player.data.local.entity.PlaybackStateEntity
import org.harmonia.player.data.local.entity.QueueItemEntity
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.QueueItem
import org.harmonia.player.domain.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakePlaybackStateDao : PlaybackStateDao {
    private val stateFlow = MutableStateFlow<PlaybackStateEntity?>(null)
    private val queueFlow = MutableStateFlow<List<QueueItemEntity>>(emptyList())

    override fun observePlaybackState(): Flow<PlaybackStateEntity?> = stateFlow

    override suspend fun getPlaybackState(): PlaybackStateEntity? = stateFlow.value

    override suspend fun upsertPlaybackState(state: PlaybackStateEntity) {
        stateFlow.value = state
    }

    override fun observeQueueItems(): Flow<List<QueueItemEntity>> = queueFlow

    override suspend fun insertQueueItems(items: List<QueueItemEntity>) {
        queueFlow.value = queueFlow.value + items
    }

    override suspend fun clearQueue() {
        queueFlow.value = emptyList()
    }
}

class OfflinePlaybackStateRepositoryTest {

    private lateinit var fakeDao: FakePlaybackStateDao
    private lateinit var repository: OfflinePlaybackStateRepository

    @Before
    fun setup() {
        fakeDao = FakePlaybackStateDao()
        repository = OfflinePlaybackStateRepository(fakeDao)
    }

    @Test
    fun `savePlaybackState persists and translates correctly`() = runBlocking {
        val state = PlaybackState(
            currentTrackId = "track-10",
            currentPositionMs = 45000L,
            isPlaying = true,
            shuffleMode = true,
            repeatMode = RepeatMode.ALL,
            volumeScalar = 0.85f
        )

        repository.savePlaybackState(state)

        val retrieved = repository.observePlaybackState().first()
        assertNotNull(retrieved)
        assertEquals("track-10", retrieved?.currentTrackId)
        assertEquals(45000L, retrieved?.currentPositionMs)
        assertTrue(retrieved?.isPlaying == true)
        assertTrue(retrieved?.shuffleMode == true)
        assertEquals(RepeatMode.ALL, retrieved?.repeatMode)
        assertEquals(0.85f, retrieved?.volumeScalar ?: 0.0f, 0.01f)
    }

    @Test
    fun `setQueue replaces active queue`() = runBlocking {
        val items = listOf(
            QueueItem(position = 0, trackId = "track-a"),
            QueueItem(position = 1, trackId = "track-b")
        )

        repository.setQueue(items)

        val retrieved = repository.observeQueue().first()
        assertEquals(2, retrieved.size)
        assertEquals("track-a", retrieved[0].trackId)
        assertEquals("track-b", retrieved[1].trackId)

        repository.clearQueue()
        val emptyQueue = repository.observeQueue().first()
        assertTrue(emptyQueue.isEmpty())
    }
}
