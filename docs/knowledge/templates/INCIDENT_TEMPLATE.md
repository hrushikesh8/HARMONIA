# Incident & Defect Record: [INC-XXX: Short Title]

## 1. Incident Overview
- **Incident ID:** `INC-[XXX]`
- **Date Detected:** [YYYY-MM-DD]
- **Target Subsystem:** [Harmonia Mobile / Harmonia Desktop / Core Scanner / Playback Engine]
- **Milestone / Phase:** [e.g. M5-B Remediation Round 2 / Personalization Phase 3]
- **Severity Classification:** [BLOCKER / HIGH / MEDIUM / LOW]
- **Investigation Status:** [RESOLVED & VERIFIED / IN PROGRESS / CANNOT REPRODUCE]
- **Confidence Rating:** [`[VERIFIED-SRC]` / `[REPORT-BACKED]` / `[INFERRED-HIST]`]

---

## 2. Symptoms & User/System Impact
- **Observed Behavior:**
  - [Exact error message, stack trace, unexpected UI state, audio glitch, or ANR]
- **User Impact:**
  - [How this defect impacts the end user, e.g. audio stops, incorrect star rating displayed, crash on launch]
- **Reproduction Steps:**
  1. [Step 1]
  2. [Step 2]
  3. [Step 3]

---

## 3. Root Cause Analysis
- **Confirmed Root Cause (Evidence-Backed):**
  - [Technical explanation of the confirmed bug, citing code lines and exact failure mechanism]
- **Hypotheses Considered & Disproven:**
  - *Hypothesis A:* [Description and why it was ruled out]
  - *Hypothesis B:* [Description and why it was ruled out]

---

## 4. Corrective Action & Implementation
- **Remediation Summary:**
  - [High-level summary of the architectural or algorithmic correction]
- **Code Modifications:**
  - [`path/to/File.kt:L123-L145`](#): [Explanation of code change]
- **Alternatives Rejected:**
  - [Why other potential fixes were rejected (e.g. performance penalty, breaking clean architecture, thread safety risks)]

---

## 5. Regression Prevention & Invariant Protection
- **Regression Test Suite:**
  - Test File: [`path/to/TestClass.kt`](#)
  - Test Method: `test_name_preventing_recurrence()`
  - Execution Output: [Verified passing output]
- **Durable Architectural Invariant:**
  - [Rule codified in system design to ensure this class of defect cannot reoccur]
