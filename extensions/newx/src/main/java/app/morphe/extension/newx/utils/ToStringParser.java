package app.morphe.extension.newx.utils;

/**
 * Minimal parser for Kotlin data class toString output, e.g.
 *
 * <pre>
 * CanonicalPost(id=123, text=hi, media=[MediaContentImage(imageUrl=..., sourceInfo=SourceInfo(...))])
 * </pre>
 *
 * This parser is intentionally limited to cold paths such as media extraction and diagnostics.
 * Timeline and Compose adapters use patch-time direct bridges instead.
 * Field values are extracted by name; list and object values are matched
 * bracket-for-bracket so nested structures arrive intact. Primitive values
 * run until the next comma.
 */
public final class ToStringParser {

    private ToStringParser() {
    }

    /**
     * Returns the raw value of the first occurrence of {@code fieldName} in a
     * Kotlin data class toString, or null when the field is absent or "null".
     */
    public static String fieldValue(String text, String fieldName) {
        if (text == null || fieldName == null) return null;
        int start = fieldValueStart(text, fieldName);
        if (start < 0) return null;
        int end = valueEnd(text, start);
        return end < 0 ? null : normalize(text.substring(start, end));
    }

    private static int fieldValueStart(String text, String fieldName) {
        String prefix = fieldName + "=";
        int start = text.indexOf(prefix);
        return start < 0 ? -1 : start + prefix.length();
    }

    /**
     * End of one value atom: nested bracket structures are consumed as a
     * whole; everything else ends at the next delimiter (top-level comma or
     * the enclosing structure's closing bracket).
     */
    private static int valueEnd(String text, int start) {
        int open = openingBracket(text, start);
        if (open >= 0) {
            char opening = text.charAt(open);
            char closing = matchingClose(opening);
            int depth = 1;
            for (int i = open + 1; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == opening) {
                    depth++;
                } else if (c == closing && --depth == 0) {
                    return i + 1;
                }
            }
            return -1;
        }

        int end = Integer.MAX_VALUE;
        for (char delimiter : new char[]{',', ')', ']'}) {
            int found = text.indexOf(delimiter, start);
            if (found >= 0 && found < end) end = found;
        }
        return end == Integer.MAX_VALUE ? text.length() : end;
    }

    /**
     * Bracket that opens a list or object value: a '[' directly, or '(' after
     * a class name, e.g. {@code SourceInfo(...)} or {@code [MediaContentImage(...)]}.
     * Primitives (ids, urls, names) have no bracket and return -1.
     */
    private static int openingBracket(String text, int start) {
        char first = text.charAt(start);
        if (first == '[') return start;
        if (!Character.isLetter(first)) return -1;

        int i = start + 1;
        while (i < text.length() && isIdentifierPart(text.charAt(i))) i++;
        return i < text.length() && text.charAt(i) == '(' ? i : -1;
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static char matchingClose(char open) {
        return switch (open) {
            case '(' -> ')';
            case '[' -> ']';
            default -> 0;
        };
    }

    private static String normalize(String value) {
        String trimmed = value.trim();
        return trimmed.equals("null") || trimmed.isEmpty() ? null : trimmed;
    }
}