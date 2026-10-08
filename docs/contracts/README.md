# Harmonia Mobile Integration Contracts

**Version:** 1.0.0  
**Target Consumer:** `harmonia-mobile` (Kotlin Multiplatform / Android / iOS)  
**Provider:** `harmonia` (Core Python/FastAPI)

---

## 1. Overview

This directory provides the published integration contracts and specifications required by the Harmonia Mobile client application.

Harmonia follows a contract-first decoupling architecture:
- **Core Server** publishes its OpenAPI 3.1 specification and vector brand assets.
- **Mobile Client** consumes these schemas to generate network clients (e.g. Ktor) and design tokens, without sharing code repository dependencies.

---

## 2. API Schema Contract

- **File:** [`openapi.json`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/docs/contracts/openapi.json)
- **Specification:** OpenAPI 3.1.0
- **Primary Service Endpoints for Mobile:**
  - `GET /api/v1/tracks` — Track catalog with pagination and sorting.
  - `GET /api/v1/tracks/{track_id}` — Detailed track inspection, including tags and audio analysis metrics.
  - `GET /api/v1/albums` & `GET /api/v1/albums/{album_id}` — Album browsing.
  - `GET /api/v1/artists` & `GET /api/v1/artists/{artist_id}` — Artist catalog.
  - `GET /api/v1/search` — FTS5 full-text search across tracks, albums, and artists.
  - `GET /api/v1/artwork/{artwork_hash}` — SHA-256 content-addressed artwork binary streaming.
  - `GET /api/v1/stream/{file_id}` — RFC 7233 byte-range audio streaming (`Range: bytes=start-end`) for offline downloading.
  - `GET /api/v1/health` — Subnet connectivity and health validation.

---

## 3. Brand Assets Contract

Canonical brand identity assets are maintained in [`assets/brand/`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/assets/brand):

| Asset | Format | Usage in Mobile App |
| :--- | :--- | :--- |
| `harmonia-mark.svg` | Scalable Vector | Monogram mark in top app bar, splash screen, and watermarks |
| `harmonia-lockup-horizontal.svg` | Scalable Vector | Header brand lockup |
| `harmonia-lockup-compact.svg` | Scalable Vector | Compact splash hero and dialogs |
| `harmonia-monochrome-white.svg` | Scalable Vector | Dark mode status and notification icons |
| `harmonia-monochrome-black.svg` | Scalable Vector | High contrast / light monochrome surfaces |
| `icon-512x512.png` | PNG Raster (512x512) | Master Android adaptive launcher icon (squircle safe zone) |
| `favicon.svg` | Scalable Vector | Web / fallback vector icon |

### Visual Identity Reference
Detailed color tokens, typography rules (`tnum` tabular figures), safe-zone padding, and dark palette specifications are defined in [`docs/BRAND_IDENTITY.md`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/docs/BRAND_IDENTITY.md).

---

## 4. Architectural Governing Document
The complete system architecture for the native mobile player is specified in:
- [`docs/MOBILE_ARCHITECTURE_v0.1.md`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/docs/MOBILE_ARCHITECTURE_v0.1.md)
