# LogShield — Algorithm Complexity Reference

## System Overview

LogShield is a memory-optimized CLI log analysis engine built in Core Java.
It indexes application log entries using an in-memory HashMap, ranks them
by severity using Cycle Sort, and retrieves them using Binary Search.
This document records the algorithmic decisions and their complexity
implications for every major operation in the system.

## System Architecture

```
┌─────────────────────────────────────────────────────┐
│                   LogShieldApp                       │
│              (CLI Controller — IRegistry)            │
└──────────────┬──────────────────────────────────────┘
               │ depends on abstraction (DIP)
               ▼
┌─────────────────────────────────────────────────────┐
│                  RegistryManager                     │
│    Singleton · HashMap Cache · Write-Through         │
├───────────────┬─────────────────┬───────────────────┤
│  FileHandler  │ AlgorithmProvider│   TrieService     │
│  (Storage)    │ (Sort + Search)  │ (Pattern Match)   │
│  O(1) write   │ CycleSort O(n²)  │ Trie O(L)        │
│  O(n) load    │ BinarySearch     │                   │
│               │ O(log n)         │                   │
└───────────────┴─────────────────┴───────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────┐
│                    LogEntry                          │
│         Model · CSV · InvalidLevelException          │
└─────────────────────────────────────────────────────┘
```

All complexities use standard Big-O notation:
- **n** = number of log entries in the system
- **k** = number of top anomalies requested (fixed at 5 in production)
- **L** = length of a log message pattern string

---

## Layer 1 — Model (`com.logshield.model`)

### LogEntry

| Method | Time | Space | Real-world implication |
|---|---|---|---|
| `LogEntry()` constructor | O(1) | O(1) | Instantiation cost is negligible at any scale |
| `toCSV()` | O(L) | O(L) | Grows with message length, not entry count |
| `fromCSV()` | O(L) | O(1) | Parsing one line never depends on file size |
| `toString()` | O(L) | O(L) | Display cost is per-entry, not system-wide |

**Design decision:** `severityScore` is computed inside the constructor
using an immutable `Map.of()` lookup rather than a switch statement.
Adding a new log level (e.g. CRITICAL) requires one Map entry change —
zero constructor modifications. This satisfies the Open/Closed Principle.

---

## Layer 2 — Algorithms (`com.logshield.algorithm`)

### AlgorithmProvider

| Method | Time | Space | Real-world implication |
|---|---|---|---|
| `cycleSort()` | O(n²) | O(1) | At 1,000 entries: ~1M comparisons. Chosen for write minimization, not raw speed |
| `binarySearch()` | O(log n) | O(1) | At 1,000,000 entries: ~20 comparisons. Requires sorted input |

### Why Cycle Sort over Collections.sort()?

`Collections.sort()` uses TimSort — O(n log n) time, but O(n) space and
frequent element moves. Cycle Sort trades time efficiency for write
efficiency: **each element moves at most once** to its final position.

In systems where writes are expensive — SSD wear, flash memory, write-limited
embedded storage, or high-frequency log streams — minimising write operations
extends hardware life. This is the core justification for Cycle Sort in
LogShield's context.

| Algorithm | Time | Space | Write operations |
|---|---|---|---|
| TimSort (Collections.sort) | O(n log n) | O(n) | O(n log n) |
| Cycle Sort | O(n²) | O(1) | O(n) — minimum possible |
| QuickSort | O(n log n) avg | O(log n) | O(n log n) |

### Why Binary Search over linear scan?

Empirically measured on 10,000 entries:
- Linear scan: **4.1553 ms**
- Binary Search: **0.0112 ms**
- Speed improvement: **371x**

The gap is not constant — it grows as n increases. See `BENCHMARK.md`
for scale projections.

---

## Layer 3 — Engine (`com.logshield.engine`)

### RegistryManager (Singleton)

| Method | Time | Space | Real-world implication |
|---|---|---|---|
| `getInstance()` | O(1) amortized | O(1) | Lock acquired once only — zero cost at steady state |
| `addLog()` | O(1) average | O(1) | Scales to millions of entries without degradation |
| `getLogs()` | O(n) | O(n) | Full rebuild — do not call inside tight loops |
| `sortLogs()` | O(n²) | O(1) | Acceptable for log analysis workloads, not real-time |
| `getSortedLogs()` | O(1) | O(1) | Zero cost — returns existing sorted reference |
| `searchBySeverity()` | O(log n) | O(1) | 20 comparisons for 1M entries |
| `loadFromFile()` | O(n) | O(n) | One-time startup cost, not repeated |
| `getTopAnomalies(k)` | O(n log k) | O(k) | Memory cost fixed at k regardless of n |
| `getAllLogs()` | O(n) | O(n) | Display only — never call in search paths |
| `getPatternFrequency()` | O(L) | O(1) | Delegates to TrieService |

**Singleton justification:** One RegistryManager means one HashMap —
one source of truth. Multiple instances would produce divergent cache
states where one part of the system sees stale data.

**Write-Through policy:** Disk is written before HashMap update.
If JVM crashes between the two writes, the entry survives on disk.
The reverse order risks silent data loss.

---

## Layer 4 — Storage (`com.logshield.storage`)

### FileHandler

| Method | Time | Space | Real-world implication |
|---|---|---|---|
| `appendLog()` | O(1) | O(1) | One write per entry — cost does not grow with file size |
| `loadLogs()` | O(n) | O(n) | Full file parse — one-time startup cost |

**Known limitation:** `appendLog()` opens and closes `BufferedWriter`
per call — 10,000 entries triggers 10,000 file open/close cycles.
Production fix: batch writes in a single writer session. Estimated
improvement: 50x faster generation.

---

## Layer 5 — Pattern Matching (`com.logshield.trie`)

### Trie + TrieService

| Method | Time | Space | Real-world implication |
|---|---|---|---|
| `insert()` | O(L) | O(L) worst | Shared prefixes save space — "NullPointer" shares nodes with "NullPointerException" |
| `search()` | O(L) | O(1) | Lookup cost never depends on how many patterns are stored |
| `incrementFrequency()` | O(L) | O(1) | insert() + counter — single traversal |
| `getFrequency()` | O(L) | O(1) | Traverses to terminal node, reads counter |
| `TrieService.reset()` | O(1) | O(1) | New Trie allocated — old structure garbage collected |

**Why Trie over HashMap for pattern search?**

| Feature | HashMap | Trie |
|---|---|---|
| Exact match | O(1) | O(L) |
| Prefix search | Not supported | O(L) |
| Frequency tracking | Separate counter needed | Built into node |
| Memory for shared prefixes | Stores full string per key | Shares prefix nodes |

For LogShield's use case — tracking message patterns across thousands
of log entries — the Trie's prefix sharing and built-in frequency
counter justify the O(L) vs O(1) lookup trade-off.

---

## Layer 6 — Factory (`com.logshield.factory`)

### LogEntryFactory

| Method | Time | Space | Notes |
|---|---|---|---|
| `createEntry()` | O(1) | O(1) | Map lookup + constructor — constant regardless of type count |
| `createServerEntry()` | O(1) | O(1) | Delegates to createEntry() |
| `createNetworkEntry()` | O(1) | O(1) | Delegates to createEntry() |
| `createInfoEntry()` | O(1) | O(1) | Delegates to createEntry() |

**OCP compliance:** `TYPE_TO_LEVEL` is an immutable `Map.of()`.
Adding "audit" → "WARN" requires one Map entry. `createEntry()` never changes.

---

## Exception Hierarchy (`com.logshield.exception`)

| Class | Extends | Thrown When | Benefit over generic exception |
|---|---|---|---|
| `LogShieldException` | Exception | Base class | Groups all LogShield errors for catch-all handling |
| `InvalidLevelException` | LogShieldException | Level not INFO/WARN/ERROR | Caller knows exactly which level was rejected |
| `LogParseException` | LogShieldException | CSV line malformed | Caller receives the exact failing line for diagnostics |

---

## System-Wide Complexity Summary

| Operation | Algorithm | Time | Space | Bottleneck? |
|---|---|---|---|---|
| Add record | HashMap + File | O(1) | O(1) | No |
| Sort all records | Cycle Sort | O(n²) | O(1) | Yes — avoid on large n |
| Search by severity | Binary Search | O(log n) | O(1) | No |
| Top k anomalies | MinHeap | O(n log k) | O(k) | No |
| Pattern lookup | Trie | O(L) | O(1) | No |
| Load from disk | Linear parse | O(n) | O(n) | Startup only |

**System bottleneck:** `sortLogs()` at O(n²) is the only operation
that degrades meaningfully at scale. At 10,000 entries this is
acceptable. At 1,000,000 entries, replacing Cycle Sort with a
merge sort variant would be the first production optimization —
while retaining Cycle Sort for small in-memory batches where
write minimization still matters.