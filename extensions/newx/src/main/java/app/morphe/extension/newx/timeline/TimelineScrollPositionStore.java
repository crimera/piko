package app.morphe.extension.newx.timeline;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.crimera.settings.SettingsRegistry;

import java.util.concurrent.ConcurrentHashMap;

public final class TimelineScrollPositionStore {
    private static final String PREFERENCES_NAME = "piko_newx_timeline_positions";
    private static final String RESTORE_TIMELINE_POSITION_SETTING =
            "newx.timeline.restore_position";
    private static final String RESTORE_PROFILE_POSITION_SETTING =
            "newx.profile.restore_position";
    private static final String INDEX_SUFFIX = ".index";
    private static final String OFFSET_SUFFIX = ".offset";
    private static final String ENTRY_ID_SUFFIX = ".entry_id";
    private static final String SORT_INDEX_SUFFIX = ".sort_index";
    private static final String ITEM_INDEX_SUFFIX = ".item_index";
    private static final String PROFILE_KEY_PREFIX = "profile.";
    private static final String TIMELINE_KEY_PREFIX = "timeline.";
    private static final Object SAVE_LOCK = new Object();
    private static volatile SharedPreferences cachedPreferences;
    private static volatile long lastAccountId;
    private static SharedPreferences lastSavedPreferences;
    private static String lastSavedKey;
    private static int lastSavedIndex = -1;
    private static int lastSavedOffset = -1;
    private static SharedPreferences lastAnchorPreferences;
    private static String lastAnchorKey;
    private static String lastAnchorEntryId;
    private static long lastAnchorSortIndex;
    private static int lastAnchorItemIndex;
    private static boolean hasLastAnchor;
    private static String stagedEntryId;
    private static long stagedSortIndex;
    private static final ConcurrentHashMap<String, PendingAnchor> pendingAnchors =
            new ConcurrentHashMap<>();

    private TimelineScrollPositionStore() {
    }

    /** Cheap admission check before the injected bridge builds a native resume anchor. */
    public static boolean shouldSaveAnchor(@Nullable Enum<?> timeline, long accountId) {
        if (timeline == null || accountId <= 0) return false;
        String name = timeline.name();
        try {
            if (isHomeTimeline(name) || isPerIdTimeline(name)) {
                return SettingsRegistry.getBooleanOrDefault(RESTORE_TIMELINE_POSITION_SETTING, true);
            }
            return name.startsWith("USER_PROFILE_")
                    && SettingsRegistry.getBooleanOrDefault(RESTORE_PROFILE_POSITION_SETTING, false);
        } catch (RuntimeException ignored) {
            // This is only an optimization. Leave error handling to the existing save path.
            return true;
        }
    }

    /** A composition with no custom restore pending needs no repository or account lookups. */
    public static boolean hasPendingAnchor() {
        return !pendingAnchors.isEmpty();
    }

    @Nullable
    public static int[] restore(Enum<?> timeline, @Nullable String profileId) {
        long accountId = lastAccountId;
        if (accountId <= 0) return null;
        // Refresh has no account in scope, so only restore after a scoped hook established one.
        return restoreInternal(timeline, profileId, accountId, false);
    }

    @Nullable
    public static int[] restore(
            Enum<?> timeline,
            @Nullable String profileId,
            long accountId
    ) {
        return restoreInternal(timeline, profileId, accountId, true);
    }

    @Nullable
    private static int[] restoreInternal(
            Enum<?> timeline,
            @Nullable String profileId,
            long accountId,
            boolean armAnchor
    ) {
        String timelineName = timeline == null ? null : timeline.name();
        if (accountId <= 0) {
            if (NewXLogger.isLoggingEnabled()) {
                NewXLogger.logger("NewX restore miss timeline=" + timelineName + " account=" + accountId
                        + " reason=no-account");
            }
            return null;
        }
        lastAccountId = accountId;

        try {
            boolean restoreTimelinePosition = SettingsRegistry.getBooleanOrDefault(
                    RESTORE_TIMELINE_POSITION_SETTING,
                    true
            );
            boolean restoreProfilePosition = SettingsRegistry.getBooleanOrDefault(
                    RESTORE_PROFILE_POSITION_SETTING,
                    false
            );
            String key = accountStorageKey(
                    accountId,
                    timelineName,
                    profileId,
                    restoreTimelinePosition,
                    restoreProfilePosition
            );
            if (NewXLogger.isLoggingEnabled()) {
                NewXLogger.logger("NewX restore timeline=" + timelineName + " profileId=" + profileId
                        + " account=" + accountId
                        + " key=" + key + " restoreTimeline=" + restoreTimelinePosition
                        + " restoreProfile=" + restoreProfilePosition);
            }
            if (key == null) return null;

            SharedPreferences preferences = preferences();
            if (preferences == null) {
                if (armAnchor) pendingAnchors.remove(key);
                if (NewXLogger.isLoggingEnabled()) {
                    NewXLogger.logger("NewX restore miss timeline=" + timelineName + " account=" + accountId
                            + " reason=no-preferences");
                }
                return null;
            }

            // Saved positions are never negative, so -1 doubles as the missing-entry
            // sentinel. One fewer prefs lookup and key concat than contains() + getInt().
            String indexKey = key + INDEX_SUFFIX;
            int index = preferences.getInt(indexKey, -1);
            if (index < 0) {
                if (armAnchor) pendingAnchors.remove(key);
                if (NewXLogger.isLoggingEnabled()) {
                    NewXLogger.logger("NewX restore miss timeline=" + timelineName + " account=" + accountId
                            + " key=" + key
                            + " reason=no-entry");
                }
                return null;
            }

            int offset = preferences.getInt(key + OFFSET_SUFFIX, 0);
            if (armAnchor) {
                String entryId = preferences.getString(key + ENTRY_ID_SUFFIX, null);
                if (entryId == null) {
                    pendingAnchors.remove(key);
                } else {
                    pendingAnchors.put(key, new PendingAnchor(
                            entryId,
                            preferences.getLong(key + SORT_INDEX_SUFFIX, 0L),
                            preferences.getInt(key + ITEM_INDEX_SUFFIX, 0),
                            index,
                            offset
                    ));
                }
            }
            if (NewXLogger.isLoggingEnabled()) {
                NewXLogger.logger("NewX restore hit timeline=" + timelineName + " account=" + accountId
                        + " key=" + key
                        + " index=" + index + " offset=" + offset);
            }
            return new int[]{index, offset};
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to restore NewX timeline position account=" + accountId,
                    exception);
            return null;
        }
    }

    public static void saveAnchor(
            Enum<?> timeline,
            @Nullable String profileId,
            long accountId,
            @Nullable String entryId,
            long sortIndex,
            int itemIndex
    ) {
        boolean loggingEnabled = NewXLogger.isLoggingEnabled();
        String timelineName = timeline == null ? null : timeline.name();
        if (accountId <= 0) return;
        lastAccountId = accountId;
        // X's resume factory can return cursor gaps and prompts; they move or vanish on reload, so
        // relocating to them lands on an unrelated post. Clear the anchor and keep the index restore.
        if (!isPostAnchor(entryId)) entryId = null;

        try {
            String key = accountStorageKeyFor(timelineName, profileId, accountId);
            if (key == null) return;

            SharedPreferences preferences = preferences();
            if (preferences == null) return;

            synchronized (SAVE_LOCK) {
                if (hasLastAnchor && preferences == lastAnchorPreferences && key.equals(lastAnchorKey)
                        && equal(entryId, lastAnchorEntryId)
                        && sortIndex == lastAnchorSortIndex && itemIndex == lastAnchorItemIndex) {
                    return;
                }
                SharedPreferences.Editor editor = preferences.edit();
                if (entryId == null) {
                    editor.remove(key + ENTRY_ID_SUFFIX)
                            .remove(key + SORT_INDEX_SUFFIX)
                            .remove(key + ITEM_INDEX_SUFFIX);
                } else {
                    editor.putString(key + ENTRY_ID_SUFFIX, entryId)
                            .putLong(key + SORT_INDEX_SUFFIX, sortIndex)
                            .putInt(key + ITEM_INDEX_SUFFIX, itemIndex);
                }
                editor.apply();
                lastAnchorPreferences = preferences;
                lastAnchorKey = key;
                lastAnchorEntryId = entryId;
                lastAnchorSortIndex = sortIndex;
                lastAnchorItemIndex = itemIndex;
                hasLastAnchor = true;
            }

            if (loggingEnabled) {
                String detail = entryId == null
                        ? "reason=no-anchor"
                        : "entryId=" + entryId + " sortIndex=" + sortIndex + " itemIndex=" + itemIndex;
                NewXLogger.logger("NewX save-anchor timeline=" + timelineName + " account=" + accountId
                        + " key=" + key + " " + detail);
            }
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to save NewX timeline anchor account=" + accountId,
                    exception);
        }
    }

    @Nullable
    public static String pendingAnchorEntryId(
            Enum<?> timeline,
            @Nullable String profileId,
            long accountId
    ) {
        String key = pendingAnchorKey(timeline, profileId, accountId);
        if (key == null) return null;
        PendingAnchor pending = pendingAnchors.get(key);
        return pending == null ? null : pending.entryId;
    }

    public static long pendingAnchorSortIndex(
            Enum<?> timeline,
            @Nullable String profileId,
            long accountId
    ) {
        String key = pendingAnchorKey(timeline, profileId, accountId);
        if (key == null) return 0L;
        PendingAnchor pending = pendingAnchors.get(key);
        return pending == null ? 0L : pending.sortIndex;
    }

    @Nullable
    public static int[] consumeAnchor(
            Enum<?> timeline,
            @Nullable String profileId,
            long accountId,
            int foundItemIndex
    ) {
        String timelineName = timeline == null ? null : timeline.name();
        String key = pendingAnchorKey(timeline, profileId, accountId);
        if (key == null) return null;
        PendingAnchor pending = pendingAnchors.remove(key);
        if (pending == null) return null;

        boolean loggingEnabled = NewXLogger.isLoggingEnabled();
        if (foundItemIndex < 0) {
            if (loggingEnabled) {
                NewXLogger.logger("NewX anchor timeline=" + timelineName + " account=" + accountId
                        + " found=" + foundItemIndex + " savedItem=" + pending.itemIndex
                        + " savedIndex=" + pending.savedIndex + " reason=post-gone -> top");
            }
            return new int[]{0, 0};
        }

        stagedEntryId = pending.entryId;
        stagedSortIndex = pending.sortIndex;
        if (loggingEnabled) {
            NewXLogger.logger("NewX anchor timeline=" + timelineName + " account=" + accountId
                    + " found=" + foundItemIndex + " savedItem=" + pending.itemIndex
                    + " -> relocate-by-key entryId=" + pending.entryId);
        }
        // Returning the restored position makes X call requestScrollToItem, which forces the remeasure
        // that applies the staged key (written right after that call).
        return new int[]{pending.savedIndex, pending.savedOffset};
    }

    @Nullable
    public static String takeStagedAnchorEntryId() {
        String entryId = stagedEntryId;
        stagedEntryId = null;
        return entryId;
    }

    public static long stagedAnchorSortIndex() {
        return stagedSortIndex;
    }

    @Nullable
    private static String pendingAnchorKey(
            Enum<?> timeline,
            @Nullable String profileId,
            long accountId
    ) {
        if (accountId <= 0) return null;
        try {
            return accountStorageKeyFor(timeline == null ? null : timeline.name(), profileId, accountId);
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to resolve NewX timeline anchor account=" + accountId,
                    exception);
            return null;
        }
    }

    /** URT post entries: plain posts and conversation modules. Promoted posts, cursors and prompts are excluded. */
    static boolean isPostAnchor(@Nullable String entryId) {
        if (entryId == null || entryId.startsWith("promoted-")) return false;
        return entryId.startsWith("tweet-") || entryId.contains("conversation-");
    }

    @Nullable
    private static String accountStorageKeyFor(
            @Nullable String timelineName,
            @Nullable String profileId,
            long accountId
    ) {
        boolean restoreTimelinePosition = SettingsRegistry.getBooleanOrDefault(
                RESTORE_TIMELINE_POSITION_SETTING,
                true
        );
        boolean restoreProfilePosition = SettingsRegistry.getBooleanOrDefault(
                RESTORE_PROFILE_POSITION_SETTING,
                false
        );
        return accountStorageKey(
                accountId,
                timelineName,
                profileId,
                restoreTimelinePosition,
                restoreProfilePosition
        );
    }

    private static boolean equal(@Nullable String first, @Nullable String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static final class PendingAnchor {
        private final String entryId;
        private final long sortIndex;
        private final int itemIndex;
        private final int savedIndex;
        private final int savedOffset;

        private PendingAnchor(
                String entryId,
                long sortIndex,
                int itemIndex,
                int savedIndex,
                int savedOffset
        ) {
            this.entryId = entryId;
            this.sortIndex = sortIndex;
            this.itemIndex = itemIndex;
            this.savedIndex = savedIndex;
            this.savedOffset = savedOffset;
        }
    }

    /** Saves an already decoded position without allocating or reflectively parsing a holder. */
    public static void save(
            Enum<?> timeline,
            @Nullable String profileId,
            long accountId,
            int index,
            int offset
    ) {
        boolean loggingEnabled = NewXLogger.isLoggingEnabled();
        String timelineName = timeline == null ? null : timeline.name();
        if (accountId <= 0) {
            if (loggingEnabled) {
                NewXLogger.logger("NewX save skip timeline=" + timelineName + " account=" + accountId
                        + " reason=no-account");
            }
            return;
        }
        lastAccountId = accountId;

        if (index < 0 || offset < 0) {
            if (loggingEnabled) {
                NewXLogger.logger("NewX save skip timeline=" + timelineName + " account=" + accountId
                        + " reason=negative-position");
            }
            return;
        }

        try {
            boolean restoreTimelinePosition = SettingsRegistry.getBooleanOrDefault(
                    RESTORE_TIMELINE_POSITION_SETTING,
                    true
            );
            boolean restoreProfilePosition = SettingsRegistry.getBooleanOrDefault(
                    RESTORE_PROFILE_POSITION_SETTING,
                    false
            );
            String key = accountStorageKey(
                    accountId,
                    timelineName,
                    profileId,
                    restoreTimelinePosition,
                    restoreProfilePosition
            );
            if (key == null) return;

            SharedPreferences preferences = preferences();
            if (preferences == null) return;

            synchronized (SAVE_LOCK) {
                if (preferences == lastSavedPreferences && key.equals(lastSavedKey)
                        && index == lastSavedIndex && offset == lastSavedOffset) {
                    return;
                }
                preferences.edit()
                        .putInt(key + INDEX_SUFFIX, index)
                        .putInt(key + OFFSET_SUFFIX, offset)
                        .apply();
                lastSavedPreferences = preferences;
                lastSavedKey = key;
                lastSavedIndex = index;
                lastSavedOffset = offset;
            }

            if (loggingEnabled) {
                NewXLogger.logger("NewX save timeline=" + timelineName + " account=" + accountId + " key=" + key
                        + " index=" + index + " offset=" + offset);
            }
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to save NewX timeline position account=" + accountId,
                    exception);
        }
    }

    /**
     * Returns whether X's process-local position map should be trusted for this timeline type.
     *
     * <p>X keys the map by timeline type only, so per-profile timelines must use the persistent
     * store instead. The same holds for per-id timelines (lists, topics, communities) while their
     * persistent restore is enabled: two pinned lists would otherwise share one LIST_POSTS slot.
     * Every other type must keep its native holder: deleting it breaks in-session back navigation
     * (for example CONVERSATION threads) because the persistent store has no entry to replace it
     * with, and X then rebuilds the list at index 0.
     */
    public static boolean useInMemoryPosition(Enum<?> timeline) {
        String timelineName = timeline == null ? null : timeline.name();
        // Only per-id types depend on the toggle, so other timelines skip the settings read.
        boolean restoreTimelinePosition = timelineName != null && isPerIdTimeline(timelineName)
                && SettingsRegistry.getBooleanOrDefault(RESTORE_TIMELINE_POSITION_SETTING, true);
        boolean useInMemory = useInMemoryPosition(timelineName, restoreTimelinePosition);
        if (NewXLogger.isLoggingEnabled()) {
            NewXLogger.logger("NewX in-memory timeline=" + timelineName + " useInMemory=" + useInMemory);
        }
        return useInMemory;
    }

    static boolean useInMemoryPosition(@Nullable String timelineName, boolean restoreTimelinePosition) {
        if (timelineName == null || timelineName.startsWith("USER_PROFILE_")) return false;
        return !(restoreTimelinePosition && isPerIdTimeline(timelineName));
    }

    static String storageKey(
            @Nullable String timelineName,
            @Nullable String profileId,
            boolean restoreTimelinePosition,
            boolean restoreProfilePosition
    ) {
        if (timelineName == null) return null;
        if (isHomeTimeline(timelineName)) {
            return restoreTimelinePosition ? timelineName : null;
        }
        if (isPerIdTimeline(timelineName)) {
            // X builds these identities as the type name plus the list/topic/community id, so a
            // bare type name means no id was available and every timeline of the type would
            // collide on one key.
            if (!restoreTimelinePosition || profileId == null) return null;
            String identity = profileId.trim();
            if (identity.isEmpty() || identity.equals(timelineName)) return null;
            return TIMELINE_KEY_PREFIX + identity;
        }
        if (!restoreProfilePosition || !timelineName.startsWith("USER_PROFILE_")) return null;
        if (profileId == null) return null;
        String trimmedProfileId = profileId.trim();
        if (trimmedProfileId.isEmpty()) return null;
        return PROFILE_KEY_PREFIX + timelineName + "." + trimmedProfileId;
    }

    @Nullable
    static String accountStorageKey(
            long accountId,
            @Nullable String timelineName,
            @Nullable String profileId,
            boolean restoreTimelinePosition,
            boolean restoreProfilePosition
    ) {
        if (accountId <= 0) return null;
        String key = storageKey(
                timelineName,
                profileId,
                restoreTimelinePosition,
                restoreProfilePosition
        );
        if (key == null) return null;
        return "account." + accountId + "." + key;
    }

    private static boolean isHomeTimeline(String timelineName) {
        return "FOR_YOU".equals(timelineName) || "FOLLOWING".equals(timelineName)
                || "RANKED_FOLLOWING".equals(timelineName);
    }

    /** Timelines that can be pinned as home tabs, each keyed by its own list/topic/community id. */
    private static boolean isPerIdTimeline(String timelineName) {
        return "LIST_POSTS".equals(timelineName) || "TOPIC".equals(timelineName)
                || "COMMUNITY_DETAIL_POSTS".equals(timelineName);
    }

    @Nullable
    private static SharedPreferences preferences() {
        SharedPreferences cached = cachedPreferences;
        if (cached != null) return cached;

        Context context = Utils.getContext();
        if (context == null) return null;

        synchronized (TimelineScrollPositionStore.class) {
            cached = cachedPreferences;
            if (cached != null) return cached;
            cached = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
            cachedPreferences = cached;
            return cached;
        }
    }
}
