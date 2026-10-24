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
import android.os.Build;
import android.window.OnBackInvokedDispatcher;
import androidx.annotation.RequiresApi;

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
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
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
import android.app.Application;

public final class InstantsVaultActivity {

    private InstantsVaultActivity() {}

    public static void setupOnActivity(Activity activity) {
        Controller controller = new Controller(activity);
        controller.setup();
        if (activity != null && activity.getApplication() != null) {
            activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity a, Bundle b) {}
                @Override public void onActivityStarted(Activity a) {}
                @Override public void onActivityResumed(Activity a) {}
                @Override public void onActivityPaused(Activity a) {}
                @Override public void onActivityStopped(Activity a) {}
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                @Override
                public void onActivityDestroyed(Activity a) {
                    if (a == activity) {
                        a.getApplication().unregisterActivityLifecycleCallbacks(this);
                        controller.onDestroy();
                    }
                }
            });
        }
    }

    private static final class Controller {
        private static final String SUBFOLDER = "Instants";
        private static final int COLUMNS = 3;

        private final Activity activity;
        private List<String[]> items;
        private String filter = "";
        private FrameLayout content;
        private EditText searchField;
        private TextView clearAllButton;
        private float density;
        private int tileSize;
        private ThumbLoader thumbs;
        private final Handler ui = new Handler(Looper.getMainLooper());
        private final Runnable rebuild = this::rebuildContent;
        private final View.OnLayoutChangeListener contentLayoutListener =
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        updateTileSize(right - left);

        private final List<Tile> tiles = new ArrayList<>();
        private ScrollView scroll;
        private final Runnable sweep = this::sweepVisible;
        /** Rows whose link came back dead, drained in one pass so a bad batch redraws once. */
        private final Set<String> doomed = new HashSet<>();
        private final Runnable drainDoomed = this::forgetDoomed;

        Controller(Activity activity) {
            this.activity = activity;
        }

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

        void setup() {
            density = activity.getResources().getDisplayMetrics().density;
            thumbs = new ThumbLoader();

            int screenW = activity.getResources().getDisplayMetrics().widthPixels;
            int gap = dp(2);
            tileSize = (screenW - gap * (COLUMNS + 1)) / COLUMNS;

            PikoInstantsDb db = PikoInstantsDb.getInstance(activity);
            db.purgeExpired();
            items = db.getAll();
            pruneThumbCache();

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
            InstagramPreferenceStyle.applySystemBarStyle(activity);
            root.addView(buildToolbar());
            if (!items.isEmpty()) {
                searchField = buildSearchField();
                root.addView(searchField);
            }

            content = new FrameLayout(activity);
            content.addOnLayoutChangeListener(contentLayoutListener);
            root.addView(content, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1));
            rebuildContent();

            root.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(0, insets.getSystemWindowInsetTop(), 0,
                        insets.getSystemWindowInsetBottom());
                return insets;
            });

            boolean backRegistered = false;
            if (activity instanceof androidx.activity.ComponentActivity) {
                try {
                    ((androidx.activity.ComponentActivity) activity).getOnBackPressedDispatcher().addCallback(
                            (androidx.lifecycle.LifecycleOwner) activity,
                            new androidx.activity.OnBackPressedCallback(true) {
                                @Override
                                public void handleOnBackPressed() {
                                    activity.finish();
                                }
                            }
                    );
                    backRegistered = true;
                } catch (Throwable ignored) {
                }
            }

            if (!backRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Api33BackHelper.register(activity, activity::finish);
            }

            activity.setContentView(root);
        }

        void onDestroy() {
            ui.removeCallbacksAndMessages(null);
            if (content != null) content.removeOnLayoutChangeListener(contentLayoutListener);
            releaseTiles();
            if (thumbs != null) {
                thumbs.shutdown();
                thumbs = null;
            }
        }

        /** Search field over the sender's username. Filtering rebuilds the body only — a full
         *  recreate() would restart every thumbnail fetch on each keystroke. */
        private EditText buildSearchField() {
            EditText search = new EditText(activity);
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
            int controlsVisibility = items.isEmpty() ? View.GONE : View.VISIBLE;
            if (searchField != null) searchField.setVisibility(controlsVisibility);
            if (clearAllButton != null) clearAllButton.setVisibility(controlsVisibility);
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
            scroll = new ScrollView(activity);
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

        private void updateTileSize(int width) {
            int availableWidth = width - content.getPaddingLeft() - content.getPaddingRight();
            int size = (availableWidth - dp(2) * (COLUMNS + 1)) / COLUMNS;
            if (size <= 0 || size == tileSize) return;

            tileSize = size;
            for (Tile tile : tiles) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) tile.view.getLayoutParams();
                params.width = size;
                params.height = size;
                tile.view.setLayoutParams(params);
            }
            scheduleSweep();
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

        private void pruneThumbCache() {
            File dir = new File(activity.getCacheDir(), "instants_thumbs");
            if (!dir.isDirectory()) return;
            Set<String> activeIds = new HashSet<>();
            for (String[] r : items) activeIds.add(r[0]);
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File f : files) {
                String name = f.getName();
                if (!name.endsWith(".jpg")) continue;
                String id = name.substring(0, name.length() - 4);
                if (!activeIds.contains(id)) f.delete();
            }
        }

        private File cacheFile(String mediaId) {
            if (mediaId == null || mediaId.isEmpty()) return null;
            return new File(new File(activity.getCacheDir(), "instants_thumbs"), mediaId + ".jpg");
        }

        private void forget(String mediaId) {
            drop(mediaId);
            rebuildContent();
        }

        private void forgetLater(String mediaId) {
            doomed.add(mediaId);
            ui.removeCallbacks(drainDoomed);
            ui.postDelayed(drainDoomed, 300);
        }

        private void forgetDoomed() {
            if (doomed.isEmpty()) return;
            for (String id : doomed) drop(id);
            doomed.clear();
            rebuildContent();
        }

        private void drop(String mediaId) {
            PikoInstantsDb.getInstance(activity).delete(mediaId);
            File f = cacheFile(mediaId);
            if (f != null) f.delete();
            for (int i = items.size() - 1; i >= 0; i--) {
                if (mediaId.equals(items.get(i)[0])) items.remove(i);
            }
        }

        private TextView centeredMessage(CharSequence text) {
            TextView tv = new TextView(activity);
            tv.setText(text);
            tv.setTextColor(themed("igds_color_secondary_text", 0xFFB0B0B0));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(24), dp(24), dp(24), dp(24));
            tv.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            return tv;
        }

        private LinearLayout buildGroupedBody(List<String[]> source) {
            Map<String, List<String[]>> groups = new LinkedHashMap<>();
            for (String[] row : source) {
                String userId = row.length > 6 ? row[6] : null;
                String senderId = userId != null && !userId.isEmpty()
                        ? userId : "name:" + (row[1] == null ? "" : row[1]);
                List<String[]> list = groups.get(senderId);
                if (list == null) {
                    list = new ArrayList<>();
                    groups.put(senderId, list);
                }
                list.add(row);
            }

            LinearLayout body = new LinearLayout(activity);
            body.setOrientation(LinearLayout.VERTICAL);

            int gap = dp(2);
            for (List<String[]> group : groups.values()) {
                String label = group.get(0)[1];
                if (label == null || label.isEmpty()) label = str("piko_someone");
                body.addView(buildHeader(label));

                LinearLayout rowLayout = null;
                for (int i = 0; i < group.size(); i++) {
                    if (i % COLUMNS == 0) {
                        rowLayout = new LinearLayout(activity);
                        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        rlp.topMargin = gap;
                        rlp.leftMargin = gap;
                        body.addView(rowLayout, rlp);
                    }
                    rowLayout.addView(buildTile(group.get(i), gap));
                }
            }
            return body;
        }

        private TextView buildHeader(String username) {
            TextView h = new TextView(activity);
            h.setText("@" + username);
            h.setTextColor(themed("igds_color_secondary_text", 0xFFB0B0B0));
            h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            h.setTypeface(null, android.graphics.Typeface.BOLD);
            h.setPadding(dp(12), dp(16), dp(12), dp(6));
            return h;
        }

        private View buildTile(String[] row, int gap) {
            FrameLayout tile = new FrameLayout(activity);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileSize, tileSize);
            lp.rightMargin = gap;
            tile.setLayoutParams(lp);
            tile.setBackgroundColor(themed("igds_color_secondary_background", 0xFF262626));

            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(image, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            TextView unavailable = new TextView(activity);
            unavailable.setText("✕");
            unavailable.setTextColor(0xFF888888);
            unavailable.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            unavailable.setGravity(Gravity.CENTER);
            unavailable.setVisibility(View.GONE);
            tile.addView(unavailable, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            String thumbUrl = (row[2] != null && !row[2].isEmpty()) ? row[2] : urlOf(row);
            tiles.add(new Tile(tile, image, unavailable, row, thumbUrl));

            if ("1".equals(row[4])) {
                TextView badge = new TextView(activity);
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
            LinearLayout bar = new LinearLayout(activity);
            bar.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

            ImageView back = new ImageView(activity);
            UI.setThemedIcon(back, UI.DRAWABLE_ARROW_BACK);
            back.setOnClickListener(v -> activity.finish());

            TextView title = new TextView(activity);
            title.setText(str("piko_view_saved_instants"));
            title.setTextColor(InstagramPreferenceStyle.primaryTextColor());
            InstagramPreferenceStyle.applyToolbarLayout(activity, bar, back, title, false);
            bar.addView(back);
            bar.addView(title);

            if (!items.isEmpty()) {
                TextView clear = new TextView(activity);
                clearAllButton = clear;
                clear.setText(str("piko_instants_clear_all"));
                clear.setTextColor(themed("igds_color_error_or_destructive", 0xFFFF5C5C));
                clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                clear.setOnClickListener(v -> confirm(str("piko_instants_clear_all_confirm"), str("piko_instants_clear_all"),
                        () -> {
                            PikoInstantsDb db = PikoInstantsDb.getInstance(activity);
                            db.clearAll();
                            items = db.getAll();
                            pruneThumbCache();
                            rebuildContent();
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
                Toast.makeText(activity, str("piko_instants_link_unavailable"), Toast.LENGTH_SHORT).show();
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
                        DownloadUtils.downloadMediaUrl(activity, url, SUBFOLDER, fileName);
                    } else if (which == 2) {
                        Utils.setClipboard(url);
                        Utils.showToastShort(str("piko_copied_media_link"));
                    } else {
                        activity.startActivity(Intent.createChooser(
                                new Intent(Intent.ACTION_VIEW, Uri.parse(url)), null));
                    }
                } catch (Exception e) {
                    PikoUtils.logger(e);
                }
            };

            CharSequence saved = savedOn(row);
            new android.app.AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(activity))
                    .setTitle(saved == null ? str("piko_download_options") : saved)
                    .setItems(options, onPick)
                    .show();
        }

        private void confirm(CharSequence message, String positiveText, Runnable onConfirm) {
            new android.app.AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(activity))
                    .setMessage(message)
                    .setPositiveButton(positiveText, (d, w) -> onConfirm.run())
                    .setNegativeButton(str("piko_cancel"), null)
                    .show();
        }

        private int dp(int v) {
            return (int) (v * density);
        }

        private static int themed(String attr, int fallback) {
            try {
                return UI.getThemedColour(attr);
            } catch (Throwable t) {
                return fallback;
            }
        }

        private interface OnLoadFailed {
            void onFailed(boolean dead);
        }

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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static class Api33BackHelper {
        static void register(Activity activity, Runnable onBack) {
            try {
                activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        onBack::run
                );
            } catch (Throwable ignored) {
            }
        }
    }
}
