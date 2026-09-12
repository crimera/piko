/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.SparseBooleanArray;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.db.PikoMessageDb;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.Dim;
import app.morphe.extension.shared.ui.ListViewDragSelectionController;

public class DeletedMessagesActivity extends Activity {

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
            CheckBox selectionView = row.findViewWithTag("selection");
            if (selectionView == null) continue;
            boolean checked = list.isItemChecked(position);
            selectionView.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            selectionView.setChecked(checked);
            row.setActivated(checked);
        }
    }
    private List<String[]> messages;
    private ListView messageList;
    private MessageAdapter adapter;
    private SimpleDateFormat timestampFormatter;
    private View backAction;
    private TextView toolbarTitle;
    private View selectAllAction;
    private CheckBox selectAllCheck;
    private TextView selectAllLabel;
    private TextView cancelSelectionAction;
    private Button deleteAction;
    private boolean selectionMode;
    private boolean selectionToolbarVisible;
    private ListViewDragSelectionController selectionController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER);
        messages = PikoMessageDb.getInstance(this).getDeletedMessages();
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        String skeleton = DateFormat.is24HourFormat(this) ? "yMMMdHm" : "yMMMdhm";
        timestampFormatter = new SimpleDateFormat(
                DateFormat.getBestDateTimePattern(locale, skeleton), locale);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
        InstagramPreferenceStyle.applySystemBarStyle(this);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

        ImageView back = new ImageView(this);
        UI.setThemedIcon(back, UI.DRAWABLE_ARROW_BACK);
        back.setOnClickListener(v -> handleBack());
        backAction = back;

        toolbarTitle = new TextView(this);
        toolbarTitle.setText(str("piko_all_deleted_messages"));
        toolbarTitle.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        InstagramPreferenceStyle.applyToolbarLayout(
                this, toolbar, back, toolbarTitle, false);

        LinearLayout selectAll = new LinearLayout(this);
        selectAll.setOrientation(LinearLayout.VERTICAL);
        selectAll.setGravity(Gravity.CENTER);
        selectAll.setVisibility(View.GONE);
        selectAll.setContentDescription(str("piko_all"));

        selectAllCheck = new CheckBox(this);
        selectAllCheck.setClickable(false);
        selectAllCheck.setFocusable(false);
        selectAllCheck.setMinWidth(0);
        selectAllCheck.setMinHeight(0);
        selectAllCheck.setPadding(0, 0, 0, 0);
        selectAllCheck.setTranslationX(-Dim.dp8 / 4f);
        selectAllCheck.setButtonTintList(selectionControlTint());
        selectAll.addView(selectAllCheck, new LinearLayout.LayoutParams(Dim.dp28, Dim.dp28));

        selectAllLabel = new TextView(this);
        selectAllLabel.setText(str("piko_all"));
        selectAllLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        selectAllLabel.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        selectAllLabel.setIncludeFontPadding(false);
        selectAllLabel.setGravity(Gravity.CENTER);
        selectAll.addView(selectAllLabel);
        selectAll.setOnClickListener(v -> toggleAll());
        selectAllAction = selectAll;

        TextView cancelSelection = new TextView(this);
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
        toolbar.addView(selectAll, new LinearLayout.LayoutParams(Dim.dp48, Dim.dp48));
        toolbar.addView(toolbarTitle);
        toolbar.addView(cancelSelection, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Dim.dp48));
        root.addView(toolbar);

        FrameLayout content = new FrameLayout(this);
        TextView empty = new TextView(this);
        empty.setText(str("piko_no_deleted_messages"));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Dim.dp8 * 2, Dim.dp8 * 4, Dim.dp8 * 2, Dim.dp8 * 4);
        empty.setTextColor(InstagramPreferenceStyle.secondaryTextColor());

        messageList = new ListView(this);
        adapter = new MessageAdapter();
        messageList.setAdapter(adapter);
        messageList.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
        messageList.setDivider(new ColorDrawable(UI.getThemedColour("igds_color_separator")));
        messageList.setDividerHeight(1);
        messageList.setChoiceMode(ListView.CHOICE_MODE_NONE);
        selectionController = new ListViewDragSelectionController(
                messageList, this::updateSelectionUi);
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
                android.widget.Toast.makeText(this, str("piko_media_not_available"),
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        messageList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!selectionMode) {
                selectionMode = true;
                messageList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            }
            selectionController.begin(position);
            return true;
        });
        messageList.setOnTouchListener((view, event) -> selectionController.handleTouch(event));

        FrameLayout.LayoutParams match = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        content.addView(empty, match);
        content.addView(messageList, match);
        messageList.setEmptyView(empty);
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        deleteAction = new Button(this);
        deleteAction.setText(str("piko_delete"));
        deleteAction.setAllCaps(false);
        deleteAction.setTextColor(0xffed4956);
        deleteAction.setBackgroundTintList(ColorStateList.valueOf(
                InstagramPreferenceStyle.pressedBackgroundColor()));
        deleteAction.setMinimumHeight(Dim.dp48);
        deleteAction.setVisibility(View.GONE);
        deleteAction.setOnClickListener(v -> confirmDeleteSelected());
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        deleteParams.setMargins(Dim.dp12, Dim.dp8, Dim.dp12, Dim.dp8);
        root.addView(deleteAction, deleteParams);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });

        setContentView(root);
    }

    private boolean isSelecting() {
        return selectionMode;
    }

    private void handleBack() {
        if (isSelecting()) {
            cancelSelection();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void cancelSelection() {
        selectionController.stop();
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
            toolbarTitle.setTranslationY(0f);
            cancelSelectionAction.setTranslationY(0f);
            selectAllAction.setTranslationY(0f);
            InstagramPreferenceStyle.applyToolbarLayout(
                    this, toolbar, (ImageView) backAction, toolbarTitle, false);
            return;
        }

        toolbar.setPadding(Dim.dp8, toolbar.getPaddingTop(),
                Dim.dp8, toolbar.getPaddingBottom());
        LinearLayout.LayoutParams titleParams =
                (LinearLayout.LayoutParams) toolbarTitle.getLayoutParams();
        titleParams.leftMargin = InstagramPreferenceStyle.dp(this, 10);
        toolbarTitle.setLayoutParams(titleParams);

        float topRowOffset = selectAllLabel.getLineHeight() / 2f;
        toolbarTitle.setTranslationY(0f);
        cancelSelectionAction.setTranslationY(0f);
        selectAllAction.setTranslationY(topRowOffset);
        selectAllLabel.setTranslationX(selectAllCheck.getTranslationX());
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
        new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(this))
                .setMessage(prompt)
                .setPositiveButton(str("piko_delete"), (dialog, which) -> {
                    PikoMessageDb db = PikoMessageDb.getInstance(this);
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

    /** Extension guess from the captured CDN url, falling back to the stored message type. */
    private static String guessExtension(String url, String type) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)\\.(jpg|jpeg|webp|heic|png|mp4|mov|m4a|aac|mp3|ogg|gif)(?:\\?.*)?$")
                .matcher(url);
        if (m.find()) return "." + m.group(1).toLowerCase();
        if ("voice_media".equals(type) || "audio".equals(type)) return ".m4a";
        if ("video".equals(type)) return ".mp4";
        if ("animated_media".equals(type)) return ".gif";
        return ".jpg";
    }

    /**
     * True when a CDN url has passed the expiry it carries in its own "oe" parameter (hex epoch).
     * Shared-post permalinks have no such parameter and never expire.
     */
    private static boolean isExpiredMediaUrl(String url) {
        try {
            int i = url.indexOf("oe=");
            // Must start a parameter, so "?oe=" or "&oe=" — not a suffix of another name.
            if (i < 1 || (url.charAt(i - 1) != '?' && url.charAt(i - 1) != '&')) return false;
            int end = i + 3;
            while (end < url.length() && Character.digit(url.charAt(end), 16) >= 0) end++;
            if (end == i + 3) return false;
            return Long.parseLong(url.substring(i + 3, end), 16) * 1000L < System.currentTimeMillis();
        } catch (Exception e) {
            return false;
        }
    }

    /** Offers open/download/copy for a captured media url, via a plain framework AlertDialog. */
    private void showMediaOptions(String messageId, String url, String type) {
        try {
            final boolean isAudio = "voice_media".equals(type) || "audio".equals(type)
                    || url.matches("(?i).*\\.(m4a|aac|mp3|ogg)(\\?.*)?$");
            final boolean isVideo = "video".equals(type) || "clip".equals(type) || "xma_clip".equals(type);
            // A shared post/reel is stored as an instagram.com permalink and opens inside the IG app,
            // so label its action "Open in Instagram" rather than "open externally".
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

            new android.app.AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(this))
                .setTitle(str("piko_download_options"))
                .setItems(options, (d, which) -> {
                    try {
                        if (which == 0) {
                            String fileName = "piko_" + messageId + guessExtension(url, type);
                            DownloadUtils.downloadMediaUrl(this, url, Constants.DEFAULT_DM_FOLDER, fileName);
                        } else if (which == 2) {
                            Utils.setClipboard(url);
                            Utils.showToastShort(str("piko_copied_media_link"));
                        } else {
                            android.net.Uri uri = android.net.Uri.parse(url);
                            // Reels/posts are stored as instagram.com permalinks — open them inside
                            // the Instagram app itself (setPackage) so they render natively; only fall
                            // back to a browser chooser if IG can't handle the link.
                            boolean igLink = url.contains("instagram.com/reel/")
                                    || url.contains("instagram.com/p/")
                                    || url.contains("instagram.com/tv/");
                            boolean opened = false;
                            if (igLink) {
                                try {
                                    startActivity(new android.content.Intent(
                                            android.content.Intent.ACTION_VIEW, uri)
                                            .setPackage("com.instagram.android"));
                                    opened = true;
                                } catch (android.content.ActivityNotFoundException ignored) {}
                            }
                            if (!opened) {
                                android.content.Intent i = new android.content.Intent(
                                        android.content.Intent.ACTION_VIEW, uri);
                                if (isAudio) i.setDataAndType(uri, "audio/*");
                                startActivity(android.content.Intent.createChooser(i, null));
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("piko", "showMediaOptions action: " + e);
                    }
                })
                .show();
        } catch (Exception e) {
            android.util.Log.e("piko", "showMediaOptions: " + e);
        }
    }

    private static String mediaLabel(String type) {
        if (type == null) return "[" + str("piko_media_unknown") + "]";
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

    private Drawable messageRowBackground() {
        int background = InstagramPreferenceStyle.backgroundColor();
        int highlight = InstagramPreferenceStyle.pressedBackgroundColor();
        StateListDrawable content = new StateListDrawable();
        content.addState(new int[]{android.R.attr.state_activated}, new ColorDrawable(highlight));
        content.addState(StateSet.WILD_CARD, new ColorDrawable(background));
        return new RippleDrawable(ColorStateList.valueOf(highlight), content, null);
    }

    private class MessageAdapter extends BaseAdapter {

        @Override public int getCount() { return messages.size(); }
        @Override public Object getItem(int pos) { return messages.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            LinearLayout contentColumn;
            CheckBox selectionView;
            TextView senderView, contentView, metaView;

            if (convertView == null) {
                row = new LinearLayout(DeletedMessagesActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = Dim.dp8;
                row.setPadding(pad * 2, pad, pad * 2, pad);
                row.setBackground(messageRowBackground());

                selectionView = new CheckBox(DeletedMessagesActivity.this);
                selectionView.setTag("selection");
                selectionView.setClickable(false);
                selectionView.setFocusable(false);
                selectionView.setMinWidth(0);
                selectionView.setMinHeight(0);
                selectionView.setPadding(0, 0, 0, 0);
                selectionView.setButtonTintList(selectionControlTint());
                row.addView(selectionView, new LinearLayout.LayoutParams(Dim.dp40, Dim.dp40));

                contentColumn = new LinearLayout(DeletedMessagesActivity.this);
                contentColumn.setOrientation(LinearLayout.VERTICAL);

                senderView = new TextView(DeletedMessagesActivity.this);
                senderView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                senderView.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
                senderView.setTag("s");

                contentView = new TextView(DeletedMessagesActivity.this);
                contentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                contentView.setTextColor(InstagramPreferenceStyle.primaryTextColor());
                contentView.setTag("c");

                metaView = new TextView(DeletedMessagesActivity.this);
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

            // [messageId, threadId, senderUsername, content, messageType, timestamp, senderId]
            String[] msg = messages.get(position);
            String sender    = msg[2];
            String content   = msg[3];
            String type      = msg[4];
            String senderId  = msg.length > 6 ? msg[6] : null;
            long   timestamp = 0;
            try { timestamp = Long.parseLong(msg[5]); } catch (Exception ignored) {}

            final String who;
            if (sender != null && !sender.isEmpty()) {
                who = "@" + sender;
            } else if (senderId != null && !senderId.isEmpty()) {
                who = "@" + senderId;
            } else {
                who = str("piko_unknown");
            }
            senderView.setText(who);
            boolean isMediaUrl = content != null && content.startsWith("http");
            if (isMediaUrl) {
                contentView.setText(mediaLabel(type) + "  ·  "
                        + str(isExpiredMediaUrl(content) ? "piko_media_expired" : "piko_tap_to_view"));
            } else {
                contentView.setText(content != null && !content.isEmpty() ? content
                        : (type != null ? "[" + type + "]" : str("piko_media_deleted_generic")));
            }
            metaView.setText(timestampFormatter.format(new Date(timestamp)));
            boolean checked = messageList.isItemChecked(position);
            selectionView.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            selectionView.setChecked(checked);
            row.setActivated(checked);

            return row;
        }
    }

    private ColorStateList selectionControlTint() {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{
                        InstagramPreferenceStyle.selectionColor(),
                        InstagramPreferenceStyle.secondaryTextColor()
                }
        );
    }
}
