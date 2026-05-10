package com.logshield.util;

/**
 * ANSI escape code constants for terminal colour output.
 *
 * <p>Used by LogShieldApp to colour-code log entries by severity:
 * ERROR → Red, WARN → Yellow, INFO → Green.</p>
 *
 * <p>ANSI codes are supported on all Unix/macOS terminals and on
 * Windows 10+ terminals (Windows Terminal, PowerShell 7+, IntelliJ
 * built-in terminal). They are ignored gracefully on unsupported
 * terminals — output remains readable, just without colour.</p>
 *
 * <p>Each constant is a String prefix that switches the terminal
 * colour. {@link #RESET} must follow every coloured string to
 * restore the default terminal colour — failing to reset causes
 * all subsequent output to inherit the last colour set.</p>
 *
 * @author  Virochan V
 * @version 1.0
 */
public class ConsoleColour {

    // Private constructor — constants-only utility class, never instantiated
    private ConsoleColour() {}

    // ANSI escape code format: \033[ = escape sequence start
    // 31m = red, 33m = yellow, 32m = green, 0m = reset to default

    /** Red — used for ERROR severity entries */
    public static final String RED    = "\033[31m";

    /** Yellow — used for WARN severity entries */
    public static final String YELLOW = "\033[33m";

    /** Green — used for INFO severity entries */
    public static final String GREEN  = "\033[32m";

    /** Bold — used for section headers and highlights */
    public static final String BOLD   = "\033[1m";

    /** Cyan — used for system INFO messages */
    public static final String CYAN   = "\033[36m";

    /** Resets terminal colour to default — ALWAYS append after coloured output */
    public static final String RESET  = "\033[0m";

    /**
     * Returns the appropriate colour constant for a given log level.
     *
     * <p>Centralises the level-to-colour mapping so it never needs
     * to be duplicated across multiple display methods.</p>
     *
     * <p><b>Time Complexity:</b> O(1) — switch on three known values.<br>
     * <b>Space Complexity:</b> O(1) — returns existing constant reference.</p>
     *
     * @param level the log level string — INFO, WARN, or ERROR
     * @return the ANSI colour constant for that level
     */
    public static String forLevel(String level) {
        switch (level.toUpperCase()) {
            case "ERROR": return RED;
            case "WARN":  return YELLOW;
            case "INFO":  return GREEN;
            default:      return RESET;
        }
    }
}