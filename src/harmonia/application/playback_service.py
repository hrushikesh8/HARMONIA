"""Playback application service implementing the PlaybackPort domain contract."""

from __future__ import annotations

import random
import time
from typing import TYPE_CHECKING
from uuid import UUID, uuid4

from harmonia.core.logging import get_logger
from harmonia.domain.entities.listening_event import ListeningEvent
from harmonia.domain.entities.playback import PlaybackState, QueueItem, QueueSnapshot
from harmonia.domain.values.playback import (
    LocalFileSource,
    PlaybackSource,
    PlaybackStatus,
    RepeatMode,
    StreamUriSource,
)
from harmonia.infrastructure.audio.backend import AudioBackend, VirtualAudioBackend

if TYPE_CHECKING:
    from sqlalchemy.orm import Session

    from harmonia.domain.ports.listening_event_repository import ListeningEventRepository
    from harmonia.domain.ports.playback_snapshot_repository import PlaybackSnapshotRepository
    from harmonia.domain.ports.track_repository import TrackRepository

logger = get_logger("harmonia.playback")


class _PlaybackSession:
    """Internal tracker for an active continuous listening session."""

    def __init__(
        self,
        track_id: UUID,
        track_file_id: UUID | None,
        track_duration: float,
        playback_source: str = "queue",
    ) -> None:
        self.session_id: UUID = uuid4()
        self.track_id: UUID = track_id
        self.track_file_id: UUID | None = track_file_id
        self.track_duration: float = max(0.0, track_duration)
        self.playback_source: str = playback_source
        self.start_wall_time = time.time()
        self.started_at_sec: float = 0.0
        self.ended_at_sec: float = 0.0
        self.listened_seconds: float = 0.0
        self.last_tick: float | None = None

    def on_play(self, current_pos: float) -> None:
        if self.last_tick is None:
            self.last_tick = time.monotonic()
            self.ended_at_sec = current_pos

    def on_pause(self, current_pos: float) -> None:
        if self.last_tick is not None:
            elapsed = time.monotonic() - self.last_tick
            if elapsed > 0:
                self.listened_seconds += elapsed
            self.last_tick = None
        self.ended_at_sec = current_pos

    def on_tick(self, current_pos: float) -> None:
        if self.last_tick is not None:
            now = time.monotonic()
            elapsed = now - self.last_tick
            if elapsed > 0:
                self.listened_seconds += elapsed
            self.last_tick = now
        self.ended_at_sec = current_pos

    def finalize(self, final_pos: float) -> ListeningEvent:
        if self.last_tick is not None:
            elapsed = time.monotonic() - self.last_tick
            if elapsed > 0:
                self.listened_seconds += elapsed
            self.last_tick = None
        self.ended_at_sec = final_pos

        return ListeningEvent.create(
            track_id=self.track_id,
            track_file_id=self.track_file_id,
            started_at_sec=self.started_at_sec,
            ended_at_sec=self.ended_at_sec,
            duration_listened_seconds=self.listened_seconds,
            track_duration_seconds=self.track_duration,
            playback_source=self.playback_source,
            event_id=self.session_id,
        )


class PlaybackService:
    """Application service implementing the PlaybackPort domain contract.

    Coordinates transport operations, dual-layer queue, persistent state,
    and factual telemetry recording.
    """

    def __init__(
        self,
        track_repo: TrackRepository,
        listening_event_repo: ListeningEventRepository,
        snapshot_repo: PlaybackSnapshotRepository,
        audio_backend: AudioBackend | None = None,
        session: Session | None = None,
    ) -> None:
        self._track_repo = track_repo
        self._listening_event_repo = listening_event_repo
        self._snapshot_repo = snapshot_repo
        self._backend: AudioBackend = audio_backend or VirtualAudioBackend()
        self._db_session = session

        # Internal Queue State
        self._manual_queue: list[QueueItem] = []  # Layer 1: FIFO Up Next
        self._context_queue: list[QueueItem] = []  # Layer 2: Context catalog
        self._original_context_queue: list[QueueItem] = []  # Unshuffled context copy
        self._context_index: int = -1

        # Current Active Playback State
        self._current_item: QueueItem | None = None
        self._state = PlaybackState()
        self._active_session: _PlaybackSession | None = None

    # ── Transport Controls ──────────────────────────────────────────────────

    def play(self) -> None:
        """Begin or resume playback."""
        if self._state.status == PlaybackStatus.PAUSED:
            self.resume()
            return

        if self._current_item is None and not self._advance_to_next():
            logger.info("Cannot play: queue is empty")
            return

        self._start_active_track()

    def pause(self) -> None:
        """Pause audio playback."""
        if self._state.status != PlaybackStatus.PLAYING:
            return

        pos = self._backend.get_position()
        self._backend.pause()
        self._state.status = PlaybackStatus.PAUSED
        self._state.position_seconds = pos

        if self._active_session:
            self._active_session.on_pause(pos)

        self._save_snapshot()

    def resume(self) -> None:
        """Resume playback from paused position."""
        if self._state.status != PlaybackStatus.PAUSED:
            if self._state.status == PlaybackStatus.STOPPED or self._state.status == PlaybackStatus.IDLE:
                self.play()
            return

        pos = self._backend.get_position()
        self._backend.resume()
        self._state.status = PlaybackStatus.PLAYING
        self._state.position_seconds = pos

        if self._active_session:
            self._active_session.on_play(pos)

        self._save_snapshot()

    def stop(self) -> None:
        """Stop playback and reset position to 0.0s."""
        pos = self._backend.get_position()
        self._backend.stop()

        self._finalize_active_session(pos)

        self._state.status = PlaybackStatus.STOPPED
        self._state.position_seconds = 0.0
        self._state.active_session_id = None

        self._save_snapshot()

    def seek(self, position_seconds: float) -> None:
        """Seek playback to specific offset."""
        target_pos = max(0.0, float(position_seconds))
        if self._state.duration_seconds > 0:
            target_pos = min(self._state.duration_seconds, target_pos)

        self._backend.seek(target_pos)
        self._state.position_seconds = target_pos

        if self._active_session:
            self._active_session.on_tick(target_pos)

        self._save_snapshot()

    def next(self) -> None:
        """Advance to the next track in the queue."""
        pos = self._backend.get_position()
        self._finalize_active_session(pos)

        if self._state.repeat_mode == RepeatMode.ONE and self._current_item is not None:
            self.seek(0.0)
            self._start_active_track()
            return

        if self._advance_to_next():
            self._start_active_track()
        else:
            self._current_item = None
            self._backend.stop()
            self._state.status = PlaybackStatus.STOPPED
            self._state.position_seconds = 0.0
            self._state.current_track_id = None
            self._state.current_track_file_id = None
            self._state.active_session_id = None
            self._save_snapshot()

    def previous(self) -> None:
        """Return to previous track or beginning of current track."""
        pos = self._backend.get_position()

        # If played > 3 seconds, restart current track
        if pos > 3.0:
            self.seek(0.0)
            if self._state.status == PlaybackStatus.PLAYING:
                self._backend.play()
            return

        self._finalize_active_session(pos)

        # Go to previous context track if available
        if self._context_index > 0:
            self._context_index -= 1
            self._current_item = self._context_queue[self._context_index]
            self._start_active_track()
        elif self._current_item is not None:
            self.seek(0.0)
            self._start_active_track()

    def set_volume(self, volume: float) -> None:
        """Set output volume level clamped between 0.0 and 1.0."""
        clamped = max(0.0, min(1.0, float(volume)))
        self._state.volume = clamped
        self._backend.set_volume(clamped)
        self._save_snapshot()

    # ── Queue Operations ────────────────────────────────────────────────────

    def set_queue(self, track_ids: list[UUID], play_index: int = 0) -> None:
        """Replace the active context queue and begin playback."""
        if not track_ids:
            self.clear_queue()
            return

        self._manual_queue.clear()
        queue_items = self._resolve_track_items(track_ids)
        if not queue_items:
            self.clear_queue()
            return

        self._original_context_queue = list(queue_items)

        if self._state.shuffle_enabled and len(queue_items) > 1:
            clamped_idx = max(0, min(len(queue_items) - 1, play_index))
            first_item = queue_items[clamped_idx]
            remaining = [item for i, item in enumerate(queue_items) if i != clamped_idx]
            random.shuffle(remaining)
            self._context_queue = [first_item, *remaining]
            self._context_index = 0
        else:
            self._context_queue = list(queue_items)
            self._context_index = max(0, min(len(queue_items) - 1, play_index))

        self._current_item = self._context_queue[self._context_index]
        self._start_active_track()

    def insert_play_next(self, track_id: UUID) -> None:
        """Insert a track into manual queue immediately after current track.

        Maintains Auxio contiguous placement without scrambling randomized context.
        """
        items = self._resolve_track_items([track_id], is_manual=True)
        if not items:
            return

        item = items[0]
        # Insert at the head of manual Up Next queue
        self._manual_queue.insert(0, item)
        self._save_snapshot()

    def append_up_next(self, track_id: UUID) -> None:
        """Append a track to the end of the manual Up Next queue."""
        items = self._resolve_track_items([track_id], is_manual=True)
        if not items:
            return

        self._manual_queue.append(items[0])
        self._save_snapshot()

    def remove_from_queue(self, index: int) -> None:
        """Remove a track at a specific index in the active combined queue."""
        combined = self.get_queue()
        if not (0 <= index < len(combined)):
            return

        target_item = combined[index]

        # Check if in manual queue
        if target_item in self._manual_queue:
            self._manual_queue.remove(target_item)
            self._save_snapshot()
            return

        # Check if in context queue
        if target_item in self._context_queue:
            ctx_idx = self._context_queue.index(target_item)
            if ctx_idx == self._context_index:
                # Removing currently playing track advances to next
                self.next()
            else:
                self._context_queue.pop(ctx_idx)
                if ctx_idx < self._context_index:
                    self._context_index -= 1
            if target_item in self._original_context_queue:
                self._original_context_queue.remove(target_item)
            self._save_snapshot()

    def clear_queue(self) -> None:
        """Clear all queued items and stop playback."""
        self.stop()
        self._manual_queue.clear()
        self._context_queue.clear()
        self._original_context_queue.clear()
        self._context_index = -1
        self._current_item = None
        self._state = PlaybackState(volume=self._state.volume)
        self._save_snapshot()

    def toggle_shuffle(self) -> bool:
        """Toggle shuffle mode.

        When enabling, shuffles remaining future context items without disturbing
        the currently playing track or history.
        """
        new_shuffle = not self._state.shuffle_enabled
        self._state.shuffle_enabled = new_shuffle

        if not self._context_queue:
            self._save_snapshot()
            return new_shuffle

        if new_shuffle:
            # Shuffle remaining context items after current index
            if 0 <= self._context_index < len(self._context_queue):
                past_and_current = self._context_queue[: self._context_index + 1]
                future = self._context_queue[self._context_index + 1 :]
                random.shuffle(future)
                self._context_queue = past_and_current + future
        else:
            # Restore sequential ordering while preserving current track position
            if self._current_item and self._current_item in self._original_context_queue:
                orig_idx = self._original_context_queue.index(self._current_item)
                self._context_queue = list(self._original_context_queue)
                self._context_index = orig_idx

        self._save_snapshot()
        return new_shuffle

    def set_repeat_mode(self, mode: RepeatMode) -> None:
        """Set repeat mode."""
        self._state.repeat_mode = mode
        self._save_snapshot()

    # ── State Inspection ────────────────────────────────────────────────────

    def get_state(self) -> PlaybackState:
        """Return current factual playback state snapshot."""
        if self._state.status == PlaybackStatus.PLAYING:
            pos = self._backend.get_position()
            self._state.position_seconds = pos
            if self._active_session:
                self._active_session.on_tick(pos)

        # Sync queue index in state
        self._state.current_queue_index = self._compute_combined_index()
        return self._state

    def get_queue(self) -> list[QueueItem]:
        """Return complete combined queue in active playback sequence."""
        items: list[QueueItem] = []
        if self._current_item is not None:
            items.append(self._current_item)
        items.extend(self._manual_queue)

        # Remaining context items after current index
        if 0 <= self._context_index < len(self._context_queue):
            items.extend(self._context_queue[self._context_index + 1 :])

        return items

    # ── Persistence & Restoration ───────────────────────────────────────────

    def restore_snapshot(self) -> bool:
        """Restore playback and queue state from SQLite snapshot."""
        snapshot = self._snapshot_repo.load()
        if snapshot is None or not snapshot.queue_items:
            return False

        try:
            # Separate manual vs context items
            self._manual_queue = [it for it in snapshot.queue_items if it.is_manual]
            self._context_queue = [it for it in snapshot.queue_items if not it.is_manual]
            self._original_context_queue = list(self._context_queue)

            self._context_index = snapshot.current_queue_index
            if 0 <= self._context_index < len(self._context_queue):
                self._current_item = self._context_queue[self._context_index]
            elif self._manual_queue and snapshot.current_track_id:
                # Active track was manual
                manual_matches = [
                    it for it in self._manual_queue if it.track_id == snapshot.current_track_id
                ]
                self._current_item = (
                    manual_matches[0] if manual_matches else self._manual_queue[0]
                )
            elif self._context_queue:
                self._context_index = 0
                self._current_item = self._context_queue[0]
            else:
                self._current_item = None

            self._state.volume = snapshot.volume
            self._state.repeat_mode = snapshot.repeat_mode
            self._state.shuffle_enabled = snapshot.shuffle_enabled
            self._state.position_seconds = snapshot.position_seconds
            self._state.status = PlaybackStatus.PAUSED

            if self._current_item is not None:
                self._state.current_track_id = self._current_item.track_id
                self._state.current_track_file_id = self._current_item.track_file_id
                self._state.duration_seconds = self._current_item.duration_seconds

                source = self._resolve_playback_source(self._current_item)
                if source:
                    self._backend.load(source)
                    self._backend.seek(snapshot.position_seconds)
                    self._backend.set_volume(snapshot.volume)

            return True
        except Exception as exc:
            logger.warning("Failed to restore playback snapshot: %s", exc)
            return False

    # ── Internal Helpers ────────────────────────────────────────────────────

    def _advance_to_next(self) -> bool:
        """Select next track from manual queue or context queue."""
        # 1. Check manual Up Next queue
        if self._manual_queue:
            self._current_item = self._manual_queue.pop(0)
            return True

        # 2. Advance in context queue
        if self._context_queue:
            next_idx = self._context_index + 1
            if next_idx < len(self._context_queue):
                self._context_index = next_idx
                self._current_item = self._context_queue[self._context_index]
                return True
            if self._state.repeat_mode == RepeatMode.ALL:
                self._context_index = 0
                self._current_item = self._context_queue[0]
                return True

        return False

    def _start_active_track(self) -> None:
        """Load and start playback of the current active track."""
        if self._current_item is None:
            return

        source = self._resolve_playback_source(self._current_item)
        if source:
            self._backend.load(source)
            self._backend.set_volume(self._state.volume)
            self._backend.play()

        self._state.status = PlaybackStatus.PLAYING
        self._state.current_track_id = self._current_item.track_id
        self._state.current_track_file_id = self._current_item.track_file_id
        self._state.duration_seconds = self._current_item.duration_seconds
        self._state.position_seconds = 0.0

        # Start new session
        self._active_session = _PlaybackSession(
            track_id=self._current_item.track_id,
            track_file_id=self._current_item.track_file_id,
            track_duration=self._current_item.duration_seconds,
            playback_source="manual" if self._current_item.is_manual else "queue",
        )
        self._active_session.on_play(0.0)
        self._state.active_session_id = self._active_session.session_id

        self._save_snapshot()

    def _finalize_active_session(self, final_pos: float) -> None:
        """Commit factual ListeningEvent for the concluded session."""
        if self._active_session is None:
            return

        event = self._active_session.finalize(final_pos)
        try:
            self._listening_event_repo.add(event)
            if self._db_session:
                self._db_session.commit()
            logger.info(
                "ListeningEvent recorded: track_id=%s duration=%.1fs completed=%s skipped=%s",
                event.track_id,
                event.duration_listened_seconds,
                event.was_completed,
                event.was_skipped,
            )
        except Exception as exc:
            logger.error("Failed to persist listening event: %s", exc)
        finally:
            self._active_session = None

    def _resolve_playback_source(self, item: QueueItem) -> PlaybackSource | None:
        """Resolve track into a PlaybackSource (local file or stream URI)."""
        files = self._track_repo.get_files(item.track_id)
        if not files:
            return StreamUriSource(
                stream_url=f"/api/v1/tracks/{item.track_id}/stream",
                mime_type="audio/octet-stream",
            )

        # Pick best file (highest quality score or first)
        best_file = max(files, key=lambda f: f.quality_score or 0)
        from pathlib import Path

        file_path = Path(best_file.file_path)
        if file_path.is_file():
            return LocalFileSource(
                file_path=file_path,
                mime_type=f"audio/{best_file.format.value}",
            )

        # Fallback to HTTP stream
        return StreamUriSource(
            stream_url=f"/api/v1/tracks/{item.track_id}/stream",
            mime_type=f"audio/{best_file.format.value}",
        )

    def _resolve_track_items(
        self, track_ids: list[UUID], is_manual: bool = False
    ) -> list[QueueItem]:
        """Fetch Track entities and convert to QueueItems."""
        items: list[QueueItem] = []
        for tid in track_ids:
            track = self._track_repo.get_by_id(tid)
            if track:
                files = self._track_repo.get_files(tid)
                best_file_id = files[0].id if files else None
                items.append(
                    QueueItem(
                        track_id=track.id,
                        track_file_id=best_file_id,
                        title=track.title,
                        artist_name="",  # May be populated if needed
                        duration_seconds=track.duration_seconds or 0.0,
                        is_manual=is_manual,
                    )
                )
        return items

    def _compute_combined_index(self) -> int:
        if self._current_item is None:
            return -1
        return 0

    def _save_snapshot(self) -> None:
        """Serialize current queue and state to SQLite snapshot."""
        try:
            # Full queue items preserve both manual and context tracks
            all_items = list(self._manual_queue) + list(self._context_queue)
            snapshot = QueueSnapshot(
                current_track_id=self._state.current_track_id,
                current_track_file_id=self._state.current_track_file_id,
                position_seconds=self._state.position_seconds,
                volume=self._state.volume,
                repeat_mode=self._state.repeat_mode,
                shuffle_enabled=self._state.shuffle_enabled,
                queue_items=all_items,
                current_queue_index=self._context_index,
            )
            self._snapshot_repo.save(snapshot)
            if self._db_session:
                self._db_session.commit()
        except Exception as exc:
            logger.debug("Snapshot save skipped or failed: %s", exc)
