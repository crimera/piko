/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import android.app.Person;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;

import com.instagram.common.session.UserSession;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.instagram.db.PikoMessageDb;
import app.morphe.extension.instagram.entity.DirectItem;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Remembers the latest messages other people sent to each chat, as Instagram receives them.
 * Instagram's server only sends a count ("3 new messages") in the notifications of group chats,
 * so these are what the notification shows instead.
 */
@SuppressWarnings("unused")
public final class ChatMessages {
    // Item id -> text of items already passed on (others' only), so repeats are dropped and edits
    // are seen. Bounded: the chat state itself lives in the reducer.
    private static final int SEEN_ITEMS = 2000;
    private static final Map<String, String> seenItems = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, String>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > SEEN_ITEMS;
                }
            });
    // User id -> how they appear in notifications.
    private static final Map<String, Person> people = new ConcurrentHashMap<>();
    // User ids already looked up in the persistent directory, so a miss is not retried.
    private static final Set<String> vaultLookups = ConcurrentHashMap.newKeySet();

    // Ids of the signed in users, whose own messages are not shown as received.
    private static final Set<String> viewers = ConcurrentHashMap.newKeySet();

    // A chat is fetched at most this often, however many digests arrive for it.
    private static final long FETCH_INTERVAL_MS = 30_000;
    private static final long IMAGE_RECENT_MS = 30 * 60_000L;
    private static final String FETCH_REASON = "PREPARE_FOR_PUSH_NOTIFICATION";
    private static final Map<String, Long> fetches = new HashMap<>();
    private static volatile UserSession session;

    private static Handler worker;

    /** REST/JSON path. */
    public static void onMessageReceived(Object item) {
        enqueue(item, null, null);
    }

    /** MQTT path. Parameters match the contiguous p0..p2 registers of the hooked method. */
    public static void onMessageReceived(Object item, UserSession session, Object delta) {
        String viewerId = null;
        try {
            if (session != null) {
                rememberSession(session);
                viewerId = session.getUserId();
                rememberViewer(viewerId);
            }
        } catch (Throwable ignored) {
            // Messages of an unknown viewer are still told apart by their notifications.
        }
        enqueue(item, threadIdHint(delta), viewerId);
    }

    /** Called whenever Instagram gets its sync manager for a session. */
    public static void rememberSession(UserSession userSession) {
        session = userSession;
        SessionCleanup.register(userSession);
    }

    /**
     * Asks Instagram for a chat's latest messages. A group digest only carries a count, and while
     * Instagram is not connected nothing else delivers them. The reply passes through the JSON
     * parser hooked by {@link #onMessageReceived(Object)}, which updates the notification.
     */
    static void fetchThread(String threadId) {
        UserSession user = session;
        if (user == null) {
            NotificationLog.event("  FETCH skipped for " + threadId + ": no session yet");
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (fetches) {
            Long last = fetches.get(threadId);
            if (last != null && now - last < FETCH_INTERVAL_MS) return;
            fetches.put(threadId, now);
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Class<?> manager = Class.forName(syncManagerClass());
                Method getter = null;
                Method snapshot = null;
                for (Method method : manager.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (parameters.length == 1 && parameters[0] == UserSession.class && method.getReturnType() == manager) {
                        getter = method;
                    } else if (parameters.length == 5 && method.getName().equals(threadSnapshotMethod())) {
                        snapshot = method;
                    }
                }
                Object reason = null;
                for (Object constant : snapshot.getParameterTypes()[0].getEnumConstants()) {
                    if (FETCH_REASON.equals(((Enum<?>) constant).name())) reason = constant;
                }
                snapshot.invoke(getter.invoke(null, user), reason, null,
                        null, threadId, true);
                NotificationLog.event("FETCH requested for " + threadId);
            } catch (Throwable ex) {
                Logger.printException(() -> "Failed to fetch a chat", ex);
                NotificationLog.event("FAILED to fetch a chat: " + android.util.Log.getStackTraceString(ex));
            }
        });
    }

    /** Tells Instagram a chat was read up to its latest message, as opening the chat does. */
    static void markSeen(String threadId) {
        // Ghost mode wins: mark as read from a notification or Android Auto only clears the notification.
        if (app.morphe.extension.instagram.utils.Pref.viewDmAnonymously()) {
            NotificationLog.event("SEEN skipped for " + threadId + ": ghost mode");
            return;
        }
        UserSession user = session;
        if (user == null) {
            NotificationLog.event("SEEN skipped for " + threadId + ": no session yet");
            return;
        }
        // Looked up in the reducer's queue, ahead of the clear that marking as read queues next.
        ConversationNotifications.withLatestFromOthers(threadId, latest -> sendSeen(user, threadId, latest));
    }

    private static void sendSeen(UserSession user, String threadId,
                                 app.morphe.extension.instagram.patches.notification.state.Message latest) {
        String itemId = latest == null ? null : latest.itemId;
        // Instagram checks this is not null; it only reaches a null check, never the server.
        String senderId = latest != null && latest.senderId != null ? latest.senderId : user.getUserId();
        if (itemId == null) {
            NotificationLog.event("SEEN skipped for " + threadId + ": no message known");
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                for (Method method : Class.forName(markSeenClass()).getDeclaredMethods()) {
                    if (method.getName().equals(markSeenMethod()) && method.getParameterTypes().length == 5) {
                        // Ghost mode is off here (checked above), so Instagram sends the marker as usual.
                        method.invoke(null, user, null, threadId, itemId, senderId);
                        NotificationLog.event("SEEN sent for " + threadId + " up to " + itemId);
                        return;
                    }
                }
                NotificationLog.event("FAILED to mark a chat as seen: method not found");
            } catch (Throwable ex) {
                Logger.printException(() -> "Failed to mark a chat as seen", ex);
                NotificationLog.event("FAILED to mark a chat as seen: " + android.util.Log.getStackTraceString(ex));
            }
        });
    }

    /** Server time of the newest message others sent to a chat that the reducer holds; 0 when none. */
    private static long newestTimestamp(String threadId) {
        app.morphe.extension.instagram.patches.notification.state.Message latest =
                ConversationNotifications.latestFromOthers(threadId);
        return latest == null ? 0 : latest.serverTime;
    }

    static void rememberViewer(String userId) {
        if (!TextUtils.isEmpty(userId)) viewers.add(userId);
    }

    /** The signed in user when exactly one is known, else null. */
    static String soleViewer() {
        return viewers.size() == 1 ? viewers.iterator().next() : null;
    }

    static boolean isViewer(String userId) {
        return userId != null && viewers.contains(userId);
    }

    /** The participants Instagram parsed for a chat, used to name the senders of messages. */
    public static void onThreadUsers(List<?> users) {
        if (users == null || users.isEmpty()) return;
        List<Object> copy = new ArrayList<>(users);
        worker().post(() -> {
            int before = people.size();
            int prefetched = 0;
            for (Object user : copy) {
                try {
                    UserData data = new UserData(user);
                    String id = data.getUserId();
                    String name = displayName(data);
                    // Big groups: the first members only; the rest get theirs when they write.
                    if (prefetched++ < MAX_PREFETCH) prefetchPhoto(id, data);
                    Person known = TextUtils.isEmpty(id) ? null : people.get(id);
                    if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(name)
                            && (known == null || isSummaryName(known.getName()))) {
                        people.put(id, new Person.Builder().setKey(id).setName(name).build());
                        saveUsername(id, name);
                    }
                } catch (Throwable ignored) {
                    // A participant without readable details keeps showing by id.
                }
            }
            NotificationLog.event("PARTICIPANTS " + copy.size() + " parsed, " + (people.size() - before) + " new name(s)");
        });
    }

    private static final java.util.regex.Pattern SUMMARY_NAME =
            java.util.regex.Pattern.compile("^(.+) and (\\d+ )?others?$");

    /**
     * A digest names its sender "Alex and others": the sender id is Alex's, so Alex is what is
     * kept. Also repairs names saved from digests before this was handled.
     */
    static CharSequence personalName(CharSequence name) {
        if (name == null) return null;
        java.util.regex.Matcher summary = SUMMARY_NAME.matcher(name);
        return summary.matches() ? summary.group(1) : name;
    }

    static boolean isSummaryName(CharSequence name) {
        return name != null && SUMMARY_NAME.matcher(name).matches();
    }

    // Thread id + "|" + user id -> how that chat's notifications name the user (a group nickname, say).
    private static final Map<String, Person> chatNames = new ConcurrentHashMap<>();

    /**
     * A chat's notification names its sender as that chat does: a group can give someone a
     * nickname ("Ally"), so the name is kept for that chat only and never saved as the user's name.
     * Its avatar is the user's in every chat, so it also completes the global entry.
     */
    static void rememberPerson(String threadId, String userId, Person person) {
        if (TextUtils.isEmpty(userId) || person == null || TextUtils.isEmpty(person.getName())) return;
        if (isSummaryName(person.getName())) person = person.toBuilder().setName(personalName(person.getName())).build();
        Person named = person.toBuilder().setKey(userId).build();
        if (!TextUtils.isEmpty(threadId)) chatNames.put(threadId + "|" + userId, named);
        if (named.getIcon() != null) Avatars.save(userId, named.getIcon());
        Person known = people.get(userId);
        if (known != null && known.getIcon() == null && named.getIcon() != null) {
            people.put(userId, known.toBuilder().setIcon(named.getIcon()).build());
        }
    }

    /** How a sender appears in one chat: that chat's own name for them, else their global name. */
    static Person person(String threadId, String userId) {
        if (!TextUtils.isEmpty(threadId) && !TextUtils.isEmpty(userId)) {
            Person inChat = chatNames.get(threadId + "|" + userId);
            if (inChat != null) return inChat;
        }
        return person(userId);
    }

    /**
     * How a sender appears in notifications. Names learned during this run are used first; an
     * unknown one is restored from the persistent directory kept for saved messages, so group
     * digests do not fall back to "Someone" after the app process restarts.
     */
    static Person person(String userId) {
        if (TextUtils.isEmpty(userId)) return null;
        Person known = people.get(userId);
        if (known != null) return known;
        Person cached = fromUserCache(userId);
        if (cached != null) {
            known = people.putIfAbsent(userId, cached);
            if (known != null) return known;
            NotificationLog.event("NAME from UserCache for " + userId);
            rememberUsername(userId, cached.getName().toString());
            return cached;
        }
        if (!vaultLookups.add(userId)) return null;
        try {
            String name = PikoMessageDb.getInstance(Utils.getContext()).getUsername(userId);
            if (TextUtils.isEmpty(name)) return null;
            name = personalName(name).toString();
            Person found = new Person.Builder().setKey(userId).setName(name).build();
            known = people.putIfAbsent(userId, found);
            return known != null ? known : found;
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to read a sender name from the message vault", ex);
            return null;
        }
    }

    /**
     * The user as Instagram itself knows them: its UserCache holds everyone it has parsed in this
     * process (inbox, chats, realtime), which is why stock Instagram never says "Someone". Null
     * when there is no session yet, the id is not numeric, or the user was not seen.
     */
    private static Person fromUserCache(String userId) {
        try {
            Object found = userCacheUser(userId);
            if (found == null) return null;
            String name = displayName(new UserData(found));
            return TextUtils.isEmpty(name) ? null : new Person.Builder().setKey(userId).setName(name).build();
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to read a user from Instagram's cache", ex);
            return null;
        }
    }

    private static final int MAX_PREFETCH = 50;
    // Senders whose photo was already looked up in this process.
    private static final Set<String> photoRequests = ConcurrentHashMap.newKeySet();

    /** A chat member's photo, fetched before their first message (Avatars skips ones already saved). */
    private static void prefetchPhoto(String userId, UserData data) {
        if (TextUtils.isEmpty(userId) || isViewer(userId)) return;
        try {
            Avatars.fetch(userId, urlOf(data.getLowResProfilePicture()), null);
        } catch (Throwable ignored) {
            // The photo then comes from a notification or the UserCache lookup.
        }
    }

    /** An ImageUrl's address; the stub does not declare it, Instagram's interface keeps the name. */
    private static String urlOf(Object imageUrl) {
        if (imageUrl == null) return null;
        try {
            Object url = imageUrl.getClass().getMethod("getUrl").invoke(imageUrl);
            return url instanceof String ? (String) url : null;
        } catch (Throwable ex) {
            return null;
        }
    }

    /**
     * A sender shown without a photo: Instagram's UserCache knows their picture, which is fetched
     * once, and the chat is drawn again with it.
     */
    static void requestPhoto(String threadId, String userId) {
        if (TextUtils.isEmpty(userId) || isViewer(userId) || session == null || !photoRequests.add(userId)) return;
        worker().post(() -> {
            try {
                Object user = userCacheUser(userId);
                if (user == null) return;
                Avatars.fetch(userId, urlOf(new UserData(user).getLowResProfilePicture()),
                        () -> ConversationNotifications.onImageReady(threadId, null));
            } catch (Throwable ex) {
                Logger.printException(() -> "Failed to look up a profile photo", ex);
            }
        });
    }

    /** The User Instagram's UserCache holds for an id, or null (no session, not seen, or not resolved). */
    private static Object userCacheUser(String userId) throws Exception {
        UserSession user = session;
        if (user == null || TextUtils.isEmpty(userId)) return null;
        Object found = lookupCachedUser(user, userId);
        // The cache's id map is resolved at patch time; a user under another id is not this one.
        return found != null && userId.equals(new UserData(found).getUserId()) ? found : null;
    }

    /**
     * Replaced at patch time with {@code UserCache.<getter>(session).<lookup by id>(userId)}, resolved
     * the same way as Save deleted messages does. Stays null when the lookup is not found.
     */
    private static Object lookupCachedUser(UserSession session, String userId) {
        return null;
    }

    /** A logout: what was learned about people and items in this process is dropped. */
    static void forgetPeople() {
        people.clear();
        chatNames.clear();
        vaultLookups.clear();
        photoRequests.clear();
        seenItems.clear();
    }

    /** As Instagram's notifications name people: the full name, else the username. */
    private static String displayName(UserData data) throws Exception {
        String name = data.getFullName();
        return TextUtils.isEmpty(name) ? data.getUsername() : name;
    }

    /** Saves a name off the notify path, so a notification is never delayed by the database. */
    private static void rememberUsername(String userId, String name) {
        worker().post(() -> saveUsername(userId, name));
    }

    private static void saveUsername(String userId, String name) {
        try {
            PikoMessageDb.getInstance(Utils.getContext()).putUsername(userId, name);
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to save a sender name to the message vault", ex);
        }
    }

    private static void enqueue(Object item, String threadIdHint, String viewerId) {
        if (item == null) return;
        worker().post(() -> {
            try {
                store(new DirectItem(item), threadIdHint, viewerId);
            } catch (Throwable ex) {
                Logger.printException(() -> "Failed to remember a message", ex);
                NotificationLog.event("FAILED to remember a message: " + android.util.Log.getStackTraceString(ex));
            }
        });
    }

    private static void store(DirectItem item, String threadIdHint, String viewerId) {
        if (item.isHideInThread()) return;

        String threadId = item.getThreadId();
        if (TextUtils.isEmpty(threadId)) threadId = threadIdHint;
        String serverId = item.getItemId();
        String id = TextUtils.isEmpty(serverId) ? item.getClientContext() : serverId;

        String type = item.getItemType();
        if ("action_log".equals(type) || "placeholder".equals(type) || "expired_placeholder".equals(type)) return;
        CharSequence text = item.getText();
        boolean hasText = !TextUtils.isEmpty(text);
        if (!hasText) text = app.morphe.extension.instagram.patches.notification.state.Message.labelOfType(
                type, item.isVideo());

        // isSentByViewer alone misses them: the server echoes your messages with it unset. They are
        // only logged, as the replay of the log needs to see every input, and never shown.
        if (item.isSentByViewer() || isViewer(item.getUserId())) {
            if (!TextUtils.isEmpty(threadId)) {
                logReceived(threadId, item.getUserId(), "you", type, serverId, viewerId, item.getTimestampMs(), text);
                String self = !TextUtils.isEmpty(viewerId) ? viewerId : !TextUtils.isEmpty(item.getUserId())
                        ? item.getUserId() : soleViewer();
                if (self != null && !TextUtils.isEmpty(id)) {
                    ConversationNotifications.onChatItem(threadId, viewerId, stateMessage(id, self, true, text,
                            item.getTimestampMs(), type));
                }
            }
            return;
        }

        if (TextUtils.isEmpty(threadId) || TextUtils.isEmpty(id)) {
            // Item id, type and time, so a later copy of the same item with its chat can be matched.
            NotificationLog.event("DROPPED a message without " + (TextUtils.isEmpty(id) ? "an id" : "a chat")
                    + " from " + item.getUserId() + " type=" + dash(type) + " item=" + dash(serverId)
                    + " @" + item.getTimestampMs());
            return;
        }

        String senderId = item.getUserId();
        long timestamp = item.getTimestampMs();
        // Inbox loads deliver the same items again; only the first copy, or an edit, goes on.
        String knownText = seenItems.get(id);
        if (knownText != null) {
            // The same item with other text is an edit. Only plain text is compared.
            if (hasText && "text".equals(type) && !knownText.equals(text.toString())) {
                seenItems.put(id, text.toString());
                NotificationLog.event("EDIT in chat " + threadId + " item=" + dash(serverId) + " source=legacy: " + text);
                ConversationNotifications.onEdited(threadId, id, text.toString());
            }
            return;
        }
        seenItems.put(id, text.toString());
        // Resolved here, on the worker, so a later digest finds the names already cached.
        Person sender = person(senderId);
        if (NotificationLog.isEnabled()) {
            logReceived(threadId, senderId, sender != null ? String.valueOf(sender.getName()) : "name unknown",
                    type, serverId, viewerId, timestamp, text);
        }

        ConversationNotifications.onChatItem(threadId, viewerId, stateMessage(id, senderId, false, text,
                timestamp, type));

        // The image follows the text: once cached, the chat is drawn again, silently.
        // Only what a notification may show: a recent item (it often arrives before Instagram's post)
        // or one of a chat that has a record. Inbox loads would otherwise fetch whole histories.
        boolean recent = System.currentTimeMillis() - timestamp < IMAGE_RECENT_MS;
        if (!hasText && !"text".equals(type)) {
            boolean enabled = NotificationImages.enabled();
            boolean wanted = enabled && (recent || ConversationNotifications.isShowing(threadId, viewerId));
            String url = wanted ? item.imageUrl() : null;
            if (url != null) {
                String chat = threadId;
                NotificationImages.fetch(id, url, "animated_media".equals(type) || "store_sticker".equals(type),
                        () -> ConversationNotifications.onImageReady(chat, viewerId));
            }
        }
    }

    /** The reducer's view of a message: its item id, sender and real server time. */
    private static app.morphe.extension.instagram.patches.notification.state.Message stateMessage(
            String id, String senderId, boolean viewer, CharSequence text, long serverTime, String type) {
        String shown = text.toString();
        return new app.morphe.extension.instagram.patches.notification.state.Message(id, senderId, viewer, shown,
                serverTime, System.currentTimeMillis(), false,
                app.morphe.extension.instagram.patches.notification.state.Message.kindOfType(type, shown));
    }

    private static void logReceived(String threadId, String senderId, String who, String type, String serverId,
                                    String viewerId, long timestamp, CharSequence text) {
        if (!NotificationLog.isEnabled()) return;
        if (TextUtils.isEmpty(viewerId) && viewers.size() == 1) viewerId = viewers.iterator().next();
        NotificationLog.event("RECEIVED in chat " + threadId + " from " + senderId + " (" + who + ") type="
                + dash(type) + " item=" + dash(serverId) + " viewer=" + dash(viewerId) + " @" + timestamp + ": " + text);
    }

    private static String dash(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    // ---- Message lifecycle: unsend, read elsewhere --------------------------------------------

    /** Modular route: a decoded IGMessageDeleteMessagingPayload. */
    public static void onModularDelete(Object payload) {
        try {
            String[] fields = deleteFields().split(",");
            unsent(stringField(payload, fields[0]), stringField(payload, fields[1]), null, "modular");
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to read a deleted message", ex);
            NotificationLog.event("FAILED to read a deleted message: " + android.util.Log.getStackTraceString(ex));
        }
    }

    /** Legacy route: the message database hides an item by its server or client id. */
    public static void onItemHidden(Object threadKey, String serverId, String clientId) {
        try {
            unsent(threadIdOfKey(threadKey), serverId, clientId, "legacy");
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to read a hidden message", ex);
            NotificationLog.event("FAILED to read a hidden message: " + android.util.Log.getStackTraceString(ex));
        }
    }

    private static void unsent(String threadId, String serverId, String clientId, String source) {
        String item = TextUtils.isEmpty(serverId) ? clientId : serverId;
        worker().post(() -> {
            try {
                String chat = threadId;
                if (TextUtils.isEmpty(chat)) chat = ConversationNotifications.chatOfItem(serverId);
                if (TextUtils.isEmpty(chat)) chat = ConversationNotifications.chatOfItem(clientId);
                NotificationLog.event("UNSEND in chat " + dash(chat) + " item=" + dash(item) + " source=" + source);
                ConversationNotifications.onUnsent(chat, serverId, clientId);
            } catch (Throwable ex) {
                Logger.printException(() -> "Failed to handle an unsent message", ex);
                NotificationLog.event("FAILED to handle an unsent message: " + android.util.Log.getStackTraceString(ex));
            }
        });
    }

    /** Modular route: a decoded IGMarkReadMessagingPayload. */
    public static void onModularRead(Object payload) {
        try {
            String[] fields = readFields().split(",");
            String readTime = stringField(payload, fields[2]);
            long at = 0;
            try {
                at = readTime == null ? 0 : Long.parseLong(readTime);
            } catch (NumberFormatException ignored) {
                // Logged as 0.
            }
            readElsewhere(stringField(payload, fields[0]), stringField(payload, fields[1]), at, "modular");
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to read a read marker", ex);
            NotificationLog.event("FAILED to read a read marker: " + android.util.Log.getStackTraceString(ex));
        }
    }

    /** Legacy route: this account's seen marker for a chat is about to be sent. */
    public static void onLegacyRead(String threadId, String itemId) {
        readElsewhere(threadId, itemId, 0, "local");
    }

    // Thread id -> newest read time (ms) any source reported, and the item this phone last marked seen itself.
    private static final Map<String, Long> lastRead = new ConcurrentHashMap<>();
    private static final Map<String, Object[]> localReads = new ConcurrentHashMap<>();
    private static final long LOCAL_ECHO_MS = 5000;

    /** Iris applied a participant's seen marker: (sink, thread, seen, userId). */
    public static void onSeenIris(Object sink, Object thread, Object seen, String userId) {
        onSeen(sink, thread, seen, userId, "iris");
    }

    /** A snapshot merged a participant's seen marker into a thread: (thread, seen, userId). */
    public static void onSeenSnapshot(Object thread, Object seen, String userId) {
        onSeen(null, thread, seen, userId, "snapshot");
    }

    /**
     * The account's own seen marker, from the server. The thread id comes from the thread's
     * DirectThreadKey, the item id from the marker's string that is a known item (else its field
     * "A02"), the time from its largest long (microseconds, normalised to ms), the user from the
     * sink's UserSession field. All by shape, so only the fallback name is obfuscation specific.
     */
    private static void onSeen(Object sink, Object thread, Object seen, String userId, String source) {
        try {
            if (thread == null || seen == null || TextUtils.isEmpty(userId)) return;
            String sessionUser = sink == null ? null : sessionUserOf(sink);
            if (sessionUser != null) {
                rememberViewer(sessionUser);
                if (!sessionUser.equals(userId)) return;
            }
            if (!isViewer(userId)) return;
            String threadId = null;
            for (Method method : thread.getClass().getMethods()) {
                if (method.getParameterTypes().length == 0
                        && method.getReturnType().getName().equals("com.instagram.model.direct.DirectThreadKey")) {
                    threadId = threadIdOfKey(method.invoke(thread));
                    break;
                }
            }
            if (TextUtils.isEmpty(threadId)) return;
            final String chat = threadId;
            List<String> strings = new ArrayList<>();
            long raw = 0;
            String fallback = null;
            for (Class<?> type = seen.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    if (field.getType() == String.class) {
                        String value = (String) field.get(seen);
                        if (!TextUtils.isEmpty(value)) strings.add(value);
                        if (field.getName().equals("A02") && fallback == null) fallback = value;
                    } else if (field.getType() == long.class) {
                        raw = Math.max(raw, field.getLong(seen));
                    }
                }
            }
            final long seenMs = raw > 100_000_000_000_000L ? raw / 1000 : raw > 0 && raw < 100_000_000_000L ? raw * 1000 : raw;
            final List<String> candidates = strings;
            final String fallbackItem = fallback;
            worker().post(() -> {
                try {
                    String itemId = fallbackItem;
                    for (String candidate : candidates) {
                        if (timestampOf(chat, candidate) > 0) itemId = candidate;
                    }
                    long itemTime = timestampOf(chat, itemId);
                    long upTo = itemTime > 0 ? itemTime : seenMs;
                    Long last = lastRead.get(chat);
                    if (upTo <= 0 || (last != null && upTo <= last)) return;
                    lastRead.put(chat, upTo);
                    Object[] local = localReads.get(chat);
                    if (local != null && itemId != null && itemId.equals(local[0])
                            && System.currentTimeMillis() - (Long) local[1] < LOCAL_ECHO_MS) return;
                    NotificationLog.event("READ_ELSEWHERE in chat " + chat + " item=" + dash(itemId) + " at=" + upTo
                            + " source=" + source);
                    long newest = newestTimestamp(chat);
                    ConversationNotifications.onReadElsewhere(chat, upTo, newest > 0 && upTo >= newest);
                } catch (Throwable ex) {
                    Logger.printException(() -> "Failed to handle a seen marker from the server", ex);
                }
            });
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to read a seen marker from the server", ex);
        }
    }

    private static String sessionUserOf(Object sink) {
        try {
            for (Class<?> type = sink.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (field.getType() != UserSession.class || Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    UserSession user = (UserSession) field.get(sink);
                    return user == null ? null : user.getUserId();
                }
            }
        } catch (Throwable ignored) {
            // The marker is then judged by the known viewers alone.
        }
        return null;
    }

    private static void readElsewhere(String threadId, String itemId, long readMs, String source) {
        if (TextUtils.isEmpty(threadId)) return;
        worker().post(() -> {
            try {
                NotificationLog.event("READ_ELSEWHERE in chat " + threadId + " item=" + dash(itemId) + " at=" + readMs
                        + " source=" + source);
                long itemTime = timestampOf(threadId, itemId);
                long newest = newestTimestamp(threadId);
                boolean local = "local".equals(source);
                // The modular payload does not say whose read it is. An item among the messages
                // others sent can only be read by this account, so anything else is ignored.
                if (!local && itemTime == 0) {
                    NotificationLog.event("  read marker ignored: item is not one of the messages received from others");
                    return;
                }
                // This account marked the chat seen here, up to an item. If that item is unknown, the
                // newest held one stands in. A modular marker covering the newest message clears the chat.
                long upTo = itemTime > 0 ? itemTime : Math.min(newest, System.currentTimeMillis());
                if (local) {
                    lastRead.merge(threadId, upTo, Math::max);
                    localReads.put(threadId, new Object[] {itemId, System.currentTimeMillis()});
                }
                ConversationNotifications.onReadElsewhere(threadId, upTo, !local && itemTime >= newest);
            } catch (Throwable ex) {
                Logger.printException(() -> "Failed to handle a read marker", ex);
                NotificationLog.event("FAILED to handle a read marker: " + android.util.Log.getStackTraceString(ex));
            }
        });
    }

    /** Server time of an item among the messages others sent, 0 when it is not one of them. */
    private static long timestampOf(String threadId, String itemId) {
        return ConversationNotifications.itemTime(threadId, itemId);
    }

    private static String stringField(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (String) field.get(target);
    }

    /** The chat id of a DirectThreadKey, null for a key made of recipients. */
    private static String threadIdOfKey(Object key) {
        if (key == null) return null;
        for (Method method : key.getClass().getDeclaredMethods()) {
            if (method.getParameterTypes().length != 0 || method.getReturnType() != String.class
                    || Modifier.isStatic(method.getModifiers())) continue;
            try {
                method.setAccessible(true);
                Object value = method.invoke(key);
                if (value instanceof String && ((String) value).startsWith("thread:")) {
                    return ((String) value).substring("thread:".length());
                }
            } catch (Throwable ignored) {
                // A key of recipients throws; it has no chat id.
            }
        }
        return null;
    }

    /** Replaced at patch time with the delete payload's chat and item fields, comma separated. */
    private static String deleteFields() {
        return "deleteFields";
    }

    /** Replaced at patch time with the read payload's chat, item and read time fields. */
    private static String readFields() {
        return "readFields";
    }

    private static String threadIdHint(Object delta) {
        if (delta == null) return null;
        try {
            Field field = delta.getClass().getDeclaredField(deltaThreadIdFieldName());
            if (field.getType() != String.class || Modifier.isStatic(field.getModifiers())) return null;
            field.setAccessible(true);
            return (String) field.get(delta);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Replaced at patch time with the delta's thread id field. */
    private static String deltaThreadIdFieldName() {
        return "deltaThreadIdField";
    }

    /** Replaced at patch time with Instagram's Iris sync manager class. */
    private static String syncManagerClass() {
        return "syncManagerClass";
    }

    /** Replaced at patch time with the sync manager's thread snapshot request. */
    private static String threadSnapshotMethod() {
        return "threadSnapshotMethod";
    }

    /** Replaced at patch time with the class that queues thread seen markers. */
    private static String markSeenClass() {
        return "markSeenClass";
    }

    /** Replaced at patch time with the method that queues a thread seen marker. */
    private static String markSeenMethod() {
        return "markSeenMethod";
    }

    private static synchronized Handler worker() {
        if (worker == null) {
            HandlerThread thread = new HandlerThread("piko-chat-messages");
            thread.start();
            worker = new Handler(thread.getLooper());
        }
        return worker;
    }
}
