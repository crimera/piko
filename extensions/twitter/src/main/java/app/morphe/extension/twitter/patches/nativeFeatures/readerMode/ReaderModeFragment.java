package app.morphe.extension.twitter.patches.nativeFeatures.readerMode;


import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import app.morphe.extension.shared.Utils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.Fragment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import app.morphe.extension.shared.StringRef;
import android.webkit.JavascriptInterface;

public class ReaderModeFragment extends Fragment {

    private WebView webView;

    private String tweetId;

     // JavaScript interface class
    public static class WebAppInterface {
        final Context mContext;

        WebAppInterface(Context context) {
            mContext = context;
        }

        @JavascriptInterface
        public void copyText(String text) {
            Utils.setClipboard(text);
            Utils.showToastShort(StringRef.str("link_copied_to_clipboard"));
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tweetId = getArguments().getString(ReaderModeUtils.ARG_TWEET_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(Utils.getResourceIdentifier("webview", "layout"), container, false);
        
        // View  progressBarView = inflater.inflate(Utils.getResourceIdentifier("progress_bar", "layout"), container, false);
        // ProgressBar progressBar = progressBarView.findViewById(Utils.getResourceIdentifier("progressbar", "id"));
        // progressBar.setVisibility(View.VISIBLE);

        webView = rootView.findViewById(Utils.getResourceIdentifier("webview", "id"));
        webView.getSettings().setJavaScriptEnabled(true);
         webView.addJavascriptInterface(new WebAppInterface(getContext()), "Android");
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient() {
            // @Override
            // public void onPageStarted(WebView view, String url, Bitmap favicon) {
            //     progressBar.setVisibility(View.VISIBLE);
            // }   
            @Override
            public void onPageFinished(WebView view, String url) {
                // progressBar.setVisibility(View.GONE);
                webView.evaluateJavascript(ReaderModeUtils.injectJS(), null);
            }
        });

        if (tweetId != null && !tweetId.isEmpty()) {
            loadDynamicUrl(tweetId);
        } else {
            webView.loadData(ReaderModeUtils.NO_CONTENT,"text/html", "UTF-8");
        }

        return rootView;
    }

    private void loadDynamicUrl(String tweetId) {

        new Thread(() -> {
            final String finalHtml = ReaderModeUtils.buildHtml(tweetId);

            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> webView.loadDataWithBaseURL(
                        null, finalHtml, "text/html", "UTF-8", null));
            }
        }).start();
    }

    private String getHtmlFromUrl(String urlString) {
        StringBuilder content = new StringBuilder();
        HttpURLConnection conn = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "TelegramBot");

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            reader.close();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null) conn.disconnect();
            try { if (reader != null) reader.close(); } catch (Exception ignore) {}
        }
        return content.toString();
    }
}