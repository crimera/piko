/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches;

import static android.text.Html.FROM_HTML_MODE_COMPACT;
import static app.morphe.extension.shared.StringRef.str;
import static app.morphe.extension.shared.requests.Route.Method.GET;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.net.HttpURLConnection;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.requests.Route;
import app.morphe.extension.twitter.Pref;

public class Changelogs {
    private static final String CHANGELOG_URL = "https://raw.githubusercontent.com";
    private static final String CHANGELOG_ROUTE_MAIN = "/crimera/piko/refs/heads/main/CHANGELOG.md";
    private static final String CHANGELOG_ROUTE_DEV = "/crimera/piko/refs/heads/dev/CHANGELOG.md";
    private static final String patchVersion = Utils.getPatchesReleaseVersion();
    private static String latestChangelogVersion = Pref.getLatestChangelogVersion();

    private static String convertMarkdownToHtml(@Nullable String markdown) {
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

    @Nullable
    private static String getUpdateMessage() throws Exception {
        try {
            String route = patchVersion.contains("dev")
                    ? CHANGELOG_ROUTE_DEV
                    : CHANGELOG_ROUTE_MAIN;

            HttpURLConnection connection = Requester.getConnectionFromRoute(
                    CHANGELOG_URL, new Route(GET, route));
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);

            String updateMessage = Requester.parseStringAndDisconnect(connection);
            return convertMarkdownToHtml(updateMessage);
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not fetch changelog", ex);
            return null;
        }
    }

    public static void showChangelogDialog(Context context){
        String htmlString = Pref.getChangelog();
        var message = Html.fromHtml(htmlString, FROM_HTML_MODE_COMPACT);
        TextView textView = new TextView(context);
        textView.setText(message);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setPadding(40, 40, 40, 40);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(str("piko_changelogs_title"))
                .setView(textView)
                .setPositiveButton(str("ok"), (dialogInterface, i) -> dialogInterface.dismiss())
                .create();

        dialog.show();
    }

    public static void showChangelog(Activity context) {
        if (latestChangelogVersion.equals(patchVersion)) {
            Logger.printInfo(() -> "Changelog check is up to date");
            return;
        }
        if (!Utils.isNetworkConnected()) {
            Logger.printInfo(() -> "Skipping changelog check, network is not connected");
            return;
        }
        if (context.isDestroyed()) {
            Logger.printInfo(() -> "Cannot check changelog, activity is destroyed: " + context);
            return;
        }

        Utils.runOnBackgroundThread(() -> {
            try {
                String htmlString = getUpdateMessage();
                if (htmlString == null) {
                    return;
                }

                Utils.runOnMainThread(()-> {
                    if (context.isDestroyed()) {
                        Logger.printInfo(() -> "Cannot show changelog, activity is destroyed: " + context);
                        return;
                    }

                    latestChangelogVersion = patchVersion;
                    Pref.setLatestChangelogVersion(patchVersion);
                    Pref.setChangelog(htmlString);
                    showChangelogDialog(context);
                });
            } catch (Exception ex) {
                PikoUtils.logger(ex);
            }
        });
    }

}
