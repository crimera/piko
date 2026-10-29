package app.morphe.extension.twitter.patches.autotranslate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;

/** Independent implementation of the web transport used by TWP.
 * Only supplied post text and language are sent. No X account credentials.
 */
public final class TranslationClient {
    private static final String BOOTSTRAP = "https://translate.googleapis.com/_/translate_http/_/js/k=translate_http.tr.en_US.YusFYy3P_ro.O/am=AAg/d=1/exm=el_conf/ed=1/rs=AN8SPfq1Hb8iJRleQqQc8zhdzXmF9E56eQ/m=el_main";
    private static final String ENDPOINT = "https://translate-pa.googleapis.com/v1/translateHtml";
    private static final Pattern AUTH = Pattern.compile("['\"]x-goog-api-key['\"]\\s*:\\s*['\"](\\w{39})['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORIGINAL = Pattern.compile("<i\\b[^>]*>.*?</i\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITIES = Pattern.compile("&(#(?:[xX][0-9a-fA-F]+|[0-9]+)|amp|lt|gt|quot|apos|nbsp);");
    private static String auth;
    private static long authTime;
    private static long authFailureUntil;
    private static Exception authFailure;

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(10000);
        c.setUseCaches(false);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/json,text/javascript,*/*");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36");
        return c;
    }

    private static String read(HttpURLConnection c, int max, String stage) throws Exception {
        int status = c.getResponseCode();
        if (status != 200) throw new Failure(status, stage);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = c.getInputStream()) {
            byte[] buffer = new byte[4096];
            for (int count; (count = input.read(buffer)) != -1;) {
                if (bytes.size() + count > max) throw new IOException("Response too large");
                bytes.write(buffer, 0, count);
            }
        }
        return bytes.toString("UTF-8");
    }

    // One bootstrap for concurrent workers; also bound failures to avoid a request storm.
    private static synchronized String authorization() throws Exception {
        long now = System.nanoTime();
        if (auth != null && now - authTime < 20L * 60 * 1000000000) return auth;
        if (authFailure != null && now < authFailureUntil) throw authFailure;
        HttpURLConnection c = open(BOOTSTRAP);
        try {
            Matcher match = AUTH.matcher(read(c, 4194304, "accesso"));
            if (!match.find()) throw new IOException("Google bootstrap changed");
            auth = match.group(1);
            authTime = System.nanoTime();
            authFailure = null;
            return auth;
        } catch (Exception error) {
            authFailure = error;
            authFailureUntil = System.nanoTime() + 60L * 1000000000;
            throw error;
        } finally { c.disconnect(); }
    }

    public static String requestBody(String source, String text) throws Exception {
        String language = source == null ? "auto" : source.toLowerCase(Locale.ROOT);
        if (!language.matches("[a-z]{2,3}(-[a-z]{2,4})?") || "und".equals(language)) language = "auto";
        if (text == null || text.trim().isEmpty() || text.length() > 6000) throw new IOException("Invalid post text");
        String html = "<pre>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;") + "</pre>";
        return new JSONArray().put(new JSONArray().put(new JSONArray().put(html)).put(language).put("it")).put("te").toString();
    }

    public static String translate(String source, String text) throws Exception {
        byte[] body = requestBody(source, text).getBytes("UTF-8");
        String token = authorization();
        HttpURLConnection c = open(ENDPOINT);
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json+protobuf");
            c.setRequestProperty("X-Goog-Api-Key", token);
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = c.getOutputStream()) { output.write(body); }
            return parse(read(c, 524288, "traduzione"));
        } finally { c.disconnect(); }
    }

    public static String parse(String body) throws Exception {
        JSONArray root = new JSONArray(body);
        JSONArray translations = root.getJSONArray(0);
        if (translations.length() != 1 || !(translations.get(0) instanceof String)) throw new Unavailable();
        String html = translations.getString(0);
        // Some responses annotate translated sentences with an italic original.
        html = ORIGINAL.matcher(html).replaceAll("");
        html = html.replaceAll("(?i)<br\\s*/?>", "\n");
        html = TAGS.matcher(html).replaceAll("");
        Matcher entities = ENTITIES.matcher(html);
        StringBuffer result = new StringBuffer();
        while (entities.find()) {
            String name = entities.group(1), replacement = entities.group();
            if ("amp".equals(name)) replacement = "&";
            else if ("lt".equals(name)) replacement = "<";
            else if ("gt".equals(name)) replacement = ">";
            else if ("quot".equals(name)) replacement = "\"";
            else if ("apos".equals(name)) replacement = "'";
            else if ("nbsp".equals(name)) replacement = "\u00a0";
            else if (name.startsWith("#")) {
                try {
                    boolean hex = name.length() > 2 && (name.charAt(1) == 'x' || name.charAt(1) == 'X');
                    int point = Integer.parseInt(name.substring(hex ? 2 : 1), hex ? 16 : 10);
                    if (Character.isValidCodePoint(point) && !(point >= 0xd800 && point <= 0xdfff)) replacement = new String(Character.toChars(point));
                } catch (IllegalArgumentException ignored) { }
            }
            entities.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        entities.appendTail(result);
        if (result.toString().trim().isEmpty()) throw new Unavailable();
        return result.toString();
    }

    public static final class Unavailable extends IOException { }
    public static final class Failure extends IOException {
        public final int status;
        public final String stage;
        Failure(int status, String stage) { super("Google HTTP " + status + " (" + stage + ")"); this.status = status; this.stage = stage; }
    }
    public static String describe(Exception error) {
        if (error instanceof Failure) { Failure f = (Failure) error; return "Traduzione non disponibile (Google " + f.status + ", " + f.stage + ")"; }
        if (error instanceof java.net.SocketTimeoutException) return "Traduzione non disponibile (timeout Google)";
        if (error instanceof java.net.UnknownHostException) return "Traduzione non disponibile (DNS Google)";
        if (error instanceof javax.net.ssl.SSLException) return "Traduzione non disponibile (TLS Google)";
        if (error instanceof Unavailable) return "Traduzione non ancora disponibile";
        if (error instanceof org.json.JSONException) return "Traduzione non disponibile (risposta Google non valida)";
        if ("Google bootstrap changed".equals(error.getMessage())) return "Traduzione non disponibile (accesso Google cambiato)";
        return "Traduzione non disponibile (Google: " + error.getClass().getSimpleName() + ")";
    }
}
