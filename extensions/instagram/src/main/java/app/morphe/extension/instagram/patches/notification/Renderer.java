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
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import app.morphe.extension.instagram.patches.notification.state.ChatKey;
import app.morphe.extension.instagram.patches.notification.state.Message;
import app.morphe.extension.instagram.patches.notification.state.RenderPlan;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Turns a {@link RenderPlan} into what the user sees: the only place a MessagingStyle is built, and
 * the only place that posts content that did not come from Instagram's own post. Updates outside
 * {@code onNotify} (late items, unsend, edit, read elsewhere) are coalesced per chat (R7) and the
 * final plan is posted on the existing record's tag.
 */
final class Renderer {
    private static final String SELF_KEY = "piko_self";
    private static final long DEBOUNCE_MS = 1000;
    // Android keeps at most 25 messages in a MessagingStyle.
    private static final int MAX_LINES = 25;

    private static final Map<ChatKey, RenderPlan> pending = new HashMap<>();
    private static ScheduledExecutorService scheduler;

    private Renderer() {
    }

    // ---- Building ----------------------------------------------------------------------------

    /**
     * Sets the plan's style and content text on a builder. {@code extras} are the notification's own
     * (self, group flag, title); {@code chat} is the other person of a one to one chat, if known.
     */
    @TargetApi(Build.VERSION_CODES.R)
    static void apply(Notification.Builder builder, RenderPlan plan, Bundle extras, Person chat, String threadId) {
        logPlan(threadId, plan);
        boolean group = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION);
        Notification.MessagingStyle style = new Notification.MessagingStyle(selfPerson(extras))
                .setGroupConversation(group);
        CharSequence title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        if (group && !TextUtils.isEmpty(title)) style.setConversationTitle(title);
        if (!group && chat == null) chat = firstSender(extras);

        List<Message> messages = plan.messages;
        int more = plan.moreLine;
        int from = Math.max(0, messages.size() - (more > 0 ? MAX_LINES - 1 : MAX_LINES));
        long last = 0;
        for (int i = from; i < messages.size(); i++) {
            Message m = messages.get(i);
            Notification.MessagingStyle.Message line =
                    new Notification.MessagingStyle.Message(m.text, m.displayTime(), sender(m, group, chat, threadId));
            // The text stays as the fallback, which is also what Android Auto reads.
            android.net.Uri image = m.kind == Message.Kind.MEDIA ? NotificationImages.uriFor(m.itemId) : null;
            if (image != null) line.setData(NotificationImages.mimeFor(m.itemId), image);
            style.addMessage(line);
            last = m.displayTime();
        }
        CharSequence content = messages.isEmpty() ? "" : messages.get(messages.size() - 1).text;
        if (more > 0) {
            String line = Texts.moreMessages(more);
            String name = plan.moreSenderName;
            Person who = new Person.Builder().setName(TextUtils.isEmpty(name) ? Texts.someone() : name).build();
            style.addMessage(line, last > 0 ? last + 1 : System.currentTimeMillis(), who);
            content = line;
        }
        builder.setStyle(style).setContentText(content);
    }

    /** Your own messages carry no sender, which is how Android and Android Auto tell a reply from news. */
    private static Person sender(Message m, boolean group, Person chat, String threadId) {
        if (m.senderIsViewer || m.senderId == null) return null;
        // A one to one chat uses the chat's name and photo, but the sender keeps their own id as key.
        if (!group && chat != null) {
            return m.senderId.startsWith(ConversationNotifications.NAME_ID_PREFIX) ? chat
                    : chat.toBuilder().setKey(m.senderId).build();
        }
        Person known = ChatMessages.person(threadId, m.senderId);
        if (known != null) return withPhoto(known, m.senderId, threadId);
        // A message known only by a name in a notification keeps that name; anything else is "Someone".
        boolean named = m.senderId.startsWith(ConversationNotifications.NAME_ID_PREFIX);
        return new Person.Builder().setKey(m.senderId)
                .setName(named ? m.senderId.substring(ConversationNotifications.NAME_ID_PREFIX.length()) : Texts.someone())
                .build();
    }

    /** A sender without a photo gets the saved one, or one is fetched and the chat drawn again. */
    private static Person withPhoto(Person person, String userId, String threadId) {
        if (person.getIcon() != null || userId.startsWith(ConversationNotifications.NAME_ID_PREFIX)) return person;
        android.graphics.drawable.Icon saved = Avatars.icon(userId);
        if (saved != null) return person.toBuilder().setIcon(saved).build();
        ChatMessages.requestPhoto(threadId, userId);
        return person;
    }

    /**
     * The person your own messages are shown as: Instagram's, with your name and photo, so a
     * rebuilt notification looks like Instagram's.
     */
    static Person selfPerson(Bundle extras) {
        Person self = extras == null ? null : extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON);
        if (self != null) return self;
        CharSequence name = extras == null ? null : extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME);
        return new Person.Builder().setKey(SELF_KEY).setName(TextUtils.isEmpty(name) ? Texts.you() : name).build();
    }

    /** The first sender a notification shows, to name the other side of a one to one chat. */
    private static Person firstSender(Bundle extras) {
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages == null) return null;
        for (Parcelable message : messages) {
            if (!(message instanceof Bundle)) continue;
            Person person = ((Bundle) message).getParcelable("sender_person");
            if (person != null) return person;
        }
        return null;
    }

    static void logPlan(String threadId, RenderPlan plan) {
        NotificationLog.event("PLAN chat=" + threadId + " msgs=" + plan.messages.size() + " more=" + plan.moreLine
                + " alert=" + plan.alert + " clear=" + plan.clear);
    }

    // ---- Updates outside Instagram's post ----------------------------------------------------

    /** Queues a plan for a chat; the last one within the debounce window is the one rendered. */
    static void submit(ChatKey key, RenderPlan plan) {
        boolean first;
        synchronized (pending) {
            RenderPlan old = pending.get(key);
            first = old == null;
            // An alert is not lost to a later silent update, but a clear ends everything.
            boolean alert = old != null && !old.clear && old.alert;
            pending.put(key, alert && !plan.clear ? plan.withAlert(true) : plan);
        }
        if (first) scheduler().schedule(() -> run(key), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    /** Drops a queued update, as Instagram's own post or a clear supersedes it. */
    static void cancelPending(ChatKey key) {
        synchronized (pending) {
            pending.remove(key);
        }
    }

    private static void run(ChatKey key) {
        RenderPlan plan;
        synchronized (pending) {
            plan = pending.remove(key);
        }
        if (plan == null) return;
        try {
            render(key, plan);
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to render a chat update", ex);
            NotificationLog.event("FAILED to render a chat update: " + android.util.Log.getStackTraceString(ex));
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static void render(ChatKey key, RenderPlan plan) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        Context context = Utils.getContext();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        StatusBarNotification record = null;
        for (StatusBarNotification posted : manager.getActiveNotifications()) {
            if (!key.equals(ConversationNotifications.keyOf(posted.getTag()))) continue;
            Notification n = posted.getNotification();
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0 || !Notification.CATEGORY_MESSAGE.equals(n.category)) continue;
            if (plan.clear || record != null) {
                // A clear removes every record of the chat; a second record is a leftover sibling.
                ConversationNotifications.remove(manager, posted.getTag(), posted.getId(), plan.clear ? "ours" : "dedupe");
            } else {
                record = posted;
            }
        }
        if (plan.clear) {
            logPlan(key.threadId, plan);
            ConversationNotifications.displayed(key, plan);
            return;
        }
        if (record == null) {
            NotificationLog.event("PLAN chat=" + key.threadId + " not rendered: no record showing");
            return;
        }

        Notification posted = record.getNotification();
        Notification.Builder builder = Notification.Builder.recoverBuilder(context, posted);
        apply(builder, plan, posted.extras, null, key.threadId);
        builder.setOnlyAlertOnce(!plan.alert);
        ChatBundle.applyUpdate(builder, plan.alert);
        Notification updated = builder.build();
        if (plan.alert) updated.flags &= ~Notification.FLAG_ONLY_ALERT_ONCE;
        NotificationLog.notification("PIKO UPDATED", record.getTag(), record.getId(), updated);
        manager.notify(record.getTag(), record.getId(), updated);
        ChatBundle.schedule();
        ConversationNotifications.displayed(key, plan);
    }

    private static synchronized ScheduledExecutorService scheduler() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "piko-chat-render");
                thread.setDaemon(true);
                return thread;
            });
        }
        return scheduler;
    }
}
