/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import static app.morphe.extension.instagram.utils.IgStr.str;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.crimera.sharedPreference.BaseSharedPref;
import app.morphe.extension.instagram.utils.Pref;

/**
 * Stores the DM notification allowlist (in Piko settings so export/import and reset include it)
 * and a local cache of inbox threads and user handles (kept separate so backups stay small).
 */
public final class DmNotificationAllowlistPref extends BaseSharedPref {

    private static final DmNotificationAllowlistPref CACHE = new DmNotificationAllowlistPref();

    private static final String KEY_KNOWN_THREADS = "known_threads";
    private static final String KEY_USER_DIR = "user_dir";
    private static final int MAX_KNOWN_THREADS = 300;
    private static final int MAX_USER_DIR_ENTRIES = 1000;

    private static final String THREAD_MARKER = "thread_id:";
    private static final Pattern THREAD_KEY_PATTERN = Pattern.compile("mThreadId='([^']*)'");

    private DmNotificationAllowlistPref() {
        super("piko_dm_notif_cache");
    }

    public enum Decision {
        ALLOW,
        MUTE,
        UNKNOWN_THREAD
    }

    public enum MatchReason {
        NONE,
        EXPLICIT_THREAD,
        ALLOWED_USER,
        GROUP_MEMBER
    }

    public static final class AllowedUser {
        public final String userId;
        public final String username;
        public final String fullName;

        public AllowedUser(String userId, String username, String fullName) {
            this.userId = clean(userId);
            this.username = normalizeUsername(username);
            this.fullName = clean(fullName);
        }

        public String displayTitle() {
            if (!username.isEmpty()) return "@" + username;
            if (!fullName.isEmpty()) return fullName;
            return userId;
        }

        public String displaySubtitle() {
            if (!fullName.isEmpty() && !fullName.equalsIgnoreCase(username)) {
                return fullName;
            }
            return "";
        }

        JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                if (!userId.isEmpty()) obj.put("userId", userId);
                if (!username.isEmpty()) obj.put("username", username);
                if (!fullName.isEmpty()) obj.put("fullName", fullName);
            } catch (Exception ignored) {
            }
            return obj;
        }

        static AllowedUser fromJson(JSONObject obj) {
            if (obj == null) return null;
            AllowedUser user = new AllowedUser(
                    obj.optString("userId", ""),
                    obj.optString("username", ""),
                    obj.optString("fullName", "")
            );
            return (user.userId.isEmpty() && user.username.isEmpty()) ? null : user;
        }
    }

    public static final class AllowedThread {
        public final String threadId;
        public final String label;
        public final boolean isGroup;

        public AllowedThread(String threadId, String label, boolean isGroup) {
            this.threadId = extractCleanThreadId(threadId);
            this.label = clean(label);
            this.isGroup = isGroup;
        }

        public String displayTitle() {
            return !label.isEmpty() ? label : threadId;
        }

        JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("threadId", threadId);
                if (!label.isEmpty()) obj.put("label", label);
                obj.put("isGroup", isGroup);
            } catch (Exception ignored) {
            }
            return obj;
        }

        static AllowedThread fromJson(JSONObject obj) {
            if (obj == null) return null;
            AllowedThread thread = new AllowedThread(
                    obj.optString("threadId", ""),
                    obj.optString("label", ""),
                    obj.optBoolean("isGroup", false)
            );
            return thread.threadId.isEmpty() ? null : thread;
        }
    }

    public static final class KnownThread {
        public final String threadId;
        public final String accountId;
        public final boolean isGroup;
        public final String label;
        public final List<String> participantIds;
        public final List<String> participantUsernames;
        public final List<String> participantFullNames;
        public final long lastSeen;

        public KnownThread(
                String threadId,
                String accountId,
                boolean isGroup,
                String label,
                List<String> participantIds,
                List<String> participantUsernames,
                List<String> participantFullNames,
                long lastSeen
        ) {
            this.threadId = extractCleanThreadId(threadId);
            this.accountId = clean(accountId);
            this.isGroup = isGroup;
            this.label = clean(label);
            this.participantIds = participantIds != null ? participantIds : Collections.emptyList();
            this.participantUsernames = participantUsernames != null ? participantUsernames : Collections.emptyList();
            this.participantFullNames = participantFullNames != null ? participantFullNames : Collections.emptyList();
            this.lastSeen = lastSeen;
        }

        public String displayTitle() {
            if (!label.isEmpty()) return label;
            if (!participantUsernames.isEmpty()) {
                return formatUsernamesLabel(participantUsernames);
            }
            if (!participantFullNames.isEmpty()) {
                return joinUpTo(participantFullNames, 3);
            }
            return threadId;
        }

        public String primaryUserId() {
            return (!isGroup && !participantIds.isEmpty()) ? participantIds.get(0) : "";
        }

        public String primaryUsername() {
            return (!isGroup && !participantUsernames.isEmpty()) ? participantUsernames.get(0) : "";
        }

        public String primaryFullName() {
            return (!isGroup && !participantFullNames.isEmpty()) ? participantFullNames.get(0) : "";
        }

        JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("threadId", threadId);
                if (!accountId.isEmpty()) obj.put("accountId", accountId);
                obj.put("isGroup", isGroup);
                if (!label.isEmpty()) obj.put("label", label);
                obj.put("participantIds", new JSONArray(participantIds));
                obj.put("participantUsernames", new JSONArray(participantUsernames));
                obj.put("participantFullNames", new JSONArray(participantFullNames));
                obj.put("lastSeen", lastSeen);
            } catch (Exception ignored) {
            }
            return obj;
        }

        static KnownThread fromJson(JSONObject obj) {
            if (obj == null) return null;
            String threadId = extractCleanThreadId(obj.optString("threadId", ""));
            if (threadId.isEmpty()) return null;
            return new KnownThread(
                    threadId,
                    obj.optString("accountId", ""),
                    obj.optBoolean("isGroup", false),
                    obj.optString("label", ""),
                    readStringList(obj.optJSONArray("participantIds")),
                    readStringList(obj.optJSONArray("participantUsernames")),
                    readStringList(obj.optJSONArray("participantFullNames")),
                    obj.optLong("lastSeen", 0L)
            );
        }
    }

    public static final class UserDirEntry {
        public final String username;
        public final String fullName;

        public UserDirEntry(String username, String fullName) {
            this.username = normalizeUsername(username);
            this.fullName = clean(fullName);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    /** Normalizes a handle (strips leading @, trims, lowercases). Returns "" if invalid. */
    public static String normalizeUsername(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        while (value.startsWith("@")) {
            value = value.substring(1).trim();
        }
        if (value.isEmpty() || value.contains(" ") || value.contains("\n") || value.contains("\r")) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /** Extracts a bare thread id from "<userId>;thread_id:<chatId>", DirectThreadKey.toString(), or a bare id. */
    public static String extractCleanThreadId(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isEmpty()) return "";
        Matcher matcher = THREAD_KEY_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        int marker = value.indexOf(THREAD_MARKER);
        if (marker >= 0) {
            value = value.substring(marker + THREAD_MARKER.length()).trim();
            int sep = value.indexOf(';');
            if (sep >= 0) value = value.substring(0, sep).trim();
        }
        return value;
    }

    private static List<String> readStringList(JSONArray array) {
        if (array == null || array.length() == 0) return Collections.emptyList();
        List<String> list = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            String value = clean(array.optString(i, ""));
            if (!value.isEmpty()) list.add(value);
        }
        return list;
    }

    static String formatUsernamesLabel(List<String> usernames) {
        List<String> formatted = new ArrayList<>(usernames.size());
        for (String u : usernames) {
            String normalized = normalizeUsername(u);
            if (!normalized.isEmpty()) formatted.add("@" + normalized);
        }
        return joinUpTo(formatted, 3);
    }

    private static String joinUpTo(List<String> items, int max) {
        if (items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(items.size(), max);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(", ");
            sb.append(items.get(i));
        }
        if (items.size() > max) {
            sb.append(" +").append(items.size() - max);
        }
        return sb.toString();
    }

    // ---- Allowed Users (persisted in Piko settings) ----

    public static synchronized List<AllowedUser> getAllowedUsers() {
        List<AllowedUser> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(Pref.dmNotificationAllowedUsers());
            for (int i = 0; i < array.length(); i++) {
                AllowedUser user = AllowedUser.fromJson(array.optJSONObject(i));
                if (user != null) result.add(user);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static synchronized void saveAllowedUsers(List<AllowedUser> users) {
        JSONArray array = new JSONArray();
        for (AllowedUser user : users) {
            array.put(user.toJson());
        }
        Pref.setDmNotificationAllowedUsers(array.toString());
    }

    public static synchronized boolean isUserAllowed(String userId, String username) {
        String cleanId = clean(userId);
        String cleanUsername = normalizeUsername(username);
        if (cleanId.isEmpty() && cleanUsername.isEmpty()) return false;
        for (AllowedUser user : getAllowedUsers()) {
            if (!cleanId.isEmpty() && cleanId.equals(user.userId)) return true;
            if (!cleanUsername.isEmpty() && cleanUsername.equals(user.username)) return true;
        }
        // Also consider a user allowed if their 1:1 chat was explicitly allowlisted before resolution.
        for (KnownThread thread : getKnownThreads()) {
            if (thread.isGroup || !isThreadExplicitlyAllowed(thread.threadId)) continue;
            if (!cleanId.isEmpty() && thread.participantIds.contains(cleanId)) return true;
            if (!cleanUsername.isEmpty() && containsNormalizedUsername(thread.participantUsernames, cleanUsername)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNormalizedUsername(List<String> usernames, String targetNormalized) {
        for (String u : usernames) {
            if (targetNormalized.equals(normalizeUsername(u))) return true;
        }
        return false;
    }

    public static synchronized boolean addAllowedUser(String userId, String username, String fullName) {
        String cleanId = clean(userId);
        String cleanUsername = normalizeUsername(username);
        String cleanFullName = clean(fullName);
        if (cleanId.isEmpty() && cleanUsername.isEmpty()) return false;

        List<AllowedUser> users = getAllowedUsers();
        boolean replaced = false;
        for (int i = 0; i < users.size(); i++) {
            AllowedUser existing = users.get(i);
            boolean sameId = !cleanId.isEmpty() && cleanId.equals(existing.userId);
            boolean sameUsername = !cleanUsername.isEmpty() && cleanUsername.equals(existing.username);
            if (sameId || sameUsername) {
                users.set(i, new AllowedUser(
                        !cleanId.isEmpty() ? cleanId : existing.userId,
                        !cleanUsername.isEmpty() ? cleanUsername : existing.username,
                        !cleanFullName.isEmpty() ? cleanFullName : existing.fullName
                ));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            users.add(new AllowedUser(cleanId, cleanUsername, cleanFullName));
        }
        saveAllowedUsers(users);
        if (!cleanId.isEmpty() && (!cleanUsername.isEmpty() || !cleanFullName.isEmpty())) {
            putUserDirEntry(cleanId, cleanUsername, cleanFullName);
        }
        return true;
    }

    public static synchronized boolean removeAllowedUser(String userId, String username) {
        String cleanId = clean(userId);
        String cleanUsername = normalizeUsername(username);
        if (cleanId.isEmpty() && cleanUsername.isEmpty()) return false;

        List<AllowedUser> users = getAllowedUsers();
        boolean removed = false;
        for (Iterator<AllowedUser> it = users.iterator(); it.hasNext(); ) {
            AllowedUser user = it.next();
            boolean matchId = !cleanId.isEmpty() && cleanId.equals(user.userId);
            boolean matchUsername = !cleanUsername.isEmpty() && cleanUsername.equals(user.username);
            if (matchId || matchUsername) {
                it.remove();
                removed = true;
            }
        }
        if (removed) saveAllowedUsers(users);
        // Remove any stale explicit 1:1 thread allowlist entries for this user as well.
        for (KnownThread thread : getKnownThreads()) {
            if (thread.isGroup || !isThreadExplicitlyAllowed(thread.threadId)) continue;
            boolean matchId = !cleanId.isEmpty() && thread.participantIds.contains(cleanId);
            boolean matchUsername = !cleanUsername.isEmpty()
                    && containsNormalizedUsername(thread.participantUsernames, cleanUsername);
            if (matchId || matchUsername) {
                if (removeAllowedThread(thread.threadId)) removed = true;
            }
        }
        return removed;
    }

    /** Enriches an already-allowed username entry with userId/fullName once learned from inbox/REST. */
    private static synchronized void enrichAllowedUserIfPresent(String userId, String username, String fullName) {
        String cleanId = clean(userId);
        String cleanUsername = normalizeUsername(username);
        String cleanFullName = clean(fullName);
        if (cleanId.isEmpty() && cleanUsername.isEmpty()) return;

        List<AllowedUser> users = getAllowedUsers();
        boolean changed = false;
        for (int i = 0; i < users.size(); i++) {
            AllowedUser existing = users.get(i);
            boolean matchId = !cleanId.isEmpty() && cleanId.equals(existing.userId);
            boolean matchUsername = !cleanUsername.isEmpty() && cleanUsername.equals(existing.username);
            if (!matchId && !matchUsername) continue;

            String newId = !cleanId.isEmpty() ? cleanId : existing.userId;
            String newUsername = !cleanUsername.isEmpty() ? cleanUsername : existing.username;
            String newFullName = !cleanFullName.isEmpty() ? cleanFullName : existing.fullName;
            if (!newId.equals(existing.userId)
                    || !newUsername.equals(existing.username)
                    || !newFullName.equals(existing.fullName)) {
                users.set(i, new AllowedUser(newId, newUsername, newFullName));
                changed = true;
            }
        }
        if (changed) saveAllowedUsers(users);
    }

    // ---- Allowed Threads (persisted in Piko settings) ----

    public static synchronized List<AllowedThread> getAllowedThreads() {
        List<AllowedThread> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(Pref.dmNotificationAllowedThreads());
            for (int i = 0; i < array.length(); i++) {
                AllowedThread thread = AllowedThread.fromJson(array.optJSONObject(i));
                if (thread != null) result.add(thread);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static synchronized void saveAllowedThreads(List<AllowedThread> threads) {
        JSONArray array = new JSONArray();
        for (AllowedThread thread : threads) {
            array.put(thread.toJson());
        }
        Pref.setDmNotificationAllowedThreads(array.toString());
    }

    public static synchronized boolean isThreadExplicitlyAllowed(String threadId) {
        String cleanId = extractCleanThreadId(threadId);
        if (cleanId.isEmpty()) return false;
        for (AllowedThread thread : getAllowedThreads()) {
            if (cleanId.equals(thread.threadId)) return true;
        }
        return false;
    }

    public static synchronized boolean addAllowedThread(String threadId, String label, boolean isGroup) {
        String cleanId = extractCleanThreadId(threadId);
        if (cleanId.isEmpty()) return false;
        String cleanLabel = clean(label);

        List<AllowedThread> threads = getAllowedThreads();
        boolean replaced = false;
        for (int i = 0; i < threads.size(); i++) {
            AllowedThread existing = threads.get(i);
            if (cleanId.equals(existing.threadId)) {
                threads.set(i, new AllowedThread(
                        cleanId,
                        !cleanLabel.isEmpty() ? cleanLabel : existing.label,
                        isGroup || existing.isGroup
                ));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            threads.add(new AllowedThread(cleanId, cleanLabel, isGroup));
        }
        saveAllowedThreads(threads);
        return true;
    }

    public static synchronized boolean removeAllowedThread(String threadId) {
        String cleanId = extractCleanThreadId(threadId);
        if (cleanId.isEmpty()) return false;

        List<AllowedThread> threads = getAllowedThreads();
        boolean removed = false;
        for (Iterator<AllowedThread> it = threads.iterator(); it.hasNext(); ) {
            if (cleanId.equals(it.next().threadId)) {
                it.remove();
                removed = true;
            }
        }
        if (removed) saveAllowedThreads(threads);
        return removed;
    }

    // ---- Known Threads & User Directory Cache (separate SharedPreferences) ----

    public static synchronized List<KnownThread> getKnownThreads() {
        List<KnownThread> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(CACHE.getString(KEY_KNOWN_THREADS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                KnownThread thread = KnownThread.fromJson(array.optJSONObject(i));
                if (thread != null) result.add(thread);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public static synchronized KnownThread findKnownThread(String threadId) {
        String cleanId = extractCleanThreadId(threadId);
        if (cleanId.isEmpty()) return null;
        for (KnownThread thread : getKnownThreads()) {
            if (cleanId.equals(thread.threadId)) return thread;
        }
        return null;
    }

    public static synchronized void upsertKnownThreads(List<KnownThread> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        Map<String, KnownThread> byId = new LinkedHashMap<>();
        for (KnownThread existing : getKnownThreads()) {
            byId.put(existing.threadId, existing);
        }
        for (KnownThread item : incoming) {
            if (item == null || item.threadId.isEmpty()) continue;
            KnownThread previous = byId.get(item.threadId);
            KnownThread merged = mergeKnownThread(previous, item);
            byId.put(merged.threadId, merged);
            // Keep allowed user entries enriched with newly learned ids/full names.
            int count = Math.max(merged.participantIds.size(), merged.participantUsernames.size());
            for (int i = 0; i < count; i++) {
                String uid = i < merged.participantIds.size() ? merged.participantIds.get(i) : "";
                String uname = i < merged.participantUsernames.size() ? merged.participantUsernames.get(i) : "";
                String fname = i < merged.participantFullNames.size() ? merged.participantFullNames.get(i) : "";
                enrichAllowedUserIfPresent(uid, uname, fname);
            }
        }

        List<KnownThread> sorted = new ArrayList<>(byId.values());
        Collections.sort(sorted, new Comparator<KnownThread>() {
            @Override
            public int compare(KnownThread a, KnownThread b) {
                return Long.compare(b.lastSeen, a.lastSeen);
            }
        });
        if (sorted.size() > MAX_KNOWN_THREADS) {
            sorted = new ArrayList<>(sorted.subList(0, MAX_KNOWN_THREADS));
        }

        JSONArray array = new JSONArray();
        for (KnownThread thread : sorted) {
            array.put(thread.toJson());
        }
        CACHE.setString(KEY_KNOWN_THREADS, array.toString());
    }

    private static KnownThread mergeKnownThread(KnownThread previous, KnownThread next) {
        if (previous == null) return next;
        List<String> ids = !next.participantIds.isEmpty() ? next.participantIds : previous.participantIds;
        List<String> usernames = !next.participantUsernames.isEmpty()
                ? next.participantUsernames : previous.participantUsernames;
        List<String> fullNames = !next.participantFullNames.isEmpty()
                ? next.participantFullNames : previous.participantFullNames;
        String label = !next.label.isEmpty() ? next.label : previous.label;
        String accountId = !next.accountId.isEmpty() ? next.accountId : previous.accountId;
        boolean isGroup = next.isGroup || (ids.size() > 1);
        long lastSeen = Math.max(previous.lastSeen, next.lastSeen);
        return new KnownThread(next.threadId, accountId, isGroup, label, ids, usernames, fullNames, lastSeen);
    }

    public static synchronized Map<String, UserDirEntry> loadUserDir() {
        Map<String, UserDirEntry> map = new HashMap<>();
        try {
            JSONObject obj = new JSONObject(CACHE.getString(KEY_USER_DIR, "{}"));
            for (Iterator<String> keys = obj.keys(); keys.hasNext(); ) {
                String uid = keys.next();
                JSONObject entry = obj.optJSONObject(uid);
                if (entry == null) continue;
                String u = normalizeUsername(entry.optString("u", ""));
                String n = clean(entry.optString("n", ""));
                if (!u.isEmpty() || !n.isEmpty()) {
                    map.put(uid, new UserDirEntry(u, n));
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    public static synchronized void putUserDirEntries(Map<String, UserDirEntry> updates) {
        if (updates == null || updates.isEmpty()) return;
        Map<String, UserDirEntry> dir = loadUserDir();
        boolean changed = false;
        for (Map.Entry<String, UserDirEntry> entry : updates.entrySet()) {
            String uid = clean(entry.getKey());
            UserDirEntry value = entry.getValue();
            if (uid.isEmpty() || value == null) continue;
            if (value.username.isEmpty() && value.fullName.isEmpty()) continue;
            UserDirEntry existing = dir.get(uid);
            String mergedUser = !value.username.isEmpty() ? value.username
                    : (existing != null ? existing.username : "");
            String mergedName = !value.fullName.isEmpty() ? value.fullName
                    : (existing != null ? existing.fullName : "");
            if (existing == null || !mergedUser.equals(existing.username) || !mergedName.equals(existing.fullName)) {
                dir.put(uid, new UserDirEntry(mergedUser, mergedName));
                changed = true;
            }
            enrichAllowedUserIfPresent(uid, mergedUser, mergedName);
        }
        if (!changed) return;

        JSONObject root = new JSONObject();
        int count = 0;
        for (Map.Entry<String, UserDirEntry> entry : dir.entrySet()) {
            if (count++ >= MAX_USER_DIR_ENTRIES) break;
            try {
                JSONObject item = new JSONObject();
                if (!entry.getValue().username.isEmpty()) item.put("u", entry.getValue().username);
                if (!entry.getValue().fullName.isEmpty()) item.put("n", entry.getValue().fullName);
                root.put(entry.getKey(), item);
            } catch (Exception ignored) {
            }
        }
        CACHE.setString(KEY_USER_DIR, root.toString());
    }

    public static void putUserDirEntry(String userId, String username, String fullName) {
        String cleanId = clean(userId);
        if (cleanId.isEmpty()) return;
        Map<String, UserDirEntry> single = new HashMap<>(1);
        single.put(cleanId, new UserDirEntry(username, fullName));
        putUserDirEntries(single);
    }

    // ---- Matching & Toggle Logic ----

    public static synchronized MatchReason matchKnownThread(
            KnownThread thread,
            boolean allowGroupsWithAllowedUsers
    ) {
        if (thread == null || thread.threadId.isEmpty()) return MatchReason.NONE;
        if (isThreadExplicitlyAllowed(thread.threadId)) return MatchReason.EXPLICIT_THREAD;

        boolean hasAllowedParticipant = hasMatchingAllowedParticipant(thread);
        if (!hasAllowedParticipant) return MatchReason.NONE;

        if (!thread.isGroup) return MatchReason.ALLOWED_USER;
        return allowGroupsWithAllowedUsers ? MatchReason.GROUP_MEMBER : MatchReason.NONE;
    }

    private static boolean hasMatchingAllowedParticipant(KnownThread thread) {
        List<AllowedUser> allowedUsers = getAllowedUsers();
        if (allowedUsers.isEmpty()) return false;

        Set<String> allowedIds = new LinkedHashSet<>();
        Set<String> allowedNames = new LinkedHashSet<>();
        for (AllowedUser user : allowedUsers) {
            if (!user.userId.isEmpty()) allowedIds.add(user.userId);
            if (!user.username.isEmpty()) allowedNames.add(user.username);
        }

        for (String id : thread.participantIds) {
            if (allowedIds.contains(id)) return true;
        }
        for (String username : thread.participantUsernames) {
            String normalized = normalizeUsername(username);
            if (!normalized.isEmpty() && allowedNames.contains(normalized)) return true;
        }
        return false;
    }

    /**
     * Toggles a known chat from the manager UI.
     * For 1:1 chats with a resolved user, toggles the person entry (and clears any redundant thread entry)
     * so unchecking always cleanly mutes that person without leaving duplicate rules behind.
     */
    public static synchronized boolean toggleKnownThread(KnownThread thread) {
        if (thread == null || thread.threadId.isEmpty()) return false;
        boolean currentlyAllowed = matchKnownThread(thread, false) != MatchReason.NONE;
        boolean hasPrimaryPerson = !thread.isGroup
                && (!thread.primaryUserId().isEmpty() || !thread.primaryUsername().isEmpty());

        if (currentlyAllowed) {
            removeAllowedThread(thread.threadId);
            if (hasPrimaryPerson) {
                removeAllowedUser(thread.primaryUserId(), thread.primaryUsername());
            }
            return false;
        } else {
            if (hasPrimaryPerson) {
                addAllowedUser(thread.primaryUserId(), thread.primaryUsername(), thread.primaryFullName());
            } else {
                addAllowedThread(thread.threadId, thread.displayTitle(), thread.isGroup);
            }
            return true;
        }
    }

    /** Primary decision used in MessageNotificationFingerprint when only threadId is available. */
    public static synchronized Decision evaluateThreadDecision(
            String rawThreadId,
            boolean allowGroupsWithAllowedUsers
    ) {
        String threadId = extractCleanThreadId(rawThreadId);
        if (threadId.isEmpty()) return Decision.UNKNOWN_THREAD;

        if (isThreadExplicitlyAllowed(threadId)) return Decision.ALLOW;

        KnownThread known = findKnownThread(threadId);
        if (known != null) {
            return matchKnownThread(known, allowGroupsWithAllowedUsers) != MatchReason.NONE
                    ? Decision.ALLOW
                    : Decision.MUTE;
        }
        return Decision.UNKNOWN_THREAD;
    }

    public static synchronized boolean hasAnyAllowedUsers() {
        return !getAllowedUsers().isEmpty();
    }

    /** Fallback matcher for unseen threads using the system notification's sender/title text. */
    public static synchronized boolean matchesAllowedSenderText(String senderText) {
        if (senderText == null) return false;
        String trimmed = senderText.trim();
        if (trimmed.isEmpty()) return false;

        String normalizedHandle = normalizeUsername(trimmed);
        String lowerText = trimmed.toLowerCase(Locale.ROOT);
        for (AllowedUser user : getAllowedUsers()) {
            if (!user.username.isEmpty()) {
                if (user.username.equals(normalizedHandle) || user.username.equals(lowerText)) {
                    return true;
                }
            }
            if (!user.fullName.isEmpty() && user.fullName.equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    /** Evaluates whether a deleted-message notification should be allowed. */
    public static synchronized boolean isDeletionAllowed(
            String rawThreadId,
            String senderId,
            String senderUsername,
            boolean allowGroupsWithAllowedUsers
    ) {
        String threadId = extractCleanThreadId(rawThreadId);
        if (!threadId.isEmpty() && isThreadExplicitlyAllowed(threadId)) {
            return true;
        }

        KnownThread known = !threadId.isEmpty() ? findKnownThread(threadId) : null;
        if (known != null) {
            if (known.isGroup) {
                if (!allowGroupsWithAllowedUsers) return false;
                return isUserAllowed(senderId, senderUsername)
                        || hasMatchingAllowedParticipant(known);
            }
            return isUserAllowed(senderId, senderUsername)
                    || hasMatchingAllowedParticipant(known);
        }

        return isUserAllowed(senderId, senderUsername);
    }

    /** Human-readable summary shown on the "Manage allowed people & chats" row in Piko DM settings. */
    public static synchronized String summaryText() {
        int users = getAllowedUsers().size();
        int threads = getAllowedThreads().size();
        if (users == 0 && threads == 0) {
            return str("piko_dm_notif_summary_empty");
        }
        if (users > 0 && threads > 0) {
            return String.format(str("piko_dm_notif_summary_both"), users, threads);
        }
        if (users > 0) {
            return String.format(str("piko_dm_notif_summary_people"), users);
        }
        return String.format(str("piko_dm_notif_summary_chats"), threads);
    }
}
