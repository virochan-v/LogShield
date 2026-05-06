package com.logshield.storage;

import com.logshield.model.LogEntry;
import com.logshield.exception.InvalidLevelException;
import com.logshield.exception.LogParseException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all file I/O operations for LogShield's log persistence layer.
 *
 * <p>This class is the single point of contact between the application and the
 * filesystem. No other class in the system should read from or write to a file
 * path directly — all disk access is routed through here.
 *
 * <p>Design constraints:
 * <ul>
 *   <li>Uses {@link java.io.BufferedReader} and {@link java.io.BufferedWriter} only —
 *       no third-party I/O libraries.</li>
 *   <li>All exceptions are caught internally and reported to {@code stderr}.
 *       Methods never propagate {@link java.io.IOException} to callers, so the
 *       application stays alive even when disk operations fail.</li>
 *   <li>Log entries are persisted in CSV format via {@link com.logshield.model.LogEntry#toCSV()}
 *       and restored via {@link com.logshield.model.LogEntry#fromCSV(String)}.</li>
 * </ul>
 *
 * @author  Virochan V
 * @version 2.0
 * @see     com.logshield.model.LogEntry
 * @see     com.logshield.engine.RegistryManager
 */

public class FileHandler {

    /**
     * Appends a single log entry to the specified file in CSV format.
     *
     * <p>The file is opened in <em>append mode</em> on every call, meaning existing
     * content is never overwritten. If the file does not yet exist, it is created
     * automatically by {@link java.io.FileWriter}.
     *
     * <p><b>Time Complexity:</b> O(1) — writes exactly one line regardless of file size.<br>
     * <b>Space Complexity:</b> O(1) — only a fixed 8 KB internal buffer is held in memory;
     * the existing file is never loaded.
     *
     * @param entry    the {@link com.logshield.model.LogEntry} to persist; must not be {@code null}
     * @param filePath the path to the target {@code .txt} file (e.g. {@code "logs.txt"});
     *                 the file is created if it does not exist
     */
    public void appendLog(LogEntry entry, String filePath) {

        // FileWriter(path, true) — the 'true' flag is the magic word.
        // Without it, Java truncates (empties) the file every time you open it.
        // With it, the OS seek-pointer starts at the very end of the file,
        // so every write naturally appends without touching existing data.
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {

            writer.write(entry.toCSV());  // Convert to "timestamp,level,message,score"
            writer.newLine();             // OS-correct line ending (\n on Unix, \r\n on Windows)

        } catch (IOException e) {
            // We do NOT rethrow — a logging system should never crash the host app
            // just because it couldn't write a log. We report and move on.
            System.err.println("[FileHandler] ERROR: Could not append log entry to '"
                    + filePath + "'. Reason: " + e.getMessage());
        }
    }


    /**
     * Reads all log entries from the specified file and returns them as a list.
     *
     * <p>Each line in the file is parsed using {@link com.logshield.model.LogEntry#fromCSV(String)}.
     * Blank lines and malformed rows are silently skipped with a warning printed to
     * {@code stderr}, so a single corrupt line never aborts the entire load.
     *
     * <p>If the file does not exist (e.g. on first application startup), an empty
     * {@link java.util.ArrayList} is returned — this is treated as normal behaviour,
     * not an error condition.
     *
     * <p><b>Time Complexity:</b> O(n) — every line is read and parsed exactly once.<br>
     * <b>Space Complexity:</b> O(n) — the returned list holds one {@code LogEntry}
     * object per valid line in the file.
     *
     * @param  filePath the path to the {@code .txt} file to read from
     * @return a {@link java.util.List} of parsed {@link com.logshield.model.LogEntry} objects;
     *         never {@code null}, but may be empty if the file is absent or contains no valid lines
     */
    public List<LogEntry> loadLogs(String filePath) {

        List<LogEntry> logs = new ArrayList<>();
        File file = new File(filePath);

        // Guard clause — if the file simply hasn't been created yet (first run),
        // return an empty list instead of crashing. This is expected behaviour,
        // not an error. The file will be created the moment the first log is appended.
        if (!file.exists()) {
            System.out.println("[FileHandler] INFO: Log file not found at '"
                    + filePath + "'. Starting with an empty log store.");
            return logs;
        }

        // BufferedReader wraps FileReader and reads the file in large internal chunks
        // (default 8 KB), drastically reducing the number of actual disk reads
        // compared to reading one character at a time.
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {

            String line;

            // readLine() returns null at end-of-file — the standard Java idiom.
            // We assign inside the while condition to avoid reading one line
            // and then checking a separate variable.
            while ((line = reader.readLine()) != null) {

                // Skip blank lines that can appear if the file was manually edited
                // or if write was interrupted mid-flush.
                if (line.trim().isEmpty()) continue;

                try {
                    logs.add(LogEntry.fromCSV(line));
                } catch (LogParseException e) {
                    // Caller gets the exact line and reason — not a generic JVM error
                    System.err.println("[FileHandler] WARN: " + e.getMessage());
                } catch (InvalidLevelException e) {
                    // Level field in this CSV line is not INFO/WARN/ERROR
                    System.err.println("[FileHandler] WARN: Skipping entry with invalid level — "
                            + e.getMessage());
                }
            }

        } catch (FileNotFoundException e) {
            // This branch is theoretically unreachable (we checked file.exists() above),
            // but the compiler requires it. If somehow the file disappears between the
            // exists() check and the open, we catch it here gracefully.
            System.err.println("[FileHandler] ERROR: File disappeared before it could be opened: '"
                    + filePath + "'. Reason: " + e.getMessage());

        } catch (IOException e) {
            // Covers disk errors, permission changes mid-read, or I/O interruptions.
            System.err.println("[FileHandler] ERROR: Failed while reading '"
                    + filePath + "'. Partial data may have been loaded. Reason: "
                    + e.getMessage());
        }

        return logs;
    }
}