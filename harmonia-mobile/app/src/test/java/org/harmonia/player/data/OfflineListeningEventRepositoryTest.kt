package org.harmonia.player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.OfflineListeningEventRepository
import org.harmonia.player.data.local.dao.ListeningEventDao
import org.harmonia.player.data.local.entity.ListeningEventEntity
import org.harmonia.player.domain.model.ListeningEvent
import org.harmonia.player.domain.model.ListeningEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeListeningEventDao : ListeningEventDao {
    private val events = mutableListOf<ListeningEventEntity>()
    private val eventsFlow = MutableStateFlow<List<ListeningEventEntity>>(emptyList())

    override suspend fun insert(event: ListeningEventEntity) {
        events.add(0, event)
        eventsFlow.value = events.toList()
    }

    override fun observeRecent(limit: Int): Flow<List<ListeningEventEntity>> {
        return eventsFlow.map { list -> list.take(limit) }
    }

    override suspend fun count(): Int = events.size

    override suspend fun pruneOldSyncedEvents(cutoffTimestamp: Long): Int {
        val before = events.size
        events.removeAll { it.isSynced && it.timestamp < cutoffTimestamp }
        eventsFlow.value = events.toList()
        return before - events.size
    }
}

class OfflineListeningEventRepositoryTest {

    private lateinit var fakeDao: FakeListeningEventDao
    private lateinit var repository: OfflineListeningEventRepository

    @Before
    fun setup() {
        fakeDao = FakeListeningEventDao()
        repository = OfflineListeningEventRepository(fakeDao)
    }

    @Test
    fun `recordEvent stores factual playback event telemetry`() = runBlocking {
        val event = ListeningEvent(
            id = "evt-1",
            eventType = ListeningEventType.STARTED,
            trackId = "track-abc",
            localFileId = "file-xyz",
            positionMs = 0L,
            durationMs = 180000L,
            durationPlayedSec = 0.0,
            completedFully = false
        )

        repository.recordEvent(event)

        assertEquals(1, repository.getEventCount())
        val recent = repository.observeRecentEvents(10).first()
        assertEquals(1, recent.size)
        val recorded = recent[0]
        assertEquals("evt-1", recorded.id)
        assertEquals(ListeningEventType.STARTED, recorded.eventType)
        assertEquals("track-abc", recorded.trackId)
        assertEquals("file-xyz", recorded.localFileId)
        assertEquals(180000L, recorded.durationMs)
    }

    @Test
    fun `record multiple events preserves reverse chronological order`() = runBlocking {
        repository.recordEvent(
            ListeningEvent(
                id = "evt-1",
                eventType = ListeningEventType.STARTED,
                trackId = "track-1"
            )
        )
        repository.recordEvent(
            ListeningEvent(
                id = "evt-2",
                eventType = ListeningEventType.PAUSED,
                trackId = "track-1",
                positionMs = 30000L
            )
        )
        repository.recordEvent(
            ListeningEvent(
                id = "evt-3",
                eventType = ListeningEventType.COMPLETED,
                trackId = "track-1",
                positionMs = 180000L,
                completedFully = true
            )
        )

        assertEquals(3, repository.getEventCount())
        val recent = repository.observeRecentEvents(2).first()
        assertEquals(2, recent.size)
        assertEquals("evt-3", recent[0].id)
        assertEquals(ListeningEventType.COMPLETED, recent[0].eventType)
        assertTrue(recent[0].completedFully)
        assertEquals("evt-2", recent[1].id)
    }
}
