package app.morphe.extension.newx.timeline;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.List;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.shared.Utils;

/**
 * Reopens the pinned home tab (list, topic, community or generic timeline) the user last viewed.
 *
 * <p>Pinned tabs load after the home pager is created, so the saved tab is held as a pending
 * restore for the process and selected the first time a pinned-tab list containing it arrives.
 * Any user tab change before that cancels it.
 */
public final class PinnedHomeTabRestore {
    private static final String RESTORE_PINNED_TAB_SETTING = "newx.timeline.restore_pinned_tab";
    private static final String PREFERENCES_NAME = "piko_newx_home_tab";
    private static final String PINNED_TAB_KEY = "pinned_tab";
    private static final Object LOCK = new Object();

    private static boolean pendingLoaded;
    @Nullable
    private static String pendingKey;

    /** Implemented on X's home tab route by the patch; returns X's own pinned-tab id or null. */
    public interface PinnedHomeTab {
        @Nullable
        String pikoPinnedTabKey();
    }

    private PinnedHomeTabRestore() {
    }

    /** Called when the user taps or swipes to a home tab, before the selection is applied. */
    public static void onTabChange(int index, int currentIndex) {
        if (index == currentIndex) return;
        synchronized (LOCK) {
            loadPendingLocked();
            if (pendingKey != null && NewXLogger.isLoggingEnabled()) {
                NewXLogger.logger("NewX pinned tab restore cancelled key=" + pendingKey);
            }
            pendingKey = null;
        }
    }

    /** Called with the selected home tab route after a user tab change. */
    public static void onTabSelected(@Nullable Object tab) {
        try {
            if (!isEnabled()) return;
            String key = pinnedTabKey(tab);
            SharedPreferences preferences;
            synchronized (LOCK) {
                loadPendingLocked();
                // A pending restore means the user has not changed tabs yet, so the stored key is
                // still the one being restored; a settle event on the default tab must not erase it.
                if (pendingKey != null) return;
                preferences = preferences();
                if (preferences == null) return;
                if (key == null) {
                    preferences.edit().remove(PINNED_TAB_KEY).apply();
                } else {
                    preferences.edit().putString(PINNED_TAB_KEY, key).apply();
                }
            }
            if (NewXLogger.isLoggingEnabled()) {
                NewXLogger.logger("NewX pinned tab save key=" + key);
            }
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to save NewX pinned home tab", exception);
        }
    }

    /**
     * Returns the index of the pending pinned tab in the routes X just loaded, or -1 to keep X's
     * selection. A hit consumes the pending restore so later pinned-tab updates never reselect it.
     */
    public static int pinnedTabToRestore(@Nullable List<?> tabs) {
        try {
            if (tabs == null || !isEnabled()) return -1;
            String key;
            synchronized (LOCK) {
                loadPendingLocked();
                key = pendingKey;
            }
            if (key == null) return -1;
            int index = indexOfPinnedTab(tabs, key);
            if (index < 0) return -1;
            synchronized (LOCK) {
                // A user tab change may have cancelled the restore while the list was scanned.
                if (!key.equals(pendingKey)) return -1;
                pendingKey = null;
            }
            if (NewXLogger.isLoggingEnabled()) {
                NewXLogger.logger("NewX pinned tab restore key=" + key + " index=" + index);
            }
            return index;
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to restore NewX pinned home tab", exception);
            return -1;
        }
    }

    static int indexOfPinnedTab(List<?> tabs, String key) {
        for (int index = 0; index < tabs.size(); index++) {
            if (key.equals(pinnedTabKey(tabs.get(index)))) return index;
        }
        return -1;
    }

    @Nullable
    private static String pinnedTabKey(@Nullable Object tab) {
        if (!(tab instanceof PinnedHomeTab pinnedHomeTab)) return null;
        String key = pinnedHomeTab.pikoPinnedTabKey();
        return key == null || key.isEmpty() ? null : key;
    }

    private static boolean isEnabled() {
        return SettingsRegistry.getBooleanOrDefault(RESTORE_PINNED_TAB_SETTING, true);
    }

    private static void loadPendingLocked() {
        if (pendingLoaded) return;
        SharedPreferences preferences = preferences();
        // Retry on a later call if the app context is not available yet.
        if (preferences == null) return;
        pendingLoaded = true;
        pendingKey = isEnabled() ? preferences.getString(PINNED_TAB_KEY, null) : null;
        if (NewXLogger.isLoggingEnabled()) {
            NewXLogger.logger("NewX pinned tab pending key=" + pendingKey);
        }
    }

    @Nullable
    private static SharedPreferences preferences() {
        Context context = Utils.getContext();
        if (context == null) return null;
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
