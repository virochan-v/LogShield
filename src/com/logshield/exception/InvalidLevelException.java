package com.logshield.exception;

/**
 * Thrown when a log level string is not one of the recognised values.
 *
 * <p>LogShield recognises exactly three levels: INFO, WARN, ERROR.
 * Any other value passed to the {@code LogEntry} constructor triggers
 * this exception, preventing silent data corruption where an unrecognised
 * level defaults to severity 1 without the caller being aware.</p>
 *
 * <p><b>Before this exception existed:</b> passing "CRITICAL" would silently
 * assign severityScore = 1 (INFO level) via {@code getOrDefault()} —
 * the log would appear in the system with the wrong severity and no warning.</p>
 *
 * <p><b>After:</b> the constructor immediately rejects the invalid input,
 * forcing the caller to handle the error explicitly.</p>
 *
 * @author  Virochan V
 * @version 1.0
 * @see     LogShieldException
 */
public class InvalidLevelException extends LogShieldException {

    // The unrecognised level string — stored so the caller knows what was rejected
    private final String invalidLevel;

    public InvalidLevelException(String invalidLevel) {
        super("Unrecognised log level: '"
                + invalidLevel
                + "'. Expected one of: INFO, WARN, ERROR.");
        this.invalidLevel = invalidLevel;
    }

    public String getInvalidLevel() {
        return invalidLevel;
    }
}