/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.xlite.misc;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;

import java.io.File;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.xlite.settings.SettingsActionHandler;
import app.morphe.extension.xlite.settings.SettingsRegistry;

public class UpdateFont {
    public static final String FONT_FILE_NAME = "custom_font.ttf";
    public static final String EMOJI_FONT_FILE_NAME = "custom_emoji_font.ttf";

    private static Typeface textTypeface;
    private static Typeface emojiTypeface;
    private static final Context context;
    private static final boolean isCustomFontEnabled;
    private static final boolean isCustomEmojiFontEnabled;

    static {
        context = app.morphe.extension.shared.Utils.getContext();
        isCustomFontEnabled = SettingsRegistry.getBoolean("xlite.content.custom_font.enabled");
        isCustomEmojiFontEnabled = SettingsRegistry.getBoolean("xlite.content.custom_emoji_font.enabled");

        if (isCustomFontEnabled) {
            loadFont(FONT_FILE_NAME, false);
        }
        if (isCustomEmojiFontEnabled) {
            loadFont(EMOJI_FONT_FILE_NAME, true);
        }
    }

    public static void loadFont(String fontName, boolean isEmojiFont) {
        File fontFile = new File(context.getFilesDir(), fontName);
        if (!fontFile.exists()) {
            PikoUtils.logger("Font not found: " + fontFile.getAbsolutePath());
        } else {
            Typeface typeface = Typeface.createFromFile(fontFile);
            if (isEmojiFont) {
                emojiTypeface = typeface;
            } else {
                textTypeface = typeface;
            }
            PikoUtils.logger("Font loaded: " + fontFile.getAbsolutePath());
        }
    }

    public static void deleteFont(boolean isEmojiFont) {
        String filename = isEmojiFont ? EMOJI_FONT_FILE_NAME : FONT_FILE_NAME;
        File fontFile = new File(context.getFilesDir(), filename);
        if (fontFile.exists()) {
            if (fontFile.delete()) {
                PikoUtils.toast(str("piko_xlite_font_deleted"));
            } else {
                PikoUtils.toast(str("piko_xlite_font_delete_failed"));
            }
        } else {
            PikoUtils.toast(str("piko_xlite_font_not_found"));
        }
    }

    public static void applyTypeface(Paint paint, Typeface typeface) {
        paint.setTypeface(processTypeface(typeface));
    }

    private static Typeface processTypeface(Typeface original) {
        if (isCustomFontEnabled && textTypeface != null) return textTypeface;
        if (isCustomEmojiFontEnabled && emojiTypeface != null) return emojiTypeface;
        return original;
    }

    public static final class AddFontAction implements SettingsActionHandler {
        @Override
        public void run(Activity activity) {
            openPicker(activity, false);
        }
    }

    public static final class AddEmojiFontAction implements SettingsActionHandler {
        @Override
        public void run(Activity activity) {
            openPicker(activity, true);
        }
    }

    public static final class DeleteFontAction implements SettingsActionHandler {
        @Override
        public void run(Activity activity) {
            deleteFont(false);
        }
    }

    public static final class DeleteEmojiFontAction implements SettingsActionHandler {
        @Override
        public void run(Activity activity) {
            deleteFont(true);
        }
    }

    private static void openPicker(Activity activity, boolean isEmojiFont) {
        Fragment fragment = new FontPickerFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean("isEmojiFont", isEmojiFont);
        fragment.setArguments(arguments);

        int containerId =
                ResourceUtils.getIdentifierOrThrow(
                        activity,
                        ResourceType.ID,
                        "fragment_container"
                );
        activity.getFragmentManager()
                .beginTransaction()
                .replace(containerId, fragment)
                .addToBackStack(null)
                .commit();
    }
}