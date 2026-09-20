/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DownloadFileNameFormatter {
    public static final String DEFAULT_TEMPLATE = "{username}_{media_id}_{variant_suffix}";

    private static final int MAX_FILENAME_BYTES = 255;
    private static final String ELLIPSIS = "...";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{([^{}]*)\\}");
    private static final Pattern INVALID_TEMPLATE_CHARACTER =
            Pattern.compile("[\\\\/:*?\"<>|{}\\p{Cntrl}]");
    private static final Pattern INVALID_FILENAME_CHARACTER =
            Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final Pattern TRAILING_DOT_OR_SPACE = Pattern.compile("[. ]+$");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH-mm-ss");

    private DownloadFileNameFormatter() {
    }

    public static String format(String template, Values values, String extension, ZoneId zoneId) {
        Values safeValues = values == null ? Values.EMPTY : values;
        ZoneId safeZoneId = zoneId == null ? ZoneId.systemDefault() : zoneId;
        String effectiveTemplate = isValidTemplate(template)
                ? template
                : DEFAULT_TEMPLATE;
        String filename = sanitize(render(effectiveTemplate, safeValues, safeZoneId));

        if (filename.isEmpty()) {
            filename = sanitize(render(DEFAULT_TEMPLATE, safeValues, safeZoneId));
        }
        if (filename.isEmpty()) {
            filename = "download";
        }

        return limitFilenameLength(filename, normalizeExtension(extension));
    }

    public static boolean isValidTemplate(String template) {
        if (template == null
                || template.trim().isEmpty()
                || TRAILING_DOT_OR_SPACE.matcher(template).find()) {
            return false;
        }

        Matcher matcher = TOKEN_PATTERN.matcher(template);
        while (matcher.find()) {
            if (Token.fromKey(matcher.group(1)) == null) {
                return false;
            }
        }
        return !INVALID_TEMPLATE_CHARACTER.matcher(matcher.replaceAll("")).find();
    }

    public static boolean isValidTemplate(
            String template,
            Values values,
            String extension,
            ZoneId zoneId
    ) {
        if (!isValidTemplate(template)) {
            return false;
        }

        Values safeValues = values == null ? Values.EMPTY : values;
        ZoneId safeZoneId = zoneId == null ? ZoneId.systemDefault() : zoneId;
        String filename = sanitize(render(template, safeValues, safeZoneId))
                + normalizeExtension(extension);
        return utf8Length(filename) <= MAX_FILENAME_BYTES;
    }

    private static String render(String template, Values values, ZoneId zoneId) {
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuilder rendered = new StringBuilder();
        int literalStart = 0;
        boolean omitNextSeparator = false;
        while (matcher.find()) {
            String literal = template.substring(literalStart, matcher.start());
            if (omitNextSeparator
                    && !literal.isEmpty()
                    && isSeparator(literal.charAt(0))) {
                literal = literal.substring(1);
            }
            omitNextSeparator = false;

            Token token = Token.fromKey(matcher.group(1));
            if (token == null) {
                throw new IllegalArgumentException("Unsupported filename template token");
            }
            String replacement = token.resolve(values, zoneId);
            if (replacement.isEmpty()) {
                // Avoid an orphaned separator when optional data, such as the quality variant, is absent.
                int lastLiteralIndex = literal.length() - 1;
                if (lastLiteralIndex >= 0
                        && isSeparator(literal.charAt(lastLiteralIndex))) {
                    literal = literal.substring(0, lastLiteralIndex);
                } else if (rendered.length() == 0 && literal.isEmpty()) {
                    omitNextSeparator = true;
                }
            }

            rendered.append(literal).append(replacement);
            literalStart = matcher.end();
        }
        String tail = template.substring(literalStart);
        if (omitNextSeparator
                && !tail.isEmpty()
                && isSeparator(tail.charAt(0))) {
            tail = tail.substring(1);
        }
        rendered.append(tail);
        return rendered.toString();
    }

    public enum Token {
        USERNAME("username"),
        MEDIA_ID("media_id"),
        SHORTCODE("shortcode"),
        UPLOAD_DATE("upload_date"),
        UPLOAD_TIME("upload_time"),
        TYPE("type"),
        CAROUSEL_INDEX("carousel_index"),
        VARIANT_SUFFIX("variant_suffix");

        private final String key;

        Token(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public String placeholder() {
            return "{" + key + "}";
        }

        private String resolve(Values values, ZoneId zoneId) {
            switch (this) {
                case USERNAME:
                    return values.username;
                case MEDIA_ID:
                    return values.mediaId;
                case SHORTCODE:
                    return values.shortcode;
                case UPLOAD_DATE:
                    return values.uploadTimestampMillis == null ? "" : DATE_FORMATTER.format(
                            Instant.ofEpochMilli(values.uploadTimestampMillis).atZone(zoneId)
                    );
                case UPLOAD_TIME:
                    return values.uploadTimestampMillis == null ? "" : TIME_FORMATTER.format(
                            Instant.ofEpochMilli(values.uploadTimestampMillis).atZone(zoneId)
                    );
                case TYPE:
                    return values.type;
                case CAROUSEL_INDEX:
                    return String.valueOf(values.carouselIndex + 1);
                case VARIANT_SUFFIX:
                    return values.variantSuffix;
                default:
                    throw new IllegalStateException("Unsupported filename template token");
            }
        }

        static Token fromKey(String key) {
            for (Token token : values()) {
                if (token.key.equals(key)) {
                    return token;
                }
            }
            return null;
        }
    }

    private static boolean isSeparator(char character) {
        return character == '_' || character == '-' || character == '.' || character == ' ';
    }

    private static String sanitize(String filename) {
        return TRAILING_DOT_OR_SPACE.matcher(
                INVALID_FILENAME_CHARACTER.matcher(filename).replaceAll("_")
        ).replaceAll("");
    }

    private static String limitFilenameLength(String filename, String extension) {
        String fullFilename = filename + extension;
        if (utf8Length(fullFilename) <= MAX_FILENAME_BYTES) {
            return fullFilename;
        }

        int extensionBytes = utf8Length(extension);
        if (extensionBytes >= MAX_FILENAME_BYTES) {
            return truncateMiddle(fullFilename, MAX_FILENAME_BYTES);
        }
        return truncateMiddle(filename, MAX_FILENAME_BYTES - extensionBytes) + extension;
    }

    private static String truncateMiddle(String value, int maxBytes) {
        if (utf8Length(value) <= maxBytes) {
            return value;
        }
        if (maxBytes < utf8Length(ELLIPSIS)) {
            return truncateEnd(value, maxBytes);
        }

        // Preserve both ends, which may contain the username and identifying media or variant information.
        StringBuilder shortened = new StringBuilder(value);
        int contentLimit = maxBytes - utf8Length(ELLIPSIS);
        while (utf8Length(shortened.toString()) > contentLimit) {
            deleteMiddleCharacter(shortened);
        }
        int insertionPoint = characterBoundaryAtOrBeforeMiddle(shortened.toString());
        return shortened.insert(insertionPoint, ELLIPSIS).toString();
    }

    private static String truncateEnd(String value, int maxBytes) {
        StringBuilder shortened = new StringBuilder();
        BreakIterator iterator = characterIterator(value);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; end = iterator.next()) {
            String character = value.substring(start, end);
            if (utf8Length(shortened.toString()) + utf8Length(character) > maxBytes) {
                break;
            }
            shortened.append(character);
            start = end;
        }
        return shortened.toString();
    }

    private static void deleteMiddleCharacter(StringBuilder value) {
        BreakIterator iterator = characterIterator(value.toString());
        int middle = value.length() / 2;
        int start = iterator.isBoundary(middle) ? middle : iterator.preceding(middle);
        if (start == BreakIterator.DONE) {
            start = iterator.first();
        }
        int end = iterator.following(start);
        if (end == BreakIterator.DONE) {
            end = value.length();
        }
        value.delete(start, end);
    }

    private static int characterBoundaryAtOrBeforeMiddle(String value) {
        BreakIterator iterator = characterIterator(value);
        int middle = value.length() / 2;
        if (iterator.isBoundary(middle)) {
            return middle;
        }
        int boundary = iterator.preceding(middle);
        return boundary == BreakIterator.DONE ? iterator.first() : boundary;
    }

    private static BreakIterator characterIterator(String value) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(value);
        return iterator;
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String normalizeExtension(String extension) {
        return extension == null || extension.isEmpty()
                ? ""
                : extension.startsWith(".") ? extension : "." + extension;
    }

    public static final class Values {
        private static final Values EMPTY = new Values("", "", "", null, "", 0, "");

        public final String username;
        public final String mediaId;
        public final String shortcode;
        public final Long uploadTimestampMillis;
        public final String type;
        public final int carouselIndex;
        public final String variantSuffix;

        public Values(
                String username,
                String mediaId,
                String shortcode,
                Long uploadTimestampMillis,
                String type,
                int carouselIndex,
                String variantSuffix
        ) {
            this.username = username == null ? "" : username;
            this.mediaId = mediaId == null ? "" : mediaId;
            this.shortcode = shortcode == null ? "" : shortcode;
            this.uploadTimestampMillis = uploadTimestampMillis;
            this.type = type == null ? "" : type;
            this.carouselIndex = carouselIndex;
            this.variantSuffix = variantSuffix == null ? "" : variantSuffix;
        }
    }
}
