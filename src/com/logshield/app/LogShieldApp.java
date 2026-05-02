package com.logshield.app;

import com.logshield.model.LogEntry;
import com.logshield.engine.RegistryManager;
import com.logshield.trie.Trie;

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
 * @version 1.0
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
     *       initialisation, preventing accidental replacement mid-session.</li>
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

        // Obtain the singleton RegistryManager — NEVER use 'new RegistryManager()'
        RegistryManager registry = RegistryManager.getInstance();

        // Load persisted logs into the HashMap cache before showing the menu
        System.out.println("[LogShield] Loading logs from: " + LOG_FILE_PATH);
        registry.loadFromFile(LOG_FILE_PATH); // FileHandler reads and populates the cache

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

                case 1: // Option 1: collect id, message, severity from user → build LogEntry → delegate to RegistryManager
                    System.out.print("Enter timestamp (e.g. 2024-01-15 10:30:00): ");
                    String timestamp = scanner.nextLine().trim();

                    System.out.print("Enter level (INFO / WARN / ERROR): ");
                    String level = scanner.nextLine().trim().toUpperCase();

                    System.out.print("Enter message: ");
                    String message = scanner.nextLine().trim();

                    LogEntry entry = new LogEntry(timestamp, level, message);
                    registry.addLog(entry);
                    System.out.println("[OK] Log entry added: " + entry);
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
                    System.out.println("\n── Sorted Logs (ascending severity) ──");
                    for (LogEntry e : registry.getSortedLogs()) {  // array, not List
                        System.out.println(e);
                    }
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
                        System.out.println("[FOUND] " + found);
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
                        System.out.println("\n── All Logs ──");
                        for (LogEntry e : all) {
                            System.out.println(e); // LogEntry.toString() should format cleanly
                        }
                    }
                    break;

                case 7: // Option 7: flip the loop flag → fall through to scanner.close()
                    System.out.println("[LogShield] Goodbye.");
                    running = false; // break the while loop gracefully
                    break;

                default:
                    System.out.println("[ERROR] Invalid option. Choose 1–7.");
            }
        }

        scanner.close(); // release System.in — always close in the same class that opened it
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
    private static void printMenu() {
        System.out.println("\n╔══════════════════════════════╗");
        System.out.println("║       LogShield Menu         ║");
        System.out.println("╠══════════════════════════════╣");
        System.out.println("║ 1. Add log entry manually    ║");
        System.out.println("║ 2. Load logs from file       ║");
        System.out.println("║ 3. Sort logs by severity     ║");
        System.out.println("║ 4. Search log by severity    ║");
        System.out.println("║ 5. Search pattern in Trie    ║");
        System.out.println("║ 6. Display all logs          ║");
        System.out.println("║ 7. Exit                      ║");
        System.out.println("╚══════════════════════════════╝");
        System.out.print("Choice: ");
    }
}