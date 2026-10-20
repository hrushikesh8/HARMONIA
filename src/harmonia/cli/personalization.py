"""Personalization CLI commands for playlists, favorites, and ratings.

Exposes Typer command groups integrated with PersonalizationService for
desktop personalization workflows, preserving transactional boundaries and
rich console / machine-readable JSON output formats.
"""

from __future__ import annotations

import json
import sys
from collections.abc import Generator
from contextlib import contextmanager
from pathlib import Path
from typing import Annotated
from uuid import UUID

import typer
from pydantic import ValidationError as PydanticValidationError
from rich.console import Console
from rich.panel import Panel
from rich.table import Table
from sqlalchemy.exc import SQLAlchemyError

from harmonia.application.personalization_dto import (
    PlaylistItemCreate,
    PlaylistUpdate,
)
from harmonia.application.personalization_service import PersonalizationService
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.core.exceptions import EntityNotFoundError, ValidationError

console = Console()

# ── Typer Command Groups ───────────────────────────────────────────────────────

playlist_app = typer.Typer(
    name="playlist",
    help="Manage playlists and playlist items.",
    no_args_is_help=True,
)

favorite_app = typer.Typer(
    name="favorite",
    help="Manage favorite tracks.",
    no_args_is_help=True,
)

rating_app = typer.Typer(
    name="rating",
    help="Manage track ratings.",
    no_args_is_help=True,
)


# ── Lifecycle and Error Helpers ───────────────────────────────────────────────


def _can_encode(text: str, encoding: str | None = None) -> bool:
    """Check whether text can be encoded in the current or specified encoding."""
    if not encoding:
        encoding = getattr(sys.stdout, "encoding", "utf-8") or "utf-8"
    try:
        text.encode(encoding)
        return True
    except (UnicodeEncodeError, LookupError):
        return False


def safe_print(*args: object, **kwargs: object) -> None:
    """Print to Rich console with fallback replacement for restricted terminal encodings."""
    try:
        console.print(*args, **kwargs)
    except UnicodeEncodeError:
        encoding = getattr(sys.stdout, "encoding", "utf-8") or "utf-8"
        with console.capture() as capture:
            console.print(*args, **kwargs)
        rendered = capture.get()
        sanitized = rendered.encode(encoding, errors="replace").decode(encoding)
        sys.stdout.write(sanitized)
        sys.stdout.flush()


def _parse_uuid(value: str, entity_name: str = "entity") -> UUID:
    """Parse a UUID string or exit with a user-friendly error message."""
    try:
        return UUID(value)
    except (ValueError, AttributeError):
        safe_print(f"[bold red]Invalid {entity_name} UUID:[/bold red] {value}")
        raise typer.Exit(code=1) from None


def _format_duration(duration_seconds: float | None) -> str:
    """Format duration in seconds as m:ss or h:mm:ss."""
    if duration_seconds is None or duration_seconds <= 0:
        return "-"
    total = int(duration_seconds)
    minutes, seconds = divmod(total, 60)
    hours, minutes = divmod(minutes, 60)
    if hours > 0:
        return f"{hours}:{minutes:02d}:{seconds:02d}"
    return f"{minutes}:{seconds:02d}"


def _format_stars(rating: int) -> str:
    """Format integer rating as star glyphs (or ASCII fallback if unencodable)."""
    if _can_encode("★"):
        return "★" * rating + "☆" * (5 - rating)
    return "*" * rating + "-" * (5 - rating)


def _can_prompt() -> bool:
    """Determine whether interactive user confirmation can be obtained."""
    if sys.stdin.isatty():
        return True
    # Test harnesses like CliRunner simulate input on a seekable stream.
    if hasattr(sys.stdin, "seekable") and sys.stdin.seekable():
        try:
            pos = sys.stdin.tell()
            char = sys.stdin.read(1)
            sys.stdin.seek(pos)
            return len(char) > 0
        except Exception:
            return False
    return False


@contextmanager
def cli_error_boundary() -> Generator[None, None, None]:
    """Catch expected service/domain exceptions and exit with established error codes."""
    try:
        yield
    except EntityNotFoundError as e:
        safe_print(f"[bold red]{e.entity_type} not found:[/bold red] {e.entity_id}")
        raise typer.Exit(code=1) from None
    except ValidationError as e:
        safe_print(f"[bold red]Validation error ({e.field}):[/bold red] {e.message}")
        raise typer.Exit(code=1) from None
    except PydanticValidationError as e:
        safe_print(f"[bold red]Validation error:[/bold red] {e}")
        raise typer.Exit(code=1) from None
    except SQLAlchemyError as e:
        safe_print(f"[bold red]Database error:[/bold red] {e}")
        raise typer.Exit(code=1) from None


@contextmanager
def personalization_session(
    config_path: Path | None = None,
    db_path: Path | None = None,
    autocommit: bool = True,
) -> Generator[PersonalizationService, None, None]:
    """Provide a lifecycle-managed PersonalizationService with isolated session cleanup."""
    config = HarmoniaConfig.load(config_path)
    config.ensure_directories()
    effective_db = db_path or config.database.path
    engine = create_database_engine(effective_db)
    try:
        init_database(engine)
        session_factory = create_session_factory(engine)
        with session_factory() as session:
            try:
                service = PersonalizationService(session=session, autocommit=autocommit)
                yield service
            except Exception:
                session.rollback()
                raise
    finally:
        engine.dispose()


# ── Playlist Commands ─────────────────────────────────────────────────────────


@playlist_app.command("create", help="Create a new playlist.")
def playlist_create_cmd(
    name: Annotated[str, typer.Argument(help="Name of the playlist.")],
    description: Annotated[
        str | None,
        typer.Option("--description", "-d", help="Optional playlist description."),
    ] = None,
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Create a new empty playlist."""
    from harmonia.application.personalization_dto import PlaylistCreate

    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        pl = svc.create_playlist(PlaylistCreate(name=name, description=description))
        if format_.lower() == "json":
            typer.echo(json.dumps(pl.model_dump(mode="json"), indent=2))
        else:
            safe_print(f"[bold green][OK] Created playlist:[/bold green] {pl.name} ({pl.id})")


@playlist_app.command("list", help="List all playlists.")
def playlist_list_cmd(
    limit: Annotated[int, typer.Option("--limit", "-l", help="Maximum number of playlists to return.")] = 50,
    offset: Annotated[int, typer.Option("--offset", "-o", help="Offset for pagination.")] = 0,
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """List playlists in the library with pagination."""
    if limit <= 0:
        safe_print("[bold red]Validation error:[/bold red] Limit must be a positive integer.")
        raise typer.Exit(code=1)
    if offset < 0:
        safe_print("[bold red]Validation error:[/bold red] Offset must be non-negative.")
        raise typer.Exit(code=1)

    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        playlists = svc.list_playlists(limit=limit, offset=offset)
        if format_.lower() == "json":
            typer.echo(json.dumps([p.model_dump(mode="json") for p in playlists], indent=2))
        else:
            if not playlists:
                safe_print("[yellow]No playlists found.[/yellow]")
                return

            table = Table(
                title=f"Playlists (showing {len(playlists)})",
                show_header=True,
                header_style="bold magenta",
            )
            table.add_column("ID", style="dim", no_wrap=True)
            table.add_column("Name", style="bold white")
            table.add_column("Tracks", justify="right", style="green")
            table.add_column("Description", style="dim")
            table.add_column("Updated", style="dim")

            for p in playlists:
                updated_str = p.updated_at.strftime("%Y-%m-%d %H:%M") if p.updated_at else "-"
                table.add_row(
                    str(p.id)[:8] + "...",
                    p.name,
                    str(p.track_count),
                    p.description or "-",
                    updated_str,
                )
            safe_print(table)


@playlist_app.command("show", help="Show details and items for a playlist.")
def playlist_show_cmd(
    playlist_id: Annotated[str, typer.Argument(help="UUID of the playlist.")],
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Show details and track listing for a specific playlist."""
    uid = _parse_uuid(playlist_id, "playlist")
    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        pl = svc.get_playlist(uid)
        if pl is None:
            safe_print(f"[bold red]Playlist not found:[/bold red] {uid}")
            raise typer.Exit(code=1)

        if format_.lower() == "json":
            typer.echo(json.dumps(pl.model_dump(mode="json"), indent=2))
        else:
            duration_str = _format_duration(pl.total_duration_seconds)
            created_str = pl.created_at.strftime("%Y-%m-%d %H:%M:%S") if pl.created_at else "-"
            updated_str = pl.updated_at.strftime("%Y-%m-%d %H:%M:%S") if pl.updated_at else "-"
            details = [
                f"[bold cyan]ID:[/bold cyan] {pl.id}",
                f"[bold cyan]Name:[/bold cyan] {pl.name}",
                f"[bold cyan]Description:[/bold cyan] {pl.description or '-'}",
                f"[bold cyan]Tracks:[/bold cyan] {pl.track_count}",
                f"[bold cyan]Duration:[/bold cyan] {duration_str}",
                f"[bold cyan]Created:[/bold cyan] {created_str}",
                f"[bold cyan]Updated:[/bold cyan] {updated_str}",
            ]
            safe_print(Panel("\n".join(details), title=f"Playlist: {pl.name}", border_style="cyan"))

            if pl.items:
                table = Table(title="Items", show_header=True, header_style="bold magenta")
                table.add_column("Pos", justify="right", style="cyan")
                table.add_column("Item ID", style="dim", no_wrap=True)
                table.add_column("Title", style="bold white")
                table.add_column("Artist", style="cyan")
                table.add_column("Duration", justify="right", style="green")

                for it in pl.items:
                    table.add_row(
                        str(it.position),
                        str(it.id)[:8] + "...",
                        it.track_title or "-",
                        it.track_artist or "-",
                        _format_duration(it.duration_seconds),
                    )
                safe_print(table)
            else:
                safe_print("[dim]Playlist has no items.[/dim]")


@playlist_app.command("update", help="Update a playlist's name or description.")
def playlist_update_cmd(
    playlist_id: Annotated[str, typer.Argument(help="UUID of the playlist to update.")],
    name: Annotated[str | None, typer.Option("--name", "-n", help="New playlist name.")] = None,
    description: Annotated[
        str | None,
        typer.Option("--description", "-d", help="New playlist description."),
    ] = None,
    clear_description: Annotated[
        bool,
        typer.Option("--clear-description", help="Clear the existing description."),
    ] = False,
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Update an existing playlist's name or description."""
    uid = _parse_uuid(playlist_id, "playlist")
    if description is not None and clear_description:
        safe_print(
            "[bold red]Validation error:[/bold red] Cannot provide both --description and --clear-description."
        )
        raise typer.Exit(code=1)

    if not clear_description and description is None and name is None:
        safe_print(
            "[yellow]No updates specified. Provide --name, --description, or --clear-description.[/yellow]"
        )
        raise typer.Exit(code=1)

    if clear_description:
        update_dto = PlaylistUpdate.model_construct(name=name, description="")
    elif description is not None:
        update_dto = PlaylistUpdate(name=name, description=description)
    else:
        update_dto = PlaylistUpdate(name=name, description=None)

    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        updated = svc.update_playlist(uid, update_dto)
        if format_.lower() == "json":
            typer.echo(json.dumps(updated.model_dump(mode="json"), indent=2))
        else:
            safe_print(f"[bold green][OK] Updated playlist:[/bold green] {updated.name} ({updated.id})")


@playlist_app.command("delete", help="Delete a playlist and all its items.")
def playlist_delete_cmd(
    playlist_id: Annotated[str, typer.Argument(help="UUID of the playlist to delete.")],
    yes: Annotated[
        bool,
        typer.Option("--yes", "-y", help="Confirm deletion without interactive prompt."),
    ] = False,
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Delete a playlist with confirmation."""
    uid = _parse_uuid(playlist_id, "playlist")
    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        pl = svc.get_playlist(uid)
        if pl is None:
            safe_print(f"[bold red]Playlist not found:[/bold red] {uid}")
            raise typer.Exit(code=1)

        if not yes:
            if not _can_prompt():
                safe_print(
                    "[bold red]Error:[/bold red] Deletion confirmation could not be obtained in non-interactive environment. Use --yes / -y to confirm."
                )
                raise typer.Exit(code=1)

            try:
                confirmed = typer.confirm(
                    f"Are you sure you want to delete playlist '{pl.name}' ({uid})?",
                    default=False,
                )
            except Exception:
                safe_print(
                    "[bold red]Error:[/bold red] Deletion confirmation could not be obtained. Deletion cancelled."
                )
                raise typer.Exit(code=1) from None

            if not confirmed:
                safe_print("[yellow]Deletion cancelled.[/yellow]")
                return

        deleted = svc.delete_playlist(uid)
        if not deleted:
            safe_print(f"[bold red]Playlist not found:[/bold red] {uid}")
            raise typer.Exit(code=1)

        if format_.lower() == "json":
            typer.echo(json.dumps({"deleted": True, "playlist_id": str(uid)}, indent=2))
        else:
            safe_print(f"[bold green][OK] Deleted playlist:[/bold green] {pl.name} ({uid})")


@playlist_app.command("add", help="Add a track to a playlist.")
def playlist_add_cmd(
    playlist_id: Annotated[str, typer.Argument(help="UUID of the target playlist.")],
    track_id: Annotated[str, typer.Argument(help="UUID of the track to add.")],
    position: Annotated[
        int | None,
        typer.Option("--position", "-p", help="Target 0-indexed position. Defaults to end."),
    ] = None,
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Add a track to a playlist at an optional position."""
    pl_uid = _parse_uuid(playlist_id, "playlist")
    tr_uid = _parse_uuid(track_id, "track")
    if position is not None and position < 0:
        safe_print(f"[bold red]Validation error:[/bold red] Position must be non-negative, got {position}.")
        raise typer.Exit(code=1)

    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        item = svc.add_playlist_item(pl_uid, PlaylistItemCreate(track_id=tr_uid, position=position))
        if format_.lower() == "json":
            typer.echo(json.dumps(item.model_dump(mode="json"), indent=2))
        else:
            title_display = item.track_title or str(tr_uid)
            safe_print(
                f"[bold green][OK] Added track to playlist at position {item.position}:[/bold green] "
                f"{title_display} (item ID: {item.id})"
            )


@playlist_app.command("items", help="List all items in a playlist.")
def playlist_items_cmd(
    playlist_id: Annotated[str, typer.Argument(help="UUID of the playlist.")],
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """List items in a playlist in ordered sequence."""
    pl_uid = _parse_uuid(playlist_id, "playlist")
    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        items = svc.get_playlist_items(pl_uid)
        if format_.lower() == "json":
            typer.echo(json.dumps([it.model_dump(mode="json") for it in items], indent=2))
        else:
            if not items:
                safe_print("[yellow]Playlist is empty (no items).[/yellow]")
                return

            table = Table(
                title=f"Playlist Items (showing {len(items)})",
                show_header=True,
                header_style="bold magenta",
            )
            table.add_column("Pos", justify="right", style="cyan")
            table.add_column("Item ID", style="dim", no_wrap=True)
            table.add_column("Track ID", style="dim", no_wrap=True)
            table.add_column("Title", style="bold white")
            table.add_column("Artist", style="cyan")
            table.add_column("Duration", justify="right", style="green")

            for it in items:
                table.add_row(
                    str(it.position),
                    str(it.id)[:8] + "...",
                    str(it.track_id)[:8] + "...",
                    it.track_title or "-",
                    it.track_artist or "-",
                    _format_duration(it.duration_seconds),
                )
            safe_print(table)


@playlist_app.command("move", help="Move a playlist item to a new position.")
def playlist_move_cmd(
    playlist_id: Annotated[str, typer.Argument(help="UUID of the playlist.")],
    item_id: Annotated[str, typer.Argument(help="UUID of the item to move.")],
    position: Annotated[int, typer.Argument(help="New 0-indexed position.")],
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Move an existing playlist item to a new position."""
    pl_uid = _parse_uuid(playlist_id, "playlist")
    it_uid = _parse_uuid(item_id, "item")
    if position < 0:
        safe_print(f"[bold red]Validation error:[/bold red] Position must be non-negative, got {position}.")
        raise typer.Exit(code=1)

    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        svc.move_playlist_item(pl_uid, it_uid, position)
        if format_.lower() == "json":
            typer.echo(
                json.dumps(
                    {"playlist_id": str(pl_uid), "item_id": str(it_uid), "position": position},
                    indent=2,
                )
            )
        else:
            safe_print(f"[bold green][OK] Moved playlist item {it_uid} to position {position}.[/bold green]")


@playlist_app.command("remove", help="Remove an item from a playlist.")
def playlist_remove_cmd(
    playlist_id: Annotated[str, typer.Argument(help="UUID of the playlist.")],
    item_id: Annotated[str, typer.Argument(help="UUID of the item to remove.")],
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Remove a specific item from a playlist."""
    pl_uid = _parse_uuid(playlist_id, "playlist")
    it_uid = _parse_uuid(item_id, "item")
    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        removed = svc.remove_playlist_item(pl_uid, it_uid)
        if not removed:
            safe_print(f"[bold red]Playlist item not found:[/bold red] {it_uid}")
            raise typer.Exit(code=1)

        if format_.lower() == "json":
            typer.echo(json.dumps({"removed": True, "playlist_id": str(pl_uid), "item_id": str(it_uid)}, indent=2))
        else:
            safe_print(f"[bold green][OK] Removed item {it_uid} from playlist {pl_uid}.[/bold green]")


# ── Favorite Commands ─────────────────────────────────────────────────────────


@favorite_app.command("add", help="Add a track to favorites.")
def favorite_add_cmd(
    track_id: Annotated[str, typer.Argument(help="UUID of the track to favorite.")],
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Mark a catalog track as a user favorite (idempotent)."""
    uid = _parse_uuid(track_id, "track")
    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        fav = svc.add_favorite(uid)
        if format_.lower() == "json":
            typer.echo(json.dumps(fav.model_dump(mode="json"), indent=2))
        else:
            title_display = fav.track_title or str(uid)
            safe_print(f"[bold green][OK][/bold green] Added track to favorites: {title_display} ({uid})")


@favorite_app.command("remove", help="Remove a track from favorites.")
def favorite_remove_cmd(
    track_id: Annotated[str, typer.Argument(help="UUID of the track to unfavorite.")],
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Remove a track from user favorites."""
    uid = _parse_uuid(track_id, "track")
    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        track = svc._track_repo.get_by_id(uid)
        if track is None:
            safe_print(f"[bold red]Track not found:[/bold red] {uid}")
            raise typer.Exit(code=1)

        removed = svc.remove_favorite(uid)
        if format_.lower() == "json":
            typer.echo(json.dumps({"removed": removed, "track_id": str(uid)}, indent=2))
        else:
            if removed:
                safe_print(f"[bold green][OK][/bold green] Removed track {uid} from favorites.")
            else:
                safe_print(f"[yellow]Track was not in favorites:[/yellow] {uid}")


@favorite_app.command("check", help="Check whether a track is favorited.")
def favorite_check_cmd(
    track_id: Annotated[str, typer.Argument(help="UUID of the track to check.")],
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Check whether a specific track is in favorites."""
    uid = _parse_uuid(track_id, "track")
    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        track = svc._track_repo.get_by_id(uid)
        if track is None:
            safe_print(f"[bold red]Track not found:[/bold red] {uid}")
            raise typer.Exit(code=1)

        is_fav = svc.is_favorite(uid)
        if format_.lower() == "json":
            typer.echo(json.dumps({"track_id": str(uid), "is_favorite": is_fav}, indent=2))
        else:
            if is_fav:
                safe_print(f"[bold green][YES][/bold green] Track {uid} is in favorites.")
            else:
                safe_print(f"[yellow][NO][/yellow] Track {uid} is not in favorites.")


@favorite_app.command("list", help="List favorite tracks.")
def favorite_list_cmd(
    limit: Annotated[int, typer.Option("--limit", "-l", help="Maximum number of favorites to return.")] = 50,
    offset: Annotated[int, typer.Option("--offset", "-o", help="Offset for pagination.")] = 0,
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """List favorite tracks with pagination."""
    if limit <= 0:
        safe_print("[bold red]Validation error:[/bold red] Limit must be a positive integer.")
        raise typer.Exit(code=1)
    if offset < 0:
        safe_print("[bold red]Validation error:[/bold red] Offset must be non-negative.")
        raise typer.Exit(code=1)

    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        favorites = svc.list_favorites(limit=limit, offset=offset)
        if format_.lower() == "json":
            typer.echo(json.dumps([f.model_dump(mode="json") for f in favorites], indent=2))
        else:
            if not favorites:
                safe_print("[yellow]No favorite tracks found.[/yellow]")
                return

            table = Table(
                title=f"Favorite Tracks (showing {len(favorites)})",
                show_header=True,
                header_style="bold magenta",
            )
            table.add_column("Track ID", style="dim", no_wrap=True)
            table.add_column("Title", style="bold white")
            table.add_column("Artist", style="cyan")
            table.add_column("Favorite", style="bold green", justify="center")

            fav_glyph = "★ Yes" if _can_encode("★") else "[Yes]"
            for f in favorites:
                table.add_row(
                    str(f.track_id)[:8] + "...",
                    f.track_title or "-",
                    f.track_artist or "-",
                    fav_glyph,
                )
            safe_print(table)


# ── Rating Commands ───────────────────────────────────────────────────────────


@rating_app.command("set", help="Set a track rating (1-5 stars).")
def rating_set_cmd(
    track_id: Annotated[str, typer.Argument(help="UUID of the track to rate.")],
    stars: Annotated[int, typer.Argument(help="Star rating (integer from 1 to 5).")],
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Set or update a track rating (1-5 stars)."""
    uid = _parse_uuid(track_id, "track")
    if not (1 <= stars <= 5):
        safe_print(f"[bold red]Validation error:[/bold red] Rating must be an integer between 1 and 5, got {stars}.")
        raise typer.Exit(code=1)

    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        summary = svc.set_rating(uid, stars)
        if format_.lower() == "json":
            typer.echo(json.dumps(summary.model_dump(mode="json"), indent=2))
        else:
            stars_str = _format_stars(stars)
            safe_print(
                f"[bold green][OK][/bold green] Set rating for track {uid} to {stars} stars ({stars_str})."
            )


@rating_app.command("get", help="Get rating for a track.")
def rating_get_cmd(
    track_id: Annotated[str, typer.Argument(help="UUID of the track.")],
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Retrieve rating for a track."""
    uid = _parse_uuid(track_id, "track")
    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        track = svc._track_repo.get_by_id(uid)
        if track is None:
            safe_print(f"[bold red]Track not found:[/bold red] {uid}")
            raise typer.Exit(code=1)

        r = svc.get_rating(uid)
        if format_.lower() == "json":
            if r is None:
                typer.echo(json.dumps({"track_id": str(uid), "rating": None}, indent=2))
            else:
                typer.echo(json.dumps(r.model_dump(mode="json"), indent=2))
        else:
            if r is None:
                safe_print(f"[yellow]Track is not rated:[/yellow] {uid}")
            else:
                stars_str = _format_stars(r.rating)
                details = [
                    f"[bold cyan]Track:[/bold cyan] {r.track_title or str(uid)}",
                    f"[bold cyan]Artist:[/bold cyan] {r.track_artist or '-'}",
                    f"[bold cyan]Rating:[/bold cyan] {r.rating}/5 ({stars_str})",
                ]
                safe_print(Panel("\n".join(details), title="Track Rating", border_style="yellow"))


@rating_app.command("remove", help="Remove rating for a track.")
def rating_remove_cmd(
    track_id: Annotated[str, typer.Argument(help="UUID of the track.")],
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Remove a track rating."""
    uid = _parse_uuid(track_id, "track")
    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        track = svc._track_repo.get_by_id(uid)
        if track is None:
            safe_print(f"[bold red]Track not found:[/bold red] {uid}")
            raise typer.Exit(code=1)

        removed = svc.remove_rating(uid)
        if format_.lower() == "json":
            typer.echo(json.dumps({"removed": removed, "track_id": str(uid)}, indent=2))
        else:
            if removed:
                safe_print(f"[bold green][OK][/bold green] Removed rating for track {uid}.")
            else:
                safe_print(f"[yellow]Track was not rated:[/yellow] {uid}")


@rating_app.command("list", help="List track ratings.")
def rating_list_cmd(
    min_rating: Annotated[
        int | None,
        typer.Option("--min-rating", "-m", help="Filter by minimum rating (1-5 stars)."),
    ] = None,
    limit: Annotated[int, typer.Option("--limit", "-l", help="Maximum number of ratings to return.")] = 50,
    offset: Annotated[int, typer.Option("--offset", "-o", help="Offset for pagination.")] = 0,
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """List rated tracks with optional min_rating filter and pagination."""
    if min_rating is not None and not (1 <= min_rating <= 5):
        safe_print(f"[bold red]Validation error:[/bold red] min_rating must be between 1 and 5, got {min_rating}.")
        raise typer.Exit(code=1)
    if limit <= 0:
        safe_print("[bold red]Validation error:[/bold red] Limit must be a positive integer.")
        raise typer.Exit(code=1)
    if offset < 0:
        safe_print("[bold red]Validation error:[/bold red] Offset must be non-negative.")
        raise typer.Exit(code=1)

    with cli_error_boundary(), personalization_session(config_path, db_path) as svc:
        ratings = svc.list_ratings(limit=limit, offset=offset, min_rating=min_rating)
        if format_.lower() == "json":
            typer.echo(json.dumps([r.model_dump(mode="json") for r in ratings], indent=2))
        else:
            if not ratings:
                safe_print("[yellow]No rated tracks found.[/yellow]")
                return

            table = Table(
                title=f"Track Ratings (showing {len(ratings)})",
                show_header=True,
                header_style="bold magenta",
            )
            table.add_column("Track ID", style="dim", no_wrap=True)
            table.add_column("Title", style="bold white")
            table.add_column("Artist", style="cyan")
            table.add_column("Rating", style="yellow")

            for r in ratings:
                table.add_row(
                    str(r.track_id)[:8] + "...",
                    r.track_title or "-",
                    r.track_artist or "-",
                    f"{r.rating} " + _format_stars(r.rating),
                )
            safe_print(table)
