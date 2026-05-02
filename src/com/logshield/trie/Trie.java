package com.logshield.trie;

/**
 * A character-level Trie (prefix tree) optimised for log pattern storage,
 * exact-match search, and occurrence frequency tracking.
 *
 * <p>Every character of an inserted pattern occupies one {@link TrieNode},
 * and patterns that share a common prefix share the same node path from the
 * root — making this structure significantly more memory-efficient than storing
 * each pattern as an independent {@link java.util.HashMap} key when the dataset
 * contains many overlapping prefixes (e.g., {@code "ERROR:"}, {@code "ERROR: disk"},
 * {@code "ERROR: network"}).</p>
 *
 * <h2>Time Complexity Summary</h2>
 * <table border="1">
 *   <tr><th>Operation</th><th>Time</th><th>Where L = pattern length</th></tr>
 *   <tr><td>{@link #insert}</td><td>O(L)</td><td>one node per character</td></tr>
 *   <tr><td>{@link #search}</td><td>O(L)</td><td>one traversal per character</td></tr>
 *   <tr><td>{@link #incrementFrequency}</td><td>O(L)</td><td>insert + traversal</td></tr>
 *   <tr><td>{@link #getFrequency}</td><td>O(L)</td><td>traversal + field read</td></tr>
 * </table>
 *
 * <h2>Space Complexity</h2>
 * <p>O(N × L) worst-case where N = number of patterns and L = average length,
 * with best-case savings when patterns share long common prefixes.</p>
 *
 * <p><b>Thread safety:</b> This class is <em>not</em> thread-safe. External
 * synchronisation is required if multiple threads insert or query concurrently.</p>
 *
 * @author  Virochan V
 * @version 1.0
 * @see     TrieNode
 */
public class Trie {

    /**
     * The root node of the Trie. It holds no character value itself and is never
     * returned to callers — it exists solely as the fixed entry point from which
     * all pattern traversals begin.
     */
    private final TrieNode root;

    public Trie() {
        this.root = new TrieNode();
    }

    /**
     * Inserts the given pattern into the Trie, creating {@link TrieNode} branches
     * for any characters not yet present.
     *
     * <p>This operation is <em>idempotent</em>: inserting a pattern that already
     * exists simply re-confirms {@code isEndOfWord = true} on the terminal node
     * without duplicating any structure or resetting {@code frequency}.</p>
     *
     * <p><b>Time Complexity:</b> O(L) — exactly one node is visited or created
     * per character.<br>
     * <b>Space Complexity:</b> O(L) worst-case when no prefix is shared with
     * existing patterns; O(1) best-case when the full pattern already exists.</p>
     *
     * @param word the log pattern or token to insert; {@code null} and empty
     *             strings are silently ignored
     */
    public void insert(String word) {
        if (word == null || word.isEmpty()) return; // guard: skip blank patterns

        TrieNode current = root; // start traversal from root

        for (char ch : word.toCharArray()) {             // iterate each character — O(L)
            // If this character branch doesn't exist yet, create a fresh node
            current.children.putIfAbsent(ch, new TrieNode());

            // Move down to the child node for this character
            current = current.children.get(ch);
        }

        current.isEndOfWord = true; // mark the terminal node for this pattern
    }

    /**
     * Returns {@code true} if and only if the exact pattern was previously inserted
     * into the Trie via {@link #insert(String)}.
     *
     * <p>This is a <em>full-word match</em>, not a prefix match. For example, if
     * only {@code "ERROR"} has been inserted, then {@code search("ERR")} returns
     * {@code false} because the node at {@code R} (index 2) does not have
     * {@code isEndOfWord = true}.</p>
     *
     * <p><b>Time Complexity:</b> O(L) — one node lookup per character, plus a
     * single boolean read at the terminal node.</p>
     *
     * @param word the pattern to look up; {@code null} and empty strings return
     *             {@code false}
     * @return {@code true} if the pattern exists as a complete inserted entry,
     *         {@code false} otherwise
     */
    public boolean search(String word) {
        if (word == null || word.isEmpty()) return false; // guard: nothing to search

        TrieNode current = root;

        for (char ch : word.toCharArray()) {      // walk each character — O(L)
            if (!current.children.containsKey(ch)) {
                return false; // character branch missing → pattern not in Trie
            }
            current = current.children.get(ch);   // descend to next node
        }

        return current.isEndOfWord; // true only if this node is a registered endpoint
    }

    /**
     * Increments the occurrence counter ({@link TrieNode#frequency}) at the terminal
     * node of the given pattern by exactly 1.
     *
     * <p>If the pattern has not been inserted yet, it is inserted automatically
     * before the counter is incremented — so the resulting frequency is {@code 1}
     * after the first call, {@code 2} after the second, and so on.</p>
     *
     * <p><b>Time Complexity:</b> O(L) for the implicit {@link #insert(String)} call,
     * plus O(L) for the traversal to the terminal node — effectively O(L) overall
     * since both passes are linear in pattern length.</p>
     *
     * @param pattern the log pattern whose occurrence count should increase;
     *                {@code null} and empty strings are silently ignored
     */
    public void incrementFrequency(String pattern) {
        if (pattern == null || pattern.isEmpty()) return;

        // Ensure the pattern is present before incrementing
        insert(pattern); // insert is idempotent — safe to call even if already present

        TrieNode current = root;

        for (char ch : pattern.toCharArray()) {   // traverse to the terminal node — O(L)
            current = current.children.get(ch);   // safe: insert() guarantees nodes exist
        }

        current.frequency++; // increment occurrence count at the terminal node
    }

    /**
     * Returns the number of times {@link #incrementFrequency(String)} has been
     * called for the given pattern.
     *
     * <p>Returns {@code 0} in any of the following cases:</p>
     * <ul>
     *   <li>The pattern is {@code null} or empty.</li>
     *   <li>The pattern was never inserted into the Trie.</li>
     *   <li>The pattern was inserted via {@link #insert(String)} but
     *       {@link #incrementFrequency(String)} was never called for it.</li>
     * </ul>
     *
     * <p><b>Time Complexity:</b> O(L) — one node lookup per character, plus a
     * single integer read at the terminal node.</p>
     *
     * @param pattern the pattern to query; {@code null} and empty strings return
     *                {@code 0}
     * @return the occurrence count for this pattern, or {@code 0} if absent or
     *         never incremented
     */
    public int getFrequency(String pattern) {
        if (pattern == null || pattern.isEmpty()) return 0;

        TrieNode current = root;

        for (char ch : pattern.toCharArray()) {         // walk to terminal node — O(L)
            if (!current.children.containsKey(ch)) {
                return 0; // prefix breaks early → pattern was never inserted
            }
            current = current.children.get(ch);
        }

        // Return frequency only if this is a true end-of-word node
        return current.isEndOfWord ? current.frequency : 0;
    }
}