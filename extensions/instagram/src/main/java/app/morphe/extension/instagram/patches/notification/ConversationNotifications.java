/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Person;
import android.content.Context;
import android.content.LocusId;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.instagram.patches.notification.state.ChatExecutor;
import app.morphe.extension.instagram.patches.notification.state.ChatKey;
import app.morphe.extension.instagram.patches.notification.state.ChatReducer;
import app.morphe.extension.instagram.patches.notification.state.Message;
import app.morphe.extension.instagram.patches.notification.state.RenderPlan;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Turns direct message notifications into Android conversations: each chat gets a long-lived
 * shortcut, so it shows in the conversations section, can bubble, and is read out by Android Auto.
 */
@SuppressWarnings("unused")
public final class ConversationNotifications {
    // Direct notifications are tagged "<category>|<key>", where the key is a "name:value;" list.
    private static final String THREAD_ID_KEY = "thread_id:";
    static final String SHORTCUT_PREFIX = "piko_direct_";

    private static final int BUBBLE_HEIGHT_DP = 600;
    // Android keeps at most 25 messages in a MessagingStyle, the same as ChatMessages retains.
    private static final int MAX_SHOWN_MESSAGES = 25;
    private static final String SENT_MESSAGE_PREFIX = "Sent a message: ";

    // Group chats are only ever notified as reply reminder digests, tagged with this type.
    private static final String DIGEST_TYPE = ";type:rr";
    private static final String SENDER_ID_EXTRA = "com.instagram.android.igns.logging.sender_id";
    private static final String RECIPIENT_ID_EXTRA = "com.instagram.android.igns.logging.intended_recipient_id";
    private static final String PUSH_CATEGORY_EXTRA = "com.instagram.android.igns.logging.push_category";
    // The notification Instagram posts itself after you reply from a notification.
    private static final String REPLY_CATEGORY = "reply_notification";
    private static final Pattern NEW_MESSAGES = Pattern.compile("(\\d+)\\+? new messages");
    private static final String SILENT_GROUP = "piko_silent";

    // A sender a notification only names gets this id prefix, so it still dedupes by sender.
    static final String NAME_ID_PREFIX = "name:";
    // Longest onNotify waits for the reducer (it runs on Instagram's IgExecutorV2 thread).
    private static final long REDUCER_WAIT_MS = 300;

    // Thread id -> when its notification was last swiped away, and when we last posted it.
    // Instagram's own swipe cleanup runs seconds later and would remove a newer message's record.
    private static final Map<String, Long> dismissedAt = new ConcurrentHashMap<>();
    private static final Map<String, Long> postedAt = new ConcurrentHashMap<>();
    private static final long LATE_CLEANUP_MS = 10_000;

    // Thread id -> the signed in user its notifications are for, to key items that name no user.
    private static final Map<String, String> threadViewers = new ConcurrentHashMap<>();

    private static ChatExecutor chatExecutor;

    /** Called by NotificationManagerCompat before a notification is posted. */
    public static Notification onNotify(String tag, int id, Notification notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || notification == null) return notification;
        boolean logged = NotificationLog.isEnabled() && Notification.CATEGORY_MESSAGE.equals(notification.category);
        if (logged) NotificationLog.notification("INSTAGRAM POSTED", tag, id, notification);
        try {
            return toConversation(tag, id, notification);
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to turn a message notification into a conversation", ex);
            NotificationLog.event("  FAILED: " + android.util.Log.getStackTraceString(ex));
            return notification;
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static Notification toConversation(String tag, int id, Notification notification) {
        if (!Notification.CATEGORY_MESSAGE.equals(notification.category)) return notification;
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            NotificationLog.event("  left unchanged: group summary");
            return notification;
        }
        if (notification.getShortcutId() != null) {
            NotificationLog.event("  left unchanged: already has a shortcut");
            return notification;
        }

        String threadId = threadId(tag);
        if (threadId == null) {
            NotificationLog.event("  no thread id in the tag");
            return notification;
        }

        Context context = Utils.getContext();
        Bundle extras = notification.extras;
        rememberPeople(tag, extras);
        boolean isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION);
        String shortcutId = SHORTCUT_PREFIX + threadId;

        // After you reply, Instagram reposts the chat titled with your own name and without the
        // avatar, so the chat is named after the other person and only shown with their photo.
        CharSequence label;
        Icon icon = ChatShortcuts.chatAvatar(context, threadId);
        String groupKind = null;
        if (isGroup) {
            label = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
            // Never the notification's large icon: that is the latest sender's photo.
            try {
                if (!TextUtils.isEmpty(label)) {
                    ChatShortcuts.GroupIcon group = ChatShortcuts.groupIcon(context, threadId, label);
                    icon = group.icon;
                    groupKind = group.kind;
                    NotificationLog.event("  icon group=" + groupKind + " for " + threadId);
                }
            } catch (Throwable ex) {
                Logger.printException(() -> "Failed to pick a group icon", ex);
            }
        } else {
            CharSequence self = selfName(extras);
            label = lastSender(extras, self);
            if (label == null) {
                CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
                if (!TextUtils.isEmpty(title) && !TextUtils.equals(title, self)) label = title;
            }
            if (icon == null && label != null && TextUtils.equals(label, lastSender(extras, null))) {
                icon = notification.getLargeIcon();
            }
        }

        Person chat = null;
        boolean published = ChatShortcuts.hasShortcut(context, shortcutId);
        NotificationLog.event("  chat " + threadId + " group=" + isGroup + " label=" + label
                + " icon=" + (icon == null ? "none" : icon == notification.getLargeIcon() ? "notification" : "saved avatar")
                + " shortcutExists=" + published);
        if (!TextUtils.isEmpty(label)) {
            chat = new Person.Builder()
                    .setKey(shortcutId)
                    .setName(label)
                    .setIcon(isGroup ? null : icon)
                    .build();
            // Without a photo, an existing chat is kept as it is, so it never loses its photo.
            // A group always has an icon, and is pushed again whenever its name or icon changed.
            if (icon != null || !published || groupKind != null) {
                boolean pushed = ChatShortcuts.publish(context, shortcutId, threadId, label, icon, isGroup ? null : chat, false, isGroup);
                if (pushed && isGroup && published) {
                    NotificationLog.event("SHORTCUT republished " + threadId + " label=" + label + " icon=" + groupKind);
                }
            }
        } else if (!published) {
            NotificationLog.event("  no chat name and no shortcut yet");
            return notification;
        }

        boolean isDigest = isGroup && tag.contains(DIGEST_TYPE);
        boolean isReply = REPLY_CATEGORY.equals(extras.getString(PUSH_CATEGORY_EXTRA));
        ChatKey key = keyOf(tag);
        if (!key.viewerId.isEmpty()) threadViewers.put(threadId, key.viewerId);
        // Instagram's post is an input: the reducer decides what is shown. It must never cost the
        // notification (R3), so when it fails the post keeps Instagram's own content.
        RenderPlan plan = reduce(key, tag, extras, isGroup, isReply);
        if (isGroup) removeSentMessagePrefix(extras);
        markOwnMessages(extras);

        Notification.Builder builder = Notification.Builder.recoverBuilder(context, notification)
                .setShortcutId(shortcutId)
                .setLocusId(new LocusId(shortcutId))
                .setBubbleMetadata(bubble(shortcutId, false))
                .setDeleteIntent(ChatActions.dismissIntent(context, tag, id, notification.deleteIntent));

        // Your own reply is not news, and neither is anything the user was already shown: without
        // this the phone and Android Auto announce it as a new message and offer to reply again.
        boolean silent = isReply || (plan != null && !plan.alert);
        if (silent) {
            builder.setOnlyAlertOnce(true);
        } else if (plan != null) {
            builder.setOnlyAlertOnce(false);
        }
        // Removing a sibling makes this a new record, which only alerting once does not cover.
        // Android mutes a group's children when only the summary should alert.
        ChatBundle.apply(builder, notification, !silent, SILENT_GROUP);

        boolean rendered = false;
        if (plan != null && !plan.clear) {
            try {
                Renderer.apply(builder, plan, extras, chat, threadId);
                rendered = true;
                // Only what was really rendered counts as displayed.
                displayed(key, plan);
            } catch (Throwable ex) {
                Logger.printException(() -> "Failed to render a chat from its plan", ex);
                NotificationLog.event("FAILED reducer render: " + android.util.Log.getStackTraceString(ex));
            }
        } else if (plan != null) {
            // Everything this post holds was read already: it is removed once Instagram has posted it.
            Renderer.logPlan(threadId, plan);
            Renderer.submit(key, plan);
            // Silent in every way the builder allows, as the user was not meant to hear this post.
            builder.setSound(null).setVibrate(null).setDefaults(0).setLights(0, 0, 0);
            NotificationLog.event("  read-only post silenced and removed");
        }
        if (!rendered) {
            // Conversations must use MessagingStyle; Instagram only does so behind a server flag.
            Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if ((messages == null || messages.length == 0) && chat != null) {
                builder.setStyle(messagingStyle(notification, chat, label, isGroup));
            }
        }
        // The digest's messages are not on the phone yet; onChatItem shows them once they are.
        // Only a bare count needs a fetch, as a digest with text carries its message itself.
        if (isDigest && !isReply && isPlaceholder(extras) && (plan == null || plan.moreLine > 0)) {
            ChatMessages.fetchThread(threadId);
        }

        // A group is posted under both its plain tag and a ;type:rr tag, which are the same chat.
        // The reducer holds the history, so the other record is simply removed.
        if (isGroup) removeSiblings(context, tag, id, key);

        if (notification.actions != null) {
            builder.setActions(ChatActions.withReplySemantics(notification.actions));
        }

        // The builder works on the notification it recovered, so the result is the same object.
        Notification conversation = builder.build();
        if (plan != null && plan.alert && !isReply) conversation.flags &= ~Notification.FLAG_ONLY_ALERT_ONCE;
        ChatActions.addMarkAsReadAction(context, conversation, tag, id);
        postedAt.put(threadId, System.currentTimeMillis());
        NotificationLog.notification("PIKO POSTED", tag, id, conversation);
        ChatBundle.schedule();
        // A new message or your reply is use of the chat, which ranks it in the share sheet. Android
        // takes usage from the shortcut being pushed again with message capabilities; without an
        // icon it cannot be pushed (it would lose its photo), so usage is only reported then.
        if (!silent || isReply) {
            try {
                if (icon == null || TextUtils.isEmpty(label)
                        || !ChatShortcuts.publish(context, shortcutId, threadId, label, icon, isGroup ? null : chat, true, isGroup)) {
                    context.getSystemService(ShortcutManager.class).reportShortcutUsed(shortcutId);
                }
            } catch (Throwable ignored) {
                // Ranking only.
            }
        }
        return conversation;
    }

    /**
     * Feeds Instagram's post to the reducer as events: a group digest as a digest, its other messages
     * as items (server time when the post has one, otherwise none), your own as viewer messages.
     * Returns the plan to show, or null when the reducer failed or took too long (fail open).
     */
    private static RenderPlan reduce(ChatKey key, String tag, Bundle extras, boolean isGroup, boolean isReply) {
        final long now = System.currentTimeMillis();
        final List<Message> items = new ArrayList<>();
        final String senderExtra = extras.getString(SENDER_ID_EXTRA);
        CharSequence chatTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        CharSequence self = selfName(extras);
        int placeholder = placeholderCount(extras);
        boolean digest = isGroup && !isReply && (tag.contains(DIGEST_TYPE) || placeholder > 0);

        Parcelable[] bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        int n = bundles == null ? 0 : bundles.length;
        // A digest's own message is the last one without a time.
        int digestAt = -1;
        for (int i = n - 1; digest && i >= 0 && digestAt < 0; i--) {
            if (bundles[i] instanceof Bundle && ((Bundle) bundles[i]).getLong("time") <= 0
                    && realText((Bundle) bundles[i], chatTitle) != null) digestAt = i;
        }
        String digestText = null;
        String digestName = null;
        for (int i = 0; i < n; i++) {
            if (!(bundles[i] instanceof Bundle)) continue;
            Bundle b = (Bundle) bundles[i];
            Person person = b.getParcelable("sender_person");
            // A digest names its sender "Alice and others"; the first name is the sender's.
            CharSequence name = ChatMessages.personalName(person != null ? person.getName() : b.getCharSequence("sender"));
            String text = realText(b, chatTitle);
            if (text == null) {
                if (digestName == null && !TextUtils.isEmpty(name)) digestName = name.toString();
                continue;
            }
            if (i == digestAt) {
                digestText = text;
                if (!TextUtils.isEmpty(name)) digestName = name.toString();
                continue;
            }
            String senderKey = person != null ? person.getKey() : null;
            boolean mine = (person == null && TextUtils.isEmpty(name)) || key.viewerId.equals(senderKey)
                    || ChatMessages.isViewer(senderKey) || (TextUtils.isEmpty(senderKey) && TextUtils.equals(name, self));
            String sender = mine ? key.viewerId : !TextUtils.isEmpty(senderKey) ? senderKey
                    : senderExtra != null && !isReply && (!isGroup || i == n - 1) ? senderExtra : NAME_ID_PREFIX + name;
            // The repost after your reply holds your phone's clock, not a server time.
            long time = isReply && mine && i == n - 1 ? 0 : Math.max(0, b.getLong("time"));
            if (mine) text = Message.stripOwnPrefix(text);
            items.add(new Message(null, sender, mine, text, time, now, false, Message.kindOfType("", text)));
        }
        if (n == 0) {
            CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            CharSequence shown = withoutChatPrefix(TextUtils.isEmpty(bigText)
                    ? extras.getCharSequence(Notification.EXTRA_TEXT) : bigText, lastSender(extras, null), chatTitle);
            if (!TextUtils.isEmpty(shown) && !NEW_MESSAGES.matcher(shown).matches()) {
                if (digest) {
                    digestText = shown.toString();
                } else {
                    String sender = isReply ? key.viewerId : senderExtra != null ? senderExtra
                            : NAME_ID_PREFIX + extras.getCharSequence(Notification.EXTRA_TITLE);
                    items.add(new Message(null, sender, isReply,
                            isReply ? Message.stripOwnPrefix(shown.toString()) : shown.toString(), 0, now));
                }
            }
        }
        if (digestName == null && digest) {
            CharSequence last = lastSender(extras, null);
            if (last != null) digestName = last.toString();
        }

        final int count = !digest ? 0 : placeholder > 0 ? placeholder : 1;
        final String text = digestText;
        final String name = digestName;
        ChatExecutor executor = executor();
        RenderPlan plan = executor.call(reducer -> {
            return reducer.onPost(key, items, count, text, senderExtra, name, now, text == null);
        }, REDUCER_WAIT_MS);
        if (plan == null) {
            NotificationLog.event("  FAILED reducer " + (executor.isDisabled() ? "disabled after repeated failures"
                    : "timeout or error: " + executor.lastFailure()) + ", keeping Instagram's content");
            return null;
        }
        // Instagram's post carries everything the chat holds, so an update still waiting is stale.
        Renderer.cancelPending(key);
        return plan;
    }

    /** A message's text without the digest wording, or null for an empty one or a "N new messages" count. */
    private static String realText(Bundle message, CharSequence chatTitle) {
        Person person = message.getParcelable("sender_person");
        CharSequence name = person != null ? person.getName() : message.getCharSequence("sender");
        CharSequence text = withoutChatPrefix(message.getCharSequence("text"), name, chatTitle);
        return TextUtils.isEmpty(text) || NEW_MESSAGES.matcher(text).matches() ? null : text.toString();
    }

    /** What "N new messages" counts, 0 when the post is not such a count. */
    private static int placeholderCount(Bundle extras) {
        List<CharSequence> texts = new ArrayList<>();
        texts.add(extras.getCharSequence(Notification.EXTRA_TEXT));
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null) {
            for (Parcelable message : messages) {
                if (message instanceof Bundle) texts.add(((Bundle) message).getCharSequence("text"));
            }
        }
        for (CharSequence text : texts) {
            Matcher count = text == null ? null : NEW_MESSAGES.matcher(text);
            if (count != null && count.matches()) return Integer.parseInt(count.group(1));
        }
        return 0;
    }

    /** Instagram posts a group chat under two tags; the one it did not just post is removed. */
    @TargetApi(Build.VERSION_CODES.R)
    private static void removeSiblings(Context context, String tag, int id, ChatKey key) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        for (StatusBarNotification posted : manager.getActiveNotifications()) {
            if (posted.getId() != id || tag.equals(posted.getTag()) || !key.equals(keyOf(posted.getTag()))) continue;
            Notification sibling = posted.getNotification();
            if (!Notification.CATEGORY_MESSAGE.equals(sibling.category)) continue;
            if ((sibling.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
            NotificationLog.event("  DEDUPE keeping " + tag + ", removing " + posted.getTag());
            remove(manager, posted.getTag(), id, "dedupe");
        }
    }

    /** Whether the notification only counts messages ("3 new messages") instead of showing them. */
    private static boolean isPlaceholder(Bundle extras) {
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (text != null && NEW_MESSAGES.matcher(text).matches()) return true;
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages == null) return false;
        for (Parcelable message : messages) {
            if (!(message instanceof Bundle)) continue;
            CharSequence shown = ((Bundle) message).getCharSequence("text");
            if (shown != null && NEW_MESSAGES.matcher(shown).matches()) return true;
        }
        return false;
    }

    /** The chat a notification tag, "<category>|<viewer>;thread_id:T", belongs to; null without a thread id. */
    static ChatKey keyOf(String tag) {
        String threadId = threadId(tag);
        if (threadId == null) return null;
        int user = tag.indexOf('|');
        int end = tag.indexOf(';');
        return new ChatKey(user >= 0 && end > user ? tag.substring(user + 1, end) : "", threadId);
    }

    /** The chat for an event that names a thread and maybe the user: realtime items and message events. */
    private static ChatKey keyFor(String threadId, String viewerId) {
        if (TextUtils.isEmpty(threadId)) return null;
        String viewer = !TextUtils.isEmpty(viewerId) ? viewerId : threadViewers.get(threadId);
        if (viewer == null) viewer = ChatMessages.soleViewer();
        return viewer == null ? null : new ChatKey(viewer, threadId);
    }

    private static synchronized ChatExecutor executor() {
        if (chatExecutor == null) chatExecutor = new ChatExecutor(new ChatReducer(System::currentTimeMillis, new SqliteChatStore()), System::currentTimeMillis);
        return chatExecutor;
    }

    /** Runs a reducer event on its owner thread (R2); the plan it returns is rendered, debounced. */
    private static void event(ChatKey key, Function<ChatReducer, RenderPlan> change) {
        executor().post(reducer -> {
            RenderPlan plan = change.apply(reducer);
            if (plan != null) Renderer.submit(key, plan);
            return null;
        });
    }

    // ---- What the reducer holds, for read markers and actions. Blocking: worker threads only. ----

    private static final long QUERY_TIMEOUT_MS = 2000;

    static long itemTime(String threadId, String itemId) {
        ChatKey key = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ? null : keyFor(threadId, null);
        if (key == null || itemId == null) return 0;
        Long time = executor().call(reducer -> reducer.itemTime(key, itemId), QUERY_TIMEOUT_MS);
        return time == null ? 0 : time;
    }

    static Message latestFromOthers(String threadId) {
        ChatKey key = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ? null : keyFor(threadId, null);
        if (key == null) return null;
        return executor().call(reducer -> reducer.latestFromOthers(key), QUERY_TIMEOUT_MS);
    }

    /**
     * Hands the newest message others sent to {@code use}, from the reducer's own thread. Queued
     * like any event, so it sees the chat before a clear queued after it (mark as read relies on it).
     */
    static void withLatestFromOthers(String threadId, java.util.function.Consumer<Message> use) {
        ChatKey key = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ? null : keyFor(threadId, null);
        if (key == null) {
            use.accept(null);
            return;
        }
        executor().post(reducer -> {
            use.accept(reducer.latestFromOthers(key));
            return null;
        });
    }

    /** The chat holding an item, for an unsend that does not name its chat. */
    static String chatOfItem(String itemId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || TextUtils.isEmpty(itemId)) return null;
        String viewer = ChatMessages.soleViewer();
        ChatKey key = executor().call(reducer -> reducer.chatOfItem(viewer, itemId), QUERY_TIMEOUT_MS);
        return key == null ? null : key.threadId;
    }

    /** An account logged out: its chats are forgotten, and shortcuts are published afresh later. */
    static void forgetViewer(String viewerId) {
        if (viewerId == null) return;
        threadViewers.values().removeIf(viewerId::equals);
        ChatShortcuts.forgetPublished();
        executor().post(reducer -> {
            reducer.forgetViewer(viewerId);
            return null;
        });
    }

    /** The renderer showed this plan: what it held counts as displayed. */
    static void displayed(ChatKey key, RenderPlan plan) {
        executor().post(reducer -> {
            reducer.onDisplayed(key, plan);
            return null;
        });
    }

    /** The chat's records are gone or read: everything held is read. */
    private static void clearChat(ChatKey key, boolean dismissed) {
        Renderer.cancelPending(key);
        executor().post(reducer -> {
            if (dismissed) reducer.onDismissed(key);
            else reducer.onCleared(key);
            Renderer.cancelPending(key);
            return null;
        });
    }

    /**
     * Called instead of NotificationManager.cancel when Instagram clears a chat's notification,
     * which it does as soon as the chat is opened. Cancelling would also close the chat's bubble,
     * so a bubbled chat only has its notification hidden from the shade.
     */
    public static void cancel(NotificationManager manager, String tag, int id) {
        try {
            cancelChat(manager, tag, id);
        } catch (Throwable ex) {
            // Never break Instagram's own cancel: fall back to what it asked for.
            Logger.printException(() -> "Failed to clear a chat notification", ex);
            manager.cancel(tag, id);
        }
    }

    private static void cancelChat(NotificationManager manager, String tag, int id) {
        ChatKey key = keyOf(tag);
        if (key != null) {
            Long dismissed = dismissedAt.get(key.threadId);
            Long posted = postedAt.get(key.threadId);
            long now = System.currentTimeMillis();
            if (dismissed != null && posted != null && posted > dismissed && now - dismissed < LATE_CLEANUP_MS) {
                NotificationLog.event("CANCEL tag=" + tag + " cause=instagram ignored: late swipe cleanup, a newer "
                        + "message was posted " + (now - posted) + " ms ago");
                return;
            }
        }
        remove(manager, tag, id, "instagram");
        // The chat is read once its last record goes; Instagram clears the plain and rr tags one by one.
        if (key != null && !hasOtherRecord(manager, tag, key)) clearChat(key, false);
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static boolean hasOtherRecord(NotificationManager manager, String tag, ChatKey key) {
        for (StatusBarNotification posted : manager.getActiveNotifications()) {
            if (!tag.equals(posted.getTag()) && key.equals(keyOf(posted.getTag()))) return true;
        }
        return false;
    }

    /** Called when Android Auto marks a chat as read: every record of the chat goes. */
    static void markAsRead(Context context, String tag, int id) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        ChatKey key = keyOf(tag);
        if (key != null) {
            ChatMessages.markSeen(key.threadId);
            for (StatusBarNotification posted : manager.getActiveNotifications()) {
                if (key.equals(keyOf(posted.getTag()))) remove(manager, posted.getTag(), posted.getId(), "markread");
            }
        }
        remove(manager, tag, id, "markread");
        if (key != null) clearChat(key, false);
    }

    /** A swipe on a notification. */
    static void onDismissed(String tag, int id) {
        NotificationLog.event("DISMISSED tag=" + tag);
        ChatKey key = keyOf(tag);
        if (key == null) return;
        dismissedAt.put(key.threadId, System.currentTimeMillis());
        // The user saw what the chat holds and dismissed it, so it is not new again.
        clearChat(key, true);
    }

    /**
     * The chat was read on another device, up to a server time. Its records go, without telling
     * the server anything, unless a bubble shows the chat. {@code all}: the read covers every
     * message this phone has, otherwise newer ones keep their records.
     */
    static void onReadElsewhere(String threadId, long upTo, boolean all) {
        ChatKey key = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ? null : keyFor(threadId, null);
        if (key == null) return;
        event(key, reducer -> all ? reducer.onCleared(key) : reducer.onReadElsewhere(key, upTo));
    }

    /** A message was unsent: it leaves the record silently, or the record goes when it was the last. */
    static void onUnsent(String threadId, String serverId, String clientId) {
        ChatKey key = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ? null : keyFor(threadId, null);
        if (key == null) return;
        event(key, reducer -> {
            RenderPlan plan = reducer.onUnsend(key, serverId);
            RenderPlan byClient = reducer.onUnsend(key, clientId);
            return byClient != null ? byClient : plan;
        });
    }

    /** A message's image is cached: the chat is drawn again with it, silently, if it is showing. */
    static void onImageReady(String threadId, String viewerId) {
        ChatKey key = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ? null : keyFor(threadId, viewerId);
        if (key == null) return;
        event(key, reducer -> {
            RenderPlan plan = reducer.peek(key);
            return plan == null ? null : plan.withAlert(false);
        });
    }

    /** A message was edited: its text is replaced in place, silently. */
    static void onEdited(String threadId, String itemId, String text) {
        ChatKey key = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ? null : keyFor(threadId, null);
        if (key == null) return;
        event(key, reducer -> reducer.onEdit(key, itemId, text));
    }

    /**
     * A message arrived from realtime or REST. Your own messages only matter to a chat that has a record.
     */
    static void onChatItem(String threadId, String viewerId, Message message) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        try {
                ChatKey key = keyFor(threadId, viewerId);
            if (key == null) {
                NotificationLog.event("  item in " + threadId + " not applied: viewer unknown");
                return;
            }
            if (message.senderIsViewer && !hasRecord(key)) return;
            event(key, reducer -> reducer.onItem(key, message));
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to apply a chat message", ex);
        }
    }

    /** Whether the chat has a notification record showing. */
    static boolean isShowing(String threadId, String viewerId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        try {
            ChatKey key = keyFor(threadId, viewerId);
            return key != null && hasRecord(key);
        } catch (Throwable ex) {
            return false;
        }
    }

    private static boolean hasRecord(ChatKey key) {
        NotificationManager manager = Utils.getContext().getSystemService(NotificationManager.class);
        for (StatusBarNotification posted : manager.getActiveNotifications()) {
            if (key.equals(keyOf(posted.getTag()))) return true;
        }
        return false;
    }

    /** {@code cause}: instagram (its own cancel), dedupe, markread, or ours (this patch decided). */
    static void remove(NotificationManager manager, String tag, int id, String cause) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (hideBubbledNotification(manager, tag, id)) {
                    NotificationLog.event("CANCEL tag=" + tag + " cause=" + cause + " caller=" + NotificationLog.callers()
                            + " hidden=bubble");
                    return;
                }
            } catch (Throwable ex) {
                Logger.printException(() -> "Failed to keep the bubble of a chat", ex);
            }
        }
        NotificationLog.event("CANCEL tag=" + tag + " cause=" + cause + " caller=" + NotificationLog.callers());
        manager.cancel(tag, id);
        ChatBundle.schedule();
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static boolean hideBubbledNotification(NotificationManager manager, String tag, int id) {
        for (StatusBarNotification posted : manager.getActiveNotifications()) {
            if (posted.getId() != id || !TextUtils.equals(posted.getTag(), tag)) continue;

            Notification notification = posted.getNotification();
            String shortcutId = notification.getShortcutId();
            if ((notification.flags & Notification.FLAG_BUBBLE) == 0 || shortcutId == null) return false;

            Notification hidden = Notification.Builder.recoverBuilder(Utils.getContext(), notification)
                    .setBubbleMetadata(bubble(shortcutId, true))
                    .setOnlyAlertOnce(true)
                    // A tap on the hidden record must not cancel the bubble with it.
                    .setAutoCancel(false)
                    .build();
            manager.notify(tag, id, hidden);
            return true;
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static Notification.BubbleMetadata bubble(String shortcutId, boolean suppressNotification) {
        return new Notification.BubbleMetadata.Builder(shortcutId)
                .setDesiredHeight(BUBBLE_HEIGHT_DP)
                .setSuppressNotification(suppressNotification)
                .build();
    }

    private static CharSequence selfName(Bundle extras) {
        Person self = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON);
        if (self != null && !TextUtils.isEmpty(self.getName())) return self.getName();
        return extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME);
    }

    /** Name of whoever sent the latest message, skipping messages sent under the name {@code skip}. */
    private static CharSequence lastSender(Bundle extras, CharSequence skip) {
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages == null) return null;
        for (int i = messages.length - 1; i >= 0; i--) {
            if (!(messages[i] instanceof Bundle)) continue;
            Bundle message = (Bundle) messages[i];
            Person person = message.getParcelable("sender_person");
            CharSequence sender = person != null ? person.getName() : message.getCharSequence("sender");
            if (TextUtils.isEmpty(sender)) continue;
            if (skip == null || !TextUtils.equals(sender, skip)) return sender;
        }
        return null;
    }

    /**
     * Learns who is who from a notification: its tag and recipient name the signed in user, its
     * messages name their senders, and its latest message is from the user it names as sender.
     */
    private static void rememberPeople(String tag, Bundle extras) {
        ChatMessages.rememberViewer(extras.getString(RECIPIENT_ID_EXTRA));
        int user = tag.indexOf('|');
        int end = tag.indexOf(';');
        if (user >= 0 && end > user) ChatMessages.rememberViewer(tag.substring(user + 1, end));

        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages == null) return;
        for (int i = 0; i < messages.length; i++) {
            if (!(messages[i] instanceof Bundle)) continue;
            Person sender = ((Bundle) messages[i]).getParcelable("sender_person");
            if (sender == null) continue;
            String key = sender.getKey();
            if (!TextUtils.isEmpty(key)) {
                ChatMessages.rememberPerson(threadId(tag), key, sender);
            } else if (i == messages.length - 1 && !REPLY_CATEGORY.equals(extras.getString(PUSH_CATEGORY_EXTRA))) {
                ChatMessages.rememberPerson(threadId(tag), extras.getString(SENDER_ID_EXTRA), sender);
            }
        }
    }

    /**
     * Group messages come from the server as "Sent a message: <text>" or "<sender> to <chat>:
     * <text>"; only the text is kept, otherwise the same message shows again once it is received.
     */
    private static void removeSentMessagePrefix(Bundle extras) {
        CharSequence chat = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null) {
            for (Parcelable message : messages) {
                if (!(message instanceof Bundle)) continue;
                Bundle bundle = (Bundle) message;
                Person sender = bundle.getParcelable("sender_person");
                bundle.putCharSequence("text", withoutChatPrefix(bundle.getCharSequence("text"),
                        sender != null ? sender.getName() : null, chat));
            }
        }
        extras.putCharSequence(Notification.EXTRA_TEXT, withoutChatPrefix(
                extras.getCharSequence(Notification.EXTRA_TEXT), lastSender(extras, null), chat));
    }

    private static CharSequence withoutChatPrefix(CharSequence message, CharSequence sender, CharSequence chat) {
        CharSequence text = withoutSentMessagePrefix(message);
        if (text == null || sender == null) return text;
        String prefix = sender + " to " + chat + ": ";
        return text.toString().startsWith(prefix) ? text.subSequence(prefix.length(), text.length()) : text;
    }

    /**
     * Instagram names you as a regular sender. A MessagingStyle message without a sender is the
     * user's own, which is how Android and Android Auto tell a reply from a received message.
     */
    private static void markOwnMessages(Bundle extras) {
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages == null) return;
        for (Parcelable message : messages) {
            if (!(message instanceof Bundle)) continue;
            Bundle bundle = (Bundle) message;
            Person sender = bundle.getParcelable("sender_person");
            if (sender == null || !ChatMessages.isViewer(sender.getKey())) continue;
            bundle.remove("sender_person");
            bundle.remove("sender");
        }
    }

    private static CharSequence withoutSentMessagePrefix(CharSequence text) {
        if (text == null || !text.toString().startsWith(SENT_MESSAGE_PREFIX)) return text;
        return text.subSequence(SENT_MESSAGE_PREFIX.length(), text.length());
    }

    private static String threadId(String tag) {
        if (tag == null) return null;
        int start = tag.indexOf(THREAD_ID_KEY);
        if (start < 0) return null;
        start += THREAD_ID_KEY.length();

        int end = start;
        while (end < tag.length() && tag.charAt(end) != ';' && tag.charAt(end) != '|') end++;
        return end > start ? tag.substring(start, end) : null;
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static Notification.MessagingStyle messagingStyle(Notification notification, Person chat,
                                                              CharSequence label, boolean isGroup) {
        Bundle extras = notification.extras;
        CharSequence text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (TextUtils.isEmpty(text)) text = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (text == null) text = "";

        Notification.MessagingStyle style = new Notification.MessagingStyle(Renderer.selfPerson(notification.extras))
                .setGroupConversation(isGroup)
                .addMessage(text, notification.when, chat);
        if (isGroup) style.setConversationTitle(label);
        return style;
    }

}
