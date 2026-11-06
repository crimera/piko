/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import android.annotation.TargetApi;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.content.LocusId;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * The conversation shortcuts of chats: one per chat, long-lived and also a share target, with
 * Instagram's own avatar of the chat or a letter tile as its icon.
 */
public final class ChatShortcuts {
    private ChatShortcuts() {
    }

    static final String MAIN_ACTIVITY = "com.instagram.mainactivity.InstagramMainActivity";

    static final String FROM_DIRECT_PUSH = "DirectThreadFragment.DIRECT_THREAD_FRAGMENT_ARGUMENT_IS_FROM_DIRECT_PUSH";

    static final String AVATAR_DIRECTORY = "piko_chat_avatars";

    // Shortcut id -> label and icon it was last published with, so a chat is only pushed again when they change.
    static final Map<String, String> publishedShortcuts = new HashMap<>();

    /** Called by Instagram's share shortcuts with the avatar it drew for a chat, group photos included. */
    public static void onChatAvatar(String threadId, Bitmap avatar) {
        if (threadId == null || avatar == null) return;
        try {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            avatar.compress(Bitmap.CompressFormat.PNG, 100, png);
            byte[] bytes = png.toByteArray();

            File file = avatarFile(Utils.getContext(), threadId);
            if (file.exists() && Arrays.equals(bytes, readFile(file))) return;

            File directory = file.getParentFile();
            if (directory != null && !directory.isDirectory() && !directory.mkdirs()) return;
            File temporary = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                out.write(bytes);
            }
            if (!temporary.renameTo(file)) temporary.delete();
            NotificationLog.event("AVATAR saved for chat " + threadId);
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to save a chat avatar", ex);
        }
    }

    static File avatarFile(Context context, String threadId) {
        return new File(new File(context.getCacheDir(), AVATAR_DIRECTORY), threadId + ".png");
    }

    static byte[] readFile(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) bytes.write(buffer, 0, read);
            return bytes.toByteArray();
        }
    }

    static Icon chatAvatar(Context context, String threadId) {
        File file = avatarFile(context, threadId);
        if (!file.isFile()) return null;
        Bitmap avatar = BitmapFactory.decodeFile(file.getPath());
        return avatar == null ? null : Icon.createWithBitmap(avatar);
    }

    @TargetApi(Build.VERSION_CODES.R)
    static boolean hasShortcut(Context context, String shortcutId) {
        List<ShortcutInfo> shortcuts = context.getSystemService(ShortcutManager.class)
                .getShortcuts(ShortcutManager.FLAG_MATCH_DYNAMIC | ShortcutManager.FLAG_MATCH_CACHED);
        for (ShortcutInfo shortcut : shortcuts) {
            if (shortcut.getId().equals(shortcutId)) return true;
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.R)
    /**
     * {@code usage}: pushed for a message received or sent, even when unchanged, with the message
     * capabilities Android ranks share targets by (Audience marks a group).
     */
    static boolean publish(Context context, String shortcutId, String threadId,
                                           CharSequence label, Icon icon, Person person, boolean usage, boolean group) {
        // "share|": shortcuts published before they were share targets are pushed once more.
        String published = "share|" + label + "|" + iconKey(icon);
        synchronized (publishedShortcuts) {
            if (!usage && published.equals(publishedShortcuts.get(shortcutId))) return false;
        }

        // Opens the chat the same way tapping its notification does.
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("ig://direct_v2?id=" + Uri.encode(threadId)))
                .setClassName(context, MAIN_ACTIVITY)
                .putExtra(FROM_DIRECT_PUSH, true);

        ShortcutInfo.Builder shortcut = new ShortcutInfo.Builder(context, shortcutId)
                .setShortLabel(label)
                .setLongLabel(label)
                .setLongLived(true)
                .setLocusId(new LocusId(shortcutId))
                .setCategories(shortcutCategories(context))
                .setIntent(intent);
        // Without an icon, Android draws a group from the avatars of its latest senders.
        if (icon != null) shortcut.setIcon(icon);
        if (person != null) shortcut.setPerson(person);
        if (Build.VERSION.SDK_INT >= 33) {
            android.content.pm.Capability receive =
                    new android.content.pm.Capability.Builder("actions.intent.RECEIVE_MESSAGE").build();
            android.content.pm.Capability send =
                    new android.content.pm.Capability.Builder("actions.intent.SEND_MESSAGE").build();
            shortcut.addCapabilityBinding(receive, group
                    ? new android.content.pm.CapabilityParams.Builder("message.sender.@type", "Audience").build() : null);
            shortcut.addCapabilityBinding(send, group
                    ? new android.content.pm.CapabilityParams.Builder("message.recipient.@type", "Audience").build() : null);
        }

        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        try {
            makeRoomForChat(manager, shortcutId);
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to make room for a chat shortcut", ex);
        }
        manager.pushDynamicShortcut(shortcut.build());
        synchronized (publishedShortcuts) {
            publishedShortcuts.put(shortcutId, published);
        }
        return true;
    }

    // A pushed shortcut takes rank 0, and a full set drops the last ranked one, which would be one
    // of Instagram's own (camera, post...). So chats make room among themselves, and at most this
    // many stay dynamic (share sheet candidates); older ones stay cached by their notifications.
    static final int MAX_DYNAMIC_CHATS = 8;

    @TargetApi(Build.VERSION_CODES.R)
    private static void makeRoomForChat(ShortcutManager manager, String shortcutId) {
        List<ShortcutInfo> dynamic = manager.getDynamicShortcuts();
        List<ShortcutInfo> chats = new java.util.ArrayList<>();
        for (ShortcutInfo shortcut : dynamic) {
            if (shortcut.getId().equals(shortcutId)) return; // An update takes no new slot.
            if (isChat(shortcut.getId())) chats.add(shortcut);
        }
        int free = manager.getMaxShortcutCountPerActivity() - manager.getManifestShortcuts().size() - dynamic.size();
        int drop = Math.max(free > 0 ? 0 : 1 - free, chats.size() + 1 - MAX_DYNAMIC_CHATS);
        if (drop <= 0) return;
        Collections.sort(chats, (a, b) -> Long.compare(a.getLastChangedTimestamp(), b.getLastChangedTimestamp()));
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(drop, chats.size()); i++) ids.add(chats.get(i).getId());
        if (!ids.isEmpty()) manager.removeDynamicShortcuts(ids);
    }

    // ---- Instagram's shortcut updates (rerouted by the patch) ----
    // Instagram replaces every dynamic shortcut of the app when it updates its own, which leaves the
    // chats only cached by their notifications, and the share sheet only offers dynamic shortcuts.
    // These do the same to Instagram's shortcuts and leave the chats alone.

    static boolean isChat(String shortcutId) {
        return shortcutId != null && shortcutId.startsWith(ConversationNotifications.SHORTCUT_PREFIX);
    }

    @TargetApi(Build.VERSION_CODES.R)
    public static void removeAllDynamicShortcuts(ShortcutManager manager) {
        try {
            List<String> ids = new java.util.ArrayList<>();
            for (ShortcutInfo shortcut : manager.getDynamicShortcuts()) {
                if (!isChat(shortcut.getId())) ids.add(shortcut.getId());
            }
            if (!ids.isEmpty()) manager.removeDynamicShortcuts(ids);
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to keep the chat shortcuts", ex);
            manager.removeAllDynamicShortcuts();
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    public static void removeDynamicShortcuts(ShortcutManager manager, List<String> ids) {
        List<String> instagram = new java.util.ArrayList<>();
        for (String id : ids) if (!isChat(id)) instagram.add(id);
        manager.removeDynamicShortcuts(instagram);
    }

    @TargetApi(Build.VERSION_CODES.R)
    public static boolean setDynamicShortcuts(ShortcutManager manager, List<ShortcutInfo> shortcuts) {
        try {
            Set<String> keep = new java.util.HashSet<>();
            for (ShortcutInfo shortcut : shortcuts) keep.add(shortcut.getId());
            List<String> stale = new java.util.ArrayList<>();
            for (ShortcutInfo shortcut : manager.getDynamicShortcuts()) {
                if (!isChat(shortcut.getId()) && !keep.contains(shortcut.getId())) stale.add(shortcut.getId());
            }
            if (!stale.isEmpty()) manager.removeDynamicShortcuts(stale);
            makeRoom(manager, shortcuts);
            return manager.addDynamicShortcuts(shortcuts);
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to keep the chat shortcuts", ex);
            return manager.setDynamicShortcuts(shortcuts);
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    public static boolean addDynamicShortcuts(ShortcutManager manager, List<ShortcutInfo> shortcuts) {
        try {
            makeRoom(manager, shortcuts);
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to make room for Instagram's shortcuts", ex);
        }
        return manager.addDynamicShortcuts(shortcuts);
    }

    /** Removes the least recently updated chats until the new shortcuts fit under Android's limit. */
    @TargetApi(Build.VERSION_CODES.R)
    private static void makeRoom(ShortcutManager manager, List<ShortcutInfo> incoming) {
        List<ShortcutInfo> dynamic = manager.getDynamicShortcuts();
        Set<String> present = new java.util.HashSet<>();
        for (ShortcutInfo shortcut : dynamic) present.add(shortcut.getId());
        int adding = 0;
        for (ShortcutInfo shortcut : incoming) if (!present.contains(shortcut.getId())) adding++;
        int over = dynamic.size() + manager.getManifestShortcuts().size() + adding
                - manager.getMaxShortcutCountPerActivity();
        if (over <= 0) return;
        List<ShortcutInfo> chats = new java.util.ArrayList<>();
        for (ShortcutInfo shortcut : dynamic) if (isChat(shortcut.getId())) chats.add(shortcut);
        Collections.sort(chats, (a, b) -> Long.compare(a.getLastChangedTimestamp(), b.getLastChangedTimestamp()));
        List<String> drop = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(over, chats.size()); i++) drop.add(chats.get(i).getId());
        if (!drop.isEmpty()) {
            manager.removeDynamicShortcuts(drop);
            NotificationLog.event("SHORTCUT made room for Instagram: " + drop.size() + " older chat(s) no longer dynamic");
        }
    }

    /** A conversation, and a share target (Instagram's own categories), so the chat is in the share sheet. */
    static Set<String> shortcutCategories(Context context) {
        Set<String> categories = new java.util.HashSet<>(ShareTargets.categories(context));
        categories.add(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION);
        return categories;
    }

    static String iconKey(Icon icon) {
        if (icon == null) return "none";
        String own = iconKeys.get(icon);
        if (own != null) return own;
        if (icon.getType() != Icon.TYPE_BITMAP && icon.getType() != Icon.TYPE_ADAPTIVE_BITMAP) {
            return String.valueOf(icon.getType());
        }
        Drawable drawable = icon.loadDrawable(Utils.getContext());
        if (!(drawable instanceof BitmapDrawable)) return "bitmap";
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        return bitmap.getWidth() + "x" + bitmap.getHeight() + ":" + Arrays.hashCode(pixels(bitmap));
    }

    static int[] pixels(Bitmap bitmap) {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        return pixels;
    }

    static final int ICON_SIZE = 192;

    static final class GroupIcon {
        final Icon icon;
        final String kind;

        GroupIcon(Icon icon, String kind) {
            this.icon = icon;
            this.kind = kind;
        }
    }

    // Icons built here carry their own key, since an adaptive bitmap cannot be read back as pixels.
    static final Map<Icon, String> iconKeys = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Never blocks: reads a saved icon (see {@link #savedGroupIcon}), else draws a letter tile from
     * the name and asks {@link GroupPhotos} to build one. A chat whose icon arrives later is pushed
     * again then, and picks it up on its next post, as the shortcut's icon key changes.
     */
    @TargetApi(Build.VERSION_CODES.R)
    static GroupIcon groupIcon(Context context, String threadId, CharSequence label) {
        GroupIcon found = savedGroupIconOrLetter(context, threadId, label);
        GroupPhotos.onGroupShown(threadId, label, found.kind);
        return found;
    }

    /** The same icon, without asking {@link GroupPhotos} for a better one (it calls this itself). */
    static GroupIcon savedGroupIconOrLetter(Context context, String threadId, CharSequence label) {
        GroupIcon found = savedGroupIcon(context, threadId);
        if (found != null) return found;
        Icon letter = Icon.createWithAdaptiveBitmap(letterTile(label));
        iconKeys.put(letter, "letter:" + label);
        return new GroupIcon(letter, "letter");
    }

    /**
     * In order: Instagram's share shortcut avatar (what Instagram itself shows, kept current for its
     * recent chats), the group's own photo (downloaded by {@link GroupPhotos}), the two-member
     * composite. Null when none is saved.
     */
    private static GroupIcon savedGroupIcon(Context context, String threadId) {
        File saved = avatarFile(context, threadId);
        Bitmap bitmap = saved.isFile() ? BitmapFactory.decodeFile(saved.getPath()) : null;
        if (bitmap != null) return keyed(Icon.createWithBitmap(bitmap), "photo", saved);
        File photo = GroupPhotos.groupFile(context, threadId, "group");
        bitmap = photo.isFile() ? BitmapFactory.decodeFile(photo.getPath()) : null;
        if (bitmap != null) return keyed(Icon.createWithBitmap(bitmap), "group", photo);
        File composite = GroupPhotos.groupFile(context, threadId, "composite");
        bitmap = composite.isFile() ? BitmapFactory.decodeFile(composite.getPath()) : null;
        if (bitmap != null) return keyed(Icon.createWithAdaptiveBitmap(bitmap), "composite", composite);
        return null;
    }

    private static GroupIcon keyed(Icon icon, String kind, File file) {
        iconKeys.put(icon, kind + ":" + file.length() + ":" + file.lastModified());
        return new GroupIcon(icon, kind);
    }

    static Bitmap letterTile(CharSequence label) {
        int[] colors = {0xFF5B7DB1, 0xFF7E6BB1, 0xFFB16B8C, 0xFFB1816B, 0xFF6BA37E, 0xFF4F9AA8};
        String text = label == null ? "" : label.toString().trim();
        String letter = text.isEmpty() ? "#" : new String(Character.toChars(text.codePointAt(0))).toUpperCase();
        Bitmap bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(colors[Math.abs(text.hashCode()) % colors.length]);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(72);
        paint.setTextAlign(Paint.Align.CENTER);
        Rect bounds = new Rect();
        paint.getTextBounds(letter, 0, letter.length(), bounds);
        canvas.drawText(letter, ICON_SIZE / 2f, ICON_SIZE / 2f - bounds.exactCenterY(), paint);
        return bitmap;
    }

    /** After a logout every shortcut is published afresh. */
    static void forgetPublished() {
        synchronized (publishedShortcuts) {
            publishedShortcuts.clear();
        }
    }
}
