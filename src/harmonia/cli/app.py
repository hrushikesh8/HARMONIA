"""Main Typer CLI application for Harmonia."""

from __future__ import annotations

import typer
from rich.console import Console

from harmonia import __version__
from harmonia.cli.analyze import analyze_cmd
from harmonia.cli.catalog import export_cmd, import_catalog_cmd
from harmonia.cli.duplicates import duplicates_app
from harmonia.cli.import_ import run_import
from harmonia.cli.library import (
    album_cmd,
    artist_cmd,
    info_cmd,
    init_db_cmd,
    list_albums_cmd,
    list_artists_cmd,
    list_tracks_cmd,
    track_cmd,
)
from harmonia.cli.search import reindex_cmd, search_cmd
from harmonia.cli.serve import serve_cmd, ui_cmd

console = Console()

app = typer.Typer(
    name="harmonia",
    help="Harmonia — Deterministic music infrastructure & personal library system.",
    add_completion=False,
    no_args_is_help=True,
)

# ── Subcommand groups ─────────────────────────────────────────────────────────

list_app = typer.Typer(
    name="list",
    help="List entities in the library (tracks, albums, artists).",
    no_args_is_help=False,
)
list_app.command("tracks", help="List tracks in the library.")(list_tracks_cmd)
list_app.command("albums", help="List albums in the library.")(list_albums_cmd)
list_app.command("artists", help="List artists in the library.")(list_artists_cmd)


# Default `harmonia list` without arguments lists tracks
@list_app.callback(invoke_without_command=True)
def list_default(ctx: typer.Context) -> None:
    if ctx.invoked_subcommand is None:
        list_tracks_cmd()


app.add_typer(list_app, name="list")
app.add_typer(duplicates_app, name="duplicates")

# ── Standalone Commands ───────────────────────────────────────────────────────

app.command("import", help="Import music files from a directory into Harmonia.")(run_import)
app.command("search", help="Search the library by track title, artist, album, or genre.")(search_cmd)
app.command("reindex", help="Rebuild the full-text search index.")(reindex_cmd)
app.command("info", help="Display overview statistics for the music library.")(info_cmd)
app.command("track", help="View full metadata and technical file info for a track.")(track_cmd)
app.command("album", help="View album details and track list.")(album_cmd)
app.command("artist", help="View artist details and albums.")(artist_cmd)
app.command("analyze", help="Measure objective audio signal properties (ebur128, true peak).")(analyze_cmd)
app.command("serve", help="Launch the local HTTP REST API server.")(serve_cmd)
app.command("ui", help="Launch the Harmonia desktop web UI.")(ui_cmd)
app.command("init-db", help="Initialize the SQLite database schema.")(init_db_cmd)
app.command("export", help="Export the library catalog to a portable JSON bundle.")(export_cmd)
app.command("import-catalog", help="Import a portable JSON catalog bundle into the library.")(import_catalog_cmd)


def version_callback(value: bool) -> None:
    if value:
        console.print(f"[bold cyan]Harmonia[/bold cyan] version [bold green]{__version__}[/bold green]")
        raise typer.Exit()


@app.callback()
def main_callback(
    version: bool = typer.Option(
        None,
        "--version",
        "-v",
        help="Show Harmonia version and exit.",
        callback=version_callback,
        is_eager=True,
    ),
) -> None:
    """Harmonia — Deterministic, offline-first personal music library."""
    pass


def main() -> None:
    """Entry point for `harmonia` command line executable."""
    app()


if __name__ == "__main__":
    main()
