/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Utils;

/**
 * Folder chips above the DM inbox. Chats are shown or hidden by collapsing their rows, and each row
 * is matched to its chat by thread key, never by name.
 */
@SuppressWarnings("unused")
public final class InboxFolderBar {
    private static final String LIST_ID = "inbox_refreshable_thread_list_recyclerview";
    private static final String LIST_CONTAINER_ID = "list_container";
    private static final String KEY_CLASS = "com.instagram.model.direct.DirectThreadKey";
    private static final Pattern POSITION = Pattern.compile("position=(\\d+)");
    private static final int BAR_TAG = 0x50494B4F;
    private static final int BAR_HEIGHT_DP = 44;
    // Chat rows are about 66dp tall; the search and header rows are smaller.
    private static final int MIN_ROW_DP = 56;
    private static final long THROTTLE_MS = 120;
    private static final long POLL_MS = 300;
    private static final long LOAD_MORE_MS = 250;
    private static final int MAX_STALLED_PRESSES = 4;
    private static final long SLOW_LOAD_MORE_MS = 1500;
    private static final int MAX_NO_FOOTER = 6;
    private static final int MAX_INJECTIONS = 3;

    // Instagram's accessibility label for a chat with unread messages, per language.
    private static final String[] UNREAD_LABELS = {
            "unread", "no leídas", "no leída", "não lidas", "não lida", "non lus", "non lu",
            "ungelesen", "non lette", "non letto", "непрочитанные", "okunmamış", "غير مقروءة",
            "belum dibaca", "未读", "未読", "읽지 않음"
    };

    private static WeakReference<ViewGroup> attachedList = new WeakReference<>(null);
    private static WeakReference<Activity> attachedActivity = new WeakReference<>(null);
    private static LinearLayout chips;
    private static Method holderOf;
    private static long lastRun;
    private static boolean initialized;
    private static boolean retryPending;
    private static boolean polling;
    private static int skippedFrames;
    private static String activeFolder = "";
    private static Map<String, String> assignments = new HashMap<>();
    private static Set<String> unreadKeys = new HashSet<>();

    // Items of categorized chats, kept to put them back after Instagram reloads the list.
    private static final Map<String, Object> cachedItems = new HashMap<>();
    private static int lastLoadedCount = -1;
    private static int stalledPresses;
    private static int noFooterHits;
    private static int injections;
    private static long lastLoadPress;
    private static long lastCacheScan;
    private static boolean scrolledForLoading;
    // Picture of each categorized chat's row, shown while Instagram has not loaded the real one.
    private static final Map<String, Long> lastCapture = new HashMap<>();
    private static final Map<String, Bitmap> rowPictures = new HashMap<>();
    private static LinearLayout placeholders;
    private static int placeholderTop = -1;
    private static String placeholderKeys = "";

    private InboxFolderBar() {
    }

    /** Called once from the application init hook. */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        refreshCache();
        try {
            Application app = (Application) Utils.getContext().getApplicationContext();
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityResumed(Activity activity) { watch(activity); }
                @Override public void onActivityCreated(Activity a, Bundle b) { }
                @Override public void onActivityStarted(Activity a) { }
                @Override public void onActivityPaused(Activity a) { }
                @Override public void onActivityStopped(Activity a) { }
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                @Override public void onActivityDestroyed(Activity a) { }
            });
        } catch (Throwable t) {
            PikoUtils.logger(t);
        }
    }

    private static void refreshCache() {
        activeFolder = DirectOrganizer.activeFolder();
        assignments = DirectOrganizer.getAssignments();
        unreadKeys = DirectOrganizer.getUnread();
        updateDots();
    }

    private static boolean filterActive() {
        return !activeFolder.isEmpty() || !assignments.isEmpty();
    }

    private static void watch(Activity activity) {
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (decor == null) return;
        decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (activity.isFinishing()) {
                    decor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    return;
                }
                try {
                    tick(activity);
                } catch (Throwable t) {
                    PikoUtils.logger(t);
                }
            }
        });
    }

    private static void tick(Activity activity) {
        long now = System.currentTimeMillis();
        if (now - lastRun < THROTTLE_MS) {
            if (!retryPending) {
                retryPending = true;
                activity.getWindow().getDecorView().postDelayed(() -> {
                    retryPending = false;
                    try {
                        tick(activity);
                    } catch (Throwable t) {
                        PikoUtils.logger(t);
                    }
                }, THROTTLE_MS);
            }
            return;
        }
        lastRun = now;

        int listId = activity.getResources().getIdentifier(LIST_ID, "id", activity.getPackageName());
        View found = listId == 0 ? null : activity.findViewById(listId);
        if (found == null || !found.getClass().getName().endsWith("RecyclerView")) return;
        ViewGroup list = (ViewGroup) found;

        if (attachedList.get() != list) {
            attachedList = new WeakReference<>(list);
            attachedActivity = new WeakReference<>(activity);
            chips = null;
            refreshCache();
            installBar(activity, list);
            watchDraws(list);
            watchAddedRows(list);
        }
        if (chips != null && chips.getChildCount() == 0 && !DirectOrganizer.getCategories().isEmpty()) {
            View bar = (View) chips.getParent();
            refreshChips(activity, list, bar);
        }
        applyFilter(list, false);
        startPolling(list);
    }

    /** Filters right before each frame so a row that should be hidden is never drawn. */
    private static void watchDraws(ViewGroup list) {
        list.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (attachedList.get() != list || !list.isAttachedToWindow()) {
                    list.getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
                try {
                    if (filterActive()) {
                        boolean changed = applyFilter(list, true);
                        // Never hold back more than a couple of frames in a row.
                        skippedFrames = changed ? skippedFrames + 1 : 0;
                        if (skippedFrames > 2) {
                            skippedFrames = 0;
                            return true;
                        }
                        return !changed;
                    }
                } catch (Throwable t) {
                    PikoUtils.logger(t);
                }
                return true;
            }
        });
    }

    /** Compose rows are rebound without a layout pass, so keep checking while filtering. */
    private static void startPolling(ViewGroup list) {
        if (polling) return;
        polling = true;
        list.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!list.isAttachedToWindow() || attachedList.get() != list) {
                    polling = false;
                    return;
                }
                try {
                    if (filterActive()) applyFilter(list, false);
                } catch (Throwable t) {
                    PikoUtils.logger(t);
                }
                list.postDelayed(this, POLL_MS);
            }
        }, POLL_MS);
    }

    private static FrameLayout listContainer(Activity activity) {
        int id = activity.getResources().getIdentifier(LIST_CONTAINER_ID, "id", activity.getPackageName());
        View container = id == 0 ? null : activity.findViewById(id);
        return container instanceof FrameLayout ? (FrameLayout) container : null;
    }

    private static void installBar(Activity activity, ViewGroup list) {
        FrameLayout host = listContainer(activity);
        if (host == null) return;
        View old = host.findViewWithTag(BAR_TAG);
        if (old != null) host.removeView(old);

        float density = activity.getResources().getDisplayMetrics().density;
        int padding = (int) (12 * density);
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setTag(BAR_TAG);
        scroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(activity);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setPadding(padding, 0, padding, 0);
        scroll.addView(chips, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        host.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (BAR_HEIGHT_DP * density), Gravity.TOP));
        refreshChips(activity, list, scroll);
    }

    private static void refreshChips(Activity activity, ViewGroup list, View bar) {
        Set<String> folders = DirectOrganizer.getCategories();
        boolean show = !folders.isEmpty();
        bar.setVisibility(show ? View.VISIBLE : View.GONE);
        View listParent = (View) list.getParent();
        if (listParent.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            int barHeight = (int) (BAR_HEIGHT_DP * activity.getResources().getDisplayMetrics().density);
            ((FrameLayout.LayoutParams) listParent.getLayoutParams()).topMargin = show ? barHeight : 0;
            listParent.requestLayout();
        }
        chips.removeAllViews();
        if (!show) return;

        addChip(activity, list, bar, str("piko_dm_all"), "", activeFolder.isEmpty());
        for (String folder : folders) {
            addChip(activity, list, bar, folder, folder, folder.equals(activeFolder));
        }
        updateDots();
    }

    /** Folder chip with an optional red dot for unread chats. */
    private static final class Chip extends TextView {
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        final String folder;
        boolean dot;

        Chip(Context context, String folder) {
            super(new ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault));
            this.folder = folder;
            density = context.getResources().getDisplayMetrics().density;
            dotPaint.setColor(0xFFFF3B30);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (dot) canvas.drawCircle(getWidth() - 7 * density, 7 * density, 4.5f * density, dotPaint);
        }
    }

    private static void updateDots() {
        if (chips == null) return;
        for (int i = 0; i < chips.getChildCount(); i++) {
            if (!(chips.getChildAt(i) instanceof Chip)) continue;
            Chip chip = (Chip) chips.getChildAt(i);
            boolean dot = false;
            for (String key : DirectOrganizer.threadsOf(chip.folder)) {
                dot |= unreadKeys.contains(key);
            }
            if (chip.dot != dot) {
                chip.dot = dot;
                chip.invalidate();
            }
        }
    }

    private static void addChip(Activity activity, ViewGroup list, View bar, String label, String folder,
                                boolean selected) {
        float density = activity.getResources().getDisplayMetrics().density;
        boolean dark = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        Chip chip = new Chip(activity, folder);
        chip.setText(label);
        chip.setTextSize(14);
        chip.setPadding((int) (14 * density), (int) (6 * density), (int) (14 * density), (int) (6 * density));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(20 * density);
        background.setColor(selected ? (dark ? 0xFFFFFFFF : 0xFF000000) : (dark ? 0xFF2C2C2E : 0xFFEFEFEF));
        chip.setTextColor(selected == dark ? 0xFF000000 : 0xFFFFFFFF);
        chip.setBackground(background);
        chip.setOnClickListener(v -> {
            lastLoadedCount = -1;
            stalledPresses = 0;
            injections = 0;
            scrolledForLoading = false;
            DirectOrganizer.setActiveFolder(folder);
            refreshCache();
            refreshChips(activity, list, bar);
            applyFilter(list, false);
            // A folder must not stay scrolled past its first row.
            list.scrollBy(0, -1000000);
        });
        if (!folder.isEmpty()) {
            chip.setOnLongClickListener(v -> {
                PopupMenu menu = new PopupMenu(activity, v);
                menu.getMenu().add(0, 1, 0, str("piko_dm_rename"));
                menu.getMenu().add(0, 2, 1, str("piko_dm_delete_category"));
                menu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) DirectOrganizer.showRenameDialog(activity, folder);
                    else DirectOrganizer.deleteCategory(folder);
                    return true;
                });
                menu.show();
                return true;
            });
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = (int) (8 * density);
        chips.addView(chip, params);
    }

    /** Re-reads the stored folders; called after they change. */
    public static void onFoldersChanged() {
        refreshCache();
        ViewGroup list = attachedList.get();
        if (list != null && !assignments.isEmpty()) {
            try {
                snapshot(list);
            } catch (Throwable t) {
                PikoUtils.logger(t);
            }
        }
        // The list's own context is a wrapper, so use the Activity it was attached from.
        Activity activity = attachedActivity.get();
        FrameLayout host = list == null || activity == null ? null : listContainer(activity);
        if (host == null) return;
        View bar = host.findViewWithTag(BAR_TAG);
        if (bar == null) installBar(activity, list);
        else refreshChips(activity, list, bar);
        applyFilter(list, false);
    }

    private static boolean isCompose(View view) {
        return view.getClass().getName().endsWith("ComposeView");
    }

    /**
     * Shows the selected folder's rows (or the uncategorized chats in the main list) and hides the
     * rest. With {@code fast}, rows already hidden are skipped.
     *
     * @return whether any row changed
     */
    private static boolean applyFilter(ViewGroup list, boolean fast) {
        boolean changed = false;
        boolean unreadChanged = false;
        boolean folderOpen = !activeFolder.isEmpty();
        int minRow = (int) (MIN_ROW_DP * list.getResources().getDisplayMetrics().density);

        // Everything above the notes tray (search) is not a chat.
        int first = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (!isCompose(child) && child.getHeight() >= minRow * 2) {
                first = i + 1;
                break;
            }
        }

        boolean headerPending = true;
        for (int i = first; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            boolean compose = isCompose(row);
            ViewGroup.LayoutParams params = row.getLayoutParams();
            // The list can reset a row's size when it rebinds it, so trust the real state.
            boolean hidden = row.getTag(BAR_TAG) != null && params != null && params.height == 1;
            if (fast && hidden) continue;

            String key = keyOfRow(list, row);
            boolean chat = key != null;
            // The "Messages / Requests" header is the first small row before any chat.
            if (compose && !chat && headerPending && !hidden && row.getHeight() < minRow) {
                headerPending = false;
                continue;
            }
            if (chat || (compose && !hidden && row.getHeight() >= minRow)) headerPending = false;

            String category = chat ? assignments.get(key) : null;
            boolean show = folderOpen ? chat && activeFolder.equals(category) : category == null;
            if (category != null && isUnread(row) != unreadKeys.contains(key)) {
                if (!unreadKeys.remove(key)) unreadKeys.add(key);
                unreadChanged = true;
            }

            if (show && chat && category != null && !hidden && row.getHeight() >= minRow) captureRow(row, key, false);
            if (show == !hidden) continue;
            changed = true;
            if (params != null) setHidden(row, params, !show, true);
        }
        if (!fast) ensureFolderChatsLoaded(list);
        if (unreadChanged) {
            DirectOrganizer.setUnread(unreadKeys);
            updateDots();
        }
        return changed;
    }

    /**
     * Called at the start of the inbox adapter's update method with its update builder. Saves the
     * items of categorized chats and, while a folder is open, adds the ones the new list lacks, so a
     * refresh never drops them.
     */
    @SuppressWarnings("unchecked")
    public static void onAdapterUpdate(Object builder) {
        try {
            Object raw = builder == null ? null : field(builder, "A00");
            if (!(raw instanceof List) || assignments.isEmpty()) return;
            List<Object> items = (List<Object>) raw;
            Set<String> keys = new HashSet<>();
            Set<Class<?>> chatTypes = new HashSet<>();
            int lastChat = -1;
            for (int i = 0; i < items.size(); i++) {
                String key = keyAt(items, i);
                if (key == null) continue;
                keys.add(key);
                chatTypes.add(items.get(i).getClass());
                lastChat = i;
                if (assignments.containsKey(key)) cachedItems.put(key, items.get(i));
            }
            if (activeFolder.isEmpty()) return;
            List<Object> extra = new ArrayList<>();
            for (Map.Entry<String, String> entry : assignments.entrySet()) {
                Object saved = cachedItems.get(entry.getKey());
                // A list without chats (the loading state) has no view definition for chat rows.
                if (entry.getValue().equals(activeFolder) && !keys.contains(entry.getKey()) && saved != null
                        && chatTypes.contains(saved.getClass())) {
                    extra.add(saved);
                }
            }
            if (!extra.isEmpty()) {
                items.addAll(Math.min(lastChat + 1, items.size()), extra);
            }
        } catch (Throwable t) {
            PikoUtils.logger(t);
        }
    }

    /** The adapter and its item list, with the chat key of each item, read through any row's holder. */
    private static final class Snapshot {
        Object adapter;
        List<?> items;
        Set<String> keys = new HashSet<>();
        int lastChatIndex = -1;
    }

    private static Snapshot snapshot(ViewGroup list) throws Exception {
        for (int i = 0; i < list.getChildCount(); i++) {
            Object holder = holderFor(list, list.getChildAt(i));
            List<?> items = holder == null ? null : itemList(holder);
            if (items == null) continue;
            Snapshot snap = new Snapshot();
            snap.adapter = field(holder, "A09");
            snap.items = items;
            for (int j = 0; j < items.size(); j++) {
                String key = keyAt(items, j);
                if (key == null) continue;
                snap.keys.add(key);
                snap.lastChatIndex = j;
                if (assignments.containsKey(key)) cachedItems.put(key, items.get(j));
            }
            return snap;
        }
        return null;
    }

    private static View findClickable(View view) {
        if (view.isClickable()) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findClickable(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static File pictureFile(String key) {
        return new File(new File(Utils.getContext().getFilesDir(), "piko_dm_rows"), Integer.toHexString(key.hashCode()) + ".png");
    }

    /** Saves a picture of a chat row that is fully on screen, at most every 20 seconds per chat. */
    private static void captureRow(View row, String key, boolean now) {
        try {
            long time = System.currentTimeMillis();
            Long last = lastCapture.get(key);
            if (!now && last != null && time - last < 20000 || row.getWidth() <= 0 || row.getHeight() <= 0) return;
            // A row that is still loading has no text yet; do not replace a good picture with it.
            List<String> texts = new ArrayList<>();
            collect(row, texts);
            if (texts.isEmpty()) return;
            Bitmap picture = Bitmap.createBitmap(row.getWidth(), row.getHeight(), Bitmap.Config.ARGB_8888);
            row.draw(new Canvas(picture));
            boolean blank = true;
            for (int y = 0; y < picture.getHeight() && blank; y += 8) {
                for (int x = 0; x < picture.getWidth(); x += 8) {
                    if ((picture.getPixel(x, y) >>> 24) != 0) {
                        blank = false;
                        break;
                    }
                }
            }
            if (blank) return;
            lastCapture.put(key, time);
            rowPictures.put(key, picture);
            File file = pictureFile(key);
            Runnable write = () -> {
                try {
                    file.getParentFile().mkdirs();
                    // Write to a temporary file first so closing the app mid-write cannot corrupt the picture.
                    File temp = new File(file.getPath() + ".tmp");
                    try (FileOutputStream out = new FileOutputStream(temp)) {
                        picture.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                    if (!temp.renameTo(file)) temp.delete();
                } catch (Exception e) {
                    PikoUtils.logger(e);
                }
            };
            if (now) write.run();
            else Utils.runOnBackgroundThread(write);
        } catch (Throwable t) {
            PikoUtils.logger(t);
        }
    }

    /** Deletes a chat's saved picture once it leaves its folder. */
    public static void forgetRow(String key) {
        rowPictures.remove(key);
        lastCapture.remove(key);
        pictureFile(key).delete();
    }

    private static Bitmap pictureOf(String key) {
        Bitmap picture = rowPictures.get(key);
        File file = pictureFile(key);
        if (picture == null && file.exists()) {
            picture = BitmapFactory.decodeFile(file.getPath());
            if (picture != null) rowPictures.put(key, picture);
            else file.delete();
        }
        return picture;
    }

    /** Shows the saved pictures of the given chats where their rows will be, until the real rows load. */
    private static void showPlaceholders(ViewGroup list, Set<String> missing) {
        Activity activity = attachedActivity.get();
        FrameLayout host = activity == null ? null : listContainer(activity);
        if (host == null) return;
        if (placeholders == null || placeholders.getParent() != host) {
            placeholderKeys = "";
            placeholders = new LinearLayout(activity);
            placeholders.setOrientation(LinearLayout.VERTICAL);
            host.addView(placeholders, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        }
        List<String> keys = new ArrayList<>();
        for (String key : missing) {
            if (pictureOf(key) != null) keys.add(key);
        }
        if (keys.isEmpty() || activeFolder.isEmpty()) {
            placeholders.setVisibility(View.GONE);
            return;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        String signature = String.join(",", keys);
        if (!signature.equals(placeholderKeys)) {
            placeholderKeys = signature;
            placeholders.removeAllViews();
            // Plain theme: Instagram's own attributes are not always resolvable early on.
            Context plain = new ContextThemeWrapper(activity, android.R.style.Theme_DeviceDefault);
            for (String key : keys) {
                FrameLayout item = new FrameLayout(plain);
                ImageView picture = new ImageView(plain);
                picture.setImageBitmap(pictureOf(key));
                picture.setAdjustViewBounds(true);
                picture.setAlpha(0.5f);
                item.addView(picture, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                ProgressBar spinner = new ProgressBar(plain);
                spinner.setIndeterminate(true);
                int size = (int) (28 * density);
                FrameLayout.LayoutParams spinnerParams = new FrameLayout.LayoutParams(
                        size, size, Gravity.END | Gravity.CENTER_VERTICAL);
                spinnerParams.setMarginEnd((int) (24 * density));
                item.addView(spinner, spinnerParams);
                placeholders.addView(item, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        // They go under the "Messages / Requests" header, after the folder's rows that already loaded.
        int minRow = (int) (MIN_ROW_DP * density);
        int[] hostPos = new int[2];
        int[] rowPos = new int[2];
        host.getLocationInWindow(hostPos);
        int top = placeholderTop >= 0 ? placeholderTop : (int) ((BAR_HEIGHT_DP + 380) * density);
        int loadedRows = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (isCompose(child) && child.getHeight() < minRow && child.getHeight() > 1) {
                child.getLocationInWindow(rowPos);
                top = rowPos[1] + child.getHeight() - hostPos[1];
                placeholderTop = top;
            } else if (isCompose(child) && child.getHeight() >= minRow && child.getTag(BAR_TAG) == null
                    && keyOfRow(list, child) != null) {
                loadedRows += child.getHeight();
            }
        }
        placeholders.setTranslationY(top + loadedRows);
        placeholders.setVisibility(View.VISIBLE);
    }

    /** Presses the "+" button after the last chat, which makes Instagram load more chats. */
    private static boolean clickLoadMore(ViewGroup list) throws Exception {
        int lastChat = -1;
        for (int i = 0; i < list.getChildCount(); i++) {
            if (keyOfRow(list, list.getChildAt(i)) != null) lastChat = i;
        }
        for (int i = list.getChildCount() - 1; i > lastChat && lastChat >= 0; i--) {
            View child = list.getChildAt(i);
            View target = isCompose(child) ? null : findClickable(child);
            if (target != null) {
                target.performClick();
                return true;
            }
        }
        return false;
    }

    /** Sends the adapter its current items plus the given ones; it works out the changes itself. */
    private static void injectItems(Snapshot snap, List<Object> extra) throws Exception {
        Method submit = null;
        for (Class<?> c = snap.adapter.getClass(); c != null && submit == null; c = c.getSuperclass()) {
            try {
                submit = c.getDeclaredMethod("A0g", List.class);
            } catch (NoSuchMethodException ignored) {
            }
        }
        if (submit == null) throw new NoSuchMethodException("A0g(List) not found on " + snap.adapter.getClass().getName());
        submit.setAccessible(true);
        List<Object> next = new ArrayList<>(snap.items);
        next.addAll(Math.min(snap.lastChatIndex + 1, next.size()), extra);
        submit.invoke(snap.adapter, next);
    }

    /**
     * A refresh resets the list to the recent chats, so an old chat of the open folder can be
     * missing. Put back its saved item; if it was never seen this session, press "+" as each page
     * arrives until it shows up or the inbox ends.
     */
    private static void ensureFolderChatsLoaded(ViewGroup list) {
        long now = System.currentTimeMillis();
        try {
            if (activeFolder.isEmpty()) {
                showPlaceholders(list, new HashSet<>());
                if (!assignments.isEmpty() && now - lastCacheScan > 2000) {
                    lastCacheScan = now;
                    snapshot(list);
                }
                return;
            }
            Set<String> wanted = DirectOrganizer.threadsOf(activeFolder);
            Snapshot snap = wanted.isEmpty() ? null : snapshot(list);
            if (snap == null) return;
            Set<String> missing = new HashSet<>(wanted);
            missing.removeAll(snap.keys);
            showPlaceholders(list, missing);
            if (missing.isEmpty()) {
                if (scrolledForLoading) {
                    scrolledForLoading = false;
                    list.scrollBy(0, -1000000);
                }
                lastLoadedCount = -1;
                stalledPresses = 0;
                injections = 0;
                return;
            }
            // A new page or a refresh changes the count: start over.
            if (snap.keys.size() != lastLoadedCount) {
                lastLoadedCount = snap.keys.size();
                stalledPresses = 0;
                noFooterHits = 0;
                lastLoadPress = 0;
            }
            if (now - lastLoadPress < LOAD_MORE_MS) return;

            List<Object> saved = new ArrayList<>();
            for (String key : missing) {
                if (cachedItems.containsKey(key)) saved.add(cachedItems.get(key));
            }
            if (!saved.isEmpty() && injections < MAX_INJECTIONS) {
                lastLoadPress = now;
                injections++;
                scrolledForLoading = true;
                injectItems(snap, saved);
            } else if (noFooterHits < MAX_NO_FOOTER) {
                // Pages can take seconds: after a few quick tries, keep going at a slower pace.
                if (stalledPresses >= MAX_STALLED_PRESSES && now - lastLoadPress < SLOW_LOAD_MORE_MS) return;
                lastLoadPress = now;
                if (stalledPresses < MAX_STALLED_PRESSES) stalledPresses++;
                scrolledForLoading = true;
                if (!clickLoadMore(list)) noFooterHits++;
            }
        } catch (Throwable t) {
            PikoUtils.logger(t);
        }
    }

    /**
     * The list keeps the size of a GONE row, so a hidden row is collapsed instead: 1px tall (Compose
     * needs a size to keep its semantics) with a -1px margin, clipped and faded because Compose can
     * draw outside its bounds.
     */
    private static void setHidden(View row, ViewGroup.LayoutParams params, boolean hide, boolean relayout) {
        ViewGroup.MarginLayoutParams margins = params instanceof ViewGroup.MarginLayoutParams
                ? (ViewGroup.MarginLayoutParams) params : null;
        if (hide) {
            row.setTag(BAR_TAG, new int[]{params.height, margins == null ? 0 : margins.topMargin,
                    margins == null ? 0 : margins.bottomMargin});
            params.height = 1;
            if (margins != null) {
                margins.topMargin = -1;
                margins.bottomMargin = 0;
            }
        } else {
            Object saved = row.getTag(BAR_TAG);
            int[] original = saved instanceof int[] ? (int[]) saved : new int[]{ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0};
            params.height = original[0];
            if (margins != null) {
                margins.topMargin = original[1];
                margins.bottomMargin = original[2];
            }
            row.setTag(BAR_TAG, null);
        }
        if (relayout) row.setLayoutParams(params);
        row.setAlpha(hide ? 0f : 1f);
        row.setClipBounds(hide ? new Rect(0, 0, 1, 1) : null);
    }

    private static boolean chatBefore(ViewGroup list, View child) {
        int end = list.indexOfChild(child);
        for (int i = 0; i < end; i++) {
            if (keyOfRow(list, list.getChildAt(i)) != null) return true;
        }
        return false;
    }

    /** Hides a chat row when the list adds it, before it is measured, so it never takes space. */
    private static void watchAddedRows(ViewGroup list) {
        list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                try {
                    ViewGroup.LayoutParams params = child.getLayoutParams();
                    String key = params == null || !filterActive() ? null : keyOfRow(list, child);
                    if (key == null) {
                        boolean hidden = params != null && child.getTag(BAR_TAG) != null && params.height == 1;
                        if (params != null && !activeFolder.isEmpty() && !hidden && chatBefore(list, child)) {
                            setHidden(child, params, true, false);
                        }
                        return;
                    }
                    String category = assignments.get(key);
                    boolean show = activeFolder.isEmpty() ? category == null : activeFolder.equals(category);
                    boolean hidden = child.getTag(BAR_TAG) != null && params.height == 1;
                    if (show != hidden) return;
                    setHidden(child, params, !show, false);
                } catch (Throwable t) {
                    PikoUtils.logger(t);
                }
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
            }
        });
    }

    private static Object field(Object owner, String name) throws Exception {
        for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    /** The list's view holder for a child row; member names are obfuscated, so match by shape. */
    private static Object holderFor(ViewGroup list, View row) throws Exception {
        if (holderOf == null) {
            for (Class<?> c = list.getClass(); c != null && holderOf == null && c != ViewGroup.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == View.class
                            && m.getReturnType().getName().equals("X.03Tj") && !Modifier.isStatic(m.getModifiers())) {
                        m.setAccessible(true);
                        Object candidate = m.invoke(list, row);
                        if (candidate != null && candidate.toString().contains("position=")) {
                            holderOf = m;
                            break;
                        }
                    }
                }
            }
        }
        return holderOf == null ? null : holderOf.invoke(list, row);
    }

    /** The adapter's ordered item list; each item carries its chat's DirectThreadKey. */
    private static List<?> itemList(Object holder) throws Exception {
        Object a09 = field(holder, "A09");
        Object a08 = a09 == null ? null : field(a09, "A08");
        Object a01 = a08 == null ? null : field(a08, "A01");
        return a01 instanceof List ? (List<?>) a01 : null;
    }

    private static String keyAt(List<?> items, int index) throws Exception {
        if (index < 0 || index >= items.size()) return null;
        Object item = items.get(index);
        Object a09 = item == null ? null : field(item, "A09");
        Object a02 = a09 == null ? null : field(a09, "A02");
        Object key = a02 == null ? null : field(a02, "A04");
        return key != null && key.getClass().getName().equals(KEY_CLASS) ? key.toString() : null;
    }

    private static int positionOf(Object holder) {
        Matcher m = POSITION.matcher(String.valueOf(holder));
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** Thread key of a row, or null when it cannot be read; a null key is never matched by name. */
    private static String keyOfRow(ViewGroup list, View row) {
        try {
            Object holder = holderFor(list, row);
            if (holder == null) return null;
            Object own = field(holder, "A03");
            if (own != null && own.getClass().getName().equals(KEY_CLASS)) return own.toString();
            List<?> items = itemList(holder);
            int position = positionOf(holder);
            return items == null || position < 0 ? null : keyAt(items, position + DirectOrganizer.rowOffset());
        } catch (Throwable t) {
            PikoUtils.logger(t);
            return null;
        }
    }

    /** Learns the row offset from the long-pressed chat, whose thread key is known. */
    public static void calibrate(View pressed, String threadKey) {
        try {
            ViewGroup list = attachedList.get();
            View row = pressed;
            while (list != null && row != null && row.getParent() != list) {
                row = row.getParent() instanceof View ? (View) row.getParent() : null;
            }
            if (list == null || row == null) return;
            captureRow(row, threadKey, true);
            Object holder = holderFor(list, row);
            List<?> items = holder == null ? null : itemList(holder);
            int position = holder == null ? -1 : positionOf(holder);
            if (items == null || position < 0) return;
            for (int i = 0; i < items.size(); i++) {
                if (threadKey.equals(keyAt(items, i))) {
                    if (i - position != DirectOrganizer.rowOffset()) DirectOrganizer.setRowOffset(i - position);
                    return;
                }
            }
        } catch (Throwable t) {
            PikoUtils.logger(t);
        }
    }

    /** Whether the row's accessibility text carries the unread label ("Name, unread, preview"). */
    private static boolean isUnread(View row) {
        List<String> descriptions = new ArrayList<>();
        collect(row, descriptions);
        for (String description : descriptions) {
            for (String piece : description.split(", ")) {
                String label = piece.trim().toLowerCase();
                for (String unread : UNREAD_LABELS) {
                    if (label.equals(unread)) return true;
                }
            }
        }
        return false;
    }

    private static void collect(View view, List<String> descriptions) {
        CharSequence own = view.getContentDescription();
        if (own != null && own.length() > 0) descriptions.add(own.toString());
        AccessibilityNodeProvider provider = view.getAccessibilityNodeProvider();
        if (provider != null) {
            walk(provider, AccessibilityNodeProvider.HOST_VIEW_ID, descriptions, 0);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), descriptions);
        }
    }

    /** Walks a Compose view's virtual accessibility tree; child ids are read reflectively. */
    private static void walk(AccessibilityNodeProvider provider, int virtualId, List<String> descriptions, int depth) {
        if (depth > 8) return;
        AccessibilityNodeInfo info = provider.createAccessibilityNodeInfo(virtualId);
        if (info == null) return;
        CharSequence description = info.getContentDescription();
        if (description != null && description.length() > 0) descriptions.add(description.toString());
        try {
            Method getChildId = AccessibilityNodeInfo.class.getMethod("getChildId", int.class);
            for (int i = 0; i < info.getChildCount(); i++) {
                walk(provider, (int) ((Long) getChildId.invoke(info, i) >> 32), descriptions, depth + 1);
            }
        } catch (Throwable ignored) {
        }
    }
}
