# Incident & Defect Record: INC-002 Concurrent Scan Lock Contention & Scanner Deadlock

## 1. Incident Overview
- **Incident ID:** `INC-002`
- **Date Detected:** 2026-10-10 *(Reconstructed / Inferred)* `[INFERRED-HIST]`
- **Target Subsystem:** Harmonia Mobile / Storage Scanner (`harmonia-mobile/app/`)
- **Milestone / Phase:** Milestone M4-E / M4-F Hardening
- **Severity Classification:** **HIGH**
- **Investigation Status:** **RESOLVED & VERIFIED**
- **Confidence Rating:** `[VERIFIED-SRC]`

---

## 2. Symptoms & User/System Impact
- **Observed Behavior:**
  When a user triggered a manual "Rescan" in Settings or Library UI while an automatic storage volume scan was running in the background, multiple callers initiated uncoordinated disk crawling and database write operations for the same storage source simultaneously. While early engineering notes highlighted the preventive risk of `SQLiteDatabaseLockedException` under concurrent WAL write transactions, the verified behavior was uncoordinated lock contention, scanner competition, and UI jank `[REPORT-BACKED]`.
- **User Impact:**
  The UI exhibited jank during scanning, and competing scans for the same storage volume caused redundant disk I/O and uncoordinated database updates.
- **Reproduction Steps:**
  1. Attach a large storage source (10,000+ audio files).
  2. Initiate an automated boot/volume-mount scan.
  3. Immediately navigate to Settings or Library and pull to refresh or tap "Rescan Library".
  4. Multiple scanning coroutines compete for SQLite write locks and file handles on the same source simultaneously.

---

## 3. Root Cause Analysis
- **Confirmed Root Cause (Evidence-Backed):**
  [`DefaultMediaReconciliationEngine.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/scanner/DefaultMediaReconciliationEngine.kt) was invoked directly by multiple callers without a centralized concurrency coordinator. Each caller spawned an independent coroutine on `Dispatchers.IO` executing long-running transactional writes against SQLite. Because SQLite in WAL mode permits concurrent readers but only one writer, overlapping transactions contended for database write access.
- **Hypotheses Considered & Disproven:**
  - *Hypothesis A: SAF file crawler was stuck in recursive symlink loop:* Disproven. File crawling completed, but database batch insertion was blocked.
  - *Hypothesis B: SQLite WAL pragma was misconfigured:* Disproven. WAL mode was enabled; the issue was uncoordinated concurrent transaction attempts from multiple callers.

---

## 4. Corrective Action & Implementation
- **Remediation Summary:**
  Introduced [`SourceScanCoordinator.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/domain/scanner/SourceScanCoordinator.kt) as a concurrency coordinator managing scan serialization.
- **Code Modifications:**
  - `SourceScanCoordinator.kt`: Maintains a `ConcurrentHashMap<String, Mutex>` mapping each storage source ID to a dedicated `Mutex`.
  - Per-Source Serialization: Enforces `withSourceLock(sourceId) { action() }` via `Mutex.withLock`. Callers attempting to scan the same source ID are serialized sequentially, while scans targeting distinct storage source IDs execute concurrently `[VERIFIED-SRC]`.
  - Cancellation Safety: Using Kotlin coroutines `Mutex.withLock` ensures the mutex is automatically released if the calling coroutine scope is cancelled or throws an exception.
- **Alternatives Rejected:**
  - *Global Single-Mutex Serialization:* Rejected. Serializing all scans across the entire application needlessly blocks scanning separate, independent storage volumes concurrently.
  - *Unbounded Concurrent Execution:* Rejected. Direct multi-caller invocation without locks risks SQLite write transaction contention.

---

## 5. Regression Prevention & Invariant Protection
- **Regression Test Suite:**
  - Test Files: [`SourceScanCoordinatorTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/domain/scanner/SourceScanCoordinatorTest.kt), [`CrossCallerEngineScanCoordinationTest.kt`](../../../harmonia-mobile/app/src/androidTest/java/org/harmonia/player/CrossCallerEngineScanCoordinationTest.kt)
  - Test Methods:
    - `SourceScanCoordinatorTest.kt`: `` `1 two callers cannot scan the same source concurrently` ``, `` `2 scans of different source IDs can execute concurrently` ``, and `` `3 cancellation of active caller releases coordination lock` `` `[VERIFIED-TEST]`
    - `CrossCallerEngineScanCoordinationTest.kt`: `engine_serializes_overlapping_scan_requests_for_the_same_source()`, `engine_allows_concurrent_scans_of_different_storage_sources()`, and `engine_releases_lock_on_cancellation_permitting_subsequent_scans()` `[VERIFIED-TEST]`
  - Execution Output: Verified clean with 0 errors across unit and AndroidTest suites.
- **Durable Architectural Invariant:**
  - Only one active storage reconciliation scan may execute for a given storage source ID at any given moment; concurrent requests for the same source must serialize via `withSourceLock`, while distinct sources may scan concurrently without blocking threads `[VERIFIED-SRC]`.

---

## 6. Related Documentation & Architecture
- **Phase Record:** [`mobile_m4f.md`](../phases/mobile_m4f.md)
- **Architecture Reference:** [`docs/MOBILE_M4E_IMPLEMENTATION.md:§6`](../../MOBILE_M4E_IMPLEMENTATION.md)
