package app.morphe.extension.newx.postfilter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import app.morphe.extension.newx.settings.StringSetSetting;

public final class VerifiedAccountWhitelistStore {
    private static final String KEY = "newx.content.verified_account_whitelist";

    private static StringSetSetting setting() {
        return Holder.SETTING;
    }
    public enum ValidationError {
        BLANK_ACCOUNT,
        DUPLICATE_ACCOUNT,
    }

    public static final class ValidationException extends IllegalArgumentException {
        private final ValidationError error;

        ValidationException(ValidationError error) {
            super(error.name());
            this.error = error;
        }

        public ValidationError getError() {
            return error;
        }
    }

    private VerifiedAccountWhitelistStore() {
    }

    public static VerifiedAccountWhitelistStore shared() {
        return Holder.INSTANCE;
    }

    public Set<String> snapshot() {
        return setting().get();
    }

    public void add(String account) {
        String normalized = normalize(account);
        if (normalized.isEmpty()) {
            throw new ValidationException(ValidationError.BLANK_ACCOUNT);
        }
        Set<String> updated = new LinkedHashSet<>(snapshot());
        if (!updated.add(normalized)) {
            throw new ValidationException(ValidationError.DUPLICATE_ACCOUNT);
        }
        setting().save(Collections.unmodifiableSet(updated));
    }

    public void remove(String account) {
        String normalized = normalize(account);
        Set<String> updated = new LinkedHashSet<>(snapshot());
        if (!updated.remove(normalized)) return;
        setting().save(Collections.unmodifiableSet(updated));
    }

    public static boolean matches(Set<String> whitelist, String authorId, String authorScreenName) {
        if (whitelist == null || whitelist.isEmpty()) return false;
        return contains(whitelist, authorId) || contains(whitelist, authorScreenName);
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        while (normalized.startsWith("@")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static boolean contains(Set<String> whitelist, String value) {
        String normalized = normalize(value);
        return !normalized.isEmpty() && whitelist.contains(normalized);
    }

    private static final class Holder {
        private static final StringSetSetting SETTING =
                new StringSetSetting(KEY, Collections.emptySet(), false);
        private static final VerifiedAccountWhitelistStore INSTANCE =
                new VerifiedAccountWhitelistStore();
    }
}
