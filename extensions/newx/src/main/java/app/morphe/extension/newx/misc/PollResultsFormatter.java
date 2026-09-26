package app.morphe.extension.newx.misc;

import java.util.Map;

import app.morphe.extension.newx.settings.SettingsRegistry;

/** Formats poll labels while the poll is still awaiting the current user's vote. */
public final class PollResultsFormatter {
    private static final String SETTING_ID = "newx.content.show_poll_results";
    private static final String COUNTS_ARE_FINAL = "counts_are_final";

    private PollResultsFormatter() {
    }

    /**
     * Returns a replacement label, or {@code null} when the caller should run its original
     * binding-value lookup. The method deliberately accepts only stable Java boundary types;
     * release-specific card model descriptors remain in the patched APK.
     */
    public static String formatLabel(int choice, String kind, Map<?, ?> bindings) {
        if (!"label".equals(kind) || bindings == null) return null;

        try {
            if (!SettingsRegistry.getBooleanOrDefault(SETTING_ID, false)) return null;
            return formatLabelValue(choice, bindings);
        } catch (RuntimeException ignored) {
            // A malformed or changed binding must leave NewX's original value untouched.
            return null;
        }
    }

    /** Pure formatter used by tests and kept separate from the optional settings registry read. */
    static String formatLabelValue(int choice, Map<?, ?> bindings) {
        if (choice < 1 || choice > 4 || bindings == null) return null;
        if (booleanValue(bindings.get(COUNTS_ARE_FINAL))) return null;

        String label = stringValue(bindings.get("choice" + choice + "_label"));
        if (label == null) return null;

        int totalVotes = 0;
        for (int index = 1; index <= 4; index++) {
            String key = "choice" + index + "_count";
            if (!bindings.containsKey(key)) break;

            Integer count = integerValue(bindings.get(key));
            if (count == null) return null;
            totalVotes += count;
        }

        Integer selectedCount = integerValue(bindings.get("choice" + choice + "_count"));
        int count = selectedCount == null ? 0 : selectedCount;
        int percentage = totalVotes == 0
                ? 0
                : Math.round(count * 100.0f / totalVotes);
        return label + " - " + percentage + "%";
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean) return (Boolean) value;

        String text = wrappedValue(value);
        return "true".equalsIgnoreCase(text);
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();

        String text = stringValue(value);
        if (text == null) return null;
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        if (value == null) return null;
        if (value instanceof String) return (String) value;

        String wrapped = wrappedValue(value);
        return wrapped == null ? null : wrapped;
    }

    /**
     * NewX's card bindings use verified Kotlin value wrappers whose stable toString forms are
     * StringValue(value=...) and BooleanValue(value=...). Parse only those labels; never inspect
     * an obfuscated class or field reflectively.
     */
    private static String wrappedValue(Object value) {
        if (value == null) return null;

        String text = value.toString();
        String[] prefixes = {"StringValue(value=", "BooleanValue(value="};
        for (String prefix : prefixes) {
            if (text.startsWith(prefix) && text.endsWith(")")) {
                return text.substring(prefix.length(), text.length() - 1);
            }
        }
        return null;
    }
}
