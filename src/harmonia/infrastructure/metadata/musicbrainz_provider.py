"""MusicBrainz external metadata provider implementation.

Adheres to:
1. Offline-safety: network failures or offline mode cleanly return empty results without errors.
2. Rate-limiting: enforces 1 request per second to conform to MusicBrainz API limits.
3. Provenance separation: returns MetadataFieldRecords with provider='musicbrainz' and priority 50.
"""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import TYPE_CHECKING, Any

from harmonia.core.logging import get_logger
from harmonia.core.types import MetadataPriority, MetadataProviderName
from harmonia.domain.services.metadata_resolver import MetadataResolver

if TYPE_CHECKING:
    from uuid import UUID

    from harmonia.domain.entities.metadata_field import MetadataFieldRecord

logger = get_logger(__name__)


class MusicBrainzProvider:
    """Rate-limited, offline-safe MusicBrainz metadata provider."""

    USER_AGENT = "Harmonia/0.2.0 ( https://github.com/harmonia/harmonia )"

    def __init__(
        self,
        rate_limit_delay: float = 1.0,
        timeout: float = 3.0,
        min_score: int = 80,
    ) -> None:
        self._rate_limit_delay = rate_limit_delay
        self._timeout = timeout
        self._min_score = min_score
        self._last_request_time: float = 0.0

    @property
    def name(self) -> str:
        """Provider identifier."""
        return MetadataProviderName.MUSICBRAINZ.value

    def _wait_for_rate_limit(self) -> None:
        """Ensure at least `_rate_limit_delay` seconds between requests."""
        now = time.time()
        elapsed = now - self._last_request_time
        if elapsed < self._rate_limit_delay:
            time.sleep(self._rate_limit_delay - elapsed)
        self._last_request_time = time.time()

    def _execute_query(self, query: str) -> dict[str, Any] | None:
        """Perform HTTP GET request to MusicBrainz API."""
        self._wait_for_rate_limit()

        url = f"https://musicbrainz.org/ws/2/recording/?query={urllib.parse.quote(query)}&fmt=json"
        req = urllib.request.Request(
            url,
            headers={
                "User-Agent": self.USER_AGENT,
                "Accept": "application/json",
            },
        )

        try:
            with urllib.request.urlopen(req, timeout=self._timeout) as resp:
                if resp.status == 200:
                    data = resp.read()
                    return json.loads(data.decode("utf-8"))
                return None
        except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as e:
            logger.debug("MusicBrainz lookup failed (offline or network error): %s", e)
            return None

    def lookup_track(
        self,
        entity_id: UUID,
        artist_name: str | None,
        album_title: str | None,
        track_title: str | None,
        duration_seconds: float | None = None,
    ) -> list[MetadataFieldRecord]:
        """Query MusicBrainz for track recording metadata."""
        if not track_title:
            return []

        query_parts: list[str] = [f'recording:"{track_title}"']
        if artist_name:
            query_parts.append(f'artist:"{artist_name}"')
        if album_title:
            query_parts.append(f'release:"{album_title}"')

        query_str = " AND ".join(query_parts)
        data = self._execute_query(query_str)
        if not data:
            return []

        recordings = data.get("recordings", [])
        if not recordings:
            return []

        # Find best candidate by score
        best = None
        for rec in recordings:
            score = int(rec.get("score", 0))
            if score >= self._min_score:
                best = rec
                break

        if not best:
            return []

        records: list[MetadataFieldRecord] = []
        prio = MetadataPriority.MUSICBRAINZ

        # Recording Title
        title = best.get("title")
        if title:
            records.append(
                MetadataResolver.create_record(
                    "track", entity_id, "title", self.name, title, prio
                )
            )

        # Recording MBID
        mbid = best.get("id")
        if mbid:
            records.append(
                MetadataResolver.create_record(
                    "track", entity_id, "mbid", self.name, mbid, prio
                )
            )

        # Artist
        artist_credit = best.get("artist-credit", [])
        if artist_credit and isinstance(artist_credit, list):
            artist_obj = artist_credit[0].get("artist", {})
            name = artist_obj.get("name")
            artist_mbid = artist_obj.get("id")
            if name:
                records.append(
                    MetadataResolver.create_record(
                        "track", entity_id, "artist_name", self.name, name, prio
                    )
                )
            if artist_mbid:
                records.append(
                    MetadataResolver.create_record(
                        "track", entity_id, "artist_mbid", self.name, artist_mbid, prio
                    )
                )

        # Release / Album
        releases = best.get("releases", [])
        if releases and isinstance(releases, list):
            rel = releases[0]
            rel_title = rel.get("title")
            rel_mbid = rel.get("id")
            date = rel.get("date") or best.get("first-release-date")

            if rel_title:
                records.append(
                    MetadataResolver.create_record(
                        "track", entity_id, "album_title", self.name, rel_title, prio
                    )
                )
            if rel_mbid:
                records.append(
                    MetadataResolver.create_record(
                        "track", entity_id, "album_mbid", self.name, rel_mbid, prio
                    )
                )
            if date:
                # Extract year
                year = date.split("-")[0]
                records.append(
                    MetadataResolver.create_record(
                        "track", entity_id, "year", self.name, year, prio
                    )
                )

        # Tags / Genre
        tags = best.get("tags", [])
        if tags and isinstance(tags, list):
            top_tag = tags[0].get("name")
            if top_tag:
                records.append(
                    MetadataResolver.create_record(
                        "track", entity_id, "genre", self.name, top_tag, prio
                    )
                )

        return records
