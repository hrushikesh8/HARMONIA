"""Duplicates CLI commands for reviewing and resolving duplicate track candidates."""

from __future__ import annotations

from pathlib import Path
from typing import Annotated

import typer
from rich.console import Console
from rich.table import Table

from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.infrastructure.persistence.duplicate_candidate_repo import (
    SqliteDuplicateCandidateRepository,
)
from harmonia.infrastructure.persistence.search_repo import SqliteSearchRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

console = Console()

duplicates_app = typer.Typer(
    name="duplicates",
    help="Inspect and resolve probable duplicate tracks.",
    no_args_is_help=False,
)


@duplicates_app.callback(invoke_without_command=True)
def duplicates_default(ctx: typer.Context) -> None:
    """Default action: list unresolved candidates."""
    if ctx.invoked_subcommand is None:
        list_duplicates_cmd()


@duplicates_app.command("list", help="List unresolved duplicate track candidates.")
def list_duplicates_cmd(
    limit: Annotated[int, typer.Option("--limit", "-l", help="Maximum candidates to display.")] = 50,
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """List duplicate candidates pending review."""
    config = HarmoniaConfig.load(config_path)
    config.ensure_directories()
    effective_db = db_path or config.database.path

    engine = create_database_engine(effective_db)
    init_database(engine)
    session_factory = create_session_factory(engine)
    session = session_factory()

    try:
        dup_repo = SqliteDuplicateCandidateRepository(session)
        track_repo = SqliteTrackRepository(session)

        candidates = dup_repo.list_unresolved(limit=limit)
        if not candidates:
            console.print("[green]No unresolved duplicate candidates found.[/green]")
            return

        table = Table(title=f"Probable Duplicate Candidates ({len(candidates)} pending)")
        table.add_column("Candidate ID", style="dim")
        table.add_column("Existing Track (A)", style="cyan")
        table.add_column("New Track (B)", style="yellow")
        table.add_column("Confidence", style="magenta")
        table.add_column("Reason", style="white")

        for cand in candidates:
            track_a = track_repo.get_by_id(cand.track_id_a)
            track_b = track_repo.get_by_id(cand.track_id_b)

            title_a = track_a.title if track_a else str(cand.track_id_a)[:8]
            title_b = track_b.title if track_b else str(cand.track_id_b)[:8]

            table.add_row(
                str(cand.id)[:8],
                title_a,
                title_b,
                cand.confidence,
                cand.reason,
            )

        console.print(table)
    finally:
        session.close()
        engine.dispose()


@duplicates_app.command("resolve", help="Resolve a duplicate candidate as 'merge' or 'distinct'.")
def resolve_duplicates_cmd(
    candidate_id: str = typer.Argument(..., help="Full or prefix UUID of candidate."),
    action: str = typer.Option(
        ...,
        "--action",
        "-a",
        help="Resolution action: 'merge' (attach files to Track A) or 'distinct' (keep separate).",
    ),
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file.")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file.")] = None,
) -> None:
    """Resolve a duplicate candidate."""
    action_clean = action.lower().strip()
    if action_clean not in {"merge", "distinct"}:
        console.print("[red]Error: --action must be 'merge' or 'distinct'[/red]")
        raise typer.Exit(code=1)

    config = HarmoniaConfig.load(config_path)
    config.ensure_directories()
    effective_db = db_path or config.database.path

    engine = create_database_engine(effective_db)
    init_database(engine)
    session_factory = create_session_factory(engine)
    session = session_factory()

    try:
        dup_repo = SqliteDuplicateCandidateRepository(session)
        track_repo = SqliteTrackRepository(session)
        search_repo = SqliteSearchRepository(session)

        # Allow matching full or 8-char prefix ID
        target_candidate = None
        for cand in dup_repo.list_unresolved(limit=1000):
            if str(cand.id).startswith(candidate_id):
                target_candidate = cand
                break

        if target_candidate is None:
            console.print(f"[red]Candidate '{candidate_id}' not found or already resolved.[/red]")
            raise typer.Exit(code=1)

        if action_clean == "distinct":
            target_candidate.mark_resolved("distinct")
            dup_repo.update(target_candidate)
            session.commit()
            console.print("[green][OK][/green] Candidate marked as distinct tracks.")
            return

        if action_clean == "merge":
            # Move all physical files from Track B to Track A
            files_b = track_repo.get_files(target_candidate.track_id_b)
            for fb in files_b:
                fb.track_id = target_candidate.track_id_a
                track_repo.update_file(fb)

            # Remove Track B from search and database
            search_repo.remove_track(target_candidate.track_id_b)
            track_repo.delete(target_candidate.track_id_b)

            target_candidate.mark_resolved("merged")
            dup_repo.update(target_candidate)
            session.commit()
            console.print(
                "[green][OK][/green] Merged Track B into Track A (files transferred, candidate resolved)."
            )
    finally:
        session.close()
        engine.dispose()
