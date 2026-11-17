package app.morphe.extension.newx.translation;

import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class GoogleProvider implements TranslationProvider {
    private static final String TRANSLATE_ENDPOINT =
            "https://translate.googleapis.com/translate_a/single";
    // Same request headers as the legacy Twitter GTranslate provider.
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Ubuntu; Linux i686; rv:56.0) Gecko/20100101 Firefox/56.0";
    private static final String ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_RESPONSE_CHARS = 2 * 1024 * 1024;
    private static final int MAX_TRANSLATION_CHUNK_CODE_POINTS = 1_500;
    private static final LruCache<String, TranslationResult> CACHE = new LruCache<>(128);

    @Override
    public String id() {
        return TranslatorConfig.GOOGLE_ID;
    }

    @Override
    public String displayName() {
        return "Google";
    }

    @Override
    public boolean supportsText() {
        return true;
    }

    @Override
    public TranslationResult translateText(
            String text,
            String sourceHint,
            String targetLanguage
    ) throws IOException {
        SegmentedText segmented = splitIntoChunks(text);
        StringBuilder translated = new StringBuilder();
        String detectedSource = null;
        translated.append(segmented.prefix);
        for (TranslationChunk chunk : segmented.chunks) {
            if (chunk.text.trim().isEmpty()) {
                translated.append(chunk.text).append(chunk.separator);
                continue;
            }
            TranslationResult result = translateChunk(chunk.text, targetLanguage, sourceHint);
            translated.append(result.text);
            translated.append(chunk.separator);
            if (detectedSource == null && result.sourceLanguage != null) {
                detectedSource = result.sourceLanguage;
            }
        }
        translated.append(segmented.suffix);
        return new TranslationResult(translated.toString(), detectedSource);
    }

    @Override
    public TranslationResult translatePost(String postId, String postText, String targetLanguage) throws IOException {
        return translateText(postText, null, targetLanguage);
    }

    private static TranslationResult translateChunk(
            String text,
            String targetLanguage,
            String sourceLanguageHint
    ) throws IOException {
        String sourceLanguage = sourceLanguageHint == null ? "auto" : sourceLanguageHint;
        String cacheKey = targetLanguage + '\u0000' + sourceLanguage + '\u0000' + text;
        synchronized (CACHE) {
            TranslationResult cached = CACHE.get(cacheKey);
            if (cached != null) return cached;
        }

        TranslationResult result = requestChunk(text, targetLanguage, sourceLanguage);
        synchronized (CACHE) {
            CACHE.put(cacheKey, result);
        }
        return result;
    }

    private static TranslationResult requestChunk(
            String text,
            String targetLanguage,
            String sourceLanguage
    ) throws IOException {
        HttpURLConnection connection = null;
        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8.name());
            String encodedTarget = URLEncoder.encode(targetLanguage, StandardCharsets.UTF_8.name());
            String encodedSource = URLEncoder.encode(sourceLanguage, StandardCharsets.UTF_8.name());
            URL url = new URL(TRANSLATE_ENDPOINT +
                    "?client=gtx&sl=" + encodedSource + "&tl=" + encodedTarget +
                    "&dj=1&dt=t&dt=bd&source=popup5&q=" + encodedText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", ACCEPT);

            int status = connection.getResponseCode();
            if (status < HttpURLConnection.HTTP_OK || status >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new IOException("Google Translate returned HTTP " + status);
            }

            return parseResponse(readResponse(connection.getInputStream()));
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Google Translate request failed", exception);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readResponse(InputStream stream) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (response.length() + count > MAX_RESPONSE_CHARS) {
                    throw new IOException("Google Translate response exceeded the size limit");
                }
                response.append(buffer, 0, count);
            }
        }
        return response.toString();
    }

    private static TranslationResult parseResponse(String response) throws IOException {
        try {
            JSONObject json = new JSONObject(response);
            JSONArray sentences = json.optJSONArray("sentences");
            if (sentences == null) throw new IOException("Google Translate response has no sentences");

            StringBuilder translated = new StringBuilder();
            for (int index = 0; index < sentences.length(); index++) {
                JSONObject sentence = sentences.optJSONObject(index);
                if (sentence != null) translated.append(sentence.optString("trans", ""));
            }
            if (translated.length() == 0) throw new IOException("Google Translate returned empty text");

            String source = json.optString("src", null);
            return new TranslationResult(translated.toString(), TranslatorConfig.normalizeLanguage(source));
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Malformed Google Translate response", exception);
        }
    }

    private static SegmentedText splitIntoChunks(String text) {
        int contentStart = 0;
        while (contentStart < text.length()) {
            int codePoint = text.codePointAt(contentStart);
            if (!Character.isWhitespace(codePoint)) break;
            contentStart += Character.charCount(codePoint);
        }

        int contentEnd = text.length();
        while (contentEnd > contentStart) {
            int codePoint = text.codePointBefore(contentEnd);
            if (!Character.isWhitespace(codePoint)) break;
            contentEnd -= Character.charCount(codePoint);
        }

        String prefix = text.substring(0, contentStart);
        String suffix = text.substring(contentEnd);
        List<TranslationChunk> chunks = new ArrayList<>();
        int start = contentStart;
        while (start < contentEnd) {
            int remainingCodePoints = text.codePointCount(start, contentEnd);
            if (remainingCodePoints <= MAX_TRANSLATION_CHUNK_CODE_POINTS) {
                chunks.add(new TranslationChunk(text.substring(start, contentEnd), ""));
                break;
            }

            int limit = text.offsetByCodePoints(start, MAX_TRANSLATION_CHUNK_CODE_POINTS);
            if (limit > contentEnd) limit = contentEnd;
            int split = findSentenceBoundary(text, start, limit);
            if (split <= start) split = findWhitespaceBoundary(text, start, limit);
            if (split <= start) split = limit;

            int separatorEnd = split;
            while (separatorEnd < contentEnd && Character.isWhitespace(text.charAt(separatorEnd))) {
                separatorEnd++;
            }
            chunks.add(new TranslationChunk(
                    text.substring(start, split),
                    text.substring(split, separatorEnd)
            ));
            start = separatorEnd;
        }
        return new SegmentedText(prefix, chunks, suffix);
    }

    private static int findSentenceBoundary(String text, int start, int limit) {
        for (int index = limit - 1; index > start; index--) {
            char character = text.charAt(index);
            if ((character == '.' || character == '!' || character == '?') &&
                    index + 1 < text.length() && Character.isWhitespace(text.charAt(index + 1))) {
                return index + 1;
            }
        }
        return -1;
    }

    private static int findWhitespaceBoundary(String text, int start, int limit) {
        for (int index = limit - 1; index > start; index--) {
            if (Character.isWhitespace(text.charAt(index))) return index;
        }
        return -1;
    }

    private static final class SegmentedText {
        final String prefix;
        final List<TranslationChunk> chunks;
        final String suffix;

        SegmentedText(String prefix, List<TranslationChunk> chunks, String suffix) {
            this.prefix = prefix;
            this.chunks = chunks;
            this.suffix = suffix;
        }
    }

    private static final class TranslationChunk {
        final String text;
        final String separator;

        TranslationChunk(String text, String separator) {
            this.text = text;
            this.separator = separator;
        }
    }
}
