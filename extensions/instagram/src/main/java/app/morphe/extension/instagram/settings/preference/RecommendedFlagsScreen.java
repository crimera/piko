/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.patches.devFlags.Flag;
import app.morphe.extension.instagram.patches.devFlags.RecommendedFlags;
import app.morphe.extension.instagram.settings.preference.widgets.RecommendedFlagPreference;

import static app.morphe.extension.instagram.utils.IgStr.str;

public final class RecommendedFlagsScreen {
    private static final String PREFIX = Constants.PIKO_FRAGMENT_REC_FLAGS + "_";
    public static final String MODIFIED = PREFIX + "modified";

    private RecommendedFlagsScreen() {}

    public static String fragmentName(Flag.Category category) {
        return PREFIX + category.id;
    }

    public static boolean isFlagsScreen(String name) {
        if (Constants.PIKO_FRAGMENT_REC_FLAGS.equals(name) || MODIFIED.equals(name)) return true;
        for (Flag.Category category : Flag.Category.values()) {
            if (fragmentName(category).equals(name)) return true;
        }
        return false;
    }

    public static String titleKey(String name) {
        if (MODIFIED.equals(name)) return "piko_rec_flags_modified";
        for (Flag.Category category : Flag.Category.values()) {
            if (fragmentName(category).equals(name)) return category.titleKey();
        }
        return "piko_category_rec_flags";
    }

    public static void build(Context context, PreferenceScreen screen, String name, boolean forSearch,
                             List<Flag> flags) {
        Helper helper = new Helper(context);
        if (Constants.PIKO_FRAGMENT_REC_FLAGS.equals(name)) {
            if (!forSearch) {
                for (Flag flag : flags) {
                    if (flag.isModified()) {
                        screen.addPreference(helper.categoryPreference(str("piko_rec_flags_modified"), "", MODIFIED));
                        break;
                    }
                }
            }
            for (Flag.Category category : Flag.Category.values()) {
                for (Flag flag : flags) {
                    if (flag.getCategory() == category) {
                        screen.addPreference(helper.categoryPreference(str(category.titleKey()), "", fragmentName(category)));
                        break;
                    }
                }
            }
            long lastModified = RecommendedFlags.getLastModified();
            String summary = str("piko_rec_flags_download_hint");
            if (lastModified > 0L) {
                Locale locale = context.getResources().getConfiguration().getLocales().get(0);
                String skeleton = DateFormat.is24HourFormat(context) ? "yMMMEdHm" : "yMMMEdhm";
                String pattern = DateFormat.getBestDateTimePattern(locale, skeleton);
                summary = str("piko_rec_flags_last_modified_at",
                        new SimpleDateFormat(pattern, locale).format(new Date(lastModified)));
            }
            screen.addPreference(helper.buttonPreference(str("piko_rec_flags_refresh_file"), summary,
                    "piko_rec_flags_refresh_file"));
            return;
        }
        for (Flag flag : flags) {
            if (MODIFIED.equals(name) ? flag.isModified() : fragmentName(flag.getCategory()).equals(name)) {
                screen.addPreference(RecommendedFlagPreference.create(context, flag, MODIFIED.equals(name)));
            }
        }
        if (screen.getPreferenceCount() == 0) {
            Preference empty = helper.buttonPreference(str("piko_settings_search_no_results"), "", null);
            empty.setSelectable(false);
            screen.addPreference(empty);
        }
    }
}
