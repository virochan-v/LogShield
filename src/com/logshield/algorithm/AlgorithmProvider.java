package com.logshield.algorithm;

import com.logshield.model.LogEntry;  // Only LogShield import allowed

/**
 * AlgorithmProvider
 *
 * Part of LogShield — a CLI log anomaly detector.
 *
 * Provides core sorting and searching algorithms adapted for LogEntry objects.
 * All methods are static; this class is never instantiated.
 *
 * Algorithms:
 *   - cycleSort     : O(n²) time, O(1) space — minimizes array writes
 *   - binarySearch  : O(log n) time, O(1) space — requires a sorted array
 *
 * @author  Virochan V
 * @version 1.0
 */
public class AlgorithmProvider {

    // Private constructor: prevents anyone from doing 'new AlgorithmProvider()'
    // This is the standard pattern for a utility/helper class in Java
    private AlgorithmProvider() {}


    /*
     * cycleSort(LogEntry[] arr)
     *
     * Time Complexity  : O(n²) — outer loop runs n times; inner counting loop also runs up to n times
     * Space Complexity : O(1)  — sorts in-place; only a handful of local variables, no extra array
     *
     * I chose this because Cycle Sort minimizes the number of WRITES to the array —
     * each element is moved at most once, which matters when writes are expensive
     * (e.g. writing to disk or a database). It also gave me a natural place to
     * practice adapting an integer algorithm to work on objects.
     */
    public static void cycleSort(LogEntry[] arr) {

        int n = arr.length;

        // Outer loop: each value of 'cycleStart' is the beginning of one cycle
        // A "cycle" is a closed chain of positions that need to rotate together
        // We go up to n-1 because the last element has nowhere to be displaced to
        for (int cycleStart = 0; cycleStart < n - 1; cycleStart++) {

            // 'item' is the element we are currently trying to place correctly
            LogEntry item = arr[cycleStart];

            // --- STEP 1: Find the correct position for 'item' ---
            // Count how many elements in the REST of the array are smaller than item
            // That count IS the correct index for item (0-based ascending sort)
            int pos = cycleStart;  // Start position search from cycleStart onward

            for (int i = cycleStart + 1; i < n; i++) {
                // Compare by severityScore — this is the key difference vs int arrays
                // With int[], you'd write: arr[i] < item
                // With objects, you extract the sort key from each side
                if (arr[i].getSeverityScore() < item.getSeverityScore()) {
                    pos++;  // One more element is smaller, so item belongs one slot further right
                }
            }

            // If pos == cycleStart, item is already in its correct place — skip this cycle
            // Without this guard, we'd waste time (and incorrectly count writes) on no-ops
            if (pos == cycleStart) {
                continue;
            }

            // --- STEP 2: Skip over duplicates ---
            // If several entries share the same severityScore, we must not place item
            // ON TOP of an equal element — we slide pos past all equals first
            while (item.getSeverityScore() == arr[pos].getSeverityScore()) {
                pos++;  // Advance until we find a slot that isn't an exact score match
            }

            // --- STEP 3: Place 'item' into its correct position ---
            // Swap item with whatever is currently sitting at pos
            // The displaced element becomes the new 'item' — it needs placing next
            LogEntry temp = arr[pos];
            arr[pos] = item;
            item = temp;  // 'item' now holds the element we just bumped out

            // --- STEP 4: Rotate the rest of the cycle ---
            // Keep placing 'item' until it lands back at cycleStart (cycle closes)
            while (pos != cycleStart) {

                // Recalculate the correct position for the NEW 'item'
                // Same logic as STEP 1: count how many elements are smaller
                pos = cycleStart;  // Reset position counter for this new item

                for (int i = cycleStart + 1; i < n; i++) {
                    if (arr[i].getSeverityScore() < item.getSeverityScore()) {
                        pos++;
                    }
                }

                // Skip duplicates again for the same reason as STEP 2
                while (item.getSeverityScore() == arr[pos].getSeverityScore()) {
                    pos++;
                }

                // Place this item and pick up the displaced element
                // When pos finally equals cycleStart, the displaced element goes back
                // to cycleStart, closing the cycle — the while condition then exits
                temp = arr[pos];
                arr[pos] = item;
                item = temp;
            }
        }
    }


    /*
     * binarySearch(LogEntry[] sortedArr, int targetScore)
     *
     * Time Complexity  : O(log n) — search space halves on every iteration
     * Space Complexity : O(1)     — only three integer pointers; no recursion stack
     *
     * I chose this because it is the textbook companion to a sorted array:
     * once cycleSort guarantees order, Binary Search exploits that order to find
     * any target in logarithmic time instead of scanning every element linearly.
     */
    public static int binarySearch(LogEntry[] sortedArr, int targetScore) {

        int low  = 0;                      // Left boundary of the current search window
        int high = sortedArr.length - 1;   // Right boundary of the current search window

        // Continue as long as the window contains at least one element
        while (low <= high) {

            // Calculate midpoint without integer overflow risk
            // Writing (low + high) / 2 can overflow if both are near Integer.MAX_VALUE
            // This form is equivalent but safe: low + half the remaining distance
            int mid = low + (high - low) / 2;

            int midScore = sortedArr[mid].getSeverityScore();  // Extract key once; reuse below

            if (midScore == targetScore) {
                // Found a match — return this index immediately
                // Note: if duplicates exist, this may not be the FIRST match; it is A match
                return mid;

            } else if (midScore < targetScore) {
                // Mid is too small → target must be in the RIGHT half
                // Move low up past mid to shrink the window from the left
                low = mid + 1;

            } else {
                // Mid is too large → target must be in the LEFT half
                // Move high down past mid to shrink the window from the right
                high = mid - 1;
            }
        }

        // Loop exited without returning → target score does not exist in the array
        return -1;
    }
}