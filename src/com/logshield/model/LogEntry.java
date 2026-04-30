package com.logshield.model;

// LogEntry.java — the heart of LogShield.
// Every single log line we read from a file becomes one of these objects.
// Think of it as a sticky note: timestamp, how bad it is, and what happened.

// TODO: Improve CSV parsing to handle quoted fields and edge cases

public class LogEntry {

    // We keep these private so nothing outside this class can just reach in
    // and change them. A log entry from the past shouldn't be editable.
    private String timestamp;    // When did this happen? e.g. "2024-01-15 10:32:01"
    private String level;        // How loud is the alarm? INFO / WARN / ERROR
    private String message;      // What actually happened, in plain English
    private int severityScore;   // A number we can sort/filter on — ERROR=3, WARN=2, INFO=1


    // The constructor — runs the moment someone does 'new LogEntry(...)'
    // We only ask for 3 things. The severity is our job to figure out, not the caller's.
    public LogEntry(String timestamp, String level, String message) {

        this.timestamp = timestamp;  // 'this' just means "the field", not the parameter
        this.level = level;
        this.message = message;

        // Translate the level word into a number once, right here, right now.
        // This way, no other class ever needs to know the INFO=1 rule — it lives here.
        switch (level) {
            case "ERROR":
                this.severityScore = 3;  // The house is on fire
                break;
            case "WARN":
                this.severityScore = 2;  // Something smells smoky
                break;
            default:
                this.severityScore = 1;  // All good, just keeping a diary
                break;
        }
    }


    // --- GETTERS ---
    // Simple read-only windows into the object.
    // Other classes can look, but they can't touch.

    public String getTimestamp() {
        return timestamp;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public int getSeverityScore() {
        return severityScore;
    }


    // Java calls this automatically whenever you print the object.
    // Without it, println() would spit out something like "LogEntry@6d06d69c" — useless.
    // Now it prints something a human can actually read.
    @Override
    public String toString() {
        // Produces: [2024-01-15 10:32:01] [ERROR] (severity=3) Disk full
        return String.format("[%s] [%s] (severity=%d) %s",
                timestamp,
                level,
                severityScore,
                message);
    }


    // Packs this log entry into a single CSV line, ready to write to a file.
    // Produces: 2024-01-15 10:32:01,ERROR,Disk full,3
    public String toCSV() {
        return timestamp + "," + level + "," + message + "," + severityScore;
    }


    // The reverse of toCSV() — takes a CSV line and rebuilds the LogEntry from it.
    // It's static because you call this BEFORE any object exists; it's the thing that creates one.
    // Usage: LogEntry entry = LogEntry.fromCSV("2024-01-15,ERROR,Disk full,3");
    public static LogEntry fromCSV(String line) {

        // Chop the line at every comma — gives us an array of the four pieces
        String[] parts = line.split(",",4);

        String timestamp = parts[0];  // First slot is always the timestamp
        String level     = parts[1];  // Second is always the level
        String message   = parts[2];  // Third is the message
        // We skip parts[3] (the saved score) — the constructor recalculates it cleanly.
        // Trusting a number from a file is how bugs sneak in.

        return new LogEntry(timestamp, level, message);
    }
}