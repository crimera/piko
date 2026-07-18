/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.instants;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.db.PikoInstantsDb;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.shared.Utils;

/**
 * "Saved Instants" screen: shows every instant captured by {@link InstantsDownloadHook} as a grid of
 * thumbnails grouped by the sender (keyed on a stable user id, labelled by username). Tap a tile for
 * download / open / copy (reusing piko's download pipeline); long-press to remove it. Plain
 * programmatic UI so it has no layout-resource dependency, mirroring DeletedMessagesActivity.
 */
public class InstantsVaultActivity extends Activity {

    private static final String SUBFOLDER = "Instants";
    private static final int COLUMNS = 3;

    private List<String[]> items;
    private float density;
    private int tileSize;
    private ThumbLoader thumbs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        thumbs = new ThumbLoader();

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int gap = dp(2);
        tileSize = (screenW - gap * (COLUMNS + 1)) / COLUMNS;

        items = PikoInstantsDb.getInstance(this).getAll();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(themed("igds_color_primary_background", 0xFF000000));
        root.addView(buildToolbar());

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(str("piko_instants_vault_empty"));
            empty.setTextColor(themed("igds_color_secondary_text", 0xFFB0B0B0));
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            empty.setPadding(dp(24), dp(24), dp(24), dp(24));
            root.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        } else {
            ScrollView scroll = new ScrollView(this);
            scroll.addView(buildGroupedBody());
            root.addView(scroll, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
            return insets;
        });
        setContentView(root);
    }

    /** Groups items by a stable user id (fallback: username), preserving recency order of groups. */
    private LinearLayout buildGroupedBody() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        Map<String, List<String[]>> groups = new LinkedHashMap<>();
        for (String[] m : items) {
            String userId = m.length > 6 ? m[6] : null;
            String key = (userId != null && !userId.isEmpty()) ? userId : "name:" + (m[1] == null ? "" : m[1]);
            List<String[]> list = groups.get(key);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(key, list);
            }
            list.add(m);
        }

        for (Map.Entry<String, List<String[]>> e : groups.entrySet()) {
            List<String[]> rows = e.getValue();
            String username = rows.get(0)[1];
            if (username == null || username.isEmpty()) username = str("piko_media_unknown");
            container.addView(buildHeader(username));

            LinearLayout grid = new LinearLayout(this);
            grid.setOrientation(LinearLayout.VERTICAL);
            int gap = dp(2);
            grid.setPadding(gap, 0, gap, dp(8));

            LinearLayout currentRow = null;
            for (int i = 0; i < rows.size(); i++) {
                if (i % COLUMNS == 0) {
                    currentRow = new LinearLayout(this);
                    currentRow.setOrientation(LinearLayout.HORIZONTAL);
                    grid.addView(currentRow);
                }
                currentRow.addView(buildTile(rows.get(i), gap));
            }
            container.addView(grid);
        }
        return container;
    }

    private TextView buildHeader(String username) {
        TextView header = new TextView(this);
        header.setText(username);
        header.setTextColor(themed("igds_color_primary_text", 0xFFFFFFFF));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        header.setPadding(dp(14), dp(14), dp(14), dp(6));
        return header;
    }

    private View buildTile(String[] row, int gap) {
        FrameLayout tile = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileSize, tileSize);
        lp.setMargins(0, gap, gap, 0);
        tile.setLayoutParams(lp);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(themed("igds_color_secondary_background", 0xFF1A1A1A));
        tile.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // thumbnail source is the image url (present even for video instants)
        thumbs.load(row[2], image);

        if ("1".equals(row[4])) {
            TextView badge = new TextView(this);
            badge.setText("▶");
            badge.setTextColor(0xFFFFFFFF);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            badge.setPadding(dp(4), 0, dp(4), 0);
            badge.setBackgroundColor(0x99000000);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            blp.gravity = Gravity.BOTTOM | Gravity.END;
            blp.setMargins(0, 0, dp(4), dp(4));
            tile.addView(badge, blp);
        }

        tile.setOnClickListener(v -> showOptions(row));
        tile.setOnLongClickListener(v -> {
            confirm(str("piko_delete_saved_confirm"), str("piko_delete"), () -> {
                PikoInstantsDb.getInstance(this).delete(row[0]);
                recreate();
            });
            return true;
        });
        return tile;
    }

    private LinearLayout buildToolbar() {
        int pad = dp(12);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(pad, pad, pad, pad);
        bar.setBackgroundColor(themed("igds_color_primary_background", 0xFF101010));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextColor(themed("igds_color_primary_text", 0xFFFFFFFF));
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        back.setPadding(0, 0, dp(16), 0);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText(str("piko_view_saved_instants"));
        title.setTextColor(themed("igds_color_primary_text", 0xFFFFFFFF));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (!items.isEmpty()) {
            TextView clear = new TextView(this);
            clear.setText(str("piko_clear_all"));
            clear.setTextColor(themed("igds_color_error_or_destructive", 0xFFFF5C5C));
            clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            clear.setOnClickListener(v -> confirm(str("piko_clear_all_confirm"), str("piko_clear_all"),
                    () -> {
                        PikoInstantsDb.getInstance(this).clearAll();
                        recreate();
                    }));
            bar.addView(clear);
        }
        return bar;
    }

    /** Prefer the video url for a video instant, else the image url. */
    private static String urlOf(String[] row) {
        boolean isVideo = "1".equals(row[4]);
        String video = row[3];
        String image = row[2];
        if (isVideo && video != null && !video.isEmpty()) return video;
        return image;
    }

    private void showOptions(String[] row) {
        String url = urlOf(row);
        if (url == null || !url.startsWith("http")) {
            Toast.makeText(this, str("piko_media_not_available"), Toast.LENGTH_SHORT).show();
            return;
        }
        boolean isVideo = "1".equals(row[4]);
        CharSequence[] options = new CharSequence[] {
                str("piko_download_current_media"),
                isVideo ? str("piko_open_video_externally") : str("piko_open_image_externally"),
                str("piko_copy_media_link"),
        };
        DialogInterface.OnClickListener onPick = (d, which) -> {
            try {
                if (which == 0) {
                    String fileName = "piko_instant_" + row[0] + (isVideo ? ".mp4" : ".jpg");
                    DownloadUtils.downloadMediaUrl(this, url, SUBFOLDER, fileName);
                } else if (which == 2) {
                    Utils.setClipboard(url);
                    Utils.showToastShort(str("piko_copied_media_link"));
                } else {
                    startActivity(Intent.createChooser(
                            new Intent(Intent.ACTION_VIEW, Uri.parse(url)), null));
                }
            } catch (Exception e) {
                PikoUtils.logger(e);
            }
        };

        // Plain AlertDialog: the IGDS native box needs an Instagram-themed context, which a
        // standalone piko activity doesn't have (same as DeletedMessagesActivity).
        new android.app.AlertDialog.Builder(this)
                .setTitle(str("piko_download_options"))
                .setItems(options, onPick)
                .show();
    }

    private void confirm(CharSequence message, String positiveText, Runnable onConfirm) {
        new android.app.AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(positiveText, (d, w) -> onConfirm.run())
                .setNegativeButton(str("piko_cancel"), null)
                .show();
    }

    private int dp(int v) {
        return (int) (v * density);
    }

    /** Instagram's native (IGDS) colour for {@code attr}, so the screen tracks their light/dark theme.
     *  Falls back to {@code fallback} if the attr can't be resolved on this build — never crashes. */
    private static int themed(String attr, int fallback) {
        try {
            return UI.getThemedColour(attr);
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** Loads tile thumbnails off the main thread, caches them, and never throws — a failed fetch
     *  just leaves the tile's placeholder. Views are url-tagged so scrolled/reused tiles stay correct. */
    private final class ThumbLoader {
        private final ExecutorService pool = Executors.newFixedThreadPool(3);
        private final Handler main = new Handler(Looper.getMainLooper());
        private final LruCache<String, Bitmap> cache =
                new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8192)) {
                    @Override protected int sizeOf(String key, Bitmap b) {
                        return b.getByteCount() / 1024;
                    }
                };

        void load(String url, ImageView view) {
            view.setImageBitmap(null);
            if (url == null || !url.startsWith("http")) return;
            view.setTag(url);

            Bitmap cached = cache.get(url);
            if (cached != null) {
                view.setImageBitmap(cached);
                return;
            }
            pool.execute(() -> {
                Bitmap bmp = fetch(url);
                if (bmp == null) return;
                cache.put(url, bmp);
                main.post(() -> {
                    if (url.equals(view.getTag())) view.setImageBitmap(bmp);
                });
            });
        }

        private Bitmap fetch(String url) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);
                try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
                    byte[] data = bos.toByteArray();

                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, o);
                    o.inSampleSize = sampleSize(o.outWidth, o.outHeight, tileSize);
                    o.inJustDecodeBounds = false;
                    return BitmapFactory.decodeByteArray(data, 0, data.length, o);
                }
            } catch (Throwable t) {
                return null;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        private int sampleSize(int w, int h, int reqPx) {
            int sample = 1;
            if (reqPx <= 0) return 1;
            while (w / sample > reqPx * 2 || h / sample > reqPx * 2) sample *= 2;
            return sample;
        }
    }
}
