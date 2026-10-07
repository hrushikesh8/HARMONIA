"""Serve command to launch the Harmonia HTTP REST API."""

from __future__ import annotations

from pathlib import Path
from typing import Annotated

import typer
import uvicorn
from rich.console import Console

from harmonia.api.app import create_app
from harmonia.core.config import HarmoniaConfig

console = Console()


def serve_cmd(
    host: Annotated[str | None, typer.Option("--host", "-h", help="Bind host (default: 127.0.0.1)")] = None,
    port: Annotated[int | None, typer.Option("--port", "-p", help="Bind port (default: 8000)")] = None,
    config_path: Annotated[Path | None, typer.Option("--config", "-c", help="Path to config TOML file")] = None,
    reload: Annotated[bool, typer.Option("--reload", help="Enable auto-reload for development")] = False,
) -> None:
    """Start the Harmonia local-first HTTP REST API server."""
    config = HarmoniaConfig.load(config_path)
    bind_host = host or config.api.host
    bind_port = port or config.api.port

    console.print(f"[bold cyan]Starting Harmonia API on[/bold cyan] http://{bind_host}:{bind_port}")
    if bind_host not in ("127.0.0.1", "localhost", "::1"):
        console.print("[bold yellow][WARNING][/bold yellow] Binding to non-loopback interface!")

    app = create_app(config)
    uvicorn.run(app, host=bind_host, port=bind_port, reload=reload)
