package com.logshield.app;

import com.logshield.algorithm.AlgorithmProvider;
import com.logshield.engine.RegistryManager;
import com.logshield.model.LogEntry;

public class Main {

    public static void main(String[] args) {

        RegistryManager.getInstance().clearFile(); // Fresh start for each test run

        // ── TEST 1: Singleton guarantee ───────────────────────────────
        System.out.println("=== TEST 1: Singleton Check ===");
        RegistryManager rm1 = RegistryManager.getInstance();
        RegistryManager rm2 = RegistryManager.getInstance();
        System.out.println("Same instance: " + (rm1 == rm2)); // must print true

        // ── TEST 2: addLog() — writes to cache AND disk ───────────────
        System.out.println("\n=== TEST 2: addLog() ===");
        RegistryManager rm = RegistryManager.getInstance();
        rm.addLog(new LogEntry("2024-01-15 10:30:00", "INFO",  "Server started"));
        rm.addLog(new LogEntry("2024-01-15 10:31:00", "ERROR", "NullPointerException in UserService"));
        rm.addLog(new LogEntry("2024-01-15 10:32:00", "WARN",  "High memory usage"));
        rm.addLog(new LogEntry("2024-01-15 10:33:00", "ERROR", "Database connection failed"));
        rm.addLog(new LogEntry("2024-01-15 10:34:00", "INFO",  "Health check passed"));
        rm.addLog(new LogEntry("2024-01-15 10:35:00", "WARN",  "Slow query detected"));
        System.out.println("Added 6 log entries.");

        // ── TEST 3: getLogs() — unsorted ──────────────────────────────
        System.out.println("\n=== TEST 3: getLogs() before sort ===");
        for (LogEntry e : rm.getLogs()) {
            System.out.println(e);
        }

        // ── TEST 4: sortLogs() — CycleSort via RegistryManager ────────
        System.out.println("\n=== TEST 4: sortLogs() ===");
        rm.sortLogs();
        for (LogEntry e : rm.getSortedLogs()) {  // ← use getSortedLogs() not getLogs()
            System.out.println(e);
        }

        // ── TEST 5: searchBySeverity() — Binary Search ────────────────
        System.out.println("\n=== TEST 5: searchBySeverity(3) ===");
        LogEntry found = rm.searchBySeverity(3);
        System.out.println(found != null ? "Found: " + found : "Not found.");

        System.out.println("\n=== TEST 5b: searchBySeverity(99) ===");
        LogEntry missing = rm.searchBySeverity(99);
        System.out.println(missing != null ? "Found: " + missing : "Correctly returned null.");

        // ── TEST 6: File persistence — restart simulation ─────────────
        System.out.println("\n=== TEST 6: loadFromFile() — simulating app restart ===");
        // Create a fresh manager simulation by loading from disk
        // In real usage this runs once at startup in LogShieldApp
        RegistryManager.getInstance().loadFromFile(RegistryManager.LOG_FILE_PATH);
        System.out.println("Logs reloaded from disk:");
        for (LogEntry e : rm.getLogs()) {
            System.out.println(e);
        }
    }
}