package com.logshield.trie;

/**
 * Service layer wrapper around {@link Trie} for log pattern frequency tracking.
 *
 * <p>Extracted from {@link com.logshield.engine.RegistryManager} to satisfy
 * the Single Responsibility Principle — RegistryManager should coordinate
 * data flow, not own pattern-matching logic.</p>
 *
 * <p>TrieService owns exactly one responsibility: track how many times
 * each log message pattern has been seen. Nothing else lives here.</p>
 *
 * <p><b>Time Complexity:</b> all operations are O(L) where L = pattern length.<br>
 * <b>Space Complexity:</b> O(total characters) across all inserted patterns,
 * with shared prefixes occupying a single node path.</p>
 *
 * @author  Virochan V
 * @version 1.0
 * @see     Trie
 */
public class TrieService {

    // The underlying Trie — private so callers never bypass TrieService methods
    private Trie trie;

    public TrieService() {
        this.trie = new Trie();
    }

    // -------------------------------------------------------------------------
    // trackPattern(String message)
    // Time Complexity : O(L) — insert + incrementFrequency both traverse L nodes
    // Called every time a log entry is added or loaded from disk
    // -------------------------------------------------------------------------
    public void trackPattern(String message) {
        if (message == null || message.isEmpty()) return;
        trie.incrementFrequency(message);
    }

    // -------------------------------------------------------------------------
    // getFrequency(String pattern)
    // Time Complexity : O(L) — traverses L nodes to reach terminal node
    // Returns 0 if pattern was never tracked
    // -------------------------------------------------------------------------
    public int getFrequency(String pattern) {
        if (pattern == null || pattern.isEmpty()) return 0;
        return trie.getFrequency(pattern);
    }

    // -------------------------------------------------------------------------
    // reset()
    // Time Complexity : O(1) — creates a new Trie, old one is GC'd
    // Called at start of loadFromFile() to prevent frequency double-counting
    // -------------------------------------------------------------------------
    public void reset() {
        this.trie = new Trie();
    }
}