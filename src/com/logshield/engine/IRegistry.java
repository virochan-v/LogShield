package com.logshield.engine;

import com.logshield.model.LogEntry;
import java.util.List;

/**
 * Defines the contract for any log registry implementation in LogShield.
 *
 * <p>All high-level components — specifically {@link com.logshield.app.LogShieldApp}
 * — depend on this interface rather than on any concrete implementation.
 * This satisfies two SOLID principles simultaneously:</p>
 * <ul>
 *   <li><b>Interface Segregation (ISP)</b> — clients depend only on the methods
 *       they actually use, grouped here into one cohesive registry contract.</li>
 *   <li><b>Dependency Inversion (DIP)</b> — high-level modules depend on this
 *       abstraction; low-level modules ({@link com.logshield.engine.RegistryManager})
 *       implement it. Neither depends on the other directly.</li>
 * </ul>
 *
 * <p><b>Extensibility guarantee:</b> replacing {@link com.logshield.engine.RegistryManager}
 * with an alternative implementation (e.g. a database-backed registry, a
 * read-only archive registry, or a mock for testing) requires <em>zero changes</em>
 * in {@link com.logshield.app.LogShieldApp} — only the instantiation site
 * in {@code main()} needs updating.</p>
 *
 * <p><b>Complexity contract:</b> this interface does not prescribe algorithmic
 * complexity. Each implementing class must document its own time and space
 * complexity per method in its Javadoc.</p>
 *
 * @author  Virochan V
 * @version 1.0
 * @see     com.logshield.engine.RegistryManager
 * @see     com.logshield.app.LogShieldApp
 */
public interface IRegistry {

    // Add a log entry to cache and persist to disk
    void addLog(LogEntry entry);

    // Return all logs as unsorted array
    LogEntry[] getLogs();

    // Sort logs in place using the chosen algorithm
    void sortLogs();

    // Return already-sorted array — call sortLogs() first
    LogEntry[] getSortedLogs();

    // Return all logs as List for display
    List<LogEntry> getAllLogs();

    // Binary search on sorted array — call sortLogs() first
    LogEntry searchBySeverity(int score);

    // Load logs from disk into cache
    void loadFromFile(String filePath);

    // Return frequency of a pattern in the Trie
    int getPatternFrequency(String pattern);
}