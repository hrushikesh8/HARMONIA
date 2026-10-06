"""Library query and inspection commands for Harmonia CLI."""

from __future__ import annotations

from pathlib import Path
from typing import Annotated
from uuid import UUID

import typer
from rich.console import Console
from rich.panel import Panel
from rich.table import Table

from harmonia.application.library_service import LibraryService
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import (
    check_database,
    create_database_engine,
    create_session_factory,
    init_database,
)

console = Console()


def get_library_service(
    config_path: Path | None = None,
    db_path: Path | None = None,
) -> tuple[LibraryService, object, object]:
    """Helper to initialize db and create a LibraryService."""
    config = HarmoniaConfig.load(config_path)
    config.ensure_directories()
    effective_db = db_path or config.database.path
    engine = create_database_engine(effective_db)
    init_database(engine)
    session_factory = create_session_factory(engine)
    session = session_factory()
    return LibraryService(session), session, engine


def info_cmd(
    config_path: Annotated[
        Path | None,
        typer.Option("--config", "-c", help="Path to config TOML file."),
    ] = None,
    db_path: Annotated[
        Path | None,
        typer.Option("--db", help="Path to SQLite database file."),
    ] = None,
) -> None:
    """Display high-level statistics about the Harmonia music library."""
    svc, session, engine = get_library_service(config_path, db_path)
    try:
        summary = svc.get_summary()

        table = Table(title="Harmonia Library Overview", show_header=True, header_style="bold magenta")
        table.add_column("Category", style="cyan")
        table.add_column("Count", style="green", justify="right")

        table.add_row("Tracks", str(summary.total_tracks))
        table.add_row("Albums", str(summary.total_albums))
        table.add_row("Artists", str(summary.total_artists))

        console.print(table)
    finally:
        session.close()  # type: ignore[attr-defined]
        engine.dispose()  # type: ignore[attr-defined]


def list_tracks_cmd(
    limit: Annotated[int, typer.Option("--limit", "-l", help="Maximum number of tracks to return.")] = 50,
    offset: Annotated[int, typer.Option("--offset", "-o", help="Offset for pagination.")] = 0,
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Config file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Database file.")] = None,
) -> None:
    """List tracks in the library."""
    svc, session, engine = get_library_service(config_path, db_path)
    try:
        tracks = svc.list_tracks(limit=limit, offset=offset)
        if not tracks:
            console.print("[yellow]No tracks found in library.[/yellow]")
            return

        table = Table(title=f"Tracks (showing {len(tracks)})", show_header=True, header_style="bold magenta")
        table.add_column("ID", style="dim", no_wrap=True)
        table.add_column("Title", style="bold white")
        table.add_column("Artist", style="cyan")
        table.add_column("Album", style="blue")
        table.add_column("Duration", justify="right", style="green")
        table.add_column("Year", justify="right", style="yellow")
        table.add_column("Genre", style="magenta")

        for t in tracks:
            table.add_row(
                str(t.id)[:8] + "...",
                t.title,
                t.artist_name or "-",
                t.album_title or "-",
                t.duration_display,
                str(t.year) if t.year else "-",
                t.genre or "-",
            )
        console.print(table)
    finally:
        session.close()  # type: ignore[attr-defined]
        engine.dispose()  # type: ignore[attr-defined]


def list_albums_cmd(
    limit: Annotated[int, typer.Option("--limit", "-l", help="Maximum number of albums to return.")] = 50,
    offset: Annotated[int, typer.Option("--offset", "-o", help="Offset for pagination.")] = 0,
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Config file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Database file.")] = None,
) -> None:
    """List albums in the library."""
    svc, session, engine = get_library_service(config_path, db_path)
    try:
        albums = svc.list_albums(limit=limit, offset=offset)
        if not albums:
            console.print("[yellow]No albums found in library.[/yellow]")
            return

        table = Table(title=f"Albums (showing {len(albums)})", show_header=True, header_style="bold magenta")
        table.add_column("ID", style="dim", no_wrap=True)
        table.add_column("Title", style="bold white")
        table.add_column("Artist", style="cyan")
        table.add_column("Year", justify="right", style="yellow")

        for a in albums:
            table.add_row(
                str(a.id)[:8] + "...",
                a.title,
                a.artist_name or "-",
                str(a.year) if a.year else "-",
            )
        console.print(table)
    finally:
        session.close()  # type: ignore[attr-defined]
        engine.dispose()  # type: ignore[attr-defined]


def list_artists_cmd(
    limit: Annotated[int, typer.Option("--limit", "-l", help="Maximum number of artists to return.")] = 50,
    offset: Annotated[int, typer.Option("--offset", "-o", help="Offset for pagination.")] = 0,
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Config file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Database file.")] = None,
) -> None:
    """List artists in the library."""
    svc, session, engine = get_library_service(config_path, db_path)
    try:
        artists = svc.list_artists(limit=limit, offset=offset)
        if not artists:
            console.print("[yellow]No artists found in library.[/yellow]")
            return

        table = Table(title=f"Artists (showing {len(artists)})", show_header=True, header_style="bold magenta")
        table.add_column("ID", style="dim", no_wrap=True)
        table.add_column("Name", style="bold white")
        table.add_column("Sort Name", style="dim")

        for a in artists:
            table.add_row(
                str(a.id)[:8] + "...",
                a.name,
                a.sort_name or "-",
            )
        console.print(table)
    finally:
        session.close()  # type: ignore[attr-defined]
        engine.dispose()  # type: ignore[attr-defined]


def track_cmd(
    track_id: Annotated[str, typer.Argument(help="UUID of the track.")],
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Config file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Database file.")] = None,
) -> None:
    """Show detailed metadata and file information for a track."""
    svc, session, engine = get_library_service(config_path, db_path)
    try:
        try:
            uid = UUID(track_id)
        except ValueError:
            console.print(f"[bold red]Invalid track UUID:[/bold red] {track_id}")
            raise typer.Exit(code=1)

        track = svc.get_track(uid)
        if not track:
            console.print(f"[bold red]Track not found:[/bold red] {track_id}")
            raise typer.Exit(code=1)

        details = [
            f"[bold cyan]Title:[/bold cyan] {track.title}",
            f"[bold cyan]Artist:[/bold cyan] {track.artist_name or '-'}",
            f"[bold cyan]Album:[/bold cyan] {track.album_title or '-'}",
            f"[bold cyan]Track #:[/bold cyan] {track.track_number or '-'} (Disc {track.disc_number or 1})",
            f"[bold cyan]Year:[/bold cyan] {track.year or '-'}",
            f"[bold cyan]Genre:[/bold cyan] {track.genre or '-'}",
            f"[bold cyan]Duration:[/bold cyan] {track.duration_display}",
            f"[bold cyan]MusicBrainz ID:[/bold cyan] {track.mbid or '-'}",
        ]
        console.print(Panel("\n".join(details), title=f"Track: {track.title}", border_style="green"))

        if track.files:
            file_table = Table(title="Audio Files", show_header=True, header_style="bold blue")
            file_table.add_column("Format", style="magenta")
            file_table.add_column("Codec", style="cyan")
            file_table.add_column("Bitrate", justify="right")
            file_table.add_column("Sample Rate", justify="right")
            file_table.add_column("Lossless", justify="center")
            file_table.add_column("Quality Score", justify="right", style="bold green")
            file_table.add_column("Size", justify="right")
            file_table.add_column("Path", style="dim")

            for f in track.files:
                file_table.add_row(
                    f.format,
                    f.codec or "-",
                    f"{f.bitrate} kbps" if f.bitrate else "-",
                    f"{f.sample_rate} Hz" if f.sample_rate else "-",
                    "[green]Yes[/green]" if f.is_lossless else "[yellow]No[/yellow]",
                    str(f.quality_score) if f.quality_score is not None else "-",
                    f.file_size_display,
                    f.file_path,
                )
            console.print(file_table)

        if track.tags:
            tag_table = Table(title="Tags", show_header=True, header_style="bold yellow")
            tag_table.add_column("Name", style="cyan")
            tag_table.add_column("Value", style="white")
            tag_table.add_column("Source", style="dim")
            for t in track.tags:
                tag_table.add_row(t.name, t.value, t.source)
            console.print(tag_table)
    finally:
        session.close()  # type: ignore[attr-defined]
        engine.dispose()  # type: ignore[attr-defined]


def album_cmd(
    album_id: Annotated[str, typer.Argument(help="UUID of the album.")],
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Config file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Database file.")] = None,
) -> None:
    """Show details and track listing for an album."""
    svc, session, engine = get_library_service(config_path, db_path)
    try:
        try:
            uid = UUID(album_id)
        except ValueError:
            console.print(f"[bold red]Invalid album UUID:[/bold red] {album_id}")
            raise typer.Exit(code=1)

        album = svc.get_album(uid)
        if not album:
            console.print(f"[bold red]Album not found:[/bold red] {album_id}")
            raise typer.Exit(code=1)

        details = [
            f"[bold cyan]Album:[/bold cyan] {album.title}",
            f"[bold cyan]Artist:[/bold cyan] {album.artist_name or '-'}",
            f"[bold cyan]Year:[/bold cyan] {album.year or '-'}",
            f"[bold cyan]Total Tracks in DB:[/bold cyan] {album.track_count}",
            f"[bold cyan]MusicBrainz ID:[/bold cyan] {album.mbid or '-'}",
        ]
        console.print(Panel("\n".join(details), title=f"Album: {album.title}", border_style="blue"))

        if album.tracks:
            t_table = Table(title="Tracks", show_header=True, header_style="bold magenta")
            t_table.add_column("ID", style="dim")
            t_table.add_column("Title", style="bold white")
            t_table.add_column("Duration", justify="right", style="green")
            t_table.add_column("Genre", style="cyan")

            for t in album.tracks:
                t_table.add_row(str(t.id)[:8] + "...", t.title, t.duration_display, t.genre or "-")
            console.print(t_table)
    finally:
        session.close()  # type: ignore[attr-defined]
        engine.dispose()  # type: ignore[attr-defined]


def artist_cmd(
    artist_id: Annotated[str, typer.Argument(help="UUID of the artist.")],
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Config file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Database file.")] = None,
) -> None:
    """Show details and albums for an artist."""
    svc, session, engine = get_library_service(config_path, db_path)
    try:
        try:
            uid = UUID(artist_id)
        except ValueError:
            console.print(f"[bold red]Invalid artist UUID:[/bold red] {artist_id}")
            raise typer.Exit(code=1)

        artist = svc.get_artist(uid)
        if not artist:
            console.print(f"[bold red]Artist not found:[/bold red] {artist_id}")
            raise typer.Exit(code=1)

        details = [
            f"[bold cyan]Artist:[/bold cyan] {artist.name}",
            f"[bold cyan]Sort Name:[/bold cyan] {artist.sort_name or '-'}",
            f"[bold cyan]MusicBrainz ID:[/bold cyan] {artist.mbid or '-'}",
            f"[bold cyan]Albums Count:[/bold cyan] {artist.album_count}",
        ]
        console.print(Panel("\n".join(details), title=f"Artist: {artist.name}", border_style="cyan"))

        if artist.albums:
            alb_table = Table(title="Albums", show_header=True, header_style="bold magenta")
            alb_table.add_column("ID", style="dim")
            alb_table.add_column("Title", style="bold white")
            alb_table.add_column("Year", justify="right", style="yellow")

            for a in artist.albums:
                alb_table.add_row(str(a.id)[:8] + "...", a.title, str(a.year) if a.year else "-")
            console.print(alb_table)
    finally:
        session.close()  # type: ignore[attr-defined]
        engine.dispose()  # type: ignore[attr-defined]


def init_db_cmd(
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Config file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Database file.")] = None,
) -> None:
    """Initialize SQLite database tables and indexes."""
    config = HarmoniaConfig.load(config_path)
    config.ensure_directories()
    target_path = db_path or config.database.path
    engine = create_database_engine(target_path)
    try:
        init_database(engine)
        if check_database(engine):
            console.print(f"[bold green][OK] Database initialized successfully at:[/bold green] {target_path}")
        else:
            console.print(f"[bold red][FAIL] Database initialization check failed for:[/bold red] {target_path}")
            raise typer.Exit(code=1)
    finally:
        engine.dispose()
