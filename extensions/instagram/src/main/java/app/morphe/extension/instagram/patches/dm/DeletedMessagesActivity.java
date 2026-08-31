/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.db.PikoMessageDb;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.Dim;

public class DeletedMessagesActivity extends Activity {

    private List<String[]> messages;
    private String threadTitle;
    private MessageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Follow the user's own auto-rotate preference (portrait when they've locked rotation,
        // landscape when they allow it) instead of forcing portrait — keeps tablets/landscape usable.
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER);

        // When launched from compose-bar button, filter to that thread only.
        // When launched from Piko settings, show all deleted messages.
        String threadId = getIntent().getStringExtra("thread_id");
        // An empty thread id can never scope a real chat — treat it like "no scope" (show all)
        // instead of matching the orphaned thread_id = "" bucket, which would leak unrelated
        // chats into a per-person screen.
        if (threadId != null && threadId.isEmpty()) threadId = null;
        threadTitle = getIntent().getStringExtra("thread_title");
        messages = threadId != null
            ? PikoMessageDb.getInstance(this).getDeletedMessagesForThread(threadId)
            : PikoMessageDb.getInstance(this).getDeletedMessages();

        String titleText = threadId != null ? str("piko_deleted_in_chat") : str("piko_all_deleted_messages");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
        InstagramPreferenceStyle.applySystemBarStyle(this);

        // Toolbar
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
        toolbar.setPadding(Dim.dp8, Dim.dp8, Dim.dp8, Dim.dp8);

        ImageView back = new ImageView(this);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(Dim.dp48, Dim.dp48);
        backParams.gravity = Gravity.CENTER_VERTICAL;
        back.setLayoutParams(backParams);
        UI.setThemedIcon(back, "material_ic_keyboard_arrow_left_black_24dp");
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, PikoUtils.spToPixels(20));
        title.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.gravity = Gravity.CENTER_VERTICAL;
        titleParams.leftMargin = Dim.dp8 / 2;
        title.setLayoutParams(titleParams);

        toolbar.addView(back);
        toolbar.addView(title);

        // "Clear" action — wipes this chat's saved messages (or all, from settings entry).
        final String clearThreadId = threadId;
        TextView clear = new TextView(this);
        clear.setText(str("piko_clear"));
        clear.setTextSize(TypedValue.COMPLEX_UNIT_PX, PikoUtils.spToPixels(16));
        clear.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        clear.setPadding(Dim.dp8, Dim.dp8, Dim.dp8, Dim.dp8);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0);
        clearParams.gravity = Gravity.CENTER_VERTICAL;
        clear.setLayoutParams(clearParams);
        // push Clear to the right
        LinearLayout.LayoutParams titleP = (LinearLayout.LayoutParams) title.getLayoutParams();
        titleP.weight = 1; title.setLayoutParams(titleP);
        clear.setOnClickListener(v -> new android.app.AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(this))
            .setMessage(clearThreadId != null ? str("piko_clear_chat_confirm") : str("piko_clear_all_confirm"))
            .setPositiveButton(str("piko_clear"), (d, w) -> {
                PikoMessageDb.getInstance(this).clearSaved(clearThreadId);
                messages.clear();
                recreate();
            })
            .setNegativeButton(str("piko_cancel"), null)
            .show());
        toolbar.addView(clear);
        root.addView(toolbar);

        if (messages.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(str("piko_no_deleted_messages"));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Dim.dp8 * 2, Dim.dp8 * 4, Dim.dp8 * 2, Dim.dp8 * 4);
            empty.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
            root.addView(empty, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ));
        } else {
            ListView listView = new ListView(this);
            adapter = new MessageAdapter();
            listView.setAdapter(adapter);
            listView.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
            listView.setDivider(new android.graphics.drawable.ColorDrawable(
                    UI.getThemedColour("igds_color_separator")));
            listView.setDividerHeight(1);
            listView.setOnItemClickListener((parent, view, pos, idLong) -> {
                String[] m = messages.get(pos);
                String messageId = m[0];
                String c = m[3];
                String t = m[4];
                if (c != null && c.startsWith("http")) {
                    showMediaOptions(messageId, c, t);
                } else if (t != null && !"text".equals(t)) {
                    android.widget.Toast.makeText(this, str("piko_media_not_available"),
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            listView.setOnItemLongClickListener((parent, view, pos, idLong) -> {
                String[] m = messages.get(pos);
                new android.app.AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(this))
                    .setMessage(str("piko_delete_saved_confirm"))
                    .setPositiveButton(str("piko_delete"), (d, w) -> {
                        PikoMessageDb.getInstance(this).deleteSaved(m[0]); // m[0] = message_id
                        messages.remove(pos);
                        adapter.notifyDataSetChanged();
                    })
                    .setNegativeButton(str("piko_cancel"), null)
                    .show();
                return true;
            });
            root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ));
        }

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
            return insets;
        });

        setContentView(root);
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

    private class MessageAdapter extends BaseAdapter {

        @Override public int getCount() { return messages.size(); }
        @Override public Object getItem(int pos) { return messages.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView senderView, contentView, metaView;

            if (convertView == null) {
                row = new LinearLayout(DeletedMessagesActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                int pad = Dim.dp8;
                row.setPadding(pad * 2, pad, pad * 2, pad);

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

                row.addView(senderView);
                row.addView(contentView);
                row.addView(metaView);
            } else {
                row = (LinearLayout) convertView;
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

            // Attribute each row by its own sender, never by the screen-level chat title (that would
            // show every sender as the same person). Order: username -> numeric sender id -> title.
            final String who;
            if (sender != null && !sender.isEmpty()) {
                who = "@" + sender;
            } else if (senderId != null && !senderId.isEmpty()) {
                who = "@" + senderId;
            } else if (threadTitle != null && !threadTitle.isEmpty()) {
                who = threadTitle; // chat title from action bar (participant's name)
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
            metaView.setText(DateFormat.format("MMM dd, yyyy  HH:mm", new Date(timestamp)));

            return row;
        }
    }
}
