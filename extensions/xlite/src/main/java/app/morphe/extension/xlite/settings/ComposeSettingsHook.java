package app.morphe.extension.xlite.settings;

import android.content.Context;
import android.content.Intent;

import kotlin.jvm.functions.Function0;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

public final class ComposeSettingsHook {
    private static final Function0<Object> SETTINGS_CLICK_HANDLER = new Function0<Object>() {
        @Override
        public Object invoke() {
            openSettings();
            return null;
        }
    };

    private ComposeSettingsHook() {
    }

    public static boolean isAdditionalResourcesTitle(String title) {
        return title != null
                && ResourceUtils.getStringOrThrow("settings_additional_resources_item_title")
                .equals(title);
    }

    public static String getSettingsTitle() {
        return ResourceUtils.getStringOrThrow("piko_xlite_settings_title");
    }

    public static Function0<?> getSettingsClickHandler() {
        return SETTINGS_CLICK_HANDLER;
    }

    private static void openSettings() {
        try {
            Context context = Utils.getContext();
            if (context == null) throw new IllegalStateException("Shared context is not initialized");
            Intent intent = new Intent(context, XLiteSettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception exception) {
            Logger.printException(() -> "Failed to open X-Lite settings", exception);
            Utils.showToastShort(ResourceUtils.getStringOrThrow("piko_xlite_settings_open_failed"));
        }
    }
}
