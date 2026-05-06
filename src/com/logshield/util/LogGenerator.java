package com.logshield.util;

import com.logshield.engine.RegistryManager;
import com.logshield.exception.InvalidLevelException;
import com.logshield.model.LogEntry;
import com.logshield.storage.FileHandler;

import java.util.Random;

/**
 * Synthetic log data factory and benchmark harness for the LogShield system.
 *
 * <p>This utility class serves two purposes:</p>
 * <ol>
 *   <li><b>Generation</b> — produces exactly {@value #TOTAL_ENTRIES} realistic
 *       {@link com.logshield.model.LogEntry} records with a weighted level
 *       distribution (INFO 60%, WARN 30%, ERROR 10%) and persists them to
 *       {@value #OUTPUT_PATH} via {@link com.logshield.storage.FileHandler}.
 *       Entries are written one at a time using the Write-Through policy —
 *       no entry is held exclusively in memory before hitting disk.</li>
 *
 *   <li><b>Benchmarking</b> — reloads the generated dataset through
 *       {@link com.logshield.engine.RegistryManager}, sorts it via Cycle Sort,
 *       then measures and compares:
 *       <ul>
 *         <li>Linear scan — {@code O(n)} — iterates every entry until first match</li>
 *         <li>Binary Search — {@code O(log n)} — operates on the pre-sorted array</li>
 *       </ul>
 *       Results are reported in milliseconds using {@link System#nanoTime()}
 *       and a speed-up factor is printed to confirm the logarithmic advantage
 *       empirically.</li>
 * </ol>
 *
 * <p><b>Benchmark result (recorded on development machine):</b><br>
 * Linear scan: 4.1553 ms | Binary Search: 0.0112 ms |
 * Binary Search was <b>371x faster</b> on 10,000 entries.</p>
 *
 * <p><b>Overall complexity:</b><br>
 * Time  — O(n) generation + O(n²) Cycle Sort + O(log n) Binary Search<br>
 * Space — O(n) entries loaded into {@link com.logshield.engine.RegistryManager}
 * HashMap; generation itself uses O(1) memory per entry (streamed to disk).</p>
 *
 * <p><b>Known limitation:</b> {@link com.logshield.storage.FileHandler#appendLog}
 * opens and closes a {@link java.io.BufferedWriter} per entry, resulting in
 * 10,000 file open/close operations. Generation time (~5 seconds) reflects this.
 * A production implementation would batch all writes in a single writer session.</p>
 *
 * <p>This class is a standalone utility — it is never instantiated and has no
 * dependencies on {@code LogShieldApp}. Run its {@code main()} method directly
 * to regenerate the sample dataset or re-run the benchmark.</p>
 *
 * @author  Virochan V
 * @version 1.0
 * @see     com.logshield.model.LogEntry
 * @see     com.logshield.storage.FileHandler
 * @see     com.logshield.engine.RegistryManager
 * @see     com.logshield.algorithm.AlgorithmProvider
 */
public class LogGenerator {

    private static final int    TOTAL_ENTRIES   = 10_000;
    private static final int    PROGRESS_STEP   = 1_000;
    private static final String OUTPUT_PATH     = "samples/sample_logs.txt";
    private static final int    SEARCH_SEVERITY = 3;

    private static final String[] INFO_MESSAGES = {
            "Health check passed",
            "Request processed in 230ms",
            "Service started successfully on port 8080",
            "Cache refreshed with 4200 entries loaded",
            "User session initialised for userId=10482"
    };

    private static final String[] WARN_MESSAGES = {
            "Disk usage at 94 percent approaching threshold",
            "Response time degraded 1850ms on api orders",
            "Retry attempt 3 of 5 for downstream service",
            "Memory usage above 80 percent GC pressure detected"
    };

    private static final String[] ERROR_MESSAGES = {
            "Connection timeout on port 8080",
            "NullPointerException in ServiceLayer",
            "Database connection pool exhausted all 20 connections in use"
    };

    public static void main(String[] args) {

        FileHandler fileHandler = new FileHandler();
        Random rng = new Random();

        // ── Phase 1: Generate and persist ─────────────────────────────
        System.out.println("Starting generation of " + TOTAL_ENTRIES + " log entries...");
        long genStart = System.currentTimeMillis();

        for (int i = 1; i <= TOTAL_ENTRIES; i++) {

            // Generate timestamp — unique per entry
            String timestamp = String.format("2024-%02d-%02d %02d:%02d:%02d",
                    ((i / 10000) % 12) + 1,  // month 01-12
                    ((i / 500) % 28) + 1,    // day 01-28
                    (i / 3600) % 24,          // hour 00-23
                    (i / 60) % 60,            // minute 00-59
                    i % 60);                  // second 00-59

            // Weighted random level: 60% INFO, 30% WARN, 10% ERROR
            int roll = rng.nextInt(100);
            String level;
            String message;

            if (roll < 60) {
                level   = "INFO";
                message = INFO_MESSAGES[rng.nextInt(INFO_MESSAGES.length)];
            } else if (roll < 90) {
                level   = "WARN";
                message = WARN_MESSAGES[rng.nextInt(WARN_MESSAGES.length)];
            } else {
                level   = "ERROR";
                message = ERROR_MESSAGES[rng.nextInt(ERROR_MESSAGES.length)];
            }

            // Write directly to file — no need to hold all in memory at once
            try {
                fileHandler.appendLog(new LogEntry(timestamp, level, message), OUTPUT_PATH);
            } catch (InvalidLevelException e) {
                // This should never happen — level is always set from our own constants
                // If it does, it means a bug in the generation logic, not user input
                System.err.println("[LogGenerator] BUG: Invalid level generated: "
                        + e.getMessage());
            }

            // Progress heartbeat every 1000 entries
            if (i % PROGRESS_STEP == 0) {
                System.out.printf("Generated %d/%d entries...%n", i, TOTAL_ENTRIES);
            }
        }

        long genEnd = System.currentTimeMillis();
        System.out.printf("%nGeneration complete. Time: %d ms%n", genEnd - genStart);

        // ── Phase 2: Load into RegistryManager ────────────────────────
        System.out.println("\nLoading entries into RegistryManager...");
        RegistryManager registry = RegistryManager.getInstance();
        registry.loadFromFile(OUTPUT_PATH);

        // Sort before searching — Binary Search requires sorted array
        registry.sortLogs();

        // ── Phase 3: Benchmark ────────────────────────────────────────
        System.out.println("\n--- Benchmark: searching severity score = "
                + SEARCH_SEVERITY + " ---");

        // Linear scan — O(n)
        long linearStart = System.nanoTime();
        LogEntry linearResult = null;
        for (LogEntry e : registry.getAllLogs()) {
            if (e.getSeverityScore() == SEARCH_SEVERITY) {
                linearResult = e;
                break;
            }
        }
        long linearEnd = System.nanoTime();
        double linearMs = (linearEnd - linearStart) / 1_000_000.0;

        System.out.printf("Linear scan   : %.4f ms | result: %s%n",
                linearMs, linearResult != null ? "found" : "not found");

        // Binary search — O(log n)
        long binaryStart = System.nanoTime();
        LogEntry binaryResult = registry.searchBySeverity(SEARCH_SEVERITY);
        long binaryEnd = System.nanoTime();
        double binaryMs = (binaryEnd - binaryStart) / 1_000_000.0;

        System.out.printf("Binary search : %.4f ms | result: %s%n",
                binaryMs, binaryResult != null ? "found" : "not found");

        // Speed-up factor
        if (binaryMs > 0.0) {
            double speedup = linearMs / binaryMs;
            System.out.printf("%nBinary Search was %.2fx faster than linear scan.%n", speedup);
        } else {
            System.out.println("\nBinary search time too small to measure precisely.");
        }
    }
}