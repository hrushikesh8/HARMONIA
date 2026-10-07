"""Content-addressed artwork serving endpoint."""

from __future__ import annotations

from typing import TYPE_CHECKING

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse

from harmonia.api.dependencies import get_config, verify_api_token

if TYPE_CHECKING:
    from harmonia.core.config import HarmoniaConfig

router = APIRouter(prefix="/artwork", tags=["Artwork"], dependencies=[Depends(verify_api_token)])


@router.get("/{art_hash}")
def get_artwork(
    art_hash: str,
    config: HarmoniaConfig = Depends(get_config),
) -> FileResponse:
    """Serve album cover artwork by content-addressed SHA-256 hash."""
    art_dir = config.artwork.path
    if not art_dir.exists():
        raise HTTPException(status_code=404, detail="Artwork storage not configured or empty.")

    # Match files starting with art_hash
    matching = list(art_dir.glob(f"{art_hash}.*"))
    if not matching or not matching[0].is_file():
        raise HTTPException(status_code=404, detail=f"Artwork with hash '{art_hash}' not found.")

    image_path = matching[0]
    media_type = "image/jpeg"
    if image_path.suffix.lower() == ".png":
        media_type = "image/png"
    elif image_path.suffix.lower() == ".webp":
        media_type = "image/webp"

    return FileResponse(
        path=image_path,
        media_type=media_type,
        headers={
            "Cache-Control": "public, max-age=31536000, immutable",
            "ETag": f'"{art_hash}"',
        },
    )
