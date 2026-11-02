# Harmonia LocalSync Protocol Specification (v0.1-draft)

**Document Identifier:** `SPEC-LOCALSYNC-001`  
**Classification:** Normative Protocol Specification  
**Status:** `NORMATIVE SPECIFICATION — UNIMPLEMENTED`  
**Derived From:** Milestone `M6-Spec` (Architecture Review Rounds `M6-R1` through `M6-R14`)  
**Ecosystem Scope:** Cross-Platform Protocol (Desktop $\leftrightarrow$ Mobile Peer Synchronization)  

---

## 1. Executive Summary & Core Invariants

The **Harmonia LocalSync Protocol** defines a secure, decentralized, zero-cloud peer-to-peer synchronization mechanism designed to synchronize library state, playlists, favorites, ratings, and playback queue snapshots directly over local networks (LAN / Wi-Fi) without third-party mediation or external telemetry.

### Core Protocol Invariants
1. **Zero Cloud Dependency**: Synchronization occurs exclusively within local subnet boundaries. No central relay, cloud database, or external discovery broker is permitted.
2. **End-to-End Cryptographic Identity**: Every Harmonia node (Desktop or Mobile) generates an Ed25519 cryptographic keypair during initial initialization. Peer identity is verified via mutual TLS 1.3 with pinned public keys or out-of-band QR pairing.
3. **Deterministic Conflict Resolution**: State replication utilizes state-based Conflict-Free Replicated Data Types (CRDTs) and Merkle DAG trees with Last-Write-Wins (LWW) timestamping and deterministic byte tie-breaking based on peer public keys.
4. **Soft Deletions & Tombstones**: Deletions are propagated via tombstone records retained for a minimum garbage collection window of 90 days to prevent resurrected records across intermittently connected peers.
5. **Lossless Audio Streaming Excluded from Sync Payloads**: Protocol payloads synchronize metadata, identifiers, and delta operations only. Heavy binary audio payloads remain addressed by the existing streaming architecture (`RFC 7233`).

---

## 2. Peer Discovery & Transport Layer

### 2.1 Multicast DNS (mDNS) Discovery
Peers advertise their presence on the local link using standard Multicast DNS (`RFC 6762`) and DNS-Based Service Discovery (`RFC 6763`):

- **Service Type**: `_harmonia-sync._tcp.local.`
- **Port**: Configurable ephemeral port (default `8443`).
- **TXT Record Attributes**:
  - `v=1`: Protocol specification version.
  - `node_id=<base64-ed25519-public-key>`: Globally unique peer identifier.
  - `dev_name=<human-readable-string>`: Friendly name (e.g., "Pixel 8 Pro", "Studio Desktop").
  - `proto=tls13`: Required transport encryption level.
  - `sync_state=<merkle-root-hash>`: 32-byte BLAKE3 root hash of the local sync catalog.

### 2.2 Secure Transport (Mutual TLS 1.3)
Once discovered, peer communication requires a direct TLS 1.3 connection:
- Cipher suites: Restricted to `TLS_AES_256_GCM_SHA384` and `TLS_CHACHA20_POLY1305_SHA256`.
- Authentication: Mutual TLS (mTLS) utilizing X.509 certificates containing the peer's Ed25519 public key.
- Pairing Ceremony: First-contact pairing requires operator confirmation via a short 6-digit SAS (Short Authentication String) or QR code exchange.

---

## 3. Synchronization Engine Architecture

```
┌────────────────────────────────────────────────────────┐
│                   Local Node DB                        │
│   (Desktop SQLite WAL / Mobile Room v5 Database)       │
└──────────────────────────┬─────────────────────────────┘
                           │ Change Trigger / CDC
                           ▼
┌────────────────────────────────────────────────────────┐
│             Sync Change-Log (Delta Store)             │
│   - entity_type (PLAYLIST, FAVORITE, RATING, QUEUE)    │
│   - entity_id (UUID)                                   │
│   - op_type (INSERT, UPDATE, DELETE/TOMBSTONE)         │
│   - hlc_timestamp (Hybrid Logical Clock)               │
│   - author_node_id (Ed25519 Public Key)                │
└──────────────────────────┬─────────────────────────────┘
                           │ Merkle Tree Rollup
                           ▼
┌────────────────────────────────────────────────────────┐
│             Hierarchical Merkle Tree                   │
│   - Root: BLAKE3 hash of complete change history       │
│   - Level 1: Subtree per entity domain                 │
│   - Leaf: Individual delta hash                        │
└──────────────────────────┬─────────────────────────────┘
                           │ Peer Handshake
                           ▼
┌────────────────────────────────────────────────────────┐
│             Peer Sync Protocol (REST/RPC)              │
│   1. Exchange Merkle Roots                             │
│   2. If match -> Sync Complete (0 delta transfer)      │
│   3. If mismatch -> Traverse subtrees to identify diff │
│   4. Request missing deltas                            │
│   5. Apply deltas in local transaction & commit        │
└────────────────────────────────────────────────────────┘
```

---

## 4. Entity Synchronization Specifications

### 4.1 Favorites (`favorites`)
- **Key**: `(entity_type, entity_id)`
- **Payload**: `created_at`, `source`
- **Conflict Rule**: In the event of a simultaneous add and delete across peers without causality, the *Add* wins (Add-Wins Observed-Remove Set pattern).

### 4.2 User Ratings (`ratings`)
- **Key**: `(entity_type, entity_id)`
- **Payload**: `rating_value`, `updated_at`
- **Conflict Rule**: Last-Write-Wins (LWW) based on Hybrid Logical Clock (HLC). If timestamps are identical to microsecond precision, the peer with the lexicographically higher Ed25519 public key wins.

### 4.3 Playlists & Playlist Items (`playlists`, `playlist_items`)
- **Playlists**: Synchronized as entity records with title, description, and smart query rules.
- **Playlist Items (Ordering)**: Fractional indexing / lexicographical ordering keys (e.g., `a0`, `a1`, `a05`) are utilized for `position` to allow concurrent insertions between existing tracks without requiring renumbering of subsequent items.

---

## 5. Architectural Review History (Rounds M6-R1 through M6-R14)

The protocol design was formulated across 14 formal architectural review rounds during subphase `M6-Spec` (Conversation `7fefe611`, Step 4112):

| Review Round | Focus Area | Architectural Decision / Invariant Established |
|---|---|---|
| **M6-R1** | Topology Selection | Rejected central relay architecture; mandated pure zero-cloud LAN peer topology. |
| **M6-R2** | Peer Discovery | Evaluated UPnP vs. mDNS; selected standard mDNS (`RFC 6762`) with TXT state hashes. |
| **M6-R3** | Cryptographic Identity | Mandated Ed25519 keys generated at install; rejected central account/password models. |
| **M6-R4** | Transport Layer Security | Specified mutual TLS 1.3 with ephemeral certificate pinning and QR SAS pairing. |
| **M6-R5** | Sync Data Structures | Formulated change-log delta store with BLAKE3 Merkle tree hierarchical rollups. |
| **M6-R6** | Clocks & Causality | Replaced wall-clock synchronization with Hybrid Logical Clocks (HLC) to tolerate clock skew. |
| **M6-R7** | Favorites Sync Rules | Designed Add-Wins Observed-Remove Set (AWORSet) for favorites semantics. |
| **M6-R8** | Ratings Sync Rules | Specified Last-Write-Wins Register with deterministic public-key tie breaking. |
| **M6-R9** | Playlist Ordering | Adopted fractional lexicographical indexing to prevent insertion collisions. |
| **M6-R10** | Tombstone Lifecycle | Established 90-day tombstone retention window before automated garbage collection. |
| **M6-R11** | Network Partitioning | Verified offline-first divergence recovery after prolonged network disconnection. |
| **M6-R12** | Heavy Media Boundary | Explicitly excluded binary audio files from sync stream; metadata and state only. |
| **M6-R13** | Database Transactions | Specified atomic change-log emission via database triggers in SQLite / Room. |
| **M6-R14** | Protocol Formalization | Synthesized normative protocol specification; frozen as `SPEC-LOCALSYNC-001`. |

---

## 6. Implementation Status & Roadmap Boundary

> [!WARNING]
> **Implementation Status: NORMATIVE SPECIFICATION ONLY**  
> The LocalSync protocol represents an authoritative architectural specification. No networking, socket listeners, mDNS daemon, or sync engines are currently implemented in either repository. Implementation is targeted for future roadmap phases following the initial product review cycle.
