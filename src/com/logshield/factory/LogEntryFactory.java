package com.logshield.factory;

import com.logshield.exception.InvalidLevelException;
import com.logshield.model.LogEntry;

/**
 * Factory for creating LogEntry objects of different types.
 *
 * <p>Implements the <b>Factory Pattern</b> — centralizes object creation
 * so the caller never uses {@code new LogEntry()} directly. This satisfies
 * the Open/Closed Principle: adding a new entry type (e.g. SYSTEM, AUDIT)
 * requires adding one line to {@link #TYPE_TO_LEVEL} — zero changes
 * anywhere else in the codebase.</p>
 *
 * <p>Why Factory Pattern here?</p>
 * <ul>
 *   <li>Decouples LogShieldApp from LogEntry construction details</li>
 *   <li>Centralizes type-to-level mapping in one immutable Map</li>
 *   <li>Makes adding new entry variants a single-line change</li>
 * </ul>
 *
 * <p><b>Time Complexity:</b> O(1) per creation — Map lookup is constant time.<br>
 * <b>Space Complexity:</b> O(1) — one LogEntry object allocated per call.</p>
 *
 * @author  Virochan V
 * @version 1.0
 */
public class LogEntryFactory {

    // Private constructor — utility class, never instantiated
    // Same pattern as AlgorithmProvider — all methods are static
    private LogEntryFactory() {}

    // Type-to-level map — OCP compliant
    // Adding "audit" → "WARN" tomorrow means one line here, nothing else changes
    // Map.of() is immutable — no accidental runtime modification possible
    // Adding a new entry type NEVER requires touching createEntry() logic
    private static final java.util.Map<String, String> TYPE_TO_LEVEL =
            java.util.Map.of(
                    "server",  "ERROR",  // server crashes are always critical
                    "network", "WARN",   // connectivity issues are warnings
                    "info",    "INFO",   // routine operational events
                    "audit",   "WARN",   // audit trail entries
                    "system",  "INFO"    // system lifecycle events
            );

    // -------------------------------------------------------------------------
    // createEntry(String type, String timestamp, String message)
    //
    // Time Complexity : O(1) — HashMap lookup + LogEntry constructor
    // Space Complexity: O(1) — one LogEntry allocated
    //
    // I chose Factory Pattern because LogShieldApp should not know HOW
    // a LogEntry is built — only WHAT type it wants. If LogEntry's
    // constructor changes tomorrow, only this class needs updating.
    // -------------------------------------------------------------------------

    /**
     * Creates a LogEntry of the specified type with appropriate level.
     *
     * <p>Supported types (case-insensitive): server, network, info, audit, system.
     * Unknown types default safely to INFO — never crash on unrecognized input.</p>
     *
     * @param type      category of log entry — determines the level
     * @param timestamp when the event occurred
     * @param message   human-readable description of the event
     * @return a fully constructed {@link LogEntry} with level set by type
     * @throws InvalidLevelException if internal level mapping produces invalid level
     */
    public static LogEntry createEntry(String type, String timestamp, String message)
            throws InvalidLevelException {

        // getOrDefault — unknown types safely fall back to INFO
        // No switch, no modification needed when new types are added
        // This is the OCP fix — createEntry() never changes, only the Map does
        String level = TYPE_TO_LEVEL.getOrDefault(type.toLowerCase(), "INFO");

        // Delegate construction to LogEntry — Factory decides level, LogEntry builds
        return new LogEntry(timestamp, level, message);
    }

    // -------------------------------------------------------------------------
    // Convenience methods — most common factory calls
    // Each delegates to createEntry() — single source of logic
    // Time Complexity: O(1) each
    // -------------------------------------------------------------------------

    /** Creates an ERROR-level server log entry. */
    public static LogEntry createServerEntry(String timestamp, String message)
            throws InvalidLevelException {
        return createEntry("server", timestamp, message);
    }

    /** Creates a WARN-level network log entry. */
    public static LogEntry createNetworkEntry(String timestamp, String message)
            throws InvalidLevelException {
        return createEntry("network", timestamp, message);
    }

    /** Creates an INFO-level routine log entry. */
    public static LogEntry createInfoEntry(String timestamp, String message)
            throws InvalidLevelException {
        return createEntry("info", timestamp, message);
    }
}