package app.morphe.extension.newx.misc;

import androidx.annotation.Nullable;

import kotlin.jvm.functions.Function0;

import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.shared.ResourceUtils;

/**
 * Runtime hooks for the optional Messages and Grok drawer shortcuts. The tab component and the
 * exact navigation enum are release-specific, so the patch captures the component once and
 * replaces the {@code open} bodies with direct smali invokes at patch time.
 */
public final class DrawerTabOpener {
    private static volatile Object component;

    private static final Function0<Object> MESSAGES_CLICK =
            new Function0<Object>() {
                @Override
                public Object invoke() {
                    openMessages();
                    return null;
                }
            };

    private static final Function0<Object> GROK_CLICK =
            new Function0<Object>() {
                @Override
                public Object invoke() {
                    openGrok();
                    return null;
                }
            };

    private static final Function0<Object> NOTIFICATIONS_CLICK =
            new Function0<Object>() {
                @Override
                public Object invoke() {
                    openNotifications();
                    return null;
                }
            };

    private DrawerTabOpener() {
    }

    /** Injection point: captures the tab component that owns the tab change method. */
    public static void setComponent(Object tabComponent) {
        if (tabComponent == null) return;
        component = tabComponent;
    }

    /**
     * Returns {@code null} while the shortcut is disabled. The drawer row is skipped on a
     * null title, which avoids spending a scarce low register on a toggle read in smali.
     */
    @Nullable
    public static String getMessagesTitle() {
        if (!isShortcutEnabled("newx.navigation.show_messages_in_drawer")) return null;
        return ResourceUtils.getStringOrThrow("piko_newx_nav_bar_dm");
    }

    /**
     * Returns {@code null} while the shortcut is disabled. The drawer row is skipped on a
     * null title, which avoids spending a scarce low register on a toggle read in smali.
     */
    @Nullable
    public static String getGrokTitle() {
        if (!isShortcutEnabled("newx.navigation.show_grok_in_drawer")) return null;
        return ResourceUtils.getStringOrThrow("piko_newx_nav_bar_grok");
    }

    /**
     * Returns {@code null} while the shortcut is disabled. The drawer row is skipped on a
     * null title, which avoids spending a scarce low register on a toggle read in smali.
     */
    @Nullable
    public static String getNotificationsTitle() {
        if (!isShortcutEnabled("newx.navigation.show_notifications_in_drawer")) return null;
        return ResourceUtils.getStringOrThrow("piko_newx_nav_bar_notifications");
    }

    private static boolean isShortcutEnabled(String key) {
        if (!SettingsRegistry.isRegistered(key)) return false;
        try {
            return SettingsRegistry.getBooleanOrDefault(key, false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static Function0<?> getMessagesClickHandler() {
        return MESSAGES_CLICK;
    }

    public static Function0<?> getGrokClickHandler() {
        return GROK_CLICK;
    }

    public static Function0<?> getNotificationsClickHandler() {
        return NOTIFICATIONS_CLICK;
    }

    /** Patch-time replaced: opens the Messages tab and closes the drawer. */
    public static void openMessages() {
    }

    /** Patch-time replaced: opens the Grok tab and closes the drawer. */
    public static void openGrok() {
    }

    /** Patch-time replaced: opens the Notifications tab and closes the drawer. */
    public static void openNotifications() {
    }
}
