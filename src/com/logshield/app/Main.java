package com.logshield.app;

import com.logshield.algorithm.AlgorithmProvider;
import com.logshield.model.LogEntry;

public class Main {

    public static void main(String[] args) {

        // ── BUILD TEST ARRAY ──────────────────────────────────────────
        // Intentionally unsorted so cycleSort has real work to do
        LogEntry[] logs = {
                new LogEntry("2024-01-15 10:30:00", "INFO",  "Server started"),
                new LogEntry("2024-01-15 10:31:00", "ERROR", "NullPointerException in UserService"),
                new LogEntry("2024-01-15 10:32:00", "WARN",  "High memory usage"),
                new LogEntry("2024-01-15 10:33:00", "ERROR", "Database connection failed"),
                new LogEntry("2024-01-15 10:34:00", "INFO",  "Health check passed"),
                new LogEntry("2024-01-15 10:35:00", "WARN",  "Slow query detected")
        };

        // ── BEFORE SORT ───────────────────────────────────────────────
        System.out.println("=== BEFORE cycleSort ===");
        for (LogEntry e : logs) {
            System.out.println("Score = " + e.getSeverityScore() + " -> " + e);
        }

        // ── RUN CYCLE SORT ────────────────────────────────────────────
        AlgorithmProvider.cycleSort(logs);

        // ── AFTER SORT ────────────────────────────────────────────────
        System.out.println("\n=== AFTER cycleSort (ascending severityScore) ===");
        for (LogEntry e : logs) {
            System.out.println("Score = " + e.getSeverityScore() + " -> " + e);
        }

        // ── BINARY SEARCH — target exists ─────────────────────────────
        System.out.println("\n=== binarySearch for severityScore = 3 (ERROR) ===");
        // Note: returns any matching index, not guaranteed first occurrence
        int index = AlgorithmProvider.binarySearch(logs, 3);
        if (index != -1) {
            System.out.println("Found at index " + index + ": " + logs[index]);
        } else {
            System.out.println("Not found.");
        }

        // ── BINARY SEARCH — target does not exist ─────────────────────
        System.out.println("\n=== binarySearch for severityScore = 99 (missing) ===");
        int missing = AlgorithmProvider.binarySearch(logs, 99);
        System.out.println(missing == -1 ? "Correctly returned -1" : "ERROR: should not find 99");
        // ── EDGE CASE: Empty array ─────────────────────────────
        System.out.println("\n=== EDGE CASE: Empty array ===");

        LogEntry[] empty = {};  // Create an empty array

        AlgorithmProvider.cycleSort(empty);  // Should NOT crash

        System.out.println("Handled empty array without crash.");
    }
}