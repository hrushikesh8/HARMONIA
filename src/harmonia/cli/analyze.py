"""Audio analysis CLI command for Harmonia."""

from __future__ import annotations

from pathlib import Path
from typing import Annotated
from uuid import UUID

import typer
from rich.console import Console
from rich.table import Table

from harmonia.application.audio_analysis_service import AudioAnalysisService
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.domain.ports import TranscodeClassification
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

console = Console()


def analyze_cmd(
    target: Annotated[str | None, typer.Argument(help="UUID of Track or TrackFile to analyze (or omit with --all)")] = None,
    all_: Annotated[bool, typer.Option("--all", "-a", help="Analyze all audio files in the library")] = False,
    force: Annotated[bool, typer.Option("--force", "-f", help="Force re-analysis even if already completed and fresh")] = False,
    check_transcode: Annotated[
        bool,
        typer.Option("--check-transcode", help="Check for suspicious spectral cutoffs characteristic of lossy upconverts"),
    ] = False,
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file")] = None,
    db_path: Annotated[Path | None, typer.Option("--db", help="Path to SQLite database file")] = None,
) -> None:
    """Run objective signal analysis on audio files in the Harmonia library."""
    if not target and not all_:
        console.print("[bold red]Please specify a track/file UUID or pass --all to analyze library.[/bold red]")
        raise typer.Exit(code=1)

    config = HarmoniaConfig.load(config_path)
    config.ensure_directories()
    effective_db = db_path or config.database.path

    engine = create_database_engine(effective_db)
    init_database(engine)
    session_factory = create_session_factory(engine)
    service = AudioAnalysisService(session_factory=session_factory)

    try:
        files_to_analyze: list[UUID] = []
        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            if all_:
                tracks = track_repo.get_all(limit=10000)
                for t in tracks:
                    files = track_repo.get_files(t.id)
                    for f in files:
                        files_to_analyze.append(f.id)
            else:
                assert target is not None
                try:
                    uid = UUID(target)
                    tf = track_repo.get_file_by_id(uid)
                    if tf:
                        files_to_analyze.append(tf.id)
                    else:
                        files = track_repo.get_files(uid)
                        if files:
                            for f in files:
                                files_to_analyze.append(f.id)
                        else:
                            console.print(f"[bold red]No track or file found with ID:[/bold red] {uid}")
                            raise typer.Exit(code=1)
                except ValueError:
                    # Check if target is a registered file path
                    resolved_path = str(Path(target).resolve())
                    tf = track_repo.get_file_by_path(resolved_path) or track_repo.get_file_by_path(target)
                    if tf:
                        files_to_analyze.append(tf.id)
                    else:
                        console.print(f"[bold red]Target is neither a valid UUID nor a registered file path:[/bold red] {target}")
                        raise typer.Exit(code=1) from None

        if not files_to_analyze:
            console.print("[yellow]No audio files found to analyze.[/yellow]")
            return

        if check_transcode:
            console.print(f"[bold cyan]Checking {len(files_to_analyze)} file(s) for suspicious spectral cutoffs...[/bold cyan]")
            table = Table(title="Spectral Cutoff / Transcode Analysis", show_header=True, header_style="bold magenta")
            table.add_column("File ID", style="dim")
            table.add_column("Classification", style="bold")
            table.add_column("Cutoff Freq", justify="right")
            table.add_column("Drop Slope", justify="right")
            table.add_column("Upper HF Ratio", justify="right")
            table.add_column("Details", style="dim")

            with console.status("[bold green]Evaluating spectral cutoffs...[/bold green]"):
                for fid in files_to_analyze:
                    res = service.check_file_transcode(fid)
                    if res.classification == TranscodeClassification.SUSPICIOUS_CUTOFF:
                        class_style = "[bold red]SUSPICIOUS_CUTOFF[/bold red]"
                    elif res.classification == TranscodeClassification.NO_SUSPICIOUS_CUTOFF:
                        class_style = "[bold green]NO_SUSPICIOUS_CUTOFF[/bold green]"
                    elif res.classification == TranscodeClassification.INSUFFICIENT_EVIDENCE:
                        class_style = "[yellow]INSUFFICIENT_EVIDENCE[/yellow]"
                    else:
                        class_style = "[dim red]ANALYSIS_UNAVAILABLE[/dim red]"

                    cutoff_str = f"{res.cutoff_frequency_hz:.0f} Hz" if res.cutoff_frequency_hz is not None else "-"
                    slope_str = f"{res.cutoff_steepness_db_per_khz:.1f} dB/kHz" if res.cutoff_steepness_db_per_khz is not None else "-"
                    ratio_str = f"{res.high_frequency_ratio_db:.1f} dB" if res.high_frequency_ratio_db is not None else "-"

                    table.add_row(
                        str(fid)[:8] + "...",
                        class_style,
                        cutoff_str,
                        slope_str,
                        ratio_str,
                        res.details,
                    )
            console.print(table)
            return

        console.print(f"[bold cyan]Analyzing {len(files_to_analyze)} audio file(s)...[/bold cyan]")
        table = Table(title="Audio Analysis Results", show_header=True, header_style="bold magenta")
        table.add_column("File ID", style="dim")
        table.add_column("Status", style="bold")
        table.add_column("Integrated LUFS", justify="right")
        table.add_column("True Peak", justify="right")
        table.add_column("LRA", justify="right")
        table.add_column("Peak-to-Loudness", justify="right")
        table.add_column("Clipped?", justify="center")

        with console.status("[bold green]Running signal measurements (ebur128)...[/bold green]"):
            for fid in files_to_analyze:
                res = service.analyze_file(fid, force=force)
                status_style = "[green]COMPLETED[/green]" if res.status.value == "COMPLETED" else f"[red]{res.status.value}[/red]"
                table.add_row(
                    str(fid)[:8] + "...",
                    status_style,
                    f"{res.integrated_lufs:.1f} LUFS" if res.integrated_lufs is not None else "-",
                    f"{res.true_peak_dbtp:.1f} dBTP" if res.true_peak_dbtp is not None else "-",
                    f"{res.loudness_range_lu:.1f} LU" if res.loudness_range_lu is not None else "-",
                    f"{res.peak_to_loudness_db:.1f} dB" if res.peak_to_loudness_db is not None else "-",
                    "[red]YES[/red]" if res.is_clipped else "[green]No[/green]",
                )

        console.print(table)
    finally:
        engine.dispose()
