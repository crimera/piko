/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import kotlin.jvm.functions.Function0;

import app.morphe.extension.shared.ResourceUtils;

/** Runtime bridge used by the new X-Lite Compose settings screen. */
public final class ComposeSettingsHook {
    private static final Function0<Object> PIKO_SETTINGS_CLICK_HANDLER = new Function0<Object>() {
        @Override
        public Object invoke() {
            ActivityHook.startSettingsActivity();
            return null;
        }
    };

    private ComposeSettingsHook() {
    }

    public static boolean isAdditionalResourcesTitle(String title) {
        return title != null
                && ResourceUtils.getString("settings_additional_resources_item_title").equals(title);
    }

    public static String getPikoSettingsTitle() {
        return ResourceUtils.getString("piko_title_settings");
    }

    public static Function0<?> getPikoSettingsClickHandler() {
        return PIKO_SETTINGS_CLICK_HANDLER;
    }
}
