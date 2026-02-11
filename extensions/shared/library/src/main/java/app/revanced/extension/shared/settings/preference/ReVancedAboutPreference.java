package app.revanced.extension.shared.settings.preference;

import static app.revanced.extension.shared.StringRef.str;
import static app.revanced.extension.shared.Utils.dipToPixels;
import static app.revanced.extension.shared.requests.Route.Method.GET;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.requests.Requester;
import app.revanced.extension.shared.requests.Route;

/**
 * Opens a dialog showing official links.
 */
@SuppressWarnings({"unused", "deprecation"})
public class ReVancedAboutPreference extends Preference {

    private static String useNonBreakingHyphens(String text) {
        // Replace any dashes with non breaking dashes, so the English text 'pre-release'
        // and the dev release number does not break and cover two lines.
        return text.replace("-", "&#8209;"); // #8209 = non breaking hyphen.
    }

    /**
     * Apps that do not support bundling resources must override this.
     *
     * @return A localized string to display for the key.
     */
    protected String getString(String key, Object ... args) {
        return str(key, args);
    }

    private String createDialogHtml(WebLink[] aboutLinks) {
        final boolean isNetworkConnected = Utils.isNetworkConnected();

        StringBuilder builder = new StringBuilder();
        builder.append("<html>");
        builder.append("<body style=\"text-align: center; padding: 10px;\">");

        String foregroundColorHex = Utils.getColorHexString(Utils.getAppForegroundColor());
        String backgroundColorHex = Utils.getColorHexString(Utils.getDialogBackgroundColor());
        // Apply light/dark mode colors.
        builder.append(String.format(
                "<style> body { background-color: %s; color: %s; } a { color: %s; } </style>",
                backgroundColorHex, foregroundColorHex, foregroundColorHex));

        if (isNetworkConnected) {
            builder.append("<img style=\"width: 100px; height: 100px;\" "
                    // Hide the image if it does not load.
                    + "onerror=\"this.style.display='none';\" "
                    + "src=\"").append(AboutLinksRoutes.aboutLogoUrl).append("\" />");
        }

        String patchesVersion = Utils.getPatchesReleaseVersion();

        // Add the title.
        builder.append("<h1>")
                .append("ReVanced")
                .append("</h1>");

        builder.append("<p>")
                // Replace hyphens with non breaking dashes so the version number does not break lines.
                .append(useNonBreakingHyphens(getString("revanced_settings_about_links_body", patchesVersion)))
                .append("</p>");

        // Add a disclaimer if using a dev release.
        if (patchesVersion.contains("dev")) {
            builder.append("<h3>")
                    // English text 'Pre-release' can break lines.
                    .append(useNonBreakingHyphens(getString("revanced_settings_about_links_dev_header")))
                    .append("</h3>");

            builder.append("<p>")
                    .append(getString("revanced_settings_about_links_dev_body"))
                    .append("</p>");
        }

        builder.append("<h2 style=\"margin-top: 30px;\">")
                .append(getString("revanced_settings_about_links_header"))
                .append("</h2>");

        builder.append("<div>");
        for (WebLink link : aboutLinks) {
            builder.append("<div style=\"margin-bottom: 20px;\">");
            builder.append(String.format("<a href=\"%s\">%s</a>", link.url, link.name));
            builder.append("</div>");
        }
        builder.append("</div>");

        builder.append("</body></html>");
        return builder.toString();
    }

    {
        setOnPreferenceClickListener(pref -> {
            // Show a progress spinner if the social links are not fetched yet.
            if (!AboutLinksRoutes.hasFetchedLinks() && Utils.isNetworkConnected()) {
                // Show a progress spinner, but only if the api fetch takes more than a half a second.
                final long delayToShowProgressSpinner = 500;
                ProgressDialog progress = new ProgressDialog(getContext());
                progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);

                Handler handler = new Handler(Looper.getMainLooper());
                Runnable showDialogRunnable = progress::show;
                handler.postDelayed(showDialogRunnable, delayToShowProgressSpinner);

                Utils.runOnBackgroundThread(() ->
                        fetchLinksAndShowDialog(handler, showDialogRunnable, progress));
            } else {
                // No network call required and can run now.
                fetchLinksAndShowDialog(null, null, null);
            }

            return false;
        });
    }

    private void fetchLinksAndShowDialog(@Nullable Handler handler,
                                         Runnable showDialogRunnable,
                                         @Nullable ProgressDialog progress) {
        WebLink[] links = AboutLinksRoutes.fetchAboutLinks();
        String htmlDialog = createDialogHtml(links);

        // Enable to randomly force a delay to debug the spinner logic.
        final boolean debugSpinnerDelayLogic = false;
        //noinspection ConstantConditions
        if (debugSpinnerDelayLogic && handler != null && Math.random() < 0.5f) {
            Utils.doNothingForDuration((long) (Math.random() * 4000));
        }

        Utils.runOnMainThreadNowOrLater(() -> {
            if (handler != null) {
                handler.removeCallbacks(showDialogRunnable);
            }
            if (progress != null) {
                progress.dismiss();
            }
            new WebViewDialog(getContext(), htmlDialog).show();
        });
    }

    public ReVancedAboutPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
    public ReVancedAboutPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    public ReVancedAboutPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public ReVancedAboutPreference(Context context) {
        super(context);
    }
}

/**
 * Displays html content as a dialog. Any links a user taps on are opened in an external browser.
 */
class WebViewDialog extends Dialog {

    private final String htmlContent;

    public WebViewDialog(@NonNull Context context, @NonNull String htmlContent) {
        super(context);
        this.htmlContent = htmlContent;
    }

    // JS required to hide any broken images. No remote javascript is ever loaded.
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE); // Remove default title bar.

        // Create main layout.
        LinearLayout mainLayout = new LinearLayout(getContext());
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        final int padding = dipToPixels(10);
        mainLayout.setPadding(padding, padding, padding, padding);
        // Set rounded rectangle background.
        ShapeDrawable mainBackground = new ShapeDrawable(new RoundRectShape(
                Utils.createCornerRadii(28), null, null));
        mainBackground.getPaint().setColor(Utils.getDialogBackgroundColor());
        mainLayout.setBackground(mainBackground);

        // Create WebView.
        WebView webView = new WebView(getContext());
        webView.setVerticalScrollBarEnabled(false); // Disable the vertical scrollbar.
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new OpenLinksExternallyWebClient());
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null);

        // Add WebView to layout.
        mainLayout.addView(webView);

        setContentView(mainLayout);

        // Set dialog window attributes
        Window window = getWindow();
        if (window != null) {
            Utils.setDialogWindowParameters(window);
        }
    }

    private class OpenLinksExternallyWebClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                getContext().startActivity(intent);
            } catch (Exception ex) {
                Logger.printException(() -> "Open link failure", ex);
            }
            // Dismiss the about dialog using a delay,
            // otherwise without a delay the UI looks hectic with the dialog dismissing
            // to show the settings while simultaneously a web browser is opening.
            Utils.runOnMainThreadDelayed(WebViewDialog.this::dismiss, 500);
            return true;
        }
    }
}

class WebLink {
    final boolean preferred;
    String name;
    final String url;

    WebLink(JSONObject json) throws JSONException {
        this(json.getBoolean("preferred"),
                json.getString("name"),
                json.getString("url")
        );
    }

    WebLink(boolean preferred, String name, String url) {
        this.preferred = preferred;
        this.name = name;
        this.url = url;
    }

    @NonNull
    @Override
    public String toString() {
        return "WebLink{" +
                "preferred=" + preferred +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}

class AboutLinksRoutes {
    /**
     * Backup icon url if the API call fails.
     */
    public static volatile String aboutLogoUrl = "https://revanced.app/favicon.ico";

    /**
     * Links to use if fetch links api call fails.
     */
    private static final WebLink[] NO_CONNECTION_STATIC_LINKS = {
            new WebLink(true, "ReVanced.app", "https://revanced.app")
    };

    private static final String SOCIAL_LINKS_PROVIDER = "https://api.revanced.app/v4";
    private static final Route.CompiledRoute GET_SOCIAL = new Route(GET, "/about").compile();

    @Nullable
    private static volatile WebLink[] fetchedLinks;

    static boolean hasFetchedLinks() {
        return fetchedLinks != null;
    }

    static WebLink[] fetchAboutLinks() {
        try {
            if (hasFetchedLinks()) return fetchedLinks;

            // Check if there is no internet connection.
            if (!Utils.isNetworkConnected()) return NO_CONNECTION_STATIC_LINKS;

            HttpURLConnection connection = Requester.getConnectionFromCompiledRoute(SOCIAL_LINKS_PROVIDER, GET_SOCIAL);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            Logger.printDebug(() -> "Fetching social links from: " + connection.getURL());

            // Do not show an exception toast if the server is down
            final int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                Logger.printDebug(() -> "Failed to get social links. Response code: " + responseCode);
                return NO_CONNECTION_STATIC_LINKS;
            }

            JSONObject json = Requester.parseJSONObjectAndDisconnect(connection);
            aboutLogoUrl = json.getJSONObject("branding").getString("logo");

            List<WebLink> links = new ArrayList<>();

            JSONArray donations = json.getJSONObject("donations").getJSONArray("links");
            for (int i = 0, length = donations.length(); i < length; i++) {
                WebLink link = new WebLink(donations.getJSONObject(i));
                if (link.preferred) {
                    // This could be localized, but TikTok does not support localized resources.
                    // All link names returned by the api are also non localized.
                    link.name = "Donate";
                    links.add(link);
                }
            }

            JSONArray socials = json.getJSONArray("socials");
            for (int i = 0, length = socials.length(); i < length; i++) {
                WebLink link = new WebLink(socials.getJSONObject(i));
                links.add(link);
            }

            Logger.printDebug(() -> "links: " + links);

            return fetchedLinks = links.toArray(new WebLink[0]);

        } catch (SocketTimeoutException ex) {
            Logger.printInfo(() -> "Could not fetch social links", ex); // No toast.
        } catch (JSONException ex) {
            Logger.printException(() -> "Could not parse about information", ex);
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to get about information", ex);
        }

        return NO_CONNECTION_STATIC_LINKS;
    }
}
