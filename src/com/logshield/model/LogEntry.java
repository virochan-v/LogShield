package com.logshield.model;

import com.logshield.exception.InvalidLevelException;
import com.logshield.exception.LogParseException;

// LogEntry.java — the heart of LogShield.
// Every single log line we read from a file becomes one of these objects.
// Think of it as a sticky note: timestamp, how bad it is, and what happened.

public class LogEntry {

    // We keep these private so nothing outside this class can just reach in
    // and change them. A log entry from the past shouldn't be editable.
    private String timestamp;    // When did this happen? e.g. "2024-01-15 10:32:01"
    private String level;        // How loud is the alarm? INFO / WARN / ERROR
    private String message;      // What actually happened, in plain English
    private int severityScore;

    // Severity map — OCP compliant: adding a new level means adding one entry here
    // The constructor never needs to change when new levels are introduced
    // Map.of() creates an immutable map — values cannot be changed at runtime
    private static final java.util.Map<String, Integer> SEVERITY_MAP = java.util.Map.of(
            "ERROR", 3,
            "WARN",  2,
            "INFO",  1
    );

    // The constructor — runs the moment someone does 'new LogEntry(...)'
    // We only ask for 3 things. The severity is our job to figure out, not the caller's.
    // throws InvalidLevelException — compiler forces every caller to handle bad levels
    // This prevents silent corruption where "CRITICAL" became severity 1 with no warning
    public LogEntry(String timestamp, String level, String message)
            throws InvalidLevelException {

        this.timestamp = timestamp;
        this.level = level;
        this.message = message;

        // Reject unrecognised levels explicitly — no silent defaulting to INFO
        // Before this fix, "CRITICAL" would silently become severity 1
        // Now the caller is forced to handle the error — no surprises
        if (!SEVERITY_MAP.containsKey(level.toUpperCase())) {
            throw new InvalidLevelException(level);
        }

        // Safe to call get() directly — we already confirmed the key exists above
        this.severityScore = SEVERITY_MAP.get(level.toUpperCase());
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
    @Override
    public String toString() {
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
    // Static because you call this BEFORE any object exists — it IS the thing that creates one.
    // throws LogParseException — caller knows exactly which line failed and why
    // throws InvalidLevelException — level field in CSV is not INFO/WARN/ERROR
    public static LogEntry fromCSV(String line)
            throws LogParseException, InvalidLevelException {

        // Split into max 4 parts — preserves commas inside the message field
        // Without the 4 limit: "Disk full, retrying" splits into 5 parts, losing data
        String[] parts = line.split(",", 4);

        // Guard: a valid CSV line must have exactly 4 fields
        // If parts.length < 4, the file is malformed — tell the caller precisely why
        if (parts.length < 4) {
            throw new LogParseException(line,
                    "expected 4 fields but found " + parts.length);
        }

        String timestamp = parts[0].trim();
        String level     = parts[1].trim();
        String message   = parts[2].trim();

        // Validate parts[3] is a real integer before trusting it
        // A corrupted file could have "three" instead of "3" — catch it here
        try {
            Integer.parseInt(parts[3].trim());
        } catch (NumberFormatException e) {
            throw new LogParseException(line,
                    "severity score is not a valid integer: '"
                            + parts[3].trim() + "'", e);
        }

        // Constructor recalculates severityScore from level — we never trust
        // the number stored in the file. That is how bugs sneak in.
        return new LogEntry(timestamp, level, message);
    }
}