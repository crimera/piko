/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.ui.Dim;

/**
 * Full-screen manager hosted inside Instagram's ModalActivity for choosing which people (@usernames)
 * and DM chats are allowed to trigger notifications.
 */
public final class DmNotificationAllowlistActivity {

    private static final Map<Activity, Controller> sControllers = new WeakHashMap<>();

    private DmNotificationAllowlistActivity() {
    }

    public static void setupOnActivity(Activity activity) {
        Controller controller = new Controller(activity);
        synchronized (sControllers) {
            sControllers.put(activity, controller);
        }
        controller.setup();
        if (activity.getApplication() != null) {
            activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity a, Bundle b) { }
                @Override public void onActivityStarted(Activity a) { }
                @Override public void onActivityResumed(Activity a) { }
                @Override public void onActivityPaused(Activity a) { }
                @Override public void onActivityStopped(Activity a) { }
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                @Override
                public void onActivityDestroyed(Activity a) {
                    if (a == activity) {
                        a.getApplication().unregisterActivityLifecycleCallbacks(this);
                        synchronized (sControllers) {
                            sControllers.remove(activity);
                        }
                    }
                }
            });
        }
    }

    public static boolean handleBackPressed(Activity activity) {
        Controller controller;
        synchronized (sControllers) {
            controller = sControllers.get(activity);
        }
        if (controller != null && controller.clearSearchIfActive()) {
            return true;
        }
        return false;
    }

    private static final class RowItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_ALLOWED_USER = 1;
        static final int TYPE_ALLOWED_THREAD = 2;
        static final int TYPE_KNOWN_THREAD = 3;
        static final int TYPE_HINT = 4;

        final int type;
        final String text;
        final DmNotificationAllowlistPref.AllowedUser allowedUser;
        final DmNotificationAllowlistPref.AllowedThread allowedThread;
        final DmNotificationAllowlistPref.KnownThread knownThread;

        private RowItem(
                int type,
                String text,
                DmNotificationAllowlistPref.AllowedUser allowedUser,
                DmNotificationAllowlistPref.AllowedThread allowedThread,
                DmNotificationAllowlistPref.KnownThread knownThread
        ) {
            this.type = type;
            this.text = text;
            this.allowedUser = allowedUser;
            this.allowedThread = allowedThread;
            this.knownThread = knownThread;
        }

        static RowItem header(String title) {
            return new RowItem(TYPE_HEADER, title, null, null, null);
        }

        static RowItem hint(String message) {
            return new RowItem(TYPE_HINT, message, null, null, null);
        }

        static RowItem user(DmNotificationAllowlistPref.AllowedUser user) {
            return new RowItem(TYPE_ALLOWED_USER, null, user, null, null);
        }

        static RowItem thread(DmNotificationAllowlistPref.AllowedThread thread) {
            return new RowItem(TYPE_ALLOWED_THREAD, null, null, thread, null);
        }

        static RowItem known(DmNotificationAllowlistPref.KnownThread thread) {
            return new RowItem(TYPE_KNOWN_THREAD, null, null, null, thread);
        }
    }

    private static final class Controller {
        private final Activity activity;
        private final List<RowItem> rows = new ArrayList<>();
        private EditText searchInput;
        private ImageView clearSearchButton;
        private ListView listView;
        private AllowlistAdapter adapter;
        private String filterQuery = "";

        Controller(Activity activity) {
            this.activity = activity;
        }

        void setup() {
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
            InstagramPreferenceStyle.applySystemBarStyle(activity);

            // ---- Toolbar ----
            LinearLayout toolbar = new LinearLayout(activity);
            toolbar.setBaselineAligned(false);
            toolbar.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

            ImageView back = new ImageView(activity);
            UI.setThemedIcon(back, UI.DRAWABLE_ARROW_BACK);
            back.setOnClickListener(v -> {
                if (!clearSearchIfActive()) activity.finish();
            });

            TextView title = new TextView(activity);
            title.setText(str("piko_dm_notif_manage_title"));
            title.setTextColor(InstagramPreferenceStyle.primaryTextColor());
            InstagramPreferenceStyle.applyToolbarLayout(activity, toolbar, back, title, false);

            TextView addButton = new TextView(activity);
            addButton.setText(str("piko_dm_notif_add_username"));
            addButton.setTextColor(InstagramPreferenceStyle.selectionColor());
            addButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            addButton.setTypeface(Typeface.DEFAULT_BOLD);
            addButton.setGravity(Gravity.CENTER);
            addButton.setPadding(
                    InstagramPreferenceStyle.dp(activity, 10),
                    InstagramPreferenceStyle.dp(activity, 8),
                    InstagramPreferenceStyle.dp(activity, 4),
                    InstagramPreferenceStyle.dp(activity, 8)
            );
            addButton.setOnClickListener(v -> showAddUsernameDialog());

            toolbar.addView(back);
            toolbar.addView(title);
            toolbar.addView(addButton);
            root.addView(toolbar);

            // ---- Search bar ----
            LinearLayout searchBox = new LinearLayout(activity);
            searchBox.setOrientation(LinearLayout.HORIZONTAL);
            searchBox.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable searchBg = new GradientDrawable();
            searchBg.setColor(InstagramPreferenceStyle.searchBackgroundColor());
            searchBg.setCornerRadius(InstagramPreferenceStyle.dp(activity, 12));
            searchBox.setBackground(searchBg);
            searchBox.setPadding(
                    InstagramPreferenceStyle.dp(activity, 12),
                    InstagramPreferenceStyle.dp(activity, 6),
                    InstagramPreferenceStyle.dp(activity, 8),
                    InstagramPreferenceStyle.dp(activity, 6)
            );
            LinearLayout.LayoutParams searchBoxParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            searchBoxParams.setMargins(
                    InstagramPreferenceStyle.dp(activity, 15),
                    InstagramPreferenceStyle.dp(activity, 4),
                    InstagramPreferenceStyle.dp(activity, 15),
                    InstagramPreferenceStyle.dp(activity, 10)
            );

            ImageView searchIcon = new ImageView(activity);
            UI.setThemedIcon(searchIcon, UI.DRAWABLE_SEARCH, "igds_color_secondary_icon");
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    InstagramPreferenceStyle.dp(activity, 18),
                    InstagramPreferenceStyle.dp(activity, 18)
            );
            iconParams.setMarginEnd(InstagramPreferenceStyle.dp(activity, 8));
            searchBox.addView(searchIcon, iconParams);

            searchInput = new EditText(activity);
            searchInput.setBackground(null);
            searchInput.setHint(str("piko_settings_search_hint"));
            searchInput.setHintTextColor(InstagramPreferenceStyle.secondaryTextColor());
            searchInput.setTextColor(InstagramPreferenceStyle.primaryTextColor());
            searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            searchInput.setSingleLine(true);
            searchInput.setPadding(0, InstagramPreferenceStyle.dp(activity, 4), 0, InstagramPreferenceStyle.dp(activity, 4));
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override
                public void afterTextChanged(Editable s) {
                    filterQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                    clearSearchButton.setVisibility(filterQuery.isEmpty() ? View.GONE : View.VISIBLE);
                    reloadRows();
                }
            });
            searchBox.addView(searchInput, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            clearSearchButton = new ImageView(activity);
            UI.setThemedIcon(clearSearchButton, UI.DRAWABLE_CLEAR, "igds_color_secondary_icon");
            clearSearchButton.setVisibility(View.GONE);
            clearSearchButton.setPadding(
                    InstagramPreferenceStyle.dp(activity, 6),
                    InstagramPreferenceStyle.dp(activity, 6),
                    InstagramPreferenceStyle.dp(activity, 6),
                    InstagramPreferenceStyle.dp(activity, 6)
            );
            clearSearchButton.setOnClickListener(v -> searchInput.setText(""));
            searchBox.addView(clearSearchButton, new LinearLayout.LayoutParams(
                    InstagramPreferenceStyle.dp(activity, 30),
                    InstagramPreferenceStyle.dp(activity, 30)
            ));

            root.addView(searchBox, searchBoxParams);

            // ---- List ----
            listView = new ListView(activity);
            listView.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setSelector(new ColorDrawable(Color.TRANSPARENT));
            listView.setCacheColorHint(Color.TRANSPARENT);
            listView.setClipToPadding(false);
            listView.setPadding(0, 0, 0, InstagramPreferenceStyle.dp(activity, 20));
            adapter = new AllowlistAdapter();
            listView.setAdapter(adapter);

            root.addView(listView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

            root.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
                return insets;
            });

            registerBackHandler();
            activity.setContentView(root);
            reloadRows();
        }

        private void registerBackHandler() {
            if (activity instanceof androidx.activity.ComponentActivity) {
                try {
                    ((androidx.activity.ComponentActivity) activity).getOnBackPressedDispatcher().addCallback(
                            (androidx.lifecycle.LifecycleOwner) activity,
                            new androidx.activity.OnBackPressedCallback(true) {
                                @Override
                                public void handleOnBackPressed() {
                                    if (clearSearchIfActive()) return;
                                    remove();
                                    activity.finish();
                                }
                            }
                    );
                    return;
                } catch (Throwable ignored) {
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Api33BackHelper.register(activity, () -> {
                    if (!clearSearchIfActive()) activity.finish();
                });
            }
        }

        boolean clearSearchIfActive() {
            if (searchInput != null && !filterQuery.isEmpty()) {
                searchInput.setText("");
                return true;
            }
            return false;
        }

        private boolean matchesFilter(String... values) {
            if (filterQuery.isEmpty()) return true;
            String needle = filterQuery.startsWith("@") ? filterQuery.substring(1) : filterQuery;
            if (needle.isEmpty()) return true;
            for (String value : values) {
                if (value != null && value.toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
            return false;
        }

        void reloadRows() {
            rows.clear();
            boolean allowGroupsByMember = Pref.dmNotificationAllowlistGroups();

            List<DmNotificationAllowlistPref.AllowedUser> allowedUsers =
                    DmNotificationAllowlistPref.getAllowedUsers();
            List<DmNotificationAllowlistPref.AllowedThread> allowedThreads =
                    DmNotificationAllowlistPref.getAllowedThreads();
            List<DmNotificationAllowlistPref.KnownThread> knownThreads =
                    DmNotificationAllowlistPref.getKnownThreads();

            // Section 1: Allowed people
            List<DmNotificationAllowlistPref.AllowedUser> matchingUsers = new ArrayList<>();
            for (DmNotificationAllowlistPref.AllowedUser user : allowedUsers) {
                if (matchesFilter(user.username, user.fullName, user.userId)) {
                    matchingUsers.add(user);
                }
            }
            if (!matchingUsers.isEmpty()) {
                rows.add(RowItem.header(str("piko_dm_notif_section_people")));
                for (DmNotificationAllowlistPref.AllowedUser user : matchingUsers) {
                    rows.add(RowItem.user(user));
                }
            }

            // Section 2: Explicitly allowed chats not shown below (e.g. restored from backup or older chats)
            Set<String> knownIds = new HashSet<>();
            for (DmNotificationAllowlistPref.KnownThread known : knownThreads) {
                knownIds.add(known.threadId);
            }
            List<DmNotificationAllowlistPref.AllowedThread> orphanThreads = new ArrayList<>();
            for (DmNotificationAllowlistPref.AllowedThread thread : allowedThreads) {
                if (!knownIds.contains(thread.threadId) && matchesFilter(thread.label, thread.threadId)) {
                    orphanThreads.add(thread);
                }
            }
            if (!orphanThreads.isEmpty()) {
                rows.add(RowItem.header(str("piko_dm_notif_section_allowed_chats")));
                for (DmNotificationAllowlistPref.AllowedThread thread : orphanThreads) {
                    rows.add(RowItem.thread(thread));
                }
            }

            // Section 3: Recent DM chats discovered from inbox (allowed chats sorted to the top)
            List<DmNotificationAllowlistPref.KnownThread> matchingKnown = new ArrayList<>();
            for (DmNotificationAllowlistPref.KnownThread known : knownThreads) {
                if (matchesKnownThread(known)) {
                    matchingKnown.add(known);
                }
            }
            Collections.sort(matchingKnown, new Comparator<DmNotificationAllowlistPref.KnownThread>() {
                @Override
                public int compare(DmNotificationAllowlistPref.KnownThread a, DmNotificationAllowlistPref.KnownThread b) {
                    boolean aAllowed = DmNotificationAllowlistPref.matchKnownThread(a, allowGroupsByMember)
                            != DmNotificationAllowlistPref.MatchReason.NONE;
                    boolean bAllowed = DmNotificationAllowlistPref.matchKnownThread(b, allowGroupsByMember)
                            != DmNotificationAllowlistPref.MatchReason.NONE;
                    if (aAllowed != bAllowed) return aAllowed ? -1 : 1;
                    return Long.compare(b.lastSeen, a.lastSeen);
                }
            });

            rows.add(RowItem.header(str("piko_dm_notif_section_recent_chats")));
            if (matchingKnown.isEmpty()) {
                rows.add(RowItem.hint(str(
                        knownThreads.isEmpty()
                                ? "piko_dm_notif_no_recent_chats"
                                : "piko_settings_search_no_results"
                )));
            } else {
                for (DmNotificationAllowlistPref.KnownThread known : matchingKnown) {
                    rows.add(RowItem.known(known));
                }
            }

            adapter.notifyDataSetChanged();
        }

        private boolean matchesKnownThread(DmNotificationAllowlistPref.KnownThread thread) {
            if (filterQuery.isEmpty()) return true;
            if (matchesFilter(thread.displayTitle(), thread.threadId)) return true;
            for (String username : thread.participantUsernames) {
                if (matchesFilter(username)) return true;
            }
            for (String fullName : thread.participantFullNames) {
                if (matchesFilter(fullName)) return true;
            }
            return false;
        }

        private void showAddUsernameDialog() {
            android.content.Context dialogCtx = InstagramPreferenceStyle.dialogContext(activity);
            LinearLayout container = new LinearLayout(dialogCtx);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = InstagramPreferenceStyle.dp(activity, 20);
            container.setPadding(pad, InstagramPreferenceStyle.dp(activity, 12), pad, 0);

            EditText input = new EditText(dialogCtx);
            input.setHint(str("piko_dm_notif_add_username_hint"));
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setSingleLine(false);
            input.setMaxLines(4);
            container.addView(input, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            new AlertDialog.Builder(dialogCtx)
                    .setTitle(str("piko_dm_notif_add_username_title"))
                    .setMessage(str("piko_dm_notif_add_username_desc"))
                    .setView(container)
                    .setNegativeButton(str("piko_cancel"), null)
                    .setPositiveButton(str("piko_ok"), (dialog, which) -> {
                        String raw = input.getText() == null ? "" : input.getText().toString();
                        int added = addUsernamesFromInput(raw);
                        if (added > 0) {
                            PikoUtils.toast(String.format(str("piko_dm_notif_added_count"), added));
                            reloadRows();
                        } else {
                            PikoUtils.toast(str("piko_dm_notif_invalid_username"));
                        }
                    })
                    .show();
        }

        private int addUsernamesFromInput(String raw) {
            if (raw == null || raw.trim().isEmpty()) return 0;
            String[] parts = raw.split("[,\\s]+");
            int added = 0;
            for (String part : parts) {
                String normalized = DmNotificationAllowlistPref.normalizeUsername(part);
                if (normalized.isEmpty()) continue;
                if (DmNotificationAllowlistPref.addAllowedUser("", normalized, "")) {
                    added++;
                }
            }
            return added;
        }

        private final class AllowlistAdapter extends BaseAdapter {
            @Override
            public int getCount() {
                return rows.size();
            }

            @Override
            public RowItem getItem(int position) {
                return rows.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public int getViewTypeCount() {
                return 5;
            }

            @Override
            public int getItemViewType(int position) {
                return rows.get(position).type;
            }

            @Override
            public boolean isEnabled(int position) {
                int type = rows.get(position).type;
                return type != RowItem.TYPE_HEADER && type != RowItem.TYPE_HINT;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                RowItem item = rows.get(position);
                switch (item.type) {
                    case RowItem.TYPE_HEADER:
                        return createHeaderView(item.text);
                    case RowItem.TYPE_HINT:
                        return createHintView(item.text);
                    case RowItem.TYPE_ALLOWED_USER:
                        return createAllowedUserView(item.allowedUser);
                    case RowItem.TYPE_ALLOWED_THREAD:
                        return createAllowedThreadView(item.allowedThread);
                    case RowItem.TYPE_KNOWN_THREAD:
                        return createKnownThreadView(item.knownThread);
                    default:
                        return new View(activity);
                }
            }

            private View createHeaderView(String title) {
                TextView header = new TextView(activity);
                header.setText(title);
                header.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
                header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                header.setTypeface(Typeface.DEFAULT_BOLD);
                header.setPadding(
                        InstagramPreferenceStyle.dp(activity, 16),
                        InstagramPreferenceStyle.dp(activity, 16),
                        InstagramPreferenceStyle.dp(activity, 16),
                        InstagramPreferenceStyle.dp(activity, 6)
                );
                return header;
            }

            private View createHintView(String message) {
                TextView hint = new TextView(activity);
                hint.setText(message);
                hint.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
                hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                hint.setPadding(
                        InstagramPreferenceStyle.dp(activity, 16),
                        InstagramPreferenceStyle.dp(activity, 12),
                        InstagramPreferenceStyle.dp(activity, 16),
                        InstagramPreferenceStyle.dp(activity, 16)
                );
                return hint;
            }

            private View createAllowedUserView(DmNotificationAllowlistPref.AllowedUser user) {
                LinearLayout row = baseRow();
                LinearLayout textColumn = textColumn(user.displayTitle(), user.displaySubtitle());
                row.addView(textColumn, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                ImageView remove = removeButton(() -> {
                    DmNotificationAllowlistPref.removeAllowedUser(user.userId, user.username);
                    PikoUtils.toast(String.format(str("piko_dm_notif_removed_user"), user.displayTitle()));
                    reloadRows();
                });
                row.addView(remove);
                return row;
            }

            private View createAllowedThreadView(DmNotificationAllowlistPref.AllowedThread thread) {
                LinearLayout row = baseRow();
                String subtitle = thread.isGroup
                        ? str("piko_dm_notif_group_allowed")
                        : str("piko_dm_notif_chat_allowed");
                LinearLayout textColumn = textColumn(thread.displayTitle(), subtitle);
                row.addView(textColumn, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                ImageView remove = removeButton(() -> {
                    DmNotificationAllowlistPref.removeAllowedThread(thread.threadId);
                    reloadRows();
                });
                row.addView(remove);
                return row;
            }

            private View createKnownThreadView(DmNotificationAllowlistPref.KnownThread thread) {
                boolean allowGroupsByMember = Pref.dmNotificationAllowlistGroups();
                DmNotificationAllowlistPref.MatchReason directReason =
                        DmNotificationAllowlistPref.matchKnownThread(thread, false);
                DmNotificationAllowlistPref.MatchReason effectiveReason =
                        DmNotificationAllowlistPref.matchKnownThread(thread, allowGroupsByMember);

                boolean checked = directReason != DmNotificationAllowlistPref.MatchReason.NONE;
                String subtitle;
                if (thread.isGroup) {
                    if (directReason == DmNotificationAllowlistPref.MatchReason.EXPLICIT_THREAD) {
                        subtitle = str("piko_dm_notif_group_allowed");
                    } else if (effectiveReason == DmNotificationAllowlistPref.MatchReason.GROUP_MEMBER) {
                        subtitle = str("piko_dm_notif_group_allowed_via_member");
                    } else {
                        subtitle = str("piko_dm_notif_group_muted");
                    }
                } else {
                    if (checked) {
                        subtitle = str("piko_dm_notif_chat_allowed");
                    } else {
                        subtitle = str("piko_dm_notif_chat_muted");
                    }
                }

                LinearLayout row = baseRow();
                LinearLayout textColumn = textColumn(thread.displayTitle(), subtitle);
                row.addView(textColumn, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                CheckBox checkBox = new CheckBox(InstagramPreferenceStyle.dialogContext(activity));
                checkBox.setChecked(checked);
                checkBox.setClickable(false);
                checkBox.setFocusable(false);
                row.addView(checkBox, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));

                row.setOnClickListener(v -> {
                    DmNotificationAllowlistPref.toggleKnownThread(thread);
                    reloadRows();
                });
                return row;
            }

            private LinearLayout baseRow() {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(Dim.dp48);
                row.setPadding(
                        InstagramPreferenceStyle.dp(activity, 16),
                        InstagramPreferenceStyle.dp(activity, 10),
                        InstagramPreferenceStyle.dp(activity, 14),
                        InstagramPreferenceStyle.dp(activity, 10)
                );
                return row;
            }

            private LinearLayout textColumn(String primary, String secondary) {
                LinearLayout column = new LinearLayout(activity);
                column.setOrientation(LinearLayout.VERTICAL);

                TextView title = new TextView(activity);
                title.setText(primary);
                title.setTextColor(InstagramPreferenceStyle.primaryTextColor());
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                title.setMaxLines(1);
                title.setEllipsize(TextUtils.TruncateAt.END);
                column.addView(title);

                if (!TextUtils.isEmpty(secondary)) {
                    TextView sub = new TextView(activity);
                    sub.setText(secondary);
                    sub.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
                    sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    sub.setMaxLines(1);
                    sub.setEllipsize(TextUtils.TruncateAt.END);
                    sub.setPadding(0, InstagramPreferenceStyle.dp(activity, 2), 0, 0);
                    column.addView(sub);
                }
                return column;
            }

            private ImageView removeButton(Runnable onRemove) {
                ImageView icon = new ImageView(activity);
                UI.setThemedIcon(icon, UI.DRAWABLE_CLEAR, "igds_color_secondary_icon");
                icon.setPadding(
                        InstagramPreferenceStyle.dp(activity, 8),
                        InstagramPreferenceStyle.dp(activity, 8),
                        InstagramPreferenceStyle.dp(activity, 8),
                        InstagramPreferenceStyle.dp(activity, 8)
                );
                icon.setOnClickListener(v -> onRemove.run());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        InstagramPreferenceStyle.dp(activity, 36),
                        InstagramPreferenceStyle.dp(activity, 36)
                );
                icon.setLayoutParams(params);
                return icon;
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static final class Api33BackHelper {
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
