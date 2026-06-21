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
import app.morphe.extension.instagram.db.PikoMessageDb;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.ui.Dim;

public class DeletedMessagesActivity extends Activity {

    private List<String[]> messages;
    private String threadTitle;
    private MessageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Instagram is a portrait-only app; lock this screen to portrait so it doesn't follow the
        // device auto-rotate (which looks broken since the layout is built for portrait).
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // When launched from compose-bar button, filter to that thread only.
        // When launched from Piko settings, show all deleted messages.
        String threadId = getIntent().getStringExtra("thread_id");
        threadTitle = getIntent().getStringExtra("thread_title");
        messages = threadId != null
            ? PikoMessageDb.getInstance(this).getDeletedMessagesForThread(threadId)
            : PikoMessageDb.getInstance(this).getDeletedMessages();

        String titleText = threadId != null ? str("piko_deleted_in_chat") : str("piko_all_deleted_messages");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // Toolbar
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
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
        title.setTextColor(UI.getThemedColour());
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
        clear.setTextColor(UI.getThemedColour());
        clear.setPadding(Dim.dp8, Dim.dp8, Dim.dp8, Dim.dp8);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0);
        clearParams.gravity = Gravity.CENTER_VERTICAL;
        clear.setLayoutParams(clearParams);
        // push Clear to the right
        LinearLayout.LayoutParams titleP = (LinearLayout.LayoutParams) title.getLayoutParams();
        titleP.weight = 1; title.setLayoutParams(titleP);
        clear.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
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
            empty.setTextColor(UI.getThemedColour());
            root.addView(empty, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ));
        } else {
            ListView listView = new ListView(this);
            adapter = new MessageAdapter();
            listView.setAdapter(adapter);
            listView.setDividerHeight(1);
            listView.setOnItemClickListener((parent, view, pos, idLong) -> {
                String[] m = messages.get(pos);
                String c = m[3];
                if (c != null && c.startsWith("http")) {
                    try {
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(c)));
                    } catch (Exception ignored) {}
                }
            });
            listView.setOnItemLongClickListener((parent, view, pos, idLong) -> {
                String[] m = messages.get(pos);
                new android.app.AlertDialog.Builder(this)
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

    private static String mediaLabel(String type) {
        if (type == null) return "[media]";
        switch (type) {
            case "media":
            case "image":          return "[photo]";
            case "video":          return "[video]";
            case "voice_media":
            case "audio":          return "[voice message]";
            case "animated_media": return "[GIF]";
            case "reel_share":     return "[reel]";
            case "story_share":    return "[story]";
            case "media_share":    return "[post]";
            case "clip":
            case "xma_clip":       return "[reel]";
            default:               return "[" + type + "]";
        }
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
                senderView.setTextColor(UI.getThemedColour());
                senderView.setTag("s");

                contentView = new TextView(DeletedMessagesActivity.this);
                contentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                contentView.setTag("c");

                metaView = new TextView(DeletedMessagesActivity.this);
                metaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
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

            // Prefer the resolved username; fall back to the numeric sender id so the row is
            // still attributable instead of an opaque "Unknown".
            final String who;
            if (sender != null && !sender.isEmpty()) {
                who = "@" + sender;
            } else if (threadTitle != null && !threadTitle.isEmpty()) {
                who = threadTitle; // chat title from action bar (participant's name)
            } else if (senderId != null && !senderId.isEmpty()) {
                who = "@" + senderId;
            } else {
                who = str("piko_unknown");
            }
            senderView.setText(who);
            boolean isMediaUrl = content != null && content.startsWith("http");
            if (isMediaUrl) {
                contentView.setText(mediaLabel(type) + "  ·  " + str("piko_tap_to_view"));
            } else {
                contentView.setText(content != null && !content.isEmpty() ? content
                        : (type != null ? "[" + type + "]" : "[deleted]"));
            }
            metaView.setText(DateFormat.format("MMM dd, yyyy  HH:mm", new Date(timestamp)));

            return row;
        }
    }
}
