package com.example.demo.dto.notification;

import java.util.regex.Pattern;

/**
 * Keeps routing identifiers in notification target fields instead of exposing
 * them in client-facing notification text.
 */
public final class NotificationContentSanitizer {

    private static final Pattern ID_FIELD = Pattern.compile(
            "(?iu)(?:\\s*[，,]\\s*)?"
                    + "(?:[\\p{L}\\p{N}_-]+\\s*)+ID\\s*[：:]\\s*[\\p{L}\\p{N}_-]*"
                    + "(?:\\s*[，,]\\s*)?");
    private static final Pattern EMPTY_PARENTHESES = Pattern.compile("[（(]\\s*[）)]");
    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile("\\s+([，。；：、）)])");
    private static final Pattern SPACE_AFTER_OPENING_PARENTHESIS = Pattern.compile("([（(])\\s+");
    private static final Pattern REPEATED_SPACES = Pattern.compile("[ \\t]{2,}");

    private NotificationContentSanitizer() {
    }

    public static String sanitize(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        String sanitized = ID_FIELD.matcher(content).replaceAll("");
        sanitized = EMPTY_PARENTHESES.matcher(sanitized).replaceAll("");
        sanitized = SPACE_BEFORE_PUNCTUATION.matcher(sanitized).replaceAll("$1");
        sanitized = SPACE_AFTER_OPENING_PARENTHESIS.matcher(sanitized).replaceAll("$1");
        sanitized = REPEATED_SPACES.matcher(sanitized).replaceAll(" ");
        return sanitized.trim();
    }
}
