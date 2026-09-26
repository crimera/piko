/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.patches.navigation.NavigationBarPatch.Tab;
import app.morphe.extension.instagram.utils.Pref;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

final class NavigationSettingsAccessPolicy {
    enum ActionBar {
        MAIN_FEED,
        PROFILE,
        INBOX,
        CHAT
    }

    private NavigationSettingsAccessPolicy() {
    }

    static boolean hasDirectAccess(Set<Tab> requestedVisible) {
        return hasDirectAccess(requestedVisible, actionBarsWithSettings());
    }

    private static boolean hasDirectAccess(
            Set<Tab> requestedVisible,
            Set<ActionBar> actionBarsWithSettings
    ) {
        if (requestedVisible == null || actionBarsWithSettings == null) return false;

        Set<Tab> visible = requestedVisible.isEmpty()
                ? Collections.singleton(Tab.HOME)
                : requestedVisible;
        boolean anyActionBarHasSettings = !actionBarsWithSettings.isEmpty();

        return (visible.contains(Tab.HOME)
                && actionBarsWithSettings.contains(ActionBar.MAIN_FEED))
                || (visible.contains(Tab.PROFILE)
                && (actionBarsWithSettings.contains(ActionBar.PROFILE)
                || !anyActionBarHasSettings))
                || (visible.contains(Tab.MESSAGES)
                && (actionBarsWithSettings.contains(ActionBar.INBOX)
                || actionBarsWithSettings.contains(ActionBar.CHAT)));
    }

    static boolean hasDirectAccessAfterActionBarChange(
            Set<Tab> requestedVisible,
            ActionBar changed,
            boolean proposedHasSettings
    ) {
        if (changed == null) return false;

        EnumSet<ActionBar> actionBarsWithSettings = actionBarsWithSettings();
        if (proposedHasSettings) actionBarsWithSettings.add(changed);
        else actionBarsWithSettings.remove(changed);

        return hasDirectAccess(requestedVisible, actionBarsWithSettings);
    }

    private static EnumSet<ActionBar> actionBarsWithSettings() {
        EnumSet<ActionBar> result = EnumSet.noneOf(ActionBar.class);
        if (Pref.mainFeedActionBarButtons().contains(Constants.AB_SETTINGS_ICON)) {
            result.add(ActionBar.MAIN_FEED);
        }
        if (Pref.userProfileActionBarButtons().contains(Constants.AB_SETTINGS_ICON)) {
            result.add(ActionBar.PROFILE);
        }
        if (Pref.inboxActionBarButtons().contains(Constants.AB_SETTINGS_ICON)) {
            result.add(ActionBar.INBOX);
        }
        if (Pref.chatActionBarButtons().contains(Constants.AB_SETTINGS_ICON)) {
            result.add(ActionBar.CHAT);
        }
        return result;
    }
}
