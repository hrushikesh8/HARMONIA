package org.harmonia.player.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.harmonia.player.data.local.dao.PlaybackStateDao
import org.harmonia.player.data.local.entity.PlaybackStateEntity
import org.harmonia.player.data.local.entity.QueueItemEntity
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.QueueItem
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.repository.PlaybackStateRepository

/**
 * Concrete implementation of PlaybackStateRepository backed by Room SQLite.
 */
class OfflinePlaybackStateRepository(
    private val playbackStateDao: PlaybackStateDao
) : PlaybackStateRepository {

    override fun observePlaybackState(): Flow<PlaybackState?> {
        return playbackStateDao.observePlaybackState().map { entity ->
            entity?.let {
                PlaybackState(
                    currentTrackId = it.currentTrackId,
                    currentPositionMs = it.currentPositionMs,
                    isPlaying = it.isPlaying,
                    shuffleMode = it.shuffleMode,
                    repeatMode = when (it.repeatMode) {
                        1 -> RepeatMode.ALL
                        2 -> RepeatMode.ONE
                        else -> RepeatMode.OFF
                    },
                    volumeScalar = it.volumeScalar,
                    updatedAt = it.updatedAt
                )
            }
        }
    }

    override suspend fun savePlaybackState(state: PlaybackState) {
        val entity = PlaybackStateEntity(
            singletonId = 1,
            currentTrackId = state.currentTrackId,
            currentPositionMs = state.currentPositionMs,
            isPlaying = state.isPlaying,
            shuffleMode = state.shuffleMode,
            repeatMode = when (state.repeatMode) {
                RepeatMode.ALL -> 1
                RepeatMode.ONE -> 2
                RepeatMode.OFF -> 0
            },
            volumeScalar = state.volumeScalar,
            updatedAt = state.updatedAt
        )
        playbackStateDao.upsertPlaybackState(entity)
    }

    override fun observeQueue(): Flow<List<QueueItem>> {
        return playbackStateDao.observeQueueItems().map { list ->
            list.map {
                QueueItem(
                    position = it.position,
                    trackId = it.trackId,
                    originalIndex = it.originalIndex,
                    addedAt = it.addedAt
                )
            }
        }
    }

    override suspend fun setQueue(items: List<QueueItem>) {
        val entities = items.map {
            QueueItemEntity(
                position = it.position,
                trackId = it.trackId,
                originalIndex = it.originalIndex,
                addedAt = it.addedAt
            )
        }
        playbackStateDao.replaceQueue(entities)
    }

    override suspend fun appendToQueue(items: List<QueueItem>) {
        val entities = items.map {
            QueueItemEntity(
                position = it.position,
                trackId = it.trackId,
                originalIndex = it.originalIndex,
                addedAt = it.addedAt
            )
        }
        playbackStateDao.insertQueueItems(entities)
    }

    override suspend fun clearQueue() {
        playbackStateDao.clearQueue()
    }
}
