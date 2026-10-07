"""Search CLI command for Harmonia."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated

import typer
from rich.console import Console
from rich.table import Table

from harmonia.application.search_service import SearchService
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database

console = Console()


def search_cmd(
    query: str = typer.Argument(..., help="Search query (matches title, artist, album, genre)."),
    limit: Annotated[int, typer.Option("--limit", "-l", help="Maximum number of results.")] = 50,
    format_: Annotated[str, typer.Option("--format", "-f", help="Output format: table or json.")] = "table",
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Search library tracks by title, artist, album, or genre."""
    config = HarmoniaConfig.load(config_path)
    config.ensure_directories()
    effective_db = db_path or config.database.path

    engine = create_database_engine(effective_db)
    init_database(engine)
    session_factory = create_session_factory(engine)
    session = session_factory()

    try:
        service = SearchService(session)
        result = service.search(query, limit=limit)

        if format_.lower() == "json":
            console.print(json.dumps(result.model_dump(mode="json"), indent=2))
            return

        if not result.items:
            console.print(f"[yellow]No tracks found matching '[bold]{query}[/bold]'.[/yellow]")
            return

        table = Table(title=f"Search Results for '{query}' ({result.total_matches} matches)")
        table.add_column("Title", style="bold cyan")
        table.add_column("Artist", style="green")
        table.add_column("Album", style="magenta")
        table.add_column("Duration", justify="right")
        table.add_column("Format", justify="center")
        table.add_column("Quality", justify="right")

        for item in result.items:
            table.add_row(
                item.title,
                item.artist_name or "—",
                item.album_title or "—",
                item.duration_display,
                item.format or "—",
                str(item.quality_score) if item.quality_score is not None else "—",
            )

        console.print(table)
    finally:
        session.close()
        engine.dispose()


def reindex_cmd(
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Rebuild the full-text search index from the database."""
    config = HarmoniaConfig.load(config_path)
    config.ensure_directories()
    effective_db = db_path or config.database.path

    engine = create_database_engine(effective_db)
    init_database(engine)
    session_factory = create_session_factory(engine)
    session = session_factory()

    try:
        service = SearchService(session)
        count = service.reindex()
        console.print(f"[bold green][OK][/bold green] Rebuilt search index ({count} tracks indexed).")
    finally:
        session.close()
        engine.dispose()
