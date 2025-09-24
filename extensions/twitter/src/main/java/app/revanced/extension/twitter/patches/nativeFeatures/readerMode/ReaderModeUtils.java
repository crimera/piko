package app.revanced.extension.twitter.patches.nativeFeatures.readerMode;

import android.content.Context;
import java.lang.reflect.InvocationTargetException;
import app.revanced.extension.twitter.settings.ActivityHook;
import app.revanced.extension.twitter.Utils;
import app.revanced.extension.twitter.Pref;
import app.revanced.extension.shared.StringRef;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import android.os.Environment;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.IOException;
import java.net.MalformedURLException;

import org.json.JSONObject;
import org.json.JSONArray;
import app.revanced.extension.twitter.entity.Tweet;
import app.revanced.extension.twitter.patches.nativeFeatures.readerMode.ReaderModeTemplate;

public class ReaderModeUtils {

    private static final String THREADS_KEY = "threads";
    public static final String ARG_TWEET_ID = "tweet_id";

    public static final String NO_CONTENT = "<h3>No content specified</h3>";
    private static final Context ctx = app.revanced.extension.shared.Utils.getContext();

    private static String fontSize() {
        return "if(document.getElementById('fontSizeControls')) return;"
                + "var container = document.createElement('div');"
                + "container.id = 'fontSizeControls';"
                + "container.style.position = 'fixed';"
                + "container.style.bottom = '10px';"
                + "container.style.left = '50%';"
                + "container.style.transform = 'translateX(-50%)';"
                + "container.style.background = 'rgba(255,255,255,0.8)';"
                + "container.style.padding = '8px 16px';"
                + "container.style.borderRadius = '8px';"
                + "container.style.zIndex = '9999';"
                + "container.style.display = 'flex';"
                + "container.style.gap = '10px';"
                + "var decBtn = document.createElement('button');"
                + "decBtn.innerText = 'A-';"
                + "decBtn.style.fontSize = '25px';"
                + "var incBtn = document.createElement('button');"
                + "incBtn.innerText = 'A+';"
                + "incBtn.style.fontSize = '25px';"
                + "container.appendChild(decBtn);"
                + "container.appendChild(incBtn);"
                + "document.body.appendChild(container);"
                + "var fontSize =100;"
                + "decBtn.onclick = function() {"
                + "  if(fontSize > 50) {"
                + "    fontSize -= 10;"
                + "    document.body.style.fontSize = fontSize + '%';"
                + "  }"
                + "};"
                + "incBtn.onclick = function() {"
                + "  if(fontSize < 200) {"
                + "    fontSize += 10;"
                + "    document.body.style.fontSize = fontSize + '%';"
                + "  }"
                + "};"
                + "var isScrolling;"
                + "window.addEventListener('scroll', function() {"
                + "  container.style.display = 'none';"
                + "  clearTimeout(isScrolling);"
                + "  isScrolling = setTimeout(function() {"
                + "    container.style.display = 'flex';"
                + "  }, 1000);"
                + "});";
    }

    private static String preferenceJS() {
        Boolean textOnlyMode = Pref.hideNativeReaderPostTextOnlyMode();
        Boolean hideQuotedPosts = Pref.hideNativeReaderHideQuotedPosts();
        Boolean noGrok = Pref.hideNativeReaderNoGrok();

        return "if(" + hideQuotedPosts + "){\n" +
                "var nodes = document.querySelectorAll(\".quoted-section\");\n" +
                "nodes.forEach(n=>n.style.display='none');\n" +
                "}\n" +
                "if(" + textOnlyMode + "){\n" +
                "var nodes = document.querySelectorAll(\".media\");\n" +
                "nodes.forEach(n=>n.style.display='none');\n" +
                "}\n" +
                "if(" + noGrok + "){\n" +
                "var nodes = document.querySelectorAll(\".grok-button\");\n" +
                "nodes.forEach(n=>n.style.display='none');\n" +
                "}";
    }

    public static void launchReaderMode(Context activity, Object tweet) throws Exception {
        String tweetId = "" + new Tweet(tweet).getTweetId();
        ActivityHook.startReaderMode(tweetId);
        return;
    }

    private static JSONObject getThreadInfo(String tweetId) throws Exception {
        String api = "https://twitter-thread.com/api/unroll-thread?id=" + tweetId;

        StringBuilder content = new StringBuilder();
        HttpURLConnection conn = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(api);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            String data = content.toString();
            return new JSONObject(content.toString());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null)
                conn.disconnect();
            try {
                if (reader != null)
                    reader.close();
            } catch (Exception ignore) {
            }
        }
    }

    private static File cacheDir() {
        File cacheDir = ctx.getCacheDir();
        File threadsDir = new File(cacheDir, "Threads");

        if (!threadsDir.exists()) {
            threadsDir.mkdirs();
        }
        return threadsDir;
    }

    private static File cacheFileDir(String tweetId) {
        return new File(cacheDir(), THREADS_KEY + "_" + tweetId + ".html");
    }

    public static void clearCache() {
        File dir = cacheDir();
        boolean deleted = true;
        if (dir != null && dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleted = deleted && child.delete();
                }
            }
        }
        if (deleted) {
            Utils.toast(StringRef.str("piko_native_reader_mode_cache_delete_success"));
        } else {
            Utils.toast(StringRef.str("piko_native_reader_mode_cache_delete_failed"));
        }
    }

    private static boolean writeCacheFile(String tweetId, String data) {
        try {
            return Utils.writeFile(cacheFileDir(tweetId), data.getBytes(), false);
        } catch (Exception e) {
            Utils.logger(e.toString());
        }
        return false;
    }

    private static String readCacheFile(String tweetId) throws Exception {
        try {
            return Utils.readFile(cacheFileDir(tweetId));
        } catch (Exception e) {
            Utils.logger(e.toString());
        }
        return null;
    }

    public static String buildHtml(String tweetId) {
        // 0 = light, 1 = dark, 2 = dim
        String html = "";
        String defThemeClass = "{themeClassName}";
        String themeClass = defThemeClass;
        int theme = Utils.getTheme();
        switch(theme){
            case 1:{
                themeClass = "dark";
                break;
            }case 2:{
                themeClass = "dim";
                break;
            }default:{
                themeClass = defThemeClass;
            }
        }
        try {
            html = readCacheFile(tweetId);
            if (html == null) {
                JSONObject threadInfo = getThreadInfo(tweetId);
                if (threadInfo != null) {
                    html = ReaderModeTemplate.generateHtml(threadInfo);
                    writeCacheFile(tweetId, html);
                }else{
                    html = ReaderModeTemplate.generateHtml(NO_CONTENT);                    
                }
            }
        } catch (Exception e) {
            html = ReaderModeTemplate.generateHtml(e.getMessage());
        }
        html = html.replace(defThemeClass, themeClass);
        return html;
    }

    public static String injectJS() {
        return "(function() {" +
                fontSize() + preferenceJS() +
                "})();";
    }

    // class end
}