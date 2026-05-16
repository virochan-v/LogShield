package com.logshield.app;

import com.logshield.engine.IRegistry;
import com.logshield.model.LogEntry;
import com.logshield.engine.RegistryManager;
import com.logshield.exception.InvalidLevelException;
import com.logshield.util.ConsoleColour;

import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

/**
 * Entry point and sole CLI controller for the LogShield log anomaly detection system.
 *
 * <p>This class owns three responsibilities and <em>only</em> these three:</p>
 * <ol>
 *   <li>Own the {@link java.util.Scanner} lifecycle — opened once on JVM start,
 *       closed once on exit. No other class in the system should create a
 *       {@code Scanner} on {@code System.in}.</li>
 *   <li>Drive the interactive menu loop — read user input, dispatch to the
 *       correct service, and print results.</li>
 *   <li>Delegate every business operation to {@link RegistryManager} or
 *       {@link com.logshield.trie.Trie} — this class contains zero
 *       data-processing logic of its own.</li>
 * </ol>
 *
 * <p><b>Design constraints:</b></p>
 * <ul>
 *   <li>{@link RegistryManager} is always obtained via
 *       {@link RegistryManager#getInstance()} — {@code new RegistryManager()}
 *       must never appear here.</li>
 *   <li>All {@code Scanner} calls are wrapped in try-catch for
 *       {@link java.util.InputMismatchException} to prevent the menu loop
 *       from crashing on non-numeric input.</li>
 *   <li>The {@link com.logshield.trie.Trie} is rebuilt on demand (option 5)
 *       rather than cached, keeping this class stateless between menu turns.</li>
 * </ul>
 *
 * @author  Virochan V
 * @version 7.0
 * @see     RegistryManager
 * @see     com.logshield.trie.Trie
 */
public class LogShieldApp {

    /**
     * Default path to the log persistence file used on startup and as a fallback
     * when the user presses Enter without specifying a custom path in option 2.
     *
     * <p>Change this constant to point at your actual log file location before
     * running the application. The path is relative to the JVM working directory
     * unless an absolute path is supplied.</p>
     */
    private static final String LOG_FILE_PATH = RegistryManager.LOG_FILE_PATH;

    /**
     * The single {@link java.util.Scanner} instance for {@code System.in} in this
     * JVM process.
     *
     * <p>Declared {@code static final} to enforce two rules simultaneously:</p>
     * <ul>
     *   <li>{@code static} — one instance per JVM, never per-object.</li>
     *   <li>{@code final} — the reference cannot be reassigned after
     *       initialization, preventing accidental replacement mid-session.</li>
     * </ul>
     *
     * <p><b>Warning:</b> creating a second {@code Scanner(System.in)} anywhere in
     * the codebase will cause both instances to race on the same underlying stream,
     * producing dropped or duplicated tokens.</p>
     */
    private static final Scanner scanner = new Scanner(System.in);

    /**
     * Application entry point. Bootstraps LogShield and runs the interactive
     * menu loop until the user selects option 7 (Exit).
     *
     * <p>Startup sequence:</p>
     * <ol>
     *   <li>Obtain the {@link RegistryManager} singleton.</li>
     *   <li>Load persisted logs from {@link #LOG_FILE_PATH} into the HashMap cache.</li>
     *   <li>Enter the menu loop — read, dispatch, print, repeat.</li>
     *   <li>On exit: close {@link #scanner} and return.</li>
     * </ol>
     *
     * <p><b>Input safety:</b> every {@link java.util.Scanner#nextInt()} call is
     * wrapped in a try-catch for {@link java.util.InputMismatchException}.
     * Invalid tokens are flushed with {@link java.util.Scanner#nextLine()} so the
     * loop restarts cleanly rather than spinning on the same bad input.</p>
     *
     * @param args command-line arguments — not used; LogShield is menu-driven
     */

    public static void main(String[] args) {

        // Print banner first — before any loading messages
        printBanner();

        // Obtain the singleton RegistryManager — NEVER use 'new RegistryManager()'
        IRegistry registry = RegistryManager.getInstance();

        // Load persisted logs into the HashMap cache before showing the menu
        System.out.println(ConsoleColour.CYAN + "[INFO] Loading logs from: "
                + LOG_FILE_PATH + ConsoleColour.RESET);
        registry.loadFromFile(LOG_FILE_PATH);
        System.out.println(ConsoleColour.CYAN + "[INFO] Trie indexed · System ready"
                + ConsoleColour.RESET);
        System.out.println(ConsoleColour.CYAN + "[INFO] Enter a number from 1-8 to navigate"
                + ConsoleColour.RESET);
        boolean running = true; // controls the menu loop; set false only on option 7

        while (running) {
            printMenu(); // extract menu print to keep the loop body readable

            int choice = -1; // sentinel value; replaced by valid input or stays -1 on error

            try {
                choice = scanner.nextInt(); // blocks until user enters a number
            } catch (InputMismatchException e) {
                // User typed letters instead of a number — consume the bad token
                System.out.println("[ERROR] Please enter a number between 1 and 7.");
                scanner.nextLine(); // flush the invalid input so the loop doesn't spin infinitely
                continue;           // restart the loop without processing the invalid choice
            }

            scanner.nextLine(); // consume the leftover newline after nextInt()

            switch (choice) {

                case 1:
                    // ── Validate timestamp ───────────────────────────────────
                    System.out.print("Enter timestamp (e.g. 2024-01-15 10:30:00): ");
                    String timestamp = scanner.nextLine().trim();

                    // Guard: timestamp cannot be blank — it is the HashMap key
                    // A blank timestamp would silently overwrite other entries
                    if (timestamp.isEmpty()) {
                        System.out.println(ConsoleColour.RED
                                + "[ERROR] Timestamp cannot be blank. Entry not saved."
                                + ConsoleColour.RESET);
                        break;
                    }

                    // ── Validate level ───────────────────────────────────────
                    System.out.print("Enter level (INFO / WARN / ERROR): ");
                    String level = scanner.nextLine().trim().toUpperCase();

                    // Guard: level must be one of the three recognised values
                    // InvalidLevelException will catch this — but we validate early
                    // to give a friendlier message before hitting the exception
                    if (!level.equals("INFO") && !level.equals("WARN") && !level.equals("ERROR")) {
                        System.out.println(ConsoleColour.RED
                                + "[ERROR] Invalid level '" + level
                                + "'. Must be INFO, WARN, or ERROR. Entry not saved."
                                + ConsoleColour.RESET);
                        break;
                    }

                    // ── Validate message ─────────────────────────────────────
                    System.out.print("Enter message: ");
                    String message = scanner.nextLine().trim();

                    // Guard: message cannot be blank — an empty log entry has no diagnostic value
                    if (message.isEmpty()) {
                        System.out.println(ConsoleColour.RED
                                + "[ERROR] Message cannot be blank. Entry not saved."
                                + ConsoleColour.RESET);
                        break;
                    }

                    // ── All inputs valid — create and store the entry ────────
                    try {
                        LogEntry entry = new LogEntry(timestamp, level, message);
                        registry.addLog(entry);
                        System.out.println(ConsoleColour.GREEN
                                + "[OK] Log entry added successfully."
                                + ConsoleColour.RESET);
                        // Show the entry back to the user with colour so they can confirm
                        printColoured(entry);
                    } catch (InvalidLevelException e) {
                        // Should not reach here — pre-validated above
                        // Kept as a safety net in case validation logic drifts
                        System.out.println(ConsoleColour.RED
                                + "[ERROR] " + e.getMessage()
                                + ConsoleColour.RESET);
                    }
                    break;

                case 2: // ── Load logs from file ─────────────────────────────────
                    System.out.print("Enter file path (or press Enter for default): ");
                    String path = scanner.nextLine().trim();
                    if (path.isEmpty()) path = LOG_FILE_PATH;    // fall back to default path

                    registry.loadFromFile(path); // re-populates cache from the given file
                    System.out.println("[OK] Logs loaded from: " + path);
                    break;

                case 3:
                    registry.sortLogs();
                    LogEntry[] sorted = registry.getSortedLogs();
                    System.out.println("\n── Sorted Logs · "
                            + sorted.length + " entries (ascending severity) ──");
                    for (LogEntry e : sorted) {
                        printColoured(e);
                    }
                    System.out.println("── End of sorted view ──");
                    break;

                case 4: // ── Search log by severity score ────────────────────────
                    System.out.print("Enter severity score to search: ");
                    int targetSeverity = -1;
                    try {
                        targetSeverity = Integer.parseInt(scanner.nextLine().trim());
                    } catch (NumberFormatException e) {
                        System.out.println("[ERROR] Must be a valid integer.");
                        break;
                    }

                    // binarySearch requires sorted data — sort first to guarantee correctness
                    registry.sortLogs();
                    LogEntry found = registry.searchBySeverity(targetSeverity); // AlgorithmProvider.binarySearch

                    if (found != null) {
                        System.out.print("[FOUND] ");
                        printColoured(found);
                    } else {
                        System.out.println("[NOT FOUND] No log with severity: " + targetSeverity);
                    }
                    break;

                case 5: // ── Pattern search using Trie (via RegistryManager) ───────────────

                    // We DO NOT build a Trie here.
                    // RegistryManager maintains a single, continuously updated Trie
                    // as logs are added. This avoids rebuilding the structure on every query
                    // and keeps pattern tracking consistent across the application lifecycle.

                    System.out.print("Enter pattern to search in Trie: ");
                    String pattern = scanner.nextLine().trim();

                    // Delegate the lookup to RegistryManager.
                    // This ensures:
                    //   - Single source of truth (Trie lives in engine layer)
                    //   - O(L) lookup time without reconstruction
                    //   - Clean separation between CLI and business logic
                    int freq = registry.getPatternFrequency(pattern);

                    // If frequency > 0 → pattern exists in Trie
                    // If frequency == 0 → pattern was never inserted
                    System.out.println(freq > 0
                            ? "[FOUND] Pattern exists. Frequency: " + freq
                            : "[NOT FOUND] Exact pattern not found. Trie matches full messages only.");

                    break;

                case 6: // ── Display all logs ────────────────────────────────────
                    List<LogEntry> all = registry.getAllLogs();
                    if (all.isEmpty()) {
                        System.out.println("[INFO] No logs currently in memory.");
                    } else {
                        System.out.println("\n── All Logs · " + all.size() + " entries ──");
                        for (LogEntry e : all) {
                            printColoured(e);
                        }
                        System.out.println("── End of log display ──");
                    }
                    break;

                case 7: // ── Show Top 5 Anomalies using MinHeap ──────────────────
                    // O(n log k) — never sorts all n entries, only maintains k in memory
                    LogEntry[] topAnomalies = registry.getTopAnomalies(5);

                    if (topAnomalies.length == 0) {
                        System.out.println("[INFO] No logs in memory.");
                    } else {
                        System.out.println("\n── Top " + topAnomalies.length
                                + " Anomalies (highest severity) ──");
                        // Sort the k results for clean display — O(k log k), negligible
                        java.util.Arrays.sort(topAnomalies,
                                (a, b) -> b.getSeverityScore() - a.getSeverityScore());
                        for (LogEntry e : topAnomalies) {
                            printColoured(e);
                        }
                    }
                    break;

                case 8: // ── Exit ────────────────────────────────────────────────
                    System.out.println("[LogShield] Goodbye.");
                    running = false;
                    break;

                default:
                    System.out.println("[ERROR] Invalid option. Choose 1–8.");
            }
        }

        scanner.close(); // release System.in — always close in the same class that opened it
    }
    /**
     * Prints the LogShield startup banner to stdout.
     * Called once at application launch before loading logs.
     * Gives the application a professional identity at first glance.
     */
    private static void printBanner() {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════╗");
        System.out.println("║        L O G S H I E L D   v1.0           ║");
        System.out.println("║        Real-Time Log Anomaly Detector     ║");
        System.out.println("║        Author : Virochan V                ║");
        System.out.println("║        GitHub : github.com/virochan-v     ║");
        System.out.println("╚═══════════════════════════════════════════╝");
        System.out.println();
    }

    /**
     * Prints the LogShield interactive menu to {@code stdout}.
     *
     * <p>Extracted into its own method for two reasons:</p>
     * <ul>
     *   <li>Keeps the {@code while} loop body focused on input handling and
     *       dispatch — not formatting.</li>
     *   <li>Makes the menu trivial to update (add/remove options) without
     *       touching any control-flow logic.</li>
     * </ul>
     *
     * <p>Called once per loop iteration, immediately before reading user input.</p>
     */
    /**
     * Prints a LogEntry to console with ANSI colour based on severity level.
     * ERROR = Red, WARN = Yellow, INFO = Green.
     * Colour is reset after each entry so subsequent output is unaffected.
     *
     * Time Complexity: O(L) — string formatting proportional to message length
     */
    private static void printColoured(LogEntry entry) {
        String colour = ConsoleColour.forLevel(entry.getLevel());
        System.out.println(colour + entry + ConsoleColour.RESET);
    }

    private static void printMenu() {
        System.out.println("\n╔═══════════════════════════════════════════╗");
        System.out.println("║              LogShield Menu               ║");
        System.out.println("╠═══════════════════════════════════════════╣");
        System.out.println("║  1. Add log entry manually                ║");
        System.out.println("║  2. Load logs from file                   ║");
        System.out.println("║  3. Sort logs by severity (Cycle Sort)    ║");
        System.out.println("║  4. Search log by severity (Binary Search)║");
        System.out.println("║  5. Search pattern in Trie                ║");
        System.out.println("║  6. Display all logs                      ║");
        System.out.println("║  7. Show top 5 anomalies (MinHeap)        ║");
        System.out.println("║  8. Exit                                  ║");
        System.out.println("╚═══════════════════════════════════════════╝");
        System.out.print("Choice: ");
    }
}