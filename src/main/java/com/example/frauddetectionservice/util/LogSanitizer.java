package com.example.frauddetectionservice.util;

import java.util.regex.Pattern;

/**
 * Last line of defence for logs. Application code is written so sensitive values never reach a logger;
 * this sanitizer catches anything that slips through anyway:
 *
 * <ul>
 *   <li>Card-like digit runs (13-19 digits, optional space or dash separators) are replaced. The check is
 *       deliberately not Luhn-based, so a PAN with a bad check digit is still masked. Over-masking a long
 *       number in a log line is an acceptable cost.</li>
 *   <li>Key/value pairs for known sensitive keys (card number, PAN, CVV, secrets, tokens) keep the key and drop the value.</li>
 *   <li>For single-line messages, CR and LF are escaped so attacker input cannot forge log lines.</li>
 * </ul>
 */
public final class LogSanitizer {

    private static final Pattern PAN_CANDIDATE =
            Pattern.compile("(?<![0-9])[0-9](?:[ -]?[0-9]){12,18}(?![0-9])");

    private static final Pattern SENSITIVE_KEY_VALUE = Pattern.compile(
            "(?i)\\b(card[_-]?number|pan|cvv|cvc|secret|password|api[_-]?key|token|authorization)"
                    + "(\"?\\s*[:=]\\s*\"?)[^\\s\"',;&}\\[\\]]+");

    private static final String PAN_REPLACEMENT = "[REDACTED-PAN]";
    private static final String VALUE_REPLACEMENT = "$1$2[REDACTED]";

    private LogSanitizer() {
    }

    /** For a single-line log message. Also escapes line breaks to prevent log forging. */
    public static String sanitizeMessage(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        return mask(message.replace("\r", "\\r").replace("\n", "\\n"));
    }

    /** For multi-line text such as stack traces, where line breaks are structure and must be kept. */
    public static String maskOnly(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return mask(text);
    }

    private static String mask(String text) {
        String masked = PAN_CANDIDATE.matcher(text).replaceAll(PAN_REPLACEMENT);
        return SENSITIVE_KEY_VALUE.matcher(masked).replaceAll(VALUE_REPLACEMENT);
    }
}
