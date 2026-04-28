package com.logshield;

import com.logshield.model.LogEntry;

public class Main {
    // TEMPORARY - To check the working of LogEntry
    public static void main(String[] args) {

        // Test 1 - Create a log entry manually
        LogEntry entry1 = new LogEntry("2024-01-15 10:30:00", "ERROR", "NullPointerException in UserService");

        // Test 2 - Create another entry
        LogEntry entry2 = new LogEntry("2024-01-15 10:31:00", "WARN", "High memory usage detected");

        // Test 3 - Create third entry
        LogEntry entry3 = new LogEntry("2024-01-15 10:32:00", "INFO", "Server started successfully");

        // Print using toString()
        System.out.println("--- toString() output ---");
        System.out.println(entry1);
        System.out.println(entry2);
        System.out.println(entry3);

        // Print using toCSV()
        System.out.println("\n--- toCSV() output ---");
        System.out.println(entry1.toCSV());
        System.out.println(entry2.toCSV());
        System.out.println(entry3.toCSV());

        // Test fromCSV() - parse back from CSV string
        System.out.println("\n--- fromCSV() round-trip test ---");
        String csv = entry1.toCSV();
        LogEntry parsed = LogEntry.fromCSV(csv);
        System.out.println(parsed);

        // Test severity scores
        System.out.println("\n--- Severity Score test ---");
        System.out.println("ERROR score: " + entry1.getSeverityScore() + " (expected 3)");
        System.out.println("WARN score:  " + entry2.getSeverityScore() + " (expected 2)");
        System.out.println("INFO score:  " + entry3.getSeverityScore() + " (expected 1)");
    }
}
