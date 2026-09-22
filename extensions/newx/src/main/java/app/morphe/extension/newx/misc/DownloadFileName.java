package app.morphe.extension.newx.misc;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.newx.utils.ToStringParser;

/**
 * Renders the user-configurable inline download filename template.
 *
 * <p>Token names are stable, model-derived names rather than R8-renamed descriptors, so they
 * survive app refactors. {@code userName} is the editor-facing name for the post author's handle
 * (NewX's {@code screenName} field); the legacy {@code screenName} token is still accepted so
 * templates saved before the rename keep rendering.
 */
public final class DownloadFileName {
    static final String TEMPLATE_SETTING = DownloadSettings.FILENAME_TEMPLATE;
    static final String DEFAULT_TEMPLATE = "{userName}_{id}";

    static final String TOKEN_ID = "id";
    static final String TOKEN_USER_NAME = "userName";
    /** Pre-rename token for the same value, kept so saved templates still render. */
    static final String TOKEN_SCREEN_NAME_LEGACY = "screenName";
    static final String TOKEN_NAME = "name";
    static final String TOKEN_DISPLAY_NAME = "displayName";
    static final String TOKEN_TIMESTAMP = "timestamp";
    static final String TOKEN_MEDIA_INDEX = "mediaIndex";
    static final String TOKEN_EXTENSION = "ext";

    /** Tokens offered as chips in the template editor, in insertion order. */
    private static final String[] EDITOR_TOKENS = {
            TOKEN_ID,
            TOKEN_USER_NAME,
            TOKEN_NAME,
            TOKEN_TIMESTAMP,
            TOKEN_MEDIA_INDEX,
            TOKEN_EXTENSION,
    };

    /** Tokens that differ between two posts, which is what keeps neighbours from colliding. */
    private static final String[] POST_TOKENS = {
            TOKEN_ID,
            TOKEN_TIMESTAMP,
    };

    private static final String UNKNOWN_TOKEN_MESSAGE = "piko_newx_download_filename_error_unknown";

    private DownloadFileName() {
    }

    public static List<String> editorTokens() {
        List<String> tokens = new ArrayList<>(EDITOR_TOKENS.length);
        Collections.addAll(tokens, EDITOR_TOKENS);
        return Collections.unmodifiableList(tokens);
    }

    /** Post-level template values, resolved once per download action. */
    public static final class PostContext {
        final String id;
        final String screenName;
        final String name;
        final String timestamp;
        final String sourcePostIdentifier;
        final String sourceUserIdentifier;
        final String sourceUserDisplayName;

        PostContext(
                String id,
                String screenName,
                String name,
                String timestamp,
                String sourcePostIdentifier,
                String sourceUserIdentifier,
                String sourceUserDisplayName
        ) {
            this.id = id;
            this.screenName = screenName;
            this.name = name;
            this.timestamp = timestamp;
            this.sourcePostIdentifier = sourcePostIdentifier;
            this.sourceUserIdentifier = sourceUserIdentifier;
            this.sourceUserDisplayName = sourceUserDisplayName;
        }

        public static PostContext from(Object post) {
            return fromText(post == null ? null : post.toString());
        }

        /**
         * Builds the context from an already-materialized post toString. A single call site can
         * resolve every field off one string instead of rebuilding the (large) data class
         * toString once per token.
         */
        public static PostContext fromText(String postText) {
            return new PostContext(
                    NewXUtils.rawSourcePostId(postText),
                    NewXUtils.rawSourceScreenName(postText),
                    NewXUtils.rawSourceDisplayName(postText),
                    normalizeTimestamp(ToStringParser.fieldValue(postText, "timestamp")),
                    NewXUtils.rawSourceMediaField(postText, "sourcePostIdentifier"),
                    NewXUtils.rawSourceMediaField(postText, "sourceUserIdentifier"),
                    NewXUtils.rawSourceMediaField(postText, "sourceUserDisplayName")
            );
        }

        /**
         * Stand-in values for the editor preview. Deliberately atypical so a user can tell the
         * preview apart from a real download.
         */
        static PostContext sample() {
            return new PostContext(
                    "1234567890123456789",
                    "jack",
                    "Jack",
                    "2026-01-31-123456",
                    "1234567890123456789",
                    "jack",
                    "Jack"
            );
        }

        @Nullable
        String value(String token) {
            switch (token) {
                case TOKEN_ID:
                    return id;
                case TOKEN_USER_NAME:
                case TOKEN_SCREEN_NAME_LEGACY:
                    return screenName;
                case TOKEN_NAME:
                case TOKEN_DISPLAY_NAME:
                    return name;
                case TOKEN_TIMESTAMP:
                    return timestamp;
                case "sourcePostIdentifier":
                    return sourcePostIdentifier;
                case "sourceUserIdentifier":
                    return sourceUserIdentifier;
                case "sourceUserDisplayName":
                    return sourceUserDisplayName;
                default:
                    return null;
            }
        }

        static boolean isKnownToken(String token) {
            return token.equals(TOKEN_ID)
                    || token.equals(TOKEN_USER_NAME)
                    || token.equals(TOKEN_SCREEN_NAME_LEGACY)
                    || token.equals(TOKEN_NAME)
                    || token.equals(TOKEN_DISPLAY_NAME)
                    || token.equals(TOKEN_TIMESTAMP)
                    || token.equals("sourcePostIdentifier")
                    || token.equals("sourceUserIdentifier")
                    || token.equals("sourceUserDisplayName")
                    || token.equals(TOKEN_MEDIA_INDEX)
                    || token.equals(TOKEN_EXTENSION);
        }
    }

    /**
     * Renders a filename for one media item. Unknown tokens are left literal so a typo surfaces in
     * the resulting filename instead of silently collapsing into a collision loop.
     */
    public static String render(
            @Nullable String template,
            PostContext post,
            int index,
            int mediaCount,
            @Nullable String extension
    ) {
        String pattern = template == null || template.trim().isEmpty() ? DEFAULT_TEMPLATE : template;
        String resolvedExtension = sanitizeSegment(extension, "bin");
        String stem = sanitizeSegment(renderTokens(pattern, post, index, resolvedExtension), null);
        if (stem == null) {
            // Every token resolved to nothing; keep the pre-template naming scheme.
            stem = sanitizeSegment(post.screenName, "twitter") + "_" +
                    sanitizeSegment(post.id, "post");
        }
        if (mediaCount > 1 && !pattern.contains("{" + TOKEN_MEDIA_INDEX + "}")) {
            stem = stem + "_" + (index + 1);
        }
        // An explicit {ext} lets the template place the extension itself, which also means it must
        // not be appended a second time.
        return pattern.contains("{" + TOKEN_EXTENSION + "}")
                ? stem
                : stem + "." + resolvedExtension;
    }

    /** Preview text for the editor, using the sample context. */
    public static String preview(@Nullable String template) {
        return render(template, PostContext.sample(), 0, 2, "jpg");
    }

    /** Template validation outcomes. */
    public enum Outcome {
        OK,
        EMPTY,
        UNCLOSED,
        UNKNOWN_TOKEN,
        STATIC,
    }

    /** Validation result: the outcome plus the token that caused it, if any. */
    public static final class Validation {
        public final Outcome outcome;
        @Nullable
        public final String token;

        Validation(Outcome outcome, @Nullable String token) {
            this.outcome = outcome;
            this.token = token;
        }
    }

    /**
     * Validates a template without resolving any string resource, so the contract stays testable
     * and the editor and the settings screen cannot disagree about what is accepted.
     */
    public static Validation validate(@Nullable String template) {
        if (template == null || template.trim().isEmpty()) {
            return new Validation(Outcome.EMPTY, null);
        }

        boolean hasPostToken = false;
        int index = 0;
        while (index < template.length()) {
            int open = template.indexOf('{', index);
            if (open < 0) break;

            int close = template.indexOf('}', open + 1);
            if (close < 0) {
                return new Validation(Outcome.UNCLOSED, null);
            }

            String token = template.substring(open + 1, close);
            if (!PostContext.isKnownToken(token)) {
                return new Validation(Outcome.UNKNOWN_TOKEN, token);
            }
            for (String postToken : POST_TOKENS) {
                if (token.equals(postToken)) hasPostToken = true;
            }
            index = close + 1;
        }

        if (!hasPostToken) {
            return new Validation(Outcome.STATIC, null);
        }
        return new Validation(Outcome.OK, null);
    }

    /** Validator entry point: {@code null} when the template is usable. */
    @Nullable
    public static String validationError(@Nullable String template) {
        Validation validation = validate(template);
        switch (validation.outcome) {
            case EMPTY:
                return stringRef("piko_newx_download_filename_error_empty");
            case UNCLOSED:
                return stringRef("piko_newx_download_filename_error_unclosed");
            case UNKNOWN_TOKEN:
                return formatStringRef(UNKNOWN_TOKEN_MESSAGE, validation.token);
            case STATIC:
                return stringRef("piko_newx_download_filename_error_static");
            default:
                return null;
        }
    }

    private static String renderTokens(String pattern, PostContext post, int index, String extension) {
        StringBuilder rendered = new StringBuilder(pattern.length() + 16);
        int cursor = 0;
        while (cursor < pattern.length()) {
            int open = pattern.indexOf('{', cursor);
            if (open < 0) {
                rendered.append(pattern, cursor, pattern.length());
                break;
            }
            rendered.append(pattern, cursor, open);

            int close = pattern.indexOf('}', open + 1);
            if (close < 0) {
                rendered.append(pattern, open, pattern.length());
                break;
            }

            String token = pattern.substring(open + 1, close);
            String value;
            if (token.equals(TOKEN_MEDIA_INDEX)) {
                value = String.valueOf(index + 1);
            } else if (token.equals(TOKEN_EXTENSION)) {
                value = extension;
            } else {
                value = post.value(token);
            }

            // A known token the post cannot supply must not leave braces in the filename; an
            // unknown token stays literal so a typo is visible rather than silently collapsing
            // every download onto one name.
            if (value == null && PostContext.isKnownToken(token)) {
                value = "";
            }
            rendered.append(value == null ? pattern.substring(open, close + 1) : value);
            cursor = close + 1;
        }
        return rendered.toString();
    }

    /** Kotlin {@code Instant.toString()} is ISO-8601; collapse it into a filename-safe stamp. */
    @Nullable
    private static String normalizeTimestamp(@Nullable String raw) {
        if (raw == null) return null;

        String value = raw.trim();
        if (value.isEmpty()) return null;

        int fraction = value.indexOf('.');
        if (fraction > 0) value = value.substring(0, fraction);
        if (value.endsWith("Z")) value = value.substring(0, value.length() - 1);

        value = value.replace('T', '-').replace(":", "");
        return value.isEmpty() ? null : value;
    }

    /**
     * Shared filename sanitizer. Also used by the download writer for the extension fragment, so
     * the exact same character policy applies to user text and derived values.
     */
    @Nullable
    public static String sanitizeSegment(@Nullable String value, @Nullable String fallback) {
        if (value == null) return fallback;

        String sanitized = value.trim().replaceFirst("^@", "")
                .replaceAll("[^A-Za-z0-9._-]+", "_")
                // Separators are already gone, so a dot run cannot traverse; collapsing every
                // punctuation run keeps the "no parent-directory component" property local too.
                .replaceAll("[_.]{2,}", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+|[._-]+$", "");
        return sanitized.isEmpty() ? fallback : sanitized;
    }

    private static String stringRef(String name) {
        return app.morphe.extension.shared.StringRef.str(name);
    }

    private static String formatStringRef(String name, Object argument) {
        return app.morphe.extension.shared.StringRef.str(name, argument);
    }
}
