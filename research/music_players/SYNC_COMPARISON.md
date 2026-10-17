# Harmonia Research: Synchronization & Networking Comparison

**Document Classification:** Architectural Research Specification  
**Status:** Complete  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Scope:** Local network discovery, zero-cloud peer-to-peer file transfer, delta catalog synchronization, conflict resolution, and offline resilience across 20 media projects.

---

## 1. Network & Sync Architectures Across Projects

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                             SYNC ARCHITECTURE MATRIX                                        │
├──────────────┬──────────────────┬─────────────────┬─────────────────────────────────────────┤
│ Project      │ Protocol / Base  │ Transport       │ Discovery & Security Model              │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ LocalSend    │ Custom JSON v2   │ HTTPS (TLS)     │ Multicast UDP / mDNS;                   │
│              │ REST API         │ Chunked Streams │ Dynamic self-signed X.509 certs         │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ Finamp       │ Jellyfin REST    │ HTTP(S)         │ Manual IP / URL; token auth;            │
│              │ + Isar Download  │ Background Task │ Offline SQLite/Isar cache mirror        │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ Phoebe       │ Subsonic/Plex/   │ HTTP(S)         │ Manual URL / Plex PIN; paged catalog    │
│              │ Navidrome REST   │ Token Auth      │ sync into SQLDelight                    │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ Harmonia     │ Harmonia Local   │ HTTP/2 (TLS)    │ mDNS (`_harmonia._tcp`); QR / PIN pair; │
│ Target       │ Sync Protocol    │ Delta JSON +    │ Strict loopback/local-only; Ed25519     │
│              │ (LocalSend-style)│ Chunked Range   │ auth tokens; SHA-256 stream integrity   │
└──────────────┴──────────────────┴─────────────────┴─────────────────────────────────────────┘
```

---

## 2. Deep Project Sync Analysis

### 2.1 [LocalSend](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/localsend-main): The Gold Standard for Local P2P Transfer

LocalSend demonstrates how to move gigabytes of media across phones, laptops, and workstations over a local Wi-Fi network without third-party servers, accounts, or internet connectivity.

#### 1. Discovery Mechanism ([`nearby_devices_provider.dart`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/localsend-main/app/lib/provider/network/nearby_devices_provider.dart)) `[SOURCE VERIFIED]`
- Uses UDP multicast on `224.0.0.167:53317` alongside mDNS.
- Periodically broadcasts a lightweight discovery beacon announcing:
  - `alias`: Human-readable device name (e.g. *"Hrushikesh's Laptop"*).
  - `deviceModel`: Platform identifier (e.g. *"Windows"*, *"Android"*).
  - `fingerprint`: Stable UUID hash to identify the instance across IP changes.
  - `port`: Local HTTP server port.
  - `protocol`: Version (`v2`).
- Listening devices build a dynamic local network routing table in memory.

#### 2. Cryptographic Security & Zero-Config TLS
- Generates dynamic, self-signed X.509 TLS certificates in memory on app startup.
- All HTTP transfers execute strictly over HTTPS (`https://192.168.x.x:53317`).
- Eliminates cleartext sniffing of filenames or file content on shared public Wi-Fi networks (coffee shops, universities).
- Device pairing is authenticated via optional PIN verification or QR code hash comparison.

#### 3. Two-Phase Handshake Transfer Protocol ([`send_provider.dart`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/localsend-main/app/lib/provider/network/send_provider.dart)) `[SOURCE VERIFIED]`
- **Phase 1: Prepare / Manifest Exchange:**
  - Sender sends `POST /api/localsend/v2/prepare-upload` with file metadata array: `fileName`, `fileSize`, `fileType`, `sha256Token`.
  - Receiver presents an approval dialog or automatically accepts if the sending device is marked as a `FavoriteDevice`.
  - Receiver returns a session token and an accepted file ID map (allowing receiver to reject certain files if storage is low).
- **Phase 2: Stream Transfer & Verification:**
  - Sender initiates `POST /api/localsend/v2/upload?sessionId=...&fileId=...`.
  - Files are streamed as multipart chunks.
  - SHA-256 stream digests are calculated concurrently during write and compared against the manifest upon EOF.
  - Aborted transfers do not leave orphan corrupt files; partial downloads use `.part` extension and are cleaned up on failure.

---

### 2.2 [Finamp](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/finamp-redesign): Offline Sync & Download State Machine

Finamp handles the client-server relationship between a desktop/home media server (Jellyfin) and a mobile music player.

#### 1. Download State Lifecycle ([`downloads_service.dart`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/finamp-redesign/lib/services/downloads_service.dart#L40)) `[SOURCE VERIFIED]`
- Explicit state enum per media item:
  - `missing`: Item is known in catalog, but audio file is not downloaded.
  - `queued`: Enqueued in persistent download queue.
  - `downloading`: Active HTTP download stream.
  - `complete`: Audio file stored in local scoped directory and verified.
  - `failed`: Download failed (network dropout or server error).
- UI uses reactive streams (`watchObject`) to show download progress spinners on track and album rows.

#### 2. Resilience to Network & Storage Failures:
- **`_fileSystemFull` Flag:** Detects low disk space before initiating large audio downloads; stops queue to prevent OS crash.
- **Consecutive Connection Errors:** If $>10$ network errors occur consecutively, pauses background sync worker for an exponential backoff period.
- **Blurhash Caching:** Downloads compact Blurhash strings alongside album metadata. When offline, albums render smooth colored gradient placeholders instantly before high-res image files load from cache.

---

## 3. Harmonia LocalSync Protocol Specification

Harmonia must reject cloud servers. Synchronization between **Harmonia Desktop (Core Archive)** and **Harmonia Mobile (Sovereign Player)** must be peer-to-peer, local-first, and deterministic.

### 3.1 Device Identity & Pairing

```
┌────────────────────────────────────────────────────────────────────────┐
│                   HARMONIA PEER-TO-PEER PAIRING FLOW                   │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
 [Desktop Core] generates Ed25519 keypair & displays pairing QR code:
 { "device_id": "UUID", "name": "Workstation", "ip": "192.168.1.50",
   "port": 8000, "pubkey": "base64...", "fingerprint": "hex..." }
                                   │
                                   ▼
 [Mobile Player] scans QR code via camera (or discovers via mDNS):
 Sends pairing request: POST /api/v1/sync/pair
 { "client_device_id": "UUID", "client_pubkey": "base64..." }
                                   │
                                   ▼
 Mutual signature verification; both devices store paired public key in SQLite.
 All future sync requests require Ed25519 cryptographic HTTP header signatures.
```

---

### 3.2 What Syncs and What Does Not

```
┌────────────────────────────────────────────────────────────────────────┐
│                        SYNC ENTITY BOUNDARIES                          │
├────────────────────┬───────────────────────────────────────────────────┤
│ Synchronized       │ • Track metadata deltas (UUID, title, artist,     │
│ Bidirectionally    │   album, track/disc number, genre, year)          │
│                    │ • User playlists & playlist order                 │
│                    │ • Favorites & star ratings                        │
│                    │ • Listening events & play counts (accumulated)    │
│                    │ • User-applied tags & field-level overrides       │
├────────────────────┼───────────────────────────────────────────────────┤
│ Synchronized       │ • Transcoded / cached audio files (selected by    │
│ Desktop -> Mobile  │   user or playlist download rule)                 │
│                    │ • Deduplicated album artwork (content-addressed)  │
│                    │ • Objective LUFS analysis metrics for ReplayGain  │
│                    │ • Synchronized lyrics (.lrc / .ttml files)        │
├────────────────────┼───────────────────────────────────────────────────┤
│ NEVER Synchronized │ • Internal absolute filesystem paths (C:\..., /..)│
│                    │ • Temporary decode caches or worker job states    │
│                    │ • Platform-specific audio sink configurations     │
│                    │ • Harmonia AI subjective taste models (kept local)│
└────────────────────┴───────────────────────────────────────────────────┘
```

---

### 3.3 Conflict Resolution Strategy

Harmonia avoids distributed consensus complexity (Paxos/Raft) by enforcing **Deterministic Domain Rules**:

1. **Listening History & Play Counts:** **Monotonically Additive**. If Mobile has 5 plays and Desktop has 3 plays since last sync, total plays $= 8$. Both devices merge play event timestamps into `ListeningEvent` table.
2. **Metadata Overrides:** **Field-Level Last-Write-Wins (LWW)** using `updated_at` UTC timestamps. A user edit on Mobile at 10:15 overrides a Desktop edit from 09:30 for that specific field only.
3. **Playlists:** **Deterministic Tombstones**. Track removals insert a soft-delete tombstone record with timestamp, preventing a deleted track from "resurrecting" during bidirectional merge.
4. **Audio File Integrity:** High-fidelity master files reside on Desktop Core. Mobile requests files by `file_hash` (SHA-256). Desktop streams the file; Mobile verifies SHA-256 upon reception before adding to local storage.
