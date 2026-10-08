package org.harmonia.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.harmonia.player.domain.model.ListeningEvent
import org.harmonia.player.domain.model.ListeningEventType

@Entity(
    tableName = "mobile_listening_events",
    indices = [
        Index(value = ["track_id"]),
        Index(value = ["timestamp"])
    ]
)
data class ListeningEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "track_id")
    val trackId: String,

    @ColumnInfo(name = "local_file_id")
    val localFileId: String?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "position_ms")
    val positionMs: Long,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "duration_played_sec")
    val durationPlayedSec: Double,

    @ColumnInfo(name = "completed_fully")
    val completedFully: Boolean,

    @ColumnInfo(name = "output_route")
    val outputRoute: String?,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean
) {
    fun toDomain(): ListeningEvent = ListeningEvent(
        id = id,
        eventType = try {
            ListeningEventType.valueOf(eventType)
        } catch (e: Exception) {
            ListeningEventType.STARTED
        },
        trackId = trackId,
        localFileId = localFileId,
        timestamp = timestamp,
        positionMs = positionMs,
        durationMs = durationMs,
        durationPlayedSec = durationPlayedSec,
        completedFully = completedFully,
        outputRoute = outputRoute,
        isSynced = isSynced
    )

    companion object {
        fun fromDomain(event: ListeningEvent): ListeningEventEntity = ListeningEventEntity(
            id = event.id,
            eventType = event.eventType.name,
            trackId = event.trackId,
            localFileId = event.localFileId,
            timestamp = event.timestamp,
            positionMs = event.positionMs,
            durationMs = event.durationMs,
            durationPlayedSec = event.durationPlayedSec,
            completedFully = event.completedFully,
            outputRoute = event.outputRoute,
            isSynced = event.isSynced
        )
    }
}
