package com.logshield.exception;

/**
 * Base checked exception for all LogShield-specific error conditions.
 *
 * <p>All custom exceptions in LogShield extend this class so callers
 * can choose to catch at any granularity:</p>
 * <ul>
 *   <li>Catch {@code LogShieldException} to handle all LogShield errors at once.</li>
 *   <li>Catch a specific subclass to handle one error type precisely.</li>
 * </ul>
 *
 * <p>Extends {@link Exception} making it a <b>checked exception</b> —
 * the compiler enforces that every method throwing this class either
 * declares it with {@code throws} or wraps it in a try-catch block.
 * This is intentional: LogShield errors are recoverable conditions,
 * not programming bugs.</p>
 *
 * @author  Virochan V
 * @version 1.0
 */
public class LogShieldException extends Exception {

    // Pass the error message up to Exception — accessible via getMessage()
    public LogShieldException(String message) {
        super(message);
    }

    // Allow wrapping a lower-level cause — preserves the original stack trace
    // Example: wrapping ArrayIndexOutOfBoundsException inside LogParseException
    public LogShieldException(String message, Throwable cause) {
        super(message, cause);
    }
}