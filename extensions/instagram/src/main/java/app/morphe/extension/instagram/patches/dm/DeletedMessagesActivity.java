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
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.SparseBooleanArray;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Build;
import android.window.OnBackInvokedDispatcher;
import androidx.annotation.RequiresApi;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.db.PikoMessageDb;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.utils.MediaUrls;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.Dim;
import app.morphe.extension.shared.ui.SelectionCheck;

public final class DeletedMessagesActivity {

    private static final Map<Activity, Controller> sControllers = new WeakHashMap<>();

    private DeletedMessagesActivity() {}

    static boolean areAllSelected(int checkedCount, int itemCount) {
        return itemCount > 0 && checkedCount == itemCount;
    }

    static boolean shouldSelectAll(int checkedCount, int itemCount) {
        return !areAllSelected(checkedCount, itemCount);
    }

    static void refreshVisibleSelectionRows(ListView list, boolean selectionMode) {
        int firstPosition = list.getFirstVisiblePosition();
        for (int childIndex = 0; childIndex < list.getChildCount(); childIndex++) {
            int position = firstPosition + childIndex;
            if (position >= list.getCount()) break;
            View row = list.getChildAt(childIndex);
            SelectionCheck selectionView = row.findViewWithTag("selection");
            if (selectionView == null) continue;
            boolean checked = list.isItemChecked(position);
            selectionView.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            selectionView.setChecked(checked);
            row.setPadding(selectionMode ? Dim.dp8 : Dim.dp16, Dim.dp12, Dim.dp16, Dim.dp12);
            row.setActivated(checked);
        }
    }

    private static String guessExtension(String url, String type) {
        String path = Uri.parse(url).getPath();
        Matcher m = Pattern
                .compile("(?i)\\.(jpg|jpeg|webp|heic|png|mp4|mov|m4a|aac|mp3|ogg|gif)$")
                .matcher(path != null ? path : "");
        if (m.find()) return "." + m.group(1).toLowerCase(Locale.ROOT);
        if ("voice_media".equals(type) || "audio".equals(type)) return ".m4a";
        if ("video".equals(type)) return ".mp4";
        if ("animated_media".equals(type)) return ".gif";
        return ".jpg";
    }

    private static boolean isVideo(String url, String type) {
        if ("video".equals(type) || "clip".equals(type) || "xma_clip".equals(type)) return true;
        return MediaUrls.isVideo(url);
    }

    private static boolean isExpiredMediaUrl(String url) {
        try {
            int i = url.indexOf("oe=");
            if (i < 1 || (url.charAt(i - 1) != '?' && url.charAt(i - 1) != '&')) return false;
            int end = i + 3;
            while (end < url.length() && Character.digit(url.charAt(end), 16) >= 0) end++;
            if (end == i + 3) return false;
            return Long.parseLong(url.substring(i + 3, end), 16) * 1000L < System.currentTimeMillis();
        } catch (Exception e) {
            return false;
        }
    }

    private static String mediaLabel(String type, String url) {
        if (type == null) return "[" + str("piko_media_unknown") + "]";
        // Instagram uses these item types for both photos and videos, including saved rows.
        if (("media".equals(type) || "raven_media".equals(type) || "visual_media".equals(type))
                && isVideo(url, type)) {
            type = "video";
        }
        String label;
        switch (type) {
            case "media":
            case "image":          label = str("piko_media_photo"); break;
            case "video":          label = str("piko_media_video"); break;
            case "voice_media":
            case "audio":          label = str("piko_media_voice"); break;
            case "animated_media": label = str("piko_media_gif"); break;
            case "reel_share":     label = str("piko_media_reel"); break;
            case "story_share":    label = str("piko_media_story"); break;
            case "media_share":    label = str("piko_media_post"); break;
            case "clip":
            case "xma_clip":       label = str("piko_media_reel"); break;
            default:               return "[" + type + "]";
        }
        return "[" + label + "]";
    }

    public static void setupOnActivity(Activity activity) {
        Controller controller = new Controller(activity);
        synchronized (sControllers) {
            sControllers.put(activity, controller);
        }
        controller.setup();
        if (activity.getApplication() != null) {
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
        if (controller != null && controller.isSelecting()) {
            controller.cancelSelection();
            return true;
        }
        return false;
    }

    private static class Controller {
        private final Activity activity;
        private List<String[]> messages;
        private ListView messageList;
        private MessageAdapter adapter;
        private SimpleDateFormat timestampFormatter;
        private View backAction;
        private TextView toolbarTitle;
        private View selectAllAction;
        private SelectionCheck selectAllCheck;
        private TextView selectAllLabel;
        private TextView cancelSelectionAction;
        private Button deleteAction;
        private boolean selectionMode;
        private boolean selectionToolbarVisible;

        Controller(Activity activity) {
            this.activity = activity;
        }

        void setup() {
            activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER);
            messages = PikoMessageDb.getInstance(activity).getDeletedMessages();
            Locale locale = activity.getResources().getConfiguration().getLocales().get(0);
            String skeleton = DateFormat.is24HourFormat(activity) ? "yMMMdHm" : "yMMMdhm";
            timestampFormatter = new SimpleDateFormat(
                    DateFormat.getBestDateTimePattern(locale, skeleton), locale);

            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
            InstagramPreferenceStyle.applySystemBarStyle(activity);

            LinearLayout toolbar = new LinearLayout(activity);
            toolbar.setBaselineAligned(false);
            toolbar.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

            ImageView back = new ImageView(activity);
            UI.setThemedIcon(back, UI.DRAWABLE_ARROW_BACK);
            back.setOnClickListener(v -> handleBack());
            backAction = back;

            toolbarTitle = new TextView(activity);
            toolbarTitle.setText(str("piko_all_deleted_messages"));
            toolbarTitle.setTextColor(InstagramPreferenceStyle.primaryTextColor());
            InstagramPreferenceStyle.applyToolbarLayout(
                    activity, toolbar, back, toolbarTitle, false);

            LinearLayout selectAll = new LinearLayout(activity);
            selectAll.setOrientation(LinearLayout.VERTICAL);
            selectAll.setGravity(Gravity.CENTER);
            selectAll.setMinimumWidth(Dim.dp48);
            selectAll.setMinimumHeight(Dim.dp48);
            selectAll.setVisibility(View.GONE);
            selectAll.setContentDescription(str("piko_all"));

            selectAllCheck = new SelectionCheck(
                    InstagramPreferenceStyle.dialogContext(activity), selectionControlDrawable());
            selectAll.addView(selectAllCheck, new LinearLayout.LayoutParams(
                    Dim.dp48, Dim.dp48));

            selectAllLabel = new TextView(activity);
            selectAllLabel.setText(str("piko_all"));
            selectAllLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            selectAllLabel.setTextColor(InstagramPreferenceStyle.primaryTextColor());
            selectAllLabel.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = -InstagramPreferenceStyle.dp(activity, 10);
            selectAll.addView(selectAllLabel, labelParams);
            selectAll.setOnClickListener(v -> toggleAll());
            bindSelectionAccessibility(selectAll, selectAllCheck);
            selectAllAction = selectAll;

            TextView cancelSelection = new TextView(activity);
            cancelSelection.setText(str("piko_cancel"));
            cancelSelection.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            cancelSelection.setTextColor(InstagramPreferenceStyle.primaryTextColor());
            cancelSelection.setGravity(Gravity.CENTER);
            cancelSelection.setMinWidth(Dim.dp48);
            cancelSelection.setPadding(Dim.dp8, 0, Dim.dp8, 0);
            cancelSelection.setVisibility(View.GONE);
            cancelSelection.setOnClickListener(v -> cancelSelection());
            cancelSelectionAction = cancelSelection;

            toolbar.addView(back);
            toolbar.addView(selectAll, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            toolbar.addView(toolbarTitle);
            toolbar.addView(cancelSelection, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, Dim.dp48));
            root.addView(toolbar);

            FrameLayout content = new FrameLayout(activity);
            TextView empty = new TextView(activity);
            empty.setText(str("piko_no_deleted_messages"));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Dim.dp8 * 2, Dim.dp8 * 4, Dim.dp8 * 2, Dim.dp8 * 4);
            empty.setTextColor(InstagramPreferenceStyle.secondaryTextColor());

            messageList = new ListView(activity);
            adapter = new MessageAdapter();
            messageList.setAdapter(adapter);
            messageList.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
            messageList.setDivider(new ColorDrawable(UI.getThemedColour("igds_color_separator")));
            messageList.setDividerHeight(1);
            messageList.setChoiceMode(ListView.CHOICE_MODE_NONE);
            messageList.setOnItemClickListener((parent, view, position, id) -> {
                if (selectionMode) {
                    updateSelectionUi();
                    return;
                }
                String[] message = messages.get(position);
                String messageId = message[0];
                String body = message[3];
                String type = message[4];
                if (body != null && body.startsWith("http")) {
                    showMediaOptions(messageId, body, type);
                } else if (type != null && !"text".equals(type)) {
                    Toast.makeText(activity, str("piko_media_not_available"),
                            Toast.LENGTH_SHORT).show();
                }
            });
            messageList.setOnItemLongClickListener((parent, view, position, id) -> {
                if (!selectionMode) {
                    selectionMode = true;
                    messageList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                }
                messageList.setItemChecked(position, true);
                updateSelectionUi();
                return true;
            });

            FrameLayout.LayoutParams match = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            content.addView(empty, match);
            content.addView(messageList, match);
            messageList.setEmptyView(empty);
            root.addView(content, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

            deleteAction = new Button(activity);
            deleteAction.setText(str("piko_delete"));
            deleteAction.setAllCaps(false);
            deleteAction.setTextColor(UI.getThemedColour("igds_color_error_or_destructive"));
            deleteAction.setBackgroundTintList(null);
            GradientDrawable deleteBackground = new GradientDrawable();
            deleteBackground.setColor(InstagramPreferenceStyle.pressedBackgroundColor());
            deleteBackground.setCornerRadius(Dim.dp24);
            deleteAction.setBackground(new RippleDrawable(ColorStateList.valueOf(
                    InstagramPreferenceStyle.secondaryTextColor() & 0x00ffffff | 0x24000000),
                    deleteBackground, null));
            deleteAction.setStateListAnimator(null);
            deleteAction.setElevation(0);
            deleteAction.setMinHeight(Dim.dp48);
            deleteAction.setMinimumHeight(Dim.dp48);
            deleteAction.setVisibility(View.GONE);
            deleteAction.setOnClickListener(v -> confirmDeleteSelected());
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            deleteParams.setMargins(Dim.dp16, Dim.dp8, Dim.dp16, Dim.dp12);
            root.addView(deleteAction, deleteParams);

            root.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets;
            });

            root.setFocusableInTouchMode(true);
            root.requestFocus();
            root.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    if (isSelecting()) {
                        cancelSelection();
                        return true;
                    }
                }
                return false;
            });

            boolean backRegistered = false;
            if (activity instanceof androidx.activity.ComponentActivity) {
                try {
                    ((androidx.activity.ComponentActivity) activity).getOnBackPressedDispatcher().addCallback(
                            (androidx.lifecycle.LifecycleOwner) activity,
                            new androidx.activity.OnBackPressedCallback(true) {
                                @Override
                                public void handleOnBackPressed() {
                                    handleBack();
                                }
                            }
                    );
                    backRegistered = true;
                } catch (Throwable ignored) {
                }
            }

            if (!backRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Api33BackHelper.register(activity, this::handleBack);
            }

            activity.setContentView(root);
        }

        boolean isSelecting() {
            return selectionMode;
        }

        void handleBack() {
            if (isSelecting()) {
                cancelSelection();
            } else {
                activity.finish();
            }
        }

        void cancelSelection() {
            selectionMode = false;
            messageList.clearChoices();
            messageList.setChoiceMode(ListView.CHOICE_MODE_NONE);
            updateSelectionUi();
        }

        private void toggleAll() {
            boolean checked = shouldSelectAll(
                    messageList.getCheckedItemCount(), messages.size());
            for (int position = 0; position < messages.size(); position++) {
                messageList.setItemChecked(position, checked);
            }
            updateSelectionUi();
        }

        private void updateSelectionUi() {
            int checkedCount = messageList.getCheckedItemCount();
            boolean selecting = isSelecting();
            boolean hasSelection = checkedCount > 0;
            if (selectionToolbarVisible != selecting) {
                applySelectionToolbarLayout(selecting);
                selectionToolbarVisible = selecting;
            }
            toolbarTitle.setText(selecting
                    ? String.format(Locale.getDefault(), str("piko_selected_count"), checkedCount)
                    : str("piko_all_deleted_messages"));
            backAction.setVisibility(selecting ? View.GONE : View.VISIBLE);
            selectAllAction.setVisibility(selecting ? View.VISIBLE : View.GONE);
            selectAllCheck.setChecked(areAllSelected(
                    checkedCount, messages.size()));
            cancelSelectionAction.setVisibility(selecting ? View.VISIBLE : View.GONE);
            deleteAction.setVisibility(selecting ? View.VISIBLE : View.GONE);
            deleteAction.setEnabled(hasSelection);
            deleteAction.setAlpha(hasSelection ? 1f : 0.5f);
            refreshVisibleSelectionRows(messageList, selecting);
        }

        private void applySelectionToolbarLayout(boolean selecting) {
            LinearLayout toolbar = (LinearLayout) toolbarTitle.getParent();
            if (!selecting) {
                toolbar.setMinimumHeight(0);
                InstagramPreferenceStyle.applyToolbarLayout(
                        activity, toolbar, (ImageView) backAction, toolbarTitle, false);
                return;
            }

            toolbar.setGravity(Gravity.TOP);
            toolbar.setPadding(Dim.dp8, toolbar.getPaddingTop(),
                    Dim.dp16, toolbar.getPaddingBottom());
            toolbar.setMinimumHeight(InstagramPreferenceStyle.dp(activity, 70));
            toolbar.getLayoutParams().height = LinearLayout.LayoutParams.WRAP_CONTENT;
            toolbar.requestLayout();
            LinearLayout.LayoutParams titleParams =
                    (LinearLayout.LayoutParams) toolbarTitle.getLayoutParams();
            titleParams.height = Dim.dp48;
            titleParams.gravity = Gravity.TOP;
            titleParams.leftMargin = InstagramPreferenceStyle.dp(activity, 10);
            toolbarTitle.setGravity(Gravity.CENTER_VERTICAL);
            toolbarTitle.setLayoutParams(titleParams);
        }

        private void confirmDeleteSelected() {
            SparseBooleanArray checked = messageList.getCheckedItemPositions();
            ArrayList<String> selectedIds = new ArrayList<>();
            for (int position = 0; position < messages.size(); position++) {
                if (checked.get(position)) selectedIds.add(messages.get(position)[0]);
            }
            if (selectedIds.isEmpty()) return;

            String prompt = String.format(Locale.getDefault(),
                    str("piko_delete_selected_confirm"), selectedIds.size());
            new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(activity))
                    .setMessage(prompt)
                    .setPositiveButton(str("piko_delete"), (dialog, which) -> {
                        PikoMessageDb db = PikoMessageDb.getInstance(activity);
                        db.deleteSaved(selectedIds);
                        selectionMode = false;
                        messageList.clearChoices();
                        messageList.setChoiceMode(ListView.CHOICE_MODE_NONE);
                        messages = db.getDeletedMessages();
                        adapter.notifyDataSetChanged();
                        updateSelectionUi();
                    })
                    .setNegativeButton(str("piko_cancel"), null)
                    .show();
        }

        private void showMediaOptions(String messageId, String url, String type) {
            try {
                final boolean isAudio = "voice_media".equals(type) || "audio".equals(type)
                        || url.matches("(?i).*\\.(m4a|aac|mp3|ogg)(\\?.*)?$");
                final boolean isVideo = isVideo(url, type);
                final boolean isShare = url.contains("instagram.com/reel/")
                        || url.contains("instagram.com/p/") || url.contains("instagram.com/tv/");

                final CharSequence[] options = new CharSequence[] {
                    str("piko_download_current_media"),
                    isShare ? str("piko_open_share_in_instagram")
                            : isAudio ? str("piko_open_voice_with_player")
                            : isVideo ? str("piko_open_video_externally")
                            : str("piko_open_image_externally"),
                    str("piko_copy_media_link"),
                };

                new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(activity))
                    .setTitle(str("piko_download_options"))
                    .setItems(options, (d, which) -> {
                        try {
                            if (which == 0) {
                                String fileName = "piko_" + messageId + guessExtension(url, type);
                                DownloadUtils.downloadMediaUrl(activity, url, Constants.DEFAULT_DM_FOLDER, fileName);
                            } else if (which == 2) {
                                Utils.setClipboard(MediaUrls.forViewing(url));
                                Utils.showToastShort(str("piko_copied_media_link"));
                            } else {
                                Uri uri = Uri.parse(url);
                                // Captured message text can also start with "http" without using a web scheme.
                                String scheme = uri.getScheme();
                                if (scheme == null
                                        || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                                    return;
                                }
                                boolean igLink = url.contains("instagram.com/reel/")
                                        || url.contains("instagram.com/p/")
                                        || url.contains("instagram.com/tv/");
                                boolean opened = false;
                                if (igLink) {
                                    try {
                                        activity.startActivity(new Intent(Intent.ACTION_VIEW, uri)
                                                .setPackage("com.instagram.android"));
                                        opened = true;
                                    } catch (Exception ignored) {}
                                }
                                if (!opened) {
                                    String path = uri.getPath();
                                    boolean isImage = "image".equals(type) || (path != null
                                            && path.matches("(?i).*\\.(jpg|jpeg|png|webp|heic|heif|avif|gif)$"));
                                    if (!igLink && !isAudio && !isVideo && isImage) {
                                        ActivityHook.handleUrlIntent(false, url);
                                    } else {
                                        Intent i = new Intent(Intent.ACTION_VIEW, uri);
                                        if (isAudio) i.setDataAndType(uri, "audio/*");
                                        else if (isVideo && !igLink) i.setDataAndType(uri, "video/*");
                                        activity.startActivity(Intent.createChooser(i, null));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Logger.printException(() -> "showMediaOptions action: ", e);
                        }
                    })
                    .show();
            } catch (Exception e) {
                Logger.printException(() -> "showMediaOptions: ", e);
            }
        }

        private Drawable messageRowBackground() {
            int background = InstagramPreferenceStyle.backgroundColor();
            int highlight = InstagramPreferenceStyle.pressedBackgroundColor();
            StateListDrawable content = new StateListDrawable();
            content.addState(new int[]{android.R.attr.state_activated}, new ColorDrawable(highlight));
            content.addState(StateSet.WILD_CARD, new ColorDrawable(background));
            return new RippleDrawable(ColorStateList.valueOf(highlight), content, null);
        }

        private Drawable selectionControlDrawable() {
            TypedArray attributes = InstagramPreferenceStyle.dialogContext(activity)
                    .obtainStyledAttributes(new int[]{android.R.attr.listChoiceIndicatorMultiple});
            try {
                return attributes.getDrawable(0);
            } finally {
                attributes.recycle();
            }
        }

        private void bindSelectionAccessibility(View view, SelectionCheck check) {
            view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    if (selectionMode) info.setClassName("android.widget.CheckBox");
                    info.setCheckable(selectionMode);
                    info.setChecked(selectionMode && check.isChecked());
                }
            });
        }

        private class MessageAdapter extends BaseAdapter {
            @Override public int getCount() { return messages.size(); }
            @Override public Object getItem(int pos) { return messages.get(pos); }
            @Override public long getItemId(int pos) { return pos; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row;
                LinearLayout contentColumn;
                SelectionCheck selectionView;
                TextView senderView, contentView, metaView;

                if (convertView == null) {
                    row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setBackground(messageRowBackground());

                    selectionView = new SelectionCheck(
                            InstagramPreferenceStyle.dialogContext(activity), selectionControlDrawable());
                    selectionView.setTag("selection");
                    LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                            Dim.dp48, Dim.dp48);
                    checkParams.setMarginEnd(Dim.dp8);
                    row.addView(selectionView, checkParams);

                    contentColumn = new LinearLayout(activity);
                    contentColumn.setOrientation(LinearLayout.VERTICAL);

                    senderView = new TextView(activity);
                    senderView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    senderView.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
                    senderView.setTag("s");

                    contentView = new TextView(activity);
                    contentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    contentView.setTextColor(InstagramPreferenceStyle.primaryTextColor());
                    contentView.setPadding(0, Dim.dp4, 0, Dim.dp4);
                    contentView.setTag("c");

                    metaView = new TextView(activity);
                    metaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    metaView.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
                    metaView.setTag("m");

                    contentColumn.addView(senderView);
                    contentColumn.addView(contentView);
                    contentColumn.addView(metaView);
                    row.addView(contentColumn, new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                } else {
                    row = (LinearLayout) convertView;
                    selectionView = row.findViewWithTag("selection");
                    senderView = row.findViewWithTag("s");
                    contentView = row.findViewWithTag("c");
                    metaView    = row.findViewWithTag("m");
                }

                String[] msg = messages.get(position);
                String sender    = msg[2];
                String content   = msg[3];
                String type      = msg[4];
                long   timestamp = 0;
                try { timestamp = Long.parseLong(msg[5]); } catch (Exception ignored) {}

                final String who;
                if (sender != null && !sender.isEmpty()) {
                    who = "@" + sender;
                } else {
                    who = str("piko_unknown");
                }
                senderView.setText(who);
                boolean isMediaUrl = content != null && content.startsWith("http");
                if (isMediaUrl) {
                    contentView.setText(mediaLabel(type, content) + "  ·  "
                            + str(isExpiredMediaUrl(content) ? "piko_media_expired" : "piko_tap_to_view"));
                } else {
                    contentView.setText(content != null && !content.isEmpty() ? content
                            : (type != null ? "[" + type + "]" : str("piko_media_deleted_generic")));
                }
                metaView.setText(timestampFormatter.format(new Date(timestamp)));
                boolean checked = messageList.isItemChecked(position);
                selectionView.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
                selectionView.setChecked(checked);
                row.setPadding(selectionMode ? Dim.dp8 : Dim.dp16, Dim.dp12, Dim.dp16, Dim.dp12);
                row.setActivated(checked);

                return row;
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
