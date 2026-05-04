package com.logshield.engine;

import com.logshield.model.LogEntry;
import com.logshield.storage.FileHandler;
import com.logshield.algorithm.AlgorithmProvider;
import com.logshield.trie.TrieService;
import java.util.HashMap;

/**
 * Singleton registry that serves as the single source of truth for all
 * in-memory log data in LogShield.
 *
 * <p>Implements the Singleton pattern with thread-safe double-checked locking
 * to guarantee that exactly one instance exists across the entire JVM lifetime.
 * The {@code volatile} keyword on {@link #instance} prevents instruction
 * reordering at the CPU level, ensuring no thread ever receives a
 * partially-constructed object.
 *
 * <p>Internal storage:
 * <ul>
 *   <li>{@link java.util.HashMap} keyed by timestamp — O(1) average insert and lookup.</li>
 *   <li>{@code LogEntry[]} array — rebuilt on demand for sorting and binary search.</li>
 * </ul>
 *
 * <p>All writes are persisted to disk immediately via {@link com.logshield.storage.FileHandler}
 * before being mirrored into the in-memory cache, so no log entry is lost on a JVM crash.
 *
 * @author  Virochan V
 * @version 4.0
 * @see     com.logshield.storage.FileHandler
 * @see     com.logshield.algorithm.AlgorithmProvider
 */

public class RegistryManager implements IRegistry {

    // Single source of truth for log file path
    // Change this one line if the path ever needs updating
    public static final String LOG_FILE_PATH = "data/logs.txt";

    // The one and only instance. volatile is NOT optional here — see explanation above.
    private static volatile RegistryManager instance;

    // Our in-memory cache. Key = timestamp string (e.g. "2024-01-15 10:32:01").
    //
    // WHY TIMESTAMP AS KEY?
    // A good HashMap key must be:
    //   (a) Unique        — two different entries must not share a key, or one silently
    //                       overwrites the other. Log timestamps are unique per event.
    //   (b) Stable        — the key must never change after insertion, because HashMap
    //                       stores items in a bucket computed from hashCode(). If the key
    //                       mutates, HashMap looks in the wrong bucket and thinks the item
    //                       is gone. String is immutable in Java — it can never change.
    //   (c) Fast to hash  — String.hashCode() is O(k) where k = key length, and short
    //                       timestamps (~19 chars) are hashed in nanoseconds.
    //
    // In production , you'd use a UUID to guarantee uniqueness. For LogShield's scope,
    // timestamp is clean, human-readable, and directly useful for display and lookup.
    private HashMap<String, LogEntry> logCache;

    // A parallel array for sorting operations.
    // HashMap has no natural order — AlgorithmProvider.cycleSort() operates on arrays.
    // We keep this in sync with the HashMap so we can sort without rebuilding the array
    // from scratch on every sort call (getLogs() does that rebuild on demand instead).
    private LogEntry[] logsArray;

    private FileHandler fileHandler;

    // Delegated to TrieService — RegistryManager coordinates, TrieService owns Trie logic
    private TrieService trieService;

    // Private constructor — the gate that enforces the Singleton guarantee.
    // It runs exactly once, ever, for the entire lifetime of the JVM.
    private RegistryManager(FileHandler fileHandler) {
        this.logCache    = new HashMap<>();
        this.logsArray   = new LogEntry[0];
        this.fileHandler = fileHandler;  // injected — not created here
        this.trieService = new TrieService(); // Inject TrieService — pattern tracking responsibility belongs to the service layer
    }


    /**
     * Returns the single shared instance of {@code RegistryManager},
     * creating it on the first call.
     *
     * <p>Uses double-checked locking with a {@code volatile} field to ensure
     * thread safety without paying synchronization cost on every call after
     * the instance is initialised.
     *
     * <p><b>Time Complexity:</b> O(1) amortized — the {@code synchronized} block
     * runs only on the very first call; all subsequent calls return immediately.<br>
     * <b>Space Complexity:</b> O(1) — exactly one instance is ever allocated.
     *
     * @return the single {@code RegistryManager} instance; never {@code null}
     */
    public static RegistryManager getInstance() {

        // Outer check (fast path): if instance is already built, skip the lock entirely.
        // This is the path that runs 99.99% of the time in a running application.
        if (instance == null) {

            // Synchronize on the class object — only one thread can own this lock at a time.
            synchronized (RegistryManager.class) {

                // Inner check (safe path): the thread that LOST the race already built
                // the instance while we were waiting for the lock. Without this second
                // check, we'd build a second instance and throw away the first.
                if (instance == null) {
                    instance = new RegistryManager(new FileHandler());
                }
            }
        }

        return instance;
    }
    // Overloaded getInstance for dependency injection — used in testing
    // Allows injecting a mock FileHandler without touching production code
    // Time Complexity: O(1) — same as standard getInstance()
    public static RegistryManager getInstance(FileHandler fileHandler) {
        if (instance == null) {
            synchronized (RegistryManager.class) {
                if (instance == null) {
                    instance = new RegistryManager(fileHandler);
                }
            }
        }
        return instance;
    }


    /**
     * Adds a log entry to the persistent store and the in-memory cache.
     *
     * <p>Disk is written to <em>first</em> — if the JVM crashes after the
     * {@link com.logshield.storage.FileHandler#appendLog} call but before the
     * HashMap insert, the entry is still safe on disk. The reverse order
     * would risk silent data loss.
     *
     * <p><b>Time Complexity:</b> O(1) average — one {@code HashMap.put()} and
     * one file append, both constant time.<br>
     * <b>Space Complexity:</b> O(1) — one new entry added to the existing map.
     *
     * @param entry the {@link com.logshield.model.LogEntry} to store;
     *              must not be {@code null}
     */
    public void addLog(LogEntry entry) {

        // Step 1: Write to disk FIRST. If the JVM crashes after this line, the log
        // is safe on disk. If we wrote to HashMap first and then crashed, we'd lose it.
        fileHandler.appendLog(entry, LOG_FILE_PATH);

        // Step 2: Mirror into the in-memory cache for fast lookups.
        // If two events share the same timestamp, the later one wins — a known
        // limitation of using timestamp as key. A UUID key would avoid this entirely.
        logCache.put(entry.getTimestamp(), entry);
        // Delegate pattern tracking to TrieService — not our responsibility to know HOW
        // the Trie works, only that the pattern needs to be tracked
        trieService.trackPattern(entry.getMessage());
    }


    /**
     * Returns all current log entries as an array, rebuilding it fresh from
     * the HashMap on every call.
     *
     * <p>The array is rebuilt rather than maintained incrementally because Java
     * arrays are fixed-size. Maintaining a live array would require an O(n) copy
     * on every {@link #addLog} call. The current design pays the O(n) cost only
     * when the array is actually needed.
     *
     * <p><b>Time Complexity:</b> O(n) — copies all HashMap values into a new array.<br>
     * <b>Space Complexity:</b> O(n) — the returned array holds n LogEntry references.
     *
     * @return a {@code LogEntry[]} snapshot of all entries currently in the cache;
     *         never {@code null}, but may be empty if no logs have been added
     */
    public LogEntry[] getLogs() {

        // toArray(new LogEntry[0]) is the modern Java idiom.
        // Passing a zero-length typed array tells the JVM what element type you want.
        // The JVM allocates a correctly-sized array internally — cleaner than computing
        // the size yourself and passing `new LogEntry[logCache.size()]`.
        logsArray = logCache.values().toArray(new LogEntry[0]);
        return logsArray;
    }


    /**
     * Sorts the internal log array by severity score using Cycle Sort.
     *
     * <p>Always calls {@link #getLogs()} first to rebuild {@code logsArray}
     * from the current HashMap state, preventing the array from drifting out
     * of sync with entries added since the last sort.
     *
     * <p>Cycle Sort is chosen specifically to minimise memory <em>writes</em>,
     * making it optimal for write-limited storage media. It is an in-place
     * algorithm requiring no auxiliary data structures.
     *
     * <p><b>Time Complexity:</b> O(n²) — Cycle Sort's worst and average case.<br>
     * <b>Space Complexity:</b> O(1) extra — sorted in place; only one temp variable used.
     *
     * <p><b>Note:</b> {@code sortLogs()} must be called before
     * {@link #searchBySeverity(int)} — binary search requires a sorted array.
     */
    public void sortLogs() {
        // Rebuild array from HashMap first
        logsArray = logCache.values().toArray(new LogEntry[0]);
        // Sort in place — do NOT call getLogs() after this
        // getLogs() rebuilds from HashMap and destroys sort order
        AlgorithmProvider.cycleSort(logsArray);
    }
    // Returns the already-sorted array without rebuilding from HashMap
    // Use this ONLY after calling sortLogs()
    // Time Complexity: O(1) — just returns the existing reference
    public LogEntry[] getSortedLogs() {
        return logsArray;
    }
    // Returns all logs as a List for CLI display
    // Time Complexity: O(n) — converts HashMap values to List
    public java.util.List<LogEntry> getAllLogs() {
        return new java.util.ArrayList<>(logCache.values());
    }
    // Clears the log file on disk — use only for testing/reset
    // Time Complexity: O(1) — just overwrites with empty content
    public void clearFile() {
        try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                new java.io.FileWriter(LOG_FILE_PATH, false))) {
            // Opening with false flag truncates the file to zero bytes
            writer.write("");
        } catch (java.io.IOException e) {
            System.err.println("[RegistryManager] Could not clear log file: " + e.getMessage());
        }
    }


    /**
     * Searches the sorted log array for an entry matching the given severity score.
     *
     * <p><b>Precondition:</b> {@link #sortLogs()} must be called before this method.
     * Binary search on an unsorted array produces undefined and incorrect results.
     *
     * <p><b>Time Complexity:</b> O(log n) — halves the search space on every comparison.<br>
     * <b>Space Complexity:</b> O(1) — only index variables are used; no extra structures.
     *
     * @param  score the severity score to search for (1 = INFO, 2 = WARN, 3 = ERROR)
     * @return the first matching {@link com.logshield.model.LogEntry},
     *         or {@code null} if no entry with that score exists
     */
    public LogEntry searchBySeverity(int score) {

        // We pass logsArray (sorted by the last sortLogs() call) to binarySearch.
        // Responsibility for sorting falls on the CALLER — a design choice that
        // keeps searchBySeverity() fast (no hidden O(n²) sort inside a search call).

        int index = AlgorithmProvider.binarySearch(logsArray, score);
        if (index == -1) return null;
        return logsArray[index];
    }


    /**
     * Loads all persisted log entries from disk into the in-memory HashMap.
     *
     * <p>Intended to be called once at application startup to restore the
     * previous session's log data. Safe to call even if the file does not
     * yet exist — {@link com.logshield.storage.FileHandler#loadLogs} returns
     * an empty list in that case and no entries are added.
     *
     * <p><b>Time Complexity:</b> O(n) — reads n lines from disk, then inserts
     * each into the HashMap at O(1) average per insert.<br>
     * <b>Space Complexity:</b> O(n) — the HashMap grows by n entries.
     *
     * @param filePath the path to the {@code .txt} file to load from
     */
    public void loadFromFile(String filePath) {

        // Reset TrieService before repopulating — prevents frequency double-counting
        // if loadFromFile() is called more than once in the same session
        trieService.reset();

        // Ask FileHandler to parse the CSV file and return a typed List<LogEntry>.
        // FileHandler handles all I/O exceptions internally and returns an empty list
        // if the file doesn't exist — so this call is always safe, even on first run.
        java.util.List<LogEntry> loaded = fileHandler.loadLogs(filePath);

        // Populate the HashMap from the loaded list.
        // We use timestamp as key, exactly as addLog() does, keeping behaviour consistent.
        for (LogEntry entry : loaded) {
            logCache.put(entry.getTimestamp(), entry);
            // Mirror pattern into TrieService so Trie stays in sync with HashMap after reload
            trieService.trackPattern(entry.getMessage());
        }

        System.out.println("[RegistryManager] Loaded " + loaded.size()
                + " log entries from '" + filePath + "'.");
    }
    /**
     * Returns how many times a given log message pattern has appeared.
     *
     * <p>Delegates directly to Trie for O(L) lookup, where L is pattern length.
     *
     * @param pattern the log message to search
     * @return frequency count, or 0 if pattern does not exist
     */
    // Delegate lookup to TrieService — O(L) exact match, where L = pattern length
    public int getPatternFrequency(String pattern) {
        return trieService.getFrequency(pattern);
    }
}