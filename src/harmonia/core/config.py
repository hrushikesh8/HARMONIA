"""Configuration management for Harmonia.

Configuration is loaded from (in priority order):
1. Environment variables (HARMONIA_ prefix)
2. .env file
3. config/default.toml
"""

from __future__ import annotations

import tomllib
from pathlib import Path
from typing import Any

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


def _load_toml_defaults(toml_path: Path) -> dict[str, Any]:
    """Load default values from TOML config file."""
    if toml_path.exists():
        with open(toml_path, "rb") as f:
            return tomllib.load(f)
    return {}


class DatabaseConfig(BaseSettings):
    """Database configuration."""

    model_config = SettingsConfigDict(env_prefix="HARMONIA_DB_")

    path: Path = Path("data/harmonia.db")


class ImportConfig(BaseSettings):
    """Import pipeline configuration."""

    model_config = SettingsConfigDict(env_prefix="HARMONIA_IMPORT_")

    supported_formats: list[str] = Field(
        default=[
            ".mp3", ".flac", ".m4a", ".ogg", ".opus",
            ".wav", ".aac", ".wma", ".alac", ".aiff",
        ],
    )
    follow_symlinks: bool = False
    compute_hashes: bool = True


class ArtworkConfig(BaseSettings):
    """Artwork storage configuration."""

    model_config = SettingsConfigDict(env_prefix="HARMONIA_ARTWORK_")

    path: Path = Path("data/artwork")
    extract_on_import: bool = True


class LoggingConfig(BaseSettings):
    """Logging configuration."""

    model_config = SettingsConfigDict(env_prefix="HARMONIA_LOG_")

    level: str = "INFO"
    file: Path | None = None

    @field_validator("level")
    @classmethod
    def _validate_level(cls, v: str) -> str:
        valid = {"DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"}
        upper = v.upper()
        if upper not in valid:
            raise ValueError(f"Invalid log level: {v}. Must be one of {valid}")
        return upper


class HarmoniaConfig(BaseSettings):
    """Root configuration for Harmonia.

    Usage::

        config = HarmoniaConfig.load()
    """

    model_config = SettingsConfigDict(
        env_prefix="HARMONIA_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    music_dirs: list[Path] = Field(default_factory=list)
    database: DatabaseConfig = Field(default_factory=DatabaseConfig)
    import_: ImportConfig = Field(default_factory=ImportConfig, alias="import")
    artwork: ArtworkConfig = Field(default_factory=ArtworkConfig)
    logging: LoggingConfig = Field(default_factory=LoggingConfig)

    @classmethod
    def load(cls, config_path: Path | None = None) -> HarmoniaConfig:
        """Load configuration from TOML file, environment, and .env.

        Args:
            config_path: Path to TOML config. Defaults to config/default.toml.
        """
        toml_path = config_path or Path("config/default.toml")
        defaults = _load_toml_defaults(toml_path)

        # Flatten TOML sections into nested model kwargs.
        kwargs: dict[str, Any] = {}

        if "database" in defaults:
            kwargs["database"] = DatabaseConfig(**defaults["database"])

        if "import" in defaults:
            kwargs["import"] = ImportConfig(**defaults["import"])

        if "artwork" in defaults:
            kwargs["artwork"] = ArtworkConfig(**defaults["artwork"])

        if "logging" in defaults:
            kwargs["logging"] = LoggingConfig(**defaults["logging"])

        if "library" in defaults:
            music_dirs_raw = defaults["library"].get("music_dirs", [])
            kwargs["music_dirs"] = [Path(d) for d in music_dirs_raw]

        return cls(**kwargs)

    def ensure_directories(self) -> None:
        """Create required data directories if they don't exist."""
        self.database.path.parent.mkdir(parents=True, exist_ok=True)
        self.artwork.path.mkdir(parents=True, exist_ok=True)
