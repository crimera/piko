/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import android.app.Notification;
import android.app.Person;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcelable;

import com.instagram.common.session.UserSession;
import com.instagram.model.direct.DirectThreadKey;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;

/**
 * Runtime hooks for "DM notification allowlist".
 * Filters system DM push notifications and deleted-message notices so only selected people or chats notify.
 */
@SuppressWarnings("unused")
public final class DmNotificationAllowlist {

    private static final String MESSAGE_CHANNEL = "ig_direct";
    private static final String USER_MODEL_CLASS = "com.instagram.user.model.User";

    private static final int SEARCH_DEPTH = 4;
    private static final int SEARCH_LIMIT = 400;
    private static final Field[] NO_PATH = new Field[0];

    private static final long DECISION_TTL_MS = 8_000L;
    private static final long SUMMARY_SUPPRESS_TTL_MS = 3_000L;

    private static final Map<Class<?>, Field[]> keyPaths = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field[]> sessionPaths = new ConcurrentHashMap<>();
    private static final Map<String, Long> recentAllowedThreads = new ConcurrentHashMap<>();
    private static final Map<String, Long> pendingUnknownThreads = new ConcurrentHashMap<>();
    private static final Map<Long, List<String>> originalNotificationSenders = new ConcurrentHashMap<>();

    private static volatile long pendingUnknownUntaggedAt;
    private static volatile long lastUnknownMutedAt;

    private static HandlerThread workerThread;
    private static Handler workerHandler;
    private static Runnable pendingInboxJob;

    private DmNotificationAllowlist() {
    }

    private static synchronized Handler getWorker() {
        if (workerHandler == null) {
            workerThread = new HandlerThread("piko-dm-allowlist");
            workerThread.start();
            workerHandler = new Handler(workerThread.getLooper());
        }
        return workerHandler;
    }

    private static synchronized void scheduleInboxSnapshot(UserSession session, List<Object> snapshot) {
        Handler worker = getWorker();
        if (pendingInboxJob != null) {
            worker.removeCallbacks(pendingInboxJob);
        }
        Runnable job = () -> processInboxSnapshot(session, snapshot);
        pendingInboxJob = job;
        worker.post(job);
    }

    /** Replaced with Instagram's session-scoped UserCache lookup at patch time. */
    private static Object lookupCachedUser(UserSession session, String userId) {
        return null;
    }

    /** Replaced with DirectThreadKey's threadId field name at patch time. */
    private static String threadKeyIdFieldName() {
        return "A00";
    }

    /** Replaced with DirectThreadKey's recipient list field name at patch time. */
    private static String threadKeyRecipientsFieldName() {
        return "A02";
    }

    /**
     * Gate 1: Called before Instagram builds a DM push notification.
     * Returns true to skip showing the notification (running Instagram's completion callback first).
     */
    public static boolean shouldMuteNotification(Object session, Runnable done, String threadId) {
        try {
            if (!Pref.dmNotificationAllowlist()) return false;

            String cleanId = DmNotificationAllowlistPref.extractCleanThreadId(threadId);
            DmNotificationAllowlistPref.Decision decision = DmNotificationAllowlistPref.evaluateThreadDecision(
                    cleanId,
                    Pref.dmNotificationAllowlistGroups()
            );

            if (decision == DmNotificationAllowlistPref.Decision.ALLOW) {
                if (!cleanId.isEmpty()) {
                    recordRecentAllowed(cleanId);
                }
                return false;
            }

            if (decision == DmNotificationAllowlistPref.Decision.MUTE) {
                if (done != null) done.run();
                return true;
            }

            // UNKNOWN_THREAD: if there are no allowlisted users (only explicit threads or empty list),
            // an unseen thread cannot match anything, so mute immediately before building the notification.
            if (!DmNotificationAllowlistPref.hasAnyAllowedUsers()) {
                if (done != null) done.run();
                return true;
            }

            // Defer to Gate 2 (NotifyFingerprint) so we can match the notification's sender name/handle.
            long now = System.currentTimeMillis();
            pruneExpiredPendingUnknowns(now);
            if (!cleanId.isEmpty()) {
                pendingUnknownThreads.put(cleanId, now);
            } else {
                pendingUnknownUntaggedAt = now;
            }
            return false;
        } catch (Throwable t) {
            Logger.printException(() -> "DmNotificationAllowlist.shouldMuteNotification failed", t);
            return false;
        }
    }

    /**
     * Called by InboxLock.hideNotification before it redacts sender text, so Gate 2 still sees the
     * original sender even when Inbox Lock runs before us on NotificationManagerCompat.notify.
     */
    public static void noteOriginalNotification(Notification original) {
        try {
            if (original == null || !Pref.dmNotificationAllowlist()) return;
            if (!isInstagramDirectNotification(original)) return;
            List<String> candidates = extractSenderCandidates(original);
            if (!candidates.isEmpty()) {
                long now = System.currentTimeMillis();
                pruneOldEntries(originalNotificationSenders, now);
                originalNotificationSenders.put(original.when, candidates);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Gate 2: Fallback check on NotificationManagerCompat.notify for unseen threads deferred by Gate 1.
     * Returns true to suppress posting the notification.
     */
    public static boolean shouldMutePostedNotification(String tag, Notification notification) {
        try {
            if (!Pref.dmNotificationAllowlist() || notification == null) return false;
            if (!isInstagramDirectNotification(notification)) return false;

            long now = System.currentTimeMillis();

            String tagThreadId = DmNotificationAllowlistPref.extractCleanThreadId(tag);
            boolean hasExplicitTagThreadId = tag != null && tag.contains("thread_id:") && !tagThreadId.isEmpty();
            if (!tagThreadId.isEmpty()) {
                if (isRecentlyAllowed(tagThreadId, now)) return false;
                if (hasExplicitTagThreadId
                        && !pendingUnknownThreads.containsKey(tagThreadId)
                        && DmNotificationAllowlistPref.evaluateThreadDecision(
                        tagThreadId, Pref.dmNotificationAllowlistGroups())
                        == DmNotificationAllowlistPref.Decision.ALLOW) {
                    return false;
                }
            }

            boolean isGroupSummary = (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
            if (isGroupSummary) {
                return lastUnknownMutedAt > 0 && (now - lastUnknownMutedAt) <= SUMMARY_SUPPRESS_TTL_MS;
            }

            // Only filter here when Gate 1 explicitly deferred an unseen thread.
            if (!consumePendingUnknown(tagThreadId, hasExplicitTagThreadId, now)) {
                return false;
            }

            List<String> candidates = new ArrayList<>();
            List<String> preRedaction = originalNotificationSenders.remove(notification.when);
            if (preRedaction != null) candidates.addAll(preRedaction);
            candidates.addAll(extractSenderCandidates(notification));

            for (String candidate : candidates) {
                if (DmNotificationAllowlistPref.matchesAllowedSenderText(candidate)) {
                    lastUnknownMutedAt = 0;
                    if (!tagThreadId.isEmpty()) recordRecentAllowed(tagThreadId);
                    return false;
                }
            }

            lastUnknownMutedAt = now;
            return true;
        } catch (Throwable t) {
            Logger.printException(() -> "DmNotificationAllowlist.shouldMutePostedNotification failed", t);
            return false;
        }
    }

    /** Called before a deleted-message notice is shown by Save deleted messages. */
    public static boolean shouldMuteDeletion(String threadId, String senderId, String senderUsername) {
        try {
            if (!Pref.dmNotificationAllowlist()) return false;
            return !DmNotificationAllowlistPref.isDeletionAllowed(
                    threadId,
                    senderId,
                    senderUsername,
                    Pref.dmNotificationAllowlistGroups()
            );
        } catch (Throwable t) {
            Logger.printException(() -> "DmNotificationAllowlist.shouldMuteDeletion failed", t);
            return false;
        }
    }

    /**
     * Called read-only when the DM inbox builds its rows. Snapshots the thread list and indexes
     * threadId -> participants asynchronously so the Settings picker and push filter stay up to date.
     */
    @SuppressWarnings("unchecked")
    public static void indexInboxRows(Object task) {
        try {
            if (!Pref.dmNotificationAllowlist() || task == null) return;

            List<Object> list = null;
            for (Field field : task.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(task);
                if (value instanceof List) list = (List<Object>) value;
            }
            if (list == null || list.isEmpty()) return;

            UserSession session = resolveUserSession(task);
            if (session == null) return;

            final List<Object> snapshot = new ArrayList<>(list);
            scheduleInboxSnapshot(session, snapshot);
        } catch (Throwable t) {
            Logger.printException(() -> "DmNotificationAllowlist.indexInboxRows failed", t);
        }
    }

    /** Harvests participant userId -> (username, fullName) mappings from REST thread deserialization. */
    public static void noteThreadUser(Object user) {
        if (!Pref.dmNotificationAllowlist() || user == null) return;
        getWorker().post(() -> {
            try {
                UserData data = new UserData(user);
                String id = data.getUserId();
                if (id == null || id.isEmpty()) return;
                String username = data.getUsername();
                String fullName = data.getFullName();
                DmNotificationAllowlistPref.putUserDirEntries(
                        Collections.singletonMap(id, new DmNotificationAllowlistPref.UserDirEntry(username, fullName))
                );
            } catch (Throwable ignored) {
            }
        });
    }

    private static void processInboxSnapshot(UserSession session, List<Object> threads) {
        try {
            String accountId = session.getUserId();
            Map<String, DmNotificationAllowlistPref.UserDirEntry> userDir =
                    DmNotificationAllowlistPref.loadUserDir();
            Map<String, DmNotificationAllowlistPref.UserDirEntry> dirUpdates = new HashMap<>();
            List<DmNotificationAllowlistPref.KnownThread> discovered = new ArrayList<>();
            long now = System.currentTimeMillis();

            for (int index = 0; index < threads.size(); index++) {
                Object thread = threads.get(index);
                if (thread == null) continue;
                DirectThreadKey key = threadKeyOf(thread);
                if (key == null) continue;

                String threadId = extractThreadId(key);
                if (threadId.isEmpty()) continue;

                List<String> recipientIds = extractRecipientIds(key, accountId);
                Map<String, DmNotificationAllowlistPref.UserDirEntry> inlineUsers = extractInlineUsers(thread, accountId);
                for (Map.Entry<String, DmNotificationAllowlistPref.UserDirEntry> entry : inlineUsers.entrySet()) {
                    if (!recipientIds.contains(entry.getKey())) {
                        recipientIds.add(entry.getKey());
                    }
                    dirUpdates.put(entry.getKey(), entry.getValue());
                }

                List<String> usernames = new ArrayList<>();
                List<String> fullNames = new ArrayList<>();
                for (String recipientId : recipientIds) {
                    DmNotificationAllowlistPref.UserDirEntry resolved = inlineUsers.get(recipientId);
                    if (resolved == null) {
                        resolved = resolveCachedUser(session, recipientId);
                        if (resolved != null) {
                            dirUpdates.put(recipientId, resolved);
                        }
                    }
                    if (resolved == null) {
                        resolved = userDir.get(recipientId);
                    }
                    if (resolved != null) {
                        if (!resolved.username.isEmpty()) usernames.add(resolved.username);
                        if (!resolved.fullName.isEmpty()) fullNames.add(resolved.fullName);
                    }
                }

                boolean isGroup = recipientIds.size() > 1;
                String label = buildThreadLabel(isGroup, usernames, fullNames, recipientIds, threadId);

                // Preserve inbox ordering with monotonically decreasing timestamps within this snapshot.
                discovered.add(new DmNotificationAllowlistPref.KnownThread(
                        threadId,
                        accountId,
                        isGroup,
                        label,
                        recipientIds,
                        usernames,
                        fullNames,
                        now - index
                ));
            }

            if (!dirUpdates.isEmpty()) {
                DmNotificationAllowlistPref.putUserDirEntries(dirUpdates);
            }
            if (!discovered.isEmpty()) {
                DmNotificationAllowlistPref.upsertKnownThreads(discovered);
            }
        } catch (Throwable t) {
            Logger.printException(() -> "DmNotificationAllowlist.processInboxSnapshot failed", t);
        }
    }

    private static String buildThreadLabel(
            boolean isGroup,
            List<String> usernames,
            List<String> fullNames,
            List<String> recipientIds,
            String threadId
    ) {
        if (!isGroup) {
            String u = usernames.isEmpty() ? "" : usernames.get(0);
            String n = fullNames.isEmpty() ? "" : fullNames.get(0);
            if (!n.isEmpty() && !u.isEmpty() && !n.equalsIgnoreCase(u)) {
                return n + " (@" + u + ")";
            }
            if (!u.isEmpty()) return "@" + u;
            if (!n.isEmpty()) return n;
            if (!recipientIds.isEmpty()) return recipientIds.get(0);
            return threadId;
        }
        if (!usernames.isEmpty()) {
            return DmNotificationAllowlistPref.formatUsernamesLabel(usernames);
        }
        if (!fullNames.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int limit = Math.min(fullNames.size(), 3);
            for (int i = 0; i < limit; i++) {
                if (i > 0) sb.append(", ");
                sb.append(fullNames.get(i));
            }
            if (fullNames.size() > 3) sb.append(" +").append(fullNames.size() - 3);
            return sb.toString();
        }
        return threadId;
    }

    private static DmNotificationAllowlistPref.UserDirEntry resolveCachedUser(UserSession session, String userId) {
        if (session == null || userId == null || userId.isEmpty()) return null;
        try {
            Object user = lookupCachedUser(session, userId);
            if (user == null) return null;
            UserData data = new UserData(user);
            String username = data.getUsername();
            String fullName = data.getFullName();
            if ((username == null || username.isEmpty()) && (fullName == null || fullName.isEmpty())) {
                return null;
            }
            return new DmNotificationAllowlistPref.UserDirEntry(username, fullName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Scans immediate List fields on a thread item for Instagram User objects to supplement UserCache. */
    private static Map<String, DmNotificationAllowlistPref.UserDirEntry> extractInlineUsers(
            Object thread,
            String accountId
    ) {
        Map<String, DmNotificationAllowlistPref.UserDirEntry> found = new HashMap<>();
        try {
            for (Field field : fieldsOf(thread.getClass())) {
                if (!List.class.isAssignableFrom(field.getType())) continue;
                Object raw = field.get(thread);
                if (!(raw instanceof List)) continue;
                List<?> list = (List<?>) raw;
                if (list.isEmpty()) continue;
                Object first = list.get(0);
                if (first == null || !USER_MODEL_CLASS.equals(first.getClass().getName())) continue;
                for (Object elem : list) {
                    if (elem == null || !USER_MODEL_CLASS.equals(elem.getClass().getName())) continue;
                    try {
                        UserData data = new UserData(elem);
                        String uid = data.getUserId();
                        if (uid == null || uid.isEmpty() || uid.equals(accountId)) continue;
                        found.put(uid, new DmNotificationAllowlistPref.UserDirEntry(
                                data.getUsername(),
                                data.getFullName()
                        ));
                    } catch (Throwable ignored) {
                    }
                }
                if (!found.isEmpty()) break;
            }
        } catch (Throwable ignored) {
        }
        return found;
    }

    private static String extractThreadId(DirectThreadKey key) {
        try {
            Field field = DirectThreadKey.class.getDeclaredField(threadKeyIdFieldName());
            field.setAccessible(true);
            Object val = field.get(key);
            if (val instanceof String && !((String) val).trim().isEmpty()) {
                return ((String) val).trim();
            }
        } catch (Throwable ignored) {
        }
        return DmNotificationAllowlistPref.extractCleanThreadId(String.valueOf(key));
    }

    private static List<String> extractRecipientIds(DirectThreadKey key, String accountId) {
        Set<String> ids = new LinkedHashSet<>();
        try {
            List<?> rawList = null;
            try {
                Field field = DirectThreadKey.class.getDeclaredField(threadKeyRecipientsFieldName());
                field.setAccessible(true);
                Object val = field.get(key);
                if (val instanceof List) rawList = (List<?>) val;
            } catch (Throwable ignored) {
            }
            if (rawList == null) {
                for (Field field : DirectThreadKey.class.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object val = field.get(key);
                    if (val instanceof List) {
                        rawList = (List<?>) val;
                        break;
                    }
                }
            }
            if (rawList != null) {
                for (Object item : rawList) {
                    if (item == null) continue;
                    String id = String.valueOf(item).trim();
                    if (!id.isEmpty() && !id.equals(accountId)) {
                        ids.add(id);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(ids);
    }

    private static boolean isInstagramDirectNotification(Notification notification) {
        if (notification == null) return false;
        String channel = notification.getChannelId();
        if (SavedMessagesHook.NOTIFICATION_CHANNEL_ID.equals(channel)) return false;
        return Notification.CATEGORY_MESSAGE.equals(notification.category) || MESSAGE_CHANNEL.equals(channel);
    }

    private static List<String> extractSenderCandidates(Notification notification) {
        List<String> out = new ArrayList<>();
        Bundle extras = notification.extras;
        if (extras == null) return out;

        addCandidate(out, extras.getCharSequence(Notification.EXTRA_TITLE));
        addCandidate(out, extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        addCandidate(out, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));

        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null) {
            for (Parcelable parcelable : messages) {
                if (!(parcelable instanceof Bundle)) continue;
                Bundle msg = (Bundle) parcelable;
                addCandidate(out, msg.getCharSequence("sender"));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Object personObj = msg.getParcelable("sender_person");
                    if (personObj instanceof Person) {
                        addCandidate(out, ((Person) personObj).getName());
                    }
                }
            }
        }
        return out;
    }

    private static void addCandidate(List<String> out, CharSequence value) {
        if (value == null) return;
        String text = value.toString().trim();
        if (!text.isEmpty() && !out.contains(text)) {
            out.add(text);
        }
    }

    private static void recordRecentAllowed(String threadId) {
        long now = System.currentTimeMillis();
        recentAllowedThreads.put(threadId, now);
        if (recentAllowedThreads.size() > 32) {
            for (Map.Entry<String, Long> entry : recentAllowedThreads.entrySet()) {
                if (now - entry.getValue() > DECISION_TTL_MS) {
                    recentAllowedThreads.remove(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private static boolean isRecentlyAllowed(String threadId, long now) {
        Long ts = recentAllowedThreads.get(threadId);
        return ts != null && (now - ts) <= DECISION_TTL_MS;
    }

    private static void pruneExpiredPendingUnknowns(long now) {
        for (Map.Entry<String, Long> entry : pendingUnknownThreads.entrySet()) {
            if ((now - entry.getValue()) > DECISION_TTL_MS) {
                pendingUnknownThreads.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static boolean consumePendingUnknown(String tagThreadId, boolean hasExplicitTagThreadId, long now) {
        pruneExpiredPendingUnknowns(now);
        if (!tagThreadId.isEmpty()) {
            Long pendingAt = pendingUnknownThreads.remove(tagThreadId);
            if (pendingAt != null && (now - pendingAt) <= DECISION_TTL_MS) {
                return true;
            }
        }
        if (pendingUnknownUntaggedAt > 0 && (now - pendingUnknownUntaggedAt) <= DECISION_TTL_MS) {
            pendingUnknownUntaggedAt = 0;
            return true;
        }
        if (!hasExplicitTagThreadId && !pendingUnknownThreads.isEmpty()) {
            String oldestKey = null;
            Long oldestTs = null;
            for (Map.Entry<String, Long> entry : pendingUnknownThreads.entrySet()) {
                Long ts = entry.getValue();
                if (ts != null && (now - ts) <= DECISION_TTL_MS
                        && (oldestTs == null || ts < oldestTs)) {
                    oldestKey = entry.getKey();
                    oldestTs = ts;
                }
            }
            if (oldestKey != null && pendingUnknownThreads.remove(oldestKey, oldestTs)) {
                return true;
            }
        }
        return false;
    }

    private static void pruneOldEntries(Map<Long, List<String>> map, long now) {
        if (map.size() <= 16) return;
        for (Long key : map.keySet()) {
            if (Math.abs(now - key) > DECISION_TTL_MS * 4) {
                map.remove(key);
            }
        }
    }

    private static UserSession resolveUserSession(Object task) throws Exception {
        Field[] path = sessionPaths.get(task.getClass());
        if (path == null) {
            path = pathTo(task, UserSession.class);
            sessionPaths.put(task.getClass(), path);
        }
        if (path == NO_PATH) return null;
        Object value = task;
        for (Field field : path) {
            value = field.get(value);
            if (value == null) return null;
        }
        return value instanceof UserSession ? (UserSession) value : null;
    }

    private static DirectThreadKey threadKeyOf(Object item) {
        if (item == null) return null;
        try {
            Field[] path = keyPaths.get(item.getClass());
            if (path == null) {
                path = pathTo(item, DirectThreadKey.class);
                keyPaths.put(item.getClass(), path);
            }
            if (path == NO_PATH) return null;
            Object value = item;
            for (Field field : path) {
                value = field.get(value);
                if (value == null) return null;
            }
            return value instanceof DirectThreadKey ? (DirectThreadKey) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field[] pathTo(Object root, Class<?> type) throws Exception {
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        ArrayDeque<Object> queue = new ArrayDeque<>();
        ArrayDeque<List<Field>> paths = new ArrayDeque<>();
        queue.add(root);
        paths.add(new ArrayList<>());
        seen.put(root, true);
        while (!queue.isEmpty() && seen.size() < SEARCH_LIMIT) {
            Object current = queue.poll();
            List<Field> path = paths.poll();
            if (path.size() >= SEARCH_DEPTH) continue;
            for (Field field : fieldsOf(current.getClass())) {
                Object value = field.get(current);
                if (value == null || seen.containsKey(value)) continue;
                List<Field> next = new ArrayList<>(path);
                next.add(field);
                if (type.isInstance(value)) return next.toArray(new Field[0]);
                if (isFramework(value.getClass())) continue;
                seen.put(value, true);
                queue.add(value);
                paths.add(next);
            }
        }
        return NO_PATH;
    }

    private static List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && !isFramework(c); c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                if (Modifier.isStatic(field.getModifiers()) || fieldType.isPrimitive()) continue;
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return fields;
    }

    private static boolean isFramework(Class<?> type) {
        String name = type.getName();
        return name.startsWith("java.")
                || name.startsWith("android.")
                || name.startsWith("kotlin.")
                || name.startsWith("androidx.");
    }
}
