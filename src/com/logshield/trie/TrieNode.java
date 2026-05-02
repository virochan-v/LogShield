package com.logshield.trie;

import java.util.HashMap;

/**
 * Represents a single node in a character-level Trie data structure.
 *
 * <p>Each {@code TrieNode} corresponds to one character in an inserted pattern.
 * Nodes are linked together through the {@code children} map, forming branches
 * that spell out patterns from root to leaf.</p>
 *
 * <p>A node is considered a <em>terminal node</em> when {@code isEndOfWord} is
 * {@code true}, meaning a complete pattern ends at this node. The {@code frequency}
 * field tracks how many times that pattern has been explicitly incremented via
 * {@link Trie#incrementFrequency(String)}.</p>
 *
 * <p><b>Memory note:</b> Children are stored in a {@link java.util.HashMap} so only
 * branches that actually exist consume memory — no fixed 26-slot array is allocated
 * regardless of alphabet size.</p>
 *
 * @author  Virochan V
 * @version 1.0
 * @see     Trie
 */
public class TrieNode {

    /**
     * Maps each outgoing character edge to the corresponding child {@code TrieNode}.
     *
     * <p>Only characters that have been explicitly inserted are present as keys,
     * keeping memory proportional to actual data rather than alphabet size.</p>
     */
    HashMap<Character, TrieNode> children;

    /**
     * Indicates whether a complete pattern terminates at this node.
     *
     * <p>{@code true} means at least one call to {@link Trie#insert(String)}
     * ended here. A node can simultaneously be a terminal node <em>and</em>
     * have children — for example, if both {@code "ERR"} and {@code "ERROR"}
     * are inserted, the node for the second {@code R} in {@code "ERR"} is
     * terminal but still has a child {@code O}.</p>
     */
    boolean isEndOfWord;

    /**
     * The number of times {@link Trie#incrementFrequency(String)} has been called
     * for the pattern that terminates at this node.
     *
     * <p>This value is {@code 0} by default and is only meaningful when
     * {@code isEndOfWord} is {@code true}. It is not modified by
     * {@link Trie#insert(String)} alone.</p>
     */
    int frequency;

    /**
     * Constructs a new {@code TrieNode} with an empty children map,
     * {@code isEndOfWord} set to {@code false}, and {@code frequency} set to {@code 0}.
     *
     * <p>Inline comments explain each field's initial intent:</p>
     */
    public TrieNode() {
        this.children    = new HashMap<>();  // lazy-populated; only branches that exist are created
        this.isEndOfWord = false;            // default: mid-word node, not a pattern endpoint
        this.frequency   = 0;               // no incrementFrequency() call has targeted this node yet
    }
}