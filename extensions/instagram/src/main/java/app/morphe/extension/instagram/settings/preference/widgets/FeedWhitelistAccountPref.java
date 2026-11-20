/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.preference.Preference;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ToggleButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.feed.FeedWhitelist;

/** A followed account, with toggles for filtering its home feed posts and its stories. */
public class FeedWhitelistAccountPref extends Preference {

    public interface OnChangeListener {
        void onChange(FeedWhitelist.Account account, boolean feed, boolean story);
    }

    private static final String TAG_AVATAR = "piko_feed_whitelist_avatar";
    private static final String TAG_TITLE = "piko_feed_whitelist_title";
    private static final String TAG_SUMMARY = "piko_feed_whitelist_summary";
    private static final String TAG_FEED = "piko_feed_whitelist_feed";
    private static final String TAG_STORY = "piko_feed_whitelist_story";

    private static final LruCache<String, Bitmap> AVATARS = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final ExecutorService AVATAR_LOADER = Executors.newFixedThreadPool(3);
    /** Main thread only: the picture each recycled avatar view currently shows. */
    private static final Map<ImageView, String> BOUND_AVATARS = new WeakHashMap<>();

    private final FeedWhitelist.Account account;
    private final OnChangeListener listener;
    private boolean feed;
    private boolean story;

    public FeedWhitelistAccountPref(Context context, FeedWhitelist.Account account, OnChangeListener listener) {
        super(context);
        this.account = account;
        this.listener = listener;
        this.feed = account.feed;
        this.story = account.story;
        setPersistent(false);
        // The toggles take the taps, not the row.
        setSelectable(false);
        setKey("piko_feed_whitelist_account_" + account.id);
        setTitle(account.username);
        setSummary(account.fullName);
    }

    public void setTargets(boolean feed, boolean story) {
        if (this.feed == feed && this.story == story) return;
        this.feed = feed;
        this.story = story;
        notifyChanged();
    }

    /** Redraws the toggles, whose colour follows the whitelist or blacklist mode. */
    public void refresh() {
        notifyChanged();
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(15), dp(6), dp(12), dp(6));
        row.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ImageView avatar = new ImageView(context);
        avatar.setTag(TAG_AVATAR);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable placeholder = new GradientDrawable();
        placeholder.setShape(GradientDrawable.OVAL);
        placeholder.setColor(InstagramPreferenceStyle.searchBackgroundColor());
        avatar.setBackground(placeholder);
        avatar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        avatar.setClipToOutline(true);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        avatarParams.setMarginEnd(dp(12));
        row.addView(avatar, avatarParams);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginEnd(dp(8));
        row.addView(texts, textParams);

        TextView title = new TextView(context);
        title.setTag(TAG_TITLE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        texts.addView(title);

        TextView summary = new TextView(context);
        summary.setTag(TAG_SUMMARY);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        summary.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
        summary.setSingleLine(true);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        summary.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        texts.addView(summary);

        row.addView(createToggle(context, TAG_FEED, str("piko_feed_whitelist_target_feed")));
        View story = createToggle(context, TAG_STORY, str("piko_feed_whitelist_target_story"));
        LinearLayout.LayoutParams storyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        storyParams.setMarginStart(dp(6));
        row.addView(story, storyParams);

        return row;
    }

    private TextView createToggle(Context context, String tag, String label) {
        TextView toggle = new TextView(context);
        toggle.setTag(tag);
        toggle.setText(label);
        toggle.setContentDescription(label);
        toggle.setGravity(Gravity.CENTER);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        toggle.setTypeface(Typeface.DEFAULT_BOLD);
        toggle.setMinWidth(dp(64));
        toggle.setMinHeight(dp(34));
        toggle.setPadding(dp(12), 0, dp(12), 0);
        toggle.setClickable(true);
        toggle.setFocusable(true);
        toggle.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(ToggleButton.class.getName());
                info.setCheckable(true);
                info.setChecked(host.isSelected());
            }
        });
        return toggle;
    }

    private void bindToggle(TextView toggle, boolean on, boolean blacklist, Runnable onClick) {
        int accent = UI.getThemedColour(blacklist ? "igds_color_error_or_destructive" : "igds_color_primary_button");
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(8));
        background.setColor(on ? accent : InstagramPreferenceStyle.searchBackgroundColor());
        // No ripple: its fade-out takes the old colour over the new state.
        toggle.setBackground(background);
        toggle.setTextColor(on ? Color.WHITE : InstagramPreferenceStyle.primaryTextColor());
        toggle.setSelected(on);
        toggle.setOnClickListener(view -> onClick.run());
    }

    @Override
    protected void onBindView(View view) {
        TextView title = view.findViewWithTag(TAG_TITLE);
        if (title != null) title.setText(account.username);

        TextView summary = view.findViewWithTag(TAG_SUMMARY);
        if (summary != null) {
            summary.setText(account.fullName);
            summary.setVisibility(account.fullName.isEmpty() ? View.GONE : View.VISIBLE);
        }

        boolean blacklist = FeedWhitelist.isBlacklist();
        TextView feedToggle = view.findViewWithTag(TAG_FEED);
        if (feedToggle != null) bindToggle(feedToggle, feed, blacklist, () -> change(!feed, story));
        TextView storyToggle = view.findViewWithTag(TAG_STORY);
        if (storyToggle != null) bindToggle(storyToggle, story, blacklist, () -> change(feed, !story));

        ImageView avatar = view.findViewWithTag(TAG_AVATAR);
        if (avatar != null) loadAvatar(avatar, account.pictureUrl);
    }

    private void change(boolean feed, boolean story) {
        setTargets(feed, story);
        listener.onChange(account, feed, story);
    }

    private static void loadAvatar(ImageView view, String url) {
        // Rows are recycled, so a finished download only lands on a view still showing its account.
        BOUND_AVATARS.put(view, url);
        Bitmap cached = url == null ? null : AVATARS.get(url);
        view.setImageBitmap(cached);
        if (url == null || cached != null) return;

        AVATAR_LOADER.execute(() -> {
            Bitmap bitmap = download(url);
            if (bitmap == null) return;
            AVATARS.put(url, bitmap);
            view.post(() -> {
                if (url.equals(BOUND_AVATARS.get(view))) view.setImageBitmap(bitmap);
            });
        });
    }

    private static Bitmap download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream stream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(stream);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private int dp(int value) {
        return InstagramPreferenceStyle.dp(getContext(), value);
    }
}
