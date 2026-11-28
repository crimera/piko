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
import java.util.EnumSet;
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
        if (Constants.PIKO_FRAGMENT_REC_FLAGS.equals(name)) {
            updateCategories(context, screen, forSearch, flags);
            return;
        }
        for (Flag flag : flags) {
            if (MODIFIED.equals(name) ? flag.isModified() : fragmentName(flag.getCategory()).equals(name)) {
                screen.addPreference(RecommendedFlagPreference.create(context, flag, MODIFIED.equals(name)));
            }
        }
        if (screen.getPreferenceCount() == 0) {
            Preference empty = new Helper(context).buttonPreference(str("piko_settings_search_no_results"), "", null);
            empty.setSelectable(false);
            screen.addPreference(empty);
        }
    }

    public static void updateCategories(Context context, PreferenceScreen screen, boolean forSearch, List<Flag> flags) {
        Helper helper = new Helper(context);
        EnumSet<Flag.Category> categories = EnumSet.noneOf(Flag.Category.class);
        boolean hasModified = false;
        for (Flag flag : flags) {
            categories.add(flag.getCategory());
            if (!forSearch && !hasModified) hasModified = flag.isModified();
        }
        updateCategory(screen, helper, MODIFIED, "piko_rec_flags_modified", 0, hasModified);
        int order = 1;
        for (Flag.Category category : Flag.Category.values()) {
            updateCategory(screen, helper, fragmentName(category), category.titleKey(), order++, categories.contains(category));
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
        String refreshKey = "piko_rec_flags_refresh_file";
        Preference refresh = screen.findPreference(refreshKey);
        if (refresh == null) {
            refresh = helper.buttonPreference(str(refreshKey), summary, refreshKey);
            refresh.setOrder(order);
            screen.addPreference(refresh);
        } else {
            refresh.setSummary(summary);
        }
    }

    private static void updateCategory(PreferenceScreen screen, Helper helper, String key, String titleKey,
                                       int order, boolean visible) {
        Preference row = screen.findPreference(key);
        if (visible && row == null) {
            row = helper.categoryPreference(str(titleKey), "", key);
            row.setOrder(order);
            screen.addPreference(row);
        } else if (!visible && row != null) {
            screen.removePreference(row);
        }
    }
}
