package org.harmonia.player.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.harmonia.player.data.local.dao.ListeningEventDao
import org.harmonia.player.data.local.entity.ListeningEventEntity
import org.harmonia.player.domain.model.ListeningEvent
import org.harmonia.player.domain.repository.ListeningEventRepository

class OfflineListeningEventRepository(
    private val dao: ListeningEventDao
) : ListeningEventRepository {

    override suspend fun recordEvent(event: ListeningEvent) {
        dao.insert(ListeningEventEntity.fromDomain(event))
    }

    override fun observeRecentEvents(limit: Int): Flow<List<ListeningEvent>> {
        return dao.observeRecent(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getEventCount(): Int {
        return dao.count()
    }
}
