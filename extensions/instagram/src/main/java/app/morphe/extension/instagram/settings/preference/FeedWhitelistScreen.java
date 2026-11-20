/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.database.DataSetObserver;
import android.graphics.drawable.GradientDrawable;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.feed.FeedWhitelist;
import app.morphe.extension.instagram.patches.feed.FollowingFetcher;
import app.morphe.extension.instagram.settings.SettingsActivity;
import app.morphe.extension.instagram.settings.preference.widgets.CategoryPref;
import app.morphe.extension.instagram.settings.preference.widgets.FeedWhitelistAccountPref;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramSearchStyle;

/** The whitelist or blacklist mode, the selected accounts, then every followed account. */
final class FeedWhitelistScreen {

    private final Activity activity;
    private final PreferenceScreen screen;
    private final ModePref modePref;
    private final CategoryPref selectedCategory;
    private final CategoryPref followingCategory;
    private final Map<String, FeedWhitelistAccountPref> followingRows = new HashMap<>();
    private final Map<String, FeedWhitelist.Account> followingById = new HashMap<>();
    private List<FeedWhitelist.Account> following;
    private InfoPref status;
    private String filter = "";

    private FeedWhitelistScreen(Activity activity, PreferenceScreen screen) {
        this.activity = activity;
        this.screen = screen;
        modePref = new ModePref(activity, this::setBlacklist);
        selectedCategory = new CategoryPref(activity);
        followingCategory = new CategoryPref(activity);
    }

    static void build(Context context, PreferenceScreen screen) {
        if (!(context instanceof Activity)) return;
        new FeedWhitelistScreen((Activity) context, screen).start();
    }

    private void start() {
        addSearchBar();
        screen.addPreference(modePref);
        screen.addPreference(selectedCategory);
        screen.addPreference(followingCategory);
        renderSelected();
        load(false);
    }

    private void load(boolean refresh) {
        showStatus(str("piko_feed_whitelist_loading"), "", null);
        FollowingFetcher.load(activity, refresh, new FollowingFetcher.Listener() {
            @Override
            public void onProgress(int loaded) {
                if (status != null) status.setSummary(str("piko_feed_whitelist_loading_count", loaded));
            }

            @Override
            public void onLoaded(List<FeedWhitelist.Account> accounts) {
                following = accounts;
                followingById.clear();
                for (FeedWhitelist.Account account : accounts) followingById.put(account.id, account);
                status = null;
                renderFollowing();
                // Stored selections get the current name and picture.
                renderSelected();
            }

            @Override
            public void onError() {
                showStatus(str("piko_feed_whitelist_error"), str("piko_feed_whitelist_retry"), () -> load(true));
            }
        });
    }

    private void renderSelected() {
        selectedCategory.removeAll();
        Map<String, FeedWhitelist.Account> selected = FeedWhitelist.selected();
        selectedCategory.setTitle(str("piko_feed_whitelist_selected", selected.size()));
        if (selected.isEmpty()) {
            selectedCategory.addPreference(new InfoPref(activity,
                    str("piko_feed_whitelist_none_selected"),
                    str("piko_feed_whitelist_none_selected_desc"),
                    null));
            return;
        }
        for (FeedWhitelist.Account stored : selected.values()) {
            FeedWhitelist.Account account = followingById.containsKey(stored.id)
                    ? followingById.get(stored.id)
                    : stored;
            if (matches(account)) {
                selectedCategory.addPreference(new FeedWhitelistAccountPref(activity,
                        account.withTargets(stored.feed, stored.story), this::onChange));
            }
        }
        if (filter.isEmpty()) {
            selectedCategory.addPreference(
                    new InfoPref(activity, str("piko_feed_whitelist_clear"), "", this::confirmClear));
        }
    }

    private void renderFollowing() {
        if (following == null) return;
        followingCategory.removeAll();
        followingCategory.setTitle(str("piko_feed_whitelist_following_count", following.size()));
        if (following.isEmpty()) {
            followingCategory.addPreference(
                    new InfoPref(activity, str("piko_feed_whitelist_empty"), "", null));
            return;
        }
        for (FeedWhitelist.Account account : following) {
            if (!matches(account)) continue;
            FeedWhitelistAccountPref row = followingRows.get(account.id);
            if (row == null) {
                row = new FeedWhitelistAccountPref(activity, account, this::onChange);
                followingRows.put(account.id, row);
            }
            FeedWhitelist.Account stored = FeedWhitelist.get(account.id);
            row.setTargets(stored != null && stored.feed, stored != null && stored.story);
            followingCategory.addPreference(row);
        }
    }

    /**
     * Rows added to or removed from the selected section would push the list below it, so the
     * first visible row below it is put back where it was once the list has the new rows.
     */
    private void renderSelectedKeepingScroll() {
        View view = activity.findViewById(android.R.id.list);
        if (!(view instanceof ListView)) {
            renderSelected();
            return;
        }
        ListView list = (ListView) view;
        Object anchor = null;
        int anchorTop = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            Object item = list.getItemAtPosition(list.getFirstVisiblePosition() + i);
            if (item == followingCategory
                    || (item instanceof Preference && ((Preference) item).getParent() == followingCategory)) {
                anchor = item;
                anchorTop = list.getChildAt(i).getTop();
                break;
            }
        }

        renderSelected();

        ListAdapter adapter = list.getAdapter();
        if (anchor == null || adapter == null) return;
        Object target = anchor;
        int top = anchorTop;
        // The preference adapter applies the new rows later, so wait for it, then fix the
        // position after that layout and before it is drawn.
        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                adapter.unregisterDataSetObserver(this);
                list.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        list.getViewTreeObserver().removeOnPreDrawListener(this);
                        for (int position = 0; position < list.getCount(); position++) {
                            if (list.getItemAtPosition(position) != target) continue;
                            View child = list.getChildAt(position - list.getFirstVisiblePosition());
                            if (child != null && child.getTop() == top) return true;
                            // The offset is measured from inside the list's top padding.
                            list.setSelectionFromTop(position, top - list.getPaddingTop());
                            return false;
                        }
                        return true;
                    }
                });
            }
        });
    }

    private void showStatus(String title, String summary, Runnable onClick) {
        followingCategory.removeAll();
        followingCategory.setTitle(str("piko_feed_whitelist_following"));
        status = new InfoPref(activity, title, summary, onClick);
        followingCategory.addPreference(status);
    }

    private void onChange(FeedWhitelist.Account account, boolean feed, boolean story) {
        FeedWhitelist.set(account, feed, story);
        FeedWhitelistAccountPref row = followingRows.get(account.id);
        if (row != null) row.setTargets(feed, story);
        renderSelectedKeepingScroll();
    }

    private void setBlacklist(boolean blacklist) {
        if (blacklist == FeedWhitelist.isBlacklist()) return;
        FeedWhitelist.setBlacklist(blacklist);
        modePref.refresh();
        for (FeedWhitelistAccountPref row : followingRows.values()) row.refresh();
        renderSelectedKeepingScroll();
    }

    private void confirmClear() {
        new AlertDialog.Builder(activity)
                .setMessage(str("piko_feed_whitelist_clear_confirm"))
                .setNegativeButton(str("piko_cancel"), null)
                .setPositiveButton(str("piko_ok"), (dialog, which) -> {
                    FeedWhitelist.clear();
                    for (FeedWhitelistAccountPref row : followingRows.values()) row.setTargets(false, false);
                    renderSelectedKeepingScroll();
                })
                .show();
    }

    private boolean matches(FeedWhitelist.Account account) {
        return filter.isEmpty()
                || account.username.toLowerCase(Locale.ROOT).contains(filter)
                || account.fullName.toLowerCase(Locale.ROOT).contains(filter);
    }

    private void addSearchBar() {
        SettingsActivity.Controller controller = SettingsActivity.getController(activity);
        LinearLayout container = controller == null ? null : controller.getCustomContainer();
        if (container == null) return;

        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(InstagramPreferenceStyle.searchBackgroundColor());
        background.setCornerRadius(dp(12));
        bar.setBackground(background);

        ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setPadding(dp(11), dp(14), dp(11), dp(14));
        UI.setThemedIcon(icon, UI.DRAWABLE_SEARCH, "igds_color_secondary_text");
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(48)));

        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setHint(str("piko_search_username"));
        search.setHintTextColor(InstagramPreferenceStyle.secondaryTextColor());
        search.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setPaddingRelative(0, dp(10), dp(14), dp(10));
        search.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        search.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        InstagramSearchStyle.applyInputStyle(search);
        bar.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                InstagramSearchStyle.removeBackgroundSpans(s);
                String next = s.toString().trim().toLowerCase(Locale.ROOT);
                if (next.equals(filter)) return;
                filter = next;
                renderSelected();
                renderFollowing();
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(12), 0, dp(12), dp(8));
        container.addView(bar, params);
    }

    private int dp(int value) {
        return InstagramPreferenceStyle.dp(activity, value);
    }

    /** Whitelist and blacklist buttons, with what the current mode does. */
    private static final class ModePref extends Preference {
        interface Listener {
            void onModeSelected(boolean blacklist);
        }

        private static final String TAG_WHITELIST = "piko_feed_whitelist_mode_whitelist";
        private static final String TAG_BLACKLIST = "piko_feed_whitelist_mode_blacklist";
        private static final String TAG_DESCRIPTION = "piko_feed_whitelist_mode_description";

        private final Listener listener;

        ModePref(Context context, Listener listener) {
            super(context);
            this.listener = listener;
            setPersistent(false);
            setSelectable(false);
            setKey("piko_feed_whitelist_mode");
        }

        void refresh() {
            notifyChanged();
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            Context context = getContext();
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(context, 15), dp(context, 12), dp(context, 15), dp(context, 8));
            root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

            LinearLayout segments = new LinearLayout(context);
            segments.setOrientation(LinearLayout.HORIZONTAL);
            GradientDrawable track = new GradientDrawable();
            track.setCornerRadius(dp(context, 10));
            track.setColor(InstagramPreferenceStyle.searchBackgroundColor());
            segments.setBackground(track);
            segments.setPadding(dp(context, 3), dp(context, 3), dp(context, 3), dp(context, 3));
            segments.addView(segment(context, TAG_WHITELIST, str("piko_feed_whitelist_mode_whitelist")),
                    new LinearLayout.LayoutParams(0, dp(context, 36), 1f));
            segments.addView(segment(context, TAG_BLACKLIST, str("piko_feed_whitelist_mode_blacklist")),
                    new LinearLayout.LayoutParams(0, dp(context, 36), 1f));
            root.addView(segments, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView description = new TextView(context);
            description.setTag(TAG_DESCRIPTION);
            description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            description.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
            description.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            description.setPadding(0, dp(context, 10), 0, 0);
            root.addView(description);
            return root;
        }

        private static TextView segment(Context context, String tag, String label) {
            TextView segment = new TextView(context);
            segment.setTag(tag);
            segment.setText(label);
            segment.setGravity(Gravity.CENTER);
            segment.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            segment.setTypeface(Typeface.DEFAULT_BOLD);
            segment.setClickable(true);
            segment.setFocusable(true);
            return segment;
        }

        @Override
        protected void onBindView(View view) {
            boolean blacklist = FeedWhitelist.isBlacklist();
            bindSegment(view.findViewWithTag(TAG_WHITELIST), !blacklist, false);
            bindSegment(view.findViewWithTag(TAG_BLACKLIST), blacklist, true);
            TextView description = view.findViewWithTag(TAG_DESCRIPTION);
            if (description != null) {
                description.setText(str(blacklist
                        ? "piko_feed_whitelist_mode_blacklist_desc"
                        : "piko_feed_whitelist_mode_whitelist_desc"));
            }
        }

        private void bindSegment(TextView segment, boolean selected, boolean blacklist) {
            if (segment == null) return;
            Context context = segment.getContext();
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(dp(context, 8));
            background.setColor(selected ? InstagramPreferenceStyle.backgroundColor() : Color.TRANSPARENT);
            segment.setBackground(background);
            segment.setTextColor(selected
                    ? InstagramPreferenceStyle.primaryTextColor()
                    : InstagramPreferenceStyle.secondaryTextColor());
            segment.setSelected(selected);
            segment.setOnClickListener(v -> listener.onModeSelected(blacklist));
        }

        private static int dp(Context context, int value) {
            return InstagramPreferenceStyle.dp(context, value);
        }
    }

    /** A plain text row, optionally tappable. */
    private static final class InfoPref extends Preference {
        private final Runnable onClick;

        InfoPref(Context context, String title, String summary, Runnable onClick) {
            super(context);
            this.onClick = onClick;
            setPersistent(false);
            setSelectable(onClick != null);
            setTitle(title);
            setSummary(summary);
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            return InstagramPreferenceStyle.createPreferenceView(getContext(), InstagramPreferenceStyle.TRAILING_NONE);
        }

        @Override
        protected void onBindView(View view) {
            InstagramPreferenceStyle.bindText(this, view);
        }

        @Override
        protected void onClick() {
            if (onClick != null) onClick.run();
        }
    }
}
