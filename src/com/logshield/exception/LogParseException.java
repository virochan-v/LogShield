package com.logshield.exception;

/**
 * Thrown when a CSV line from the log file cannot be parsed into a LogEntry.
 *
 * <p>Common causes:</p>
 * <ul>
 *   <li>Fewer than 4 comma-separated fields in the line</li>
 *   <li>Severity score field is not a valid integer</li>
 *   <li>Line is blank or contains only whitespace</li>
 * </ul>
 *
 * <p>By throwing this instead of letting {@link ArrayIndexOutOfBoundsException}
 * propagate, the caller receives actionable information — specifically which
 * line failed and why — rather than a generic JVM error with no business context.</p>
 *
 * @author  Virochan V
 * @version 1.0
 * @see     LogShieldException
 */
public class LogParseException extends LogShieldException {

    // The raw CSV line that triggered the failure — stored for diagnostics
    private final String rawLine;

    public LogParseException(String rawLine, String reason) {
        super("Failed to parse log line: [" + rawLine + "] Reason: " + reason);
        this.rawLine = rawLine;
    }

    public LogParseException(String rawLine, String reason, Throwable cause) {
        super("Failed to parse log line: [" + rawLine + "] Reason: " + reason, cause);
        this.rawLine = rawLine;
    }

    // Getter so callers can log or display the offending line separately
    public String getRawLine() {
        return rawLine;
    }
}