"""Watch command for Harmonia CLI."""

from __future__ import annotations

import signal
import threading
import time
from pathlib import Path
from typing import Annotated

import typer
from rich.console import Console
from rich.table import Table

from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.core.logging import get_logger
from harmonia.infrastructure.filesystem.watcher import LibraryWatcher

console = Console()
logger = get_logger("harmonia.cli.watch")


def watch_cmd(
    directories: Annotated[
        list[Path] | None,
        typer.Argument(
            help="Library directories to watch. Defaults to configured music_dirs if omitted.",
        ),
    ] = None,
    config_path: Annotated[
        Path | None,
        typer.Option("--config", "-c", help="Path to config TOML file."),
    ] = None,
    db_path: Annotated[
        Path | None,
        typer.Option("--db", help="Path to SQLite database file."),
    ] = None,
    debounce: Annotated[
        float,
        typer.Option("--debounce", help="Debounce delay in seconds for coalescing filesystem changes."),
    ] = 2.0,
    once: Annotated[
        bool,
        typer.Option("--once", help="Run a one-shot scan and reconciliation on library roots, then exit."),
    ] = False,
    rescan: Annotated[
        bool,
        typer.Option("--rescan", help="Force re-checking of all files, bypassing fast mtime/size skip."),
    ] = False,
    follow_symlinks: Annotated[
        bool,
        typer.Option("--symlinks/--no-symlinks", help="Follow symbolic links during watch/scan."),
    ] = False,
) -> None:
    """Watch library directories and automatically update the Harmonia catalog."""
    config = HarmoniaConfig.load(config_path)
    config.ensure_directories()

    candidate_roots = [p.resolve() for p in directories] if directories else [p.resolve() for p in config.music_dirs]

    if not candidate_roots:
        console.print("[bold red]Error:[/bold red] No music directories specified.")
        console.print("Provide one or more directories as arguments, or set 'music_dirs' in config/default.toml or HARMONIA_MUSIC_DIRS.")
        raise typer.Exit(code=1)

    valid_roots: list[Path] = []
    missing_roots: list[Path] = []
    for root in candidate_roots:
        if root.is_dir():
            valid_roots.append(root)
        else:
            missing_roots.append(root)

    if missing_roots:
        console.print("[yellow]Warning: The following directories do not exist or are inaccessible:[/yellow]")
        for m in missing_roots:
            console.print(f"  [yellow]•[/yellow] {m}")

    if not valid_roots:
        console.print("[bold red]Error:[/bold red] None of the specified watch directories exist.")
        raise typer.Exit(code=1)

    effective_db_path = db_path or config.database.path
    engine = create_database_engine(effective_db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    watcher = LibraryWatcher(
        watch_directories=valid_roots,
        session_factory=session_factory,
        supported_formats=config.import_.supported_formats,
        debounce_delay=debounce,
        follow_symlinks=follow_symlinks,
    )

    if once:
        console.print("[bold cyan]Harmonia Library Reconciliation (One-shot)[/bold cyan]")
        console.print(f"Reconciling {len(valid_roots)} directory root(s)...")
        try:
            with console.status("[bold green]Scanning and reconciling library...[/bold green]"):
                result = watcher.run_once(rescan=rescan)

            table = Table(title="Reconciliation Summary", show_header=True, header_style="bold magenta")
            table.add_column("Metric", style="cyan")
            table.add_column("Count", style="green", justify="right")

            table.add_row("Files Scanned", str(result.total_scanned))
            table.add_row("Imported / Updated", f"[bold green]{result.imported}[/bold green]")
            table.add_row("Skipped (Unchanged)", f"[yellow]{result.skipped}[/yellow]")
            table.add_row("Failed", f"[bold red]{result.failed}[/bold red]" if result.failed > 0 else "0")

            console.print()
            console.print(table)

            if result.errors:
                console.print("\n[bold red]Errors encountered during reconciliation:[/bold red]")
                for err in result.errors[:15]:
                    console.print(f" [red]*[/red] [dim]{err.file_path}[/dim]: {err.reason}")
        finally:
            engine.dispose()
        raise typer.Exit(code=0)

    # Long-running watch mode
    console.print("[bold cyan]Harmonia Filesystem Watcher[/bold cyan]")
    console.print(f"Monitoring {len(valid_roots)} library root(s) with {debounce:.1f}s debounce:")
    for root in valid_roots:
        console.print(f"  [green]•[/green] {root}")
    console.print("[dim]Press Ctrl+C to stop watching.[/dim]\n")

    stop_event = threading.Event()

    def _handle_signal(signum: int, frame: object) -> None:
        logger.info("Shutdown signal received (%s)", signum)
        stop_event.set()

    old_sigint = None
    old_sigterm = None
    try:
        old_sigint = signal.getsignal(signal.SIGINT)
        signal.signal(signal.SIGINT, _handle_signal)
        if hasattr(signal, "SIGTERM"):
            old_sigterm = signal.getsignal(signal.SIGTERM)
            signal.signal(signal.SIGTERM, _handle_signal)
    except (ValueError, AttributeError):
        pass

    try:
        watcher.start()
        while not stop_event.is_set():
            time.sleep(0.5)
    except KeyboardInterrupt:
        pass
    finally:
        try:
            if old_sigint is not None:
                signal.signal(signal.SIGINT, old_sigint)
            if old_sigterm is not None and hasattr(signal, "SIGTERM"):
                signal.signal(signal.SIGTERM, old_sigterm)
        except (ValueError, AttributeError):
            pass
        console.print("\n[bold yellow]Stopping filesystem watcher...[/bold yellow]")
        watcher.stop()
        engine.dispose()
        console.print("[bold green]Watcher stopped cleanly.[/bold green]")
