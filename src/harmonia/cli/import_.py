"""Import command for Harmonia CLI."""

from __future__ import annotations

from pathlib import Path
from typing import Annotated

import typer
from rich.console import Console
from rich.table import Table

from harmonia.application.import_service import ImportService
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database

console = Console()


def run_import(
    directory: Annotated[
        Path,
        typer.Argument(
            help="Directory containing music files to import.",
            exists=True,
            file_okay=False,
            dir_okay=True,
            readable=True,
            resolve_path=True,
        ),
    ],
    config_path: Annotated[
        Path | None,
        typer.Option("--config", "-c", help="Path to config TOML file."),
    ] = None,
    db_path: Annotated[
        Path | None,
        typer.Option("--db", help="Path to SQLite database file."),
    ] = None,
    formats: Annotated[
        str | None,
        typer.Option("--formats", help="Comma-separated file extensions to scan (e.g. .mp3,.flac)."),
    ] = None,
    follow_symlinks: Annotated[
        bool,
        typer.Option("--symlinks/--no-symlinks", help="Follow symbolic links during scan."),
    ] = False,
    compute_hashes: Annotated[
        bool,
        typer.Option("--hash/--no-hash", help="Compute SHA-256 hashes for imported files."),
    ] = True,
    fetch_metadata: Annotated[
        bool,
        typer.Option("--fetch-metadata", help="Fetch online metadata from MusicBrainz (opt-in, default OFF)."),
    ] = False,
    rescan: Annotated[
        bool,
        typer.Option("--rescan", help="Force re-checking of all files, bypassing fast mtime/size skip."),
    ] = False,
    artwork: Annotated[
        bool,
        typer.Option("--artwork/--no-artwork", help="Extract embedded cover art during import."),
    ] = True,
) -> None:
    """Scan a directory and import music files into the Harmonia library."""
    config = HarmoniaConfig.load(config_path)
    config.ensure_directories()

    effective_db_path = db_path or config.database.path
    engine = create_database_engine(effective_db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    effective_formats = (
        [f.strip() if f.strip().startswith(".") else f".{f.strip()}" for f in formats.split(",")]
        if formats
        else config.import_.supported_formats
    )

    console.print(f"[bold cyan]Scanning directory:[/bold cyan] {directory}")
    console.print(f"[dim]Supported formats: {', '.join(effective_formats)}[/dim]")
    if fetch_metadata:
        console.print("[dim]MusicBrainz metadata lookup: [green]ENABLED[/green][/dim]")

    mb_provider = None
    if fetch_metadata:
        from harmonia.infrastructure.metadata.musicbrainz_provider import MusicBrainzProvider
        mb_provider = MusicBrainzProvider()

    try:
        with session_factory() as session:
            importer = ImportService(
                session=session,
                supported_formats=effective_formats,
                follow_symlinks=follow_symlinks,
                compute_hashes=compute_hashes,
                extract_artwork=artwork,
                fetch_metadata=fetch_metadata,
                metadata_provider=mb_provider,
            )

            with console.status("[bold green]Importing tracks...[/bold green]"):
                result = importer.import_directory(directory, rescan=rescan)

        # Display results table
        table = Table(title="Import Summary", show_header=True, header_style="bold magenta")
        table.add_column("Metric", style="cyan")
        table.add_column("Count", style="green", justify="right")

        table.add_row("Files Scanned", str(result.total_scanned))
        table.add_row("Imported", f"[bold green]{result.imported}[/bold green]")
        table.add_row("Skipped (Existing)", f"[yellow]{result.skipped}[/yellow]")
        table.add_row("Failed", f"[bold red]{result.failed}[/bold red]" if result.failed > 0 else "0")

        console.print()
        console.print(table)

        if result.errors:
            console.print("\n[bold red]Errors encountered during import:[/bold red]")
            for err in result.errors[:15]:
                console.print(f" [red]*[/red] [dim]{err.file_path}[/dim]: {err.reason}")
            if len(result.errors) > 15:
                console.print(f" [dim]...and {len(result.errors) - 15} more errors.[/dim]")
    finally:
        engine.dispose()
