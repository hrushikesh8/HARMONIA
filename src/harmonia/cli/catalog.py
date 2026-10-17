"""CLI commands for Harmonia catalog portability (export and import)."""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path
from typing import Annotated

import typer
from rich.console import Console
from rich.panel import Panel
from rich.table import Table

from harmonia.application.catalog_import_service import CatalogImportService
from harmonia.application.export_service import CatalogExportService
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.core.exceptions import (
    CatalogConflictError,
    CatalogError,
    CatalogIntegrityError,
    CatalogValidationError,
    CatalogVersionError,
)

console = Console()
err_console = Console(stderr=True)


def export_cmd(
    output: Annotated[
        Path | None,
        typer.Option(
            "--output",
            "-o",
            help="Destination path for catalog bundle JSON. Defaults to 'harmonia_catalog_<timestamp>.json'.",
        ),
    ] = None,
    force: Annotated[
        bool,
        typer.Option("--force", "-f", help="Overwrite destination file if it already exists."),
    ] = False,
    pretty: Annotated[
        bool,
        typer.Option("--pretty/--compact", help="Format JSON output with indentation (default: pretty)."),
    ] = True,
    include_telemetry: Annotated[
        bool,
        typer.Option(
            "--include-telemetry/--no-telemetry",
            help="Include listening event telemetry in exported bundle (default: include).",
        ),
    ] = True,
    config_path: Annotated[
        Path | None,
        typer.Option("--config", "-c", help="Path to custom config TOML file."),
    ] = None,
    db_path: Annotated[
        Path | None,
        typer.Option("--db", help="Path to custom SQLite database file."),
    ] = None,
) -> None:
    """Export the Harmonia library catalog into a portable, versioned JSON bundle."""
    config = HarmoniaConfig.load(config_path)
    config.ensure_directories()
    effective_db = db_path or config.database.path

    engine = create_database_engine(effective_db)
    init_database(engine)
    session_factory = create_session_factory(engine)

    dest = output or Path(f"harmonia_catalog_{datetime.now(UTC).strftime('%Y%m%d_%H%M%S')}.json")

    try:
        with session_factory() as session:
            service = CatalogExportService(session)
            try:
                saved_path = service.export_to_file(
                    destination_path=dest,
                    force=force,
                    pretty=pretty,
                    include_telemetry=include_telemetry,
                )
            except FileExistsError as exc:
                err_console.print(f"[bold red]Export Error:[/bold red] {exc}")
                raise typer.Exit(code=1) from exc
            except Exception as exc:
                err_console.print(f"[bold red]Unexpected Export Failure:[/bold red] {exc}")
                raise typer.Exit(code=1) from exc

            # Read back bundle manifest to display verification summary
            bundle = service.build_bundle(include_telemetry=include_telemetry)

            table = Table(title="Catalog Export Summary", show_header=True, header_style="bold magenta")
            table.add_column("Property", style="cyan")
            table.add_column("Value", style="green")

            table.add_row("Destination", str(saved_path.resolve()))
            table.add_row("Schema Version", bundle.manifest.schema_version)
            table.add_row("Harmonia Version", bundle.manifest.harmonia_version)
            table.add_row("Checksum (SHA-256)", bundle.checksum)
            table.add_row("Artists", str(bundle.manifest.entity_counts.get("artists", 0)))
            table.add_row("Albums", str(bundle.manifest.entity_counts.get("albums", 0)))
            table.add_row("Tracks", str(bundle.manifest.entity_counts.get("tracks", 0)))
            table.add_row("Track Files", str(bundle.manifest.entity_counts.get("track_files", 0)))
            table.add_row("Tags", str(bundle.manifest.entity_counts.get("tags", 0)))
            table.add_row("Metadata Provenance", str(bundle.manifest.entity_counts.get("metadata_fields", 0)))
            table.add_row("Listening Events", str(bundle.manifest.entity_counts.get("listening_events", 0)))

            console.print(table)
            console.print(Panel(f"[bold green]Catalog bundle successfully exported to[/bold green] [bold]{saved_path}[/bold]"))
    finally:
        engine.dispose()


def import_catalog_cmd(
    catalog_file: Annotated[
        Path,
        typer.Argument(help="Path to the JSON catalog bundle to import.", exists=False),
    ],
    on_conflict: Annotated[
        str,
        typer.Option(
            "--on-conflict",
            help="Conflict resolution policy for existing entity IDs: 'skip', 'replace', or 'fail'.",
        ),
    ] = "skip",
    dry_run: Annotated[
        bool,
        typer.Option(
            "--dry-run",
            help="Validate bundle structure, version, checksum, and references without committing changes.",
        ),
    ] = False,
    config_path: Annotated[
        Path | None,
        typer.Option("--config", "-c", help="Path to custom config TOML file."),
    ] = None,
    db_path: Annotated[
        Path | None,
        typer.Option("--db", help="Path to custom SQLite database file."),
    ] = None,
) -> None:
    """Import a versioned Harmonia catalog bundle into the library."""
    if on_conflict not in ("skip", "replace", "fail"):
        err_console.print(
            f"[bold red]Invalid --on-conflict policy '{on_conflict}'. "
            "Must be one of: 'skip', 'replace', 'fail'.[/bold red]"
        )
        raise typer.Exit(code=1)

    if not catalog_file.exists():
        err_console.print(f"[bold red]Catalog file does not exist:[/bold red] {catalog_file}")
        raise typer.Exit(code=1)

    config = HarmoniaConfig.load(config_path)
    config.ensure_directories()
    effective_db = db_path or config.database.path

    engine = create_database_engine(effective_db)
    init_database(engine)
    session_factory = create_session_factory(engine)

    try:
        with session_factory() as session:
            service = CatalogImportService(session)
            try:
                result = service.import_bundle(
                    bundle_source=catalog_file,
                    on_conflict=on_conflict,  # type: ignore[arg-type]
                    dry_run=dry_run,
                )
            except CatalogValidationError as exc:
                err_console.print(f"[bold red]Catalog Validation Error:[/bold red] {exc}")
                raise typer.Exit(code=1) from exc
            except CatalogIntegrityError as exc:
                err_console.print(f"[bold red]Catalog Integrity Error:[/bold red] {exc}")
                raise typer.Exit(code=1) from exc
            except CatalogVersionError as exc:
                err_console.print(f"[bold red]Catalog Version Error:[/bold red] {exc}")
                raise typer.Exit(code=1) from exc
            except CatalogConflictError as exc:
                err_console.print(f"[bold red]Catalog Conflict Error:[/bold red] {exc}")
                raise typer.Exit(code=1) from exc
            except CatalogError as exc:
                err_console.print(f"[bold red]Catalog Error:[/bold red] {exc}")
                raise typer.Exit(code=1) from exc
            except Exception as exc:
                err_console.print(f"[bold red]Unexpected Import Failure:[/bold red] {exc}")
                raise typer.Exit(code=1) from exc

            # Display results table
            title = "Catalog Import Simulation (Dry-Run)" if dry_run else "Catalog Import Results"
            table = Table(title=title, show_header=True, header_style="bold cyan")
            table.add_column("Entity Type", style="magenta")
            table.add_column("Created", justify="right", style="green")
            table.add_column("Updated", justify="right", style="yellow")
            table.add_column("Skipped", justify="right", style="dim")

            table.add_row("Artists", str(result.artists_created), str(result.artists_updated), str(result.artists_skipped))
            table.add_row("Albums", str(result.albums_created), str(result.albums_updated), str(result.albums_skipped))
            table.add_row("Tracks", str(result.tracks_created), str(result.tracks_updated), str(result.tracks_skipped))
            table.add_row("Track Files", str(result.track_files_created), str(result.track_files_updated), str(result.track_files_skipped))
            table.add_row("Tags", str(result.tags_created), "0", str(result.tags_skipped))
            table.add_row("Metadata Provenance", str(result.metadata_fields_created), str(result.metadata_fields_updated), str(result.metadata_fields_skipped))
            table.add_row("Listening Events", str(result.listening_events_created), "0", str(result.listening_events_skipped))

            console.print(table)

            if result.warnings:
                console.print(f"[bold yellow]Warnings encountered ({len(result.warnings)}):[/bold yellow]")
                for w in result.warnings[:10]:
                    console.print(f"  • {w}")
                if len(result.warnings) > 10:
                    console.print(f"  ... and {len(result.warnings) - 10} more warnings.")

            status_msg = (
                f"[bold yellow]Dry-run complete. Would create {result.total_created} records, "
                f"update {result.total_updated}, skip {result.total_skipped}. No database changes committed.[/bold yellow]"
                if dry_run
                else f"[bold green]Import complete. Successfully created {result.total_created} records, "
                f"updated {result.total_updated}, skipped {result.total_skipped}.[/bold green]"
            )
            console.print(Panel(status_msg))
    finally:
        engine.dispose()
