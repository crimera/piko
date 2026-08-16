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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.db.PikoInstantsDb;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.shared.Logger;
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
    private String filter = "";
    private FrameLayout content;
    private float density;
    private int tileSize;
    private ThumbLoader thumbs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable rebuild = this::rebuildContent;

    private final List<Tile> tiles = new ArrayList<>();
    private ScrollView scroll;
    private final Runnable sweep = this::sweepVisible;
    /** Rows whose link came back dead, drained in one pass so a bad batch redraws once. */
    private final Set<String> doomed = new HashSet<>();
    private final Runnable drainDoomed = this::forgetDoomed;

    private static final class Tile {
        final FrameLayout view;
        final ImageView image;
        final TextView unavailable;
        final String[] row;
        final String thumbUrl;
        boolean loaded;

        Tile(FrameLayout view, ImageView image, TextView unavailable, String[] row, String thumbUrl) {
            this.view = view;
            this.image = image;
            this.unavailable = unavailable;
            this.row = row;
            this.thumbUrl = thumbUrl;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        thumbs = new ThumbLoader();

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int gap = dp(2);
        tileSize = (screenW - gap * (COLUMNS + 1)) / COLUMNS;

        PikoInstantsDb db = PikoInstantsDb.getInstance(this);
        db.purgeExpired();
        items = db.getAll();
        pruneThumbCache();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(themed("igds_color_primary_background", 0xFF000000));
        root.addView(buildToolbar());
        if (!items.isEmpty()) root.addView(buildSearchField());

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        rebuildContent();

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
            return insets;
        });
        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        releaseTiles();
        if (thumbs != null) thumbs.shutdown();
        super.onDestroy();
    }

    /** Search field over the sender's username. Filtering rebuilds the body only — a full
     *  recreate() would restart every thumbnail fetch on each keystroke. */
    private EditText buildSearchField() {
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setHint(str("piko_search_username"));
        search.setHintTextColor(themed("igds_color_secondary_text", 0xFFB0B0B0));
        search.setTextColor(themed("igds_color_primary_text", 0xFFFFFFFF));
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        search.setBackgroundColor(themed("igds_color_secondary_background", 0xFF1A1A1A));
        search.setPadding(dp(14), dp(10), dp(14), dp(10));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(12), 0, dp(12), dp(8));
        search.setLayoutParams(lp);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                filter = s.toString().trim().toLowerCase(Locale.ROOT);
                rebuildContent();
            }
        });
        return search;
    }

    private List<String[]> visibleItems() {
        if (filter.isEmpty()) return items;
        List<String[]> out = new ArrayList<>();
        for (String[] m : items) {
            String username = m[1];
            if (username != null && username.toLowerCase(Locale.ROOT).contains(filter)) out.add(m);
        }
        return out;
    }

    private void rebuildContent() {
        releaseTiles();
        content.removeAllViews();
        List<String[]> visible = visibleItems();

        if (visible.isEmpty()) {
            scroll = null;
            content.addView(centeredMessage(items.isEmpty()
                    ? str("piko_instants_vault_empty")
                    : str("piko_instants_no_matches")));
            return;
        }
        scroll = new ScrollView(this);
        scroll.addView(buildGroupedBody(visible));
        content.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> scheduleSweep());
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(this::scheduleSweep);
        scheduleSweep();
    }

    private void scheduleSweep() {
        ui.removeCallbacks(sweep);
        ui.post(sweep);
    }

    private void sweepVisible() {
        if (scroll == null || tiles.isEmpty()) return;

        int top = scroll.getScrollY();
        int height = scroll.getHeight();
        if (height == 0) return;
        int from = top - height;
        int to = top + height * 2;

        for (Tile tile : tiles) {
            int tileTop = topWithinScroll(tile.view);
            boolean wanted = tileTop >= 0 && tileTop + tileSize >= from && tileTop <= to;
            if (wanted && !tile.loaded) {
                tile.loaded = true;
                thumbs.load(tile.thumbUrl, tile.row[0], tile.image, dead -> {
                    if (dead) forgetLater(tile.row[0]);
                    else tile.unavailable.setVisibility(View.VISIBLE);
                });
            } else if (!wanted && tile.loaded) {
                tile.loaded = false;
                tile.image.setImageBitmap(null);
                tile.image.setTag(null);
            }
        }
    }

    private int topWithinScroll(View view) {
        int offset = 0;
        View node = view;
        while (node != null && node != scroll) {
            offset += node.getTop();
            if (!(node.getParent() instanceof View)) return -1;
            node = (View) node.getParent();
        }
        return node == scroll ? offset : -1;
    }

    private void releaseTiles() {
        for (Tile tile : tiles) {
            tile.image.setImageBitmap(null);
            tile.image.setTag(null);
        }
        tiles.clear();
        ui.removeCallbacks(sweep);
    }

    /** Purging expired rows leaves their thumbnails behind, so drop any that no longer have one. */
    private void pruneThumbCache() {
        try {
            File dir = new File(getCacheDir(), "instants_thumbs");
            File[] files = dir.listFiles();
            if (files == null) return;

            Set<String> live = new HashSet<>();
            for (String[] m : items) live.add(m[0] + ".jpg");
            for (File f : files) {
                if (!live.contains(f.getName())) f.delete();
            }
        } catch (Throwable t) {
            Logger.printException(() -> "instants vault: thumb cache prune failed", t);
        }
    }

    /** Where a tile's thumbnail is kept between visits. Null if the cache dir isn't available. */
    private File cacheFile(String mediaId) {
        File dir = getCacheDir();
        if (dir == null || mediaId == null || mediaId.isEmpty()) return null;
        return new File(new File(dir, "instants_thumbs"), mediaId + ".jpg");
    }

    /** Drops a row that is gone for good and redraws. */
    private void forget(String mediaId) {
        drop(mediaId);
        ui.removeCallbacks(rebuild);
        ui.post(rebuild);
    }

    private void forgetLater(String mediaId) {
        doomed.add(mediaId);
        ui.removeCallbacks(drainDoomed);
        ui.postDelayed(drainDoomed, 400);
    }

    private void forgetDoomed() {
        if (doomed.isEmpty()) return;
        for (String mediaId : doomed) drop(mediaId);
        doomed.clear();
        ui.removeCallbacks(rebuild);
        ui.post(rebuild);
    }

    private void drop(String mediaId) {
        PikoInstantsDb.getInstance(this).delete(mediaId);
        File cached = cacheFile(mediaId);
        if (cached != null) cached.delete();
        for (int i = 0; i < items.size(); i++) {
            if (mediaId.equals(items.get(i)[0])) {
                items.remove(i);
                break;
            }
        }
    }

    private TextView centeredMessage(CharSequence text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(themed("igds_color_secondary_text", 0xFFB0B0B0));
        view.setGravity(Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        view.setPadding(dp(24), dp(24), dp(24), dp(24));
        view.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return view;
    }

    /** Groups items by a stable user id (fallback: username), preserving recency order of groups. */
    private LinearLayout buildGroupedBody(List<String[]> source) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        Map<String, List<String[]>> groups = new LinkedHashMap<>();
        for (String[] m : source) {
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
            if (username == null || username.isEmpty()) username = str("piko_instants_unknown_user");
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

        TextView unavailable = new TextView(this);
        unavailable.setText(str("piko_instants_unavailable"));
        unavailable.setTextColor(themed("igds_color_secondary_text", 0xFFB0B0B0));
        unavailable.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        unavailable.setGravity(Gravity.CENTER);
        unavailable.setVisibility(View.GONE);
        tile.addView(unavailable, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Normally the image url, which is present even for video instants — but fall back to the
        // playback url so a video that saved without one isn't mistaken for a dead row.
        String thumbUrl = (row[2] != null && !row[2].isEmpty()) ? row[2] : urlOf(row);
        tiles.add(new Tile(tile, image, unavailable, row, thumbUrl));

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
            confirm(str("piko_instants_delete_confirm"), str("piko_delete"), () -> forget(row[0]));
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

        // Instagram's own back arrow, themed. If the drawable can't be resolved on this build we'd
        // otherwise ship a toolbar with no way back, so fall back to a plain glyph.
        if (UI.addImageViewToViewGroup(bar, UI.DRAWABLE_ARROW_BACK, this::finish) == null) {
            TextView back = new TextView(this);
            back.setText("←");
            back.setTextColor(themed("igds_color_primary_text", 0xFFFFFFFF));
            back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            back.setOnClickListener(v -> finish());
            bar.addView(back);
        }

        TextView title = new TextView(this);
        title.setText(str("piko_view_saved_instants"));
        title.setTextColor(themed("igds_color_primary_text", 0xFFFFFFFF));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (!items.isEmpty()) {
            TextView clear = new TextView(this);
            clear.setText(str("piko_instants_clear_all"));
            clear.setTextColor(themed("igds_color_error_or_destructive", 0xFFFF5C5C));
            clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            clear.setOnClickListener(v -> confirm(str("piko_instants_clear_all_confirm"), str("piko_instants_clear_all"),
                    () -> {
                        PikoInstantsDb.getInstance(this).clearAll();
                        recreate();
                    }));
            bar.addView(clear);
        }
        return bar;
    }

    /** When this instant was captured, or null if the row predates the timestamp column. */
    private static CharSequence savedOn(String[] row) {
        try {
            long ts = Long.parseLong(row[5]);
            if (ts <= 0) return null;
            return str("piko_saved_on") + " "
                    + new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(ts));
        } catch (Exception e) {
            return null;
        }
    }

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
            Toast.makeText(this, str("piko_instants_link_unavailable"), Toast.LENGTH_SHORT).show();
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
        // Title carries the date, not setMessage — a message would replace the option list entirely.
        CharSequence saved = savedOn(row);
        new android.app.AlertDialog.Builder(this)
                .setTitle(saved == null ? str("piko_download_options") : saved)
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

    /** Told a thumbnail didn't load. {@code dead} means the CDN answered that it is gone. */
    private interface OnLoadFailed {
        void onFailed(boolean dead);
    }

    /** Thumbnails are cached in memory and on disk, so reopening the vault doesn't refetch the
     *  whole grid. Views are url-tagged so reused tiles stay correct. */
    private final class ThumbLoader {
        private final ExecutorService pool = Executors.newFixedThreadPool(3);
        private final Handler main = new Handler(Looper.getMainLooper());
        private final LruCache<String, Bitmap> cache =
                new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8192)) {
                    @Override protected int sizeOf(String key, Bitmap b) {
                        return b.getByteCount() / 1024;
                    }
                };

        void shutdown() {
            pool.shutdownNow();
            cache.evictAll();
        }

        void load(String url, String mediaId, ImageView view, OnLoadFailed onFailed) {
            view.setImageBitmap(null);
            view.setTag(url);

            Bitmap cached = url == null ? null : cache.get(url);
            if (cached != null) {
                view.setImageBitmap(cached);
                return;
            }
            pool.execute(() -> {
                Bitmap disk = readCached(mediaId);
                if (disk != null) {
                    if (url != null) cache.put(url, disk);
                    main.post(() -> {
                        if (url == null || url.equals(view.getTag())) view.setImageBitmap(disk);
                    });
                    return;
                }
                if (url == null || !url.startsWith("http")) {
                    main.post(() -> onFailed.onFailed(true));
                    return;
                }
                boolean[] dead = new boolean[1];
                Bitmap bmp = fetch(url, dead, mediaId);
                if (bmp == null) {
                    main.post(() -> onFailed.onFailed(dead[0]));
                    return;
                }
                cache.put(url, bmp);
                main.post(() -> {
                    if (url.equals(view.getTag())) view.setImageBitmap(bmp);
                });
            });
        }

        private Bitmap readCached(String mediaId) {
            try {
                File f = cacheFile(mediaId);
                if (f == null || !f.isFile()) return null;
                String path = f.getAbsolutePath();

                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, o);
                o.inSampleSize = sampleSize(o.outWidth, o.outHeight, tileSize);
                o.inJustDecodeBounds = false;
                return BitmapFactory.decodeFile(path, o);
            } catch (Throwable t) {
                return null;
            }
        }

        private void writeCached(String mediaId, Bitmap bmp) {
            try {
                File f = cacheFile(mediaId);
                if (f == null) return;
                File dir = f.getParentFile();
                if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
                try (FileOutputStream out = new FileOutputStream(f)) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, out);
                }
            } catch (Throwable t) {
                // A thumbnail we couldn't cache just gets refetched next time.
            }
        }

        private Bitmap fetch(String url, boolean[] dead, String mediaId) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    dead[0] = code == HttpURLConnection.HTTP_FORBIDDEN
                            || code == HttpURLConnection.HTTP_NOT_FOUND
                            || code == HttpURLConnection.HTTP_GONE;
                    return null;
                }
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
                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                    if (bmp != null) writeCached(mediaId, bmp);
                    return bmp;
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
            while (w / sample > reqPx || h / sample > reqPx) sample *= 2;
            return sample;
        }
    }
}
