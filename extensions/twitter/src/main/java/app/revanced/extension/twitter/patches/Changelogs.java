package app.revanced.extension.twitter.patches;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;
import android.app.Activity;
import app.revanced.extension.twitter.settings.Settings;
import app.revanced.extension.shared.requests.Requester;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.json.JSONObject;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.requests.Route;
import static app.revanced.extension.shared.requests.Route.Method.GET;
import app.revanced.extension.twitter.Pref;
import static android.text.Html.FROM_HTML_MODE_COMPACT;
import app.revanced.extension.shared.StringRef;

public class Changelogs {
    private static String CHANGELOG_PROVIDER = "https://api.github.com/repos/crimera/piko/releases/tags";
    private static String patchVersion, latestChangelogVersion;
    static{
        latestChangelogVersion = Pref.getLatestChangelogVersion();
        patchVersion = Utils.getPatchesReleaseVersion();
    }

    private static String convertMarkdownToHtml(String markdown) {
        // Basic manual Markdown to HTML conversion
        if(markdown!=null) {
            return markdown
                    .replaceAll("### (.*?)\\n", "<h3>$1</h3>")
                    .replaceAll("## (.*?)\\n", "<h2>$1</h2>")
                    .replaceAll("\\`([^*]+)\\`", "<i>$1</i>")
                    .replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>")
                    .replaceAll("\\[(.+?)\\]\\((http.+?)\\)", "<a href=\"$2\">$1</a>")
                    .replaceAll("\\* (.*)", "&#8226; $1<br>")
                    .replaceAll("\n", "<br>");
        }
        return null;
    }

    private static String getUpdateMessage() throws Exception{
        if(!Utils.isNetworkConnected()) return null;

        Route route = new Route(GET, "/v"+patchVersion);
        HttpURLConnection connection = Requester.getConnectionFromRoute(CHANGELOG_PROVIDER, route);

        JSONObject responseJson = Requester.parseJSONObject(connection);
        String updateMessage = responseJson.getString("body");
        return convertMarkdownToHtml(updateMessage);
    }

    public static void showChangelogDialog(Context context){
        String htmlString = Pref.getChangelog();
        final var finalMessage = Html.fromHtml(htmlString, FROM_HTML_MODE_COMPACT);
        Utils.runOnMainThread(() -> {
            TextView textView = new TextView(context);
            textView.setText(finalMessage);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
            textView.setPadding(40, 40, 40, 40);

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(StringRef.str("piko_changelogs_title"))
                    .setView(textView)
                    .setPositiveButton(StringRef.str("ok"), (dialogInterface, i) -> dialogInterface.dismiss())
                    .create();

            dialog.show();
        });
    }

    public static void showChangelog(Activity context) {
        if (latestChangelogVersion.equals(patchVersion)) return;
        Utils.runOnBackgroundThread(() -> {
            String htmlString = null;
            try {
                htmlString = getUpdateMessage();
            } catch (Exception ex) {
                app.revanced.extension.twitter.Utils.logger(ex);
                htmlString = null;
            }
            if (htmlString == null) return;

            Pref.setLatestChangelogVersion(patchVersion);
            Pref.setChangelog(htmlString);
            latestChangelogVersion = patchVersion;
            showChangelogDialog(context);
        });


    }
}
