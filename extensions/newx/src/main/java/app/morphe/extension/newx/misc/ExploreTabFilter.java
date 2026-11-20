/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.newx.misc;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.morphe.extension.crimera.settings.SettingsRegistry;
import app.morphe.extension.newx.settings.NewXLogger;

/**
 * Filters the UrpTimeline list that NewX builds for its tabbed URP surfaces.
 *
 * <p>The patched factory converts the explore_page GraphQL response into one
 * UrpTimeline per Explore tab. An item is matched against a hidden tab through
 * every string it carries (title, timeline id, segmented timeline id and the
 * scribe page/section), so the exact server-side naming stays an
 * implementation detail. Matching is fail-open on errors only: if filtering
 * itself throws, the original list is returned unchanged. Hiding every tab
 * is a valid choice and produces an empty list.
 */
public final class ExploreTabFilter {
    private static final String SETTING_PREFIX = "newx.explore_tabs.hide_";

    private static final class TabRule {
        final String key;
        final String[] idAliases;
        final String[] titleAliases;

        TabRule(String key, String[] idAliases, String[] titleAliases) {
            this.key = key;
            this.idAliases = idAliases;
            this.titleAliases = titleAliases;
        }
    }

    private static final TabRule[] TAB_RULES = {
            new TabRule(
                    "for_you",
                    new String[]{"for_you", "foryou", "explore_for_you"},
                    new String[]{"For You", "話題を検索"}),
            new TabRule(
                    "trending",
                    new String[]{"trending", "explore_trending"},
                    new String[]{"Trending", "トレンド"}),
            new TabRule(
                    "news",
                    new String[]{"news"},
                    new String[]{"News", "ニュース"}),
            new TabRule(
                    "sports",
                    new String[]{"sports", "explore_sports"},
                    new String[]{"Sports", "スポーツ"}),
            new TabRule(
                    "entertainment",
                    new String[]{"entertainment"},
                    new String[]{"Entertainment", "エンターテイメント"}),
    };

    private ExploreTabFilter() {
    }

    public static List filter(List tabs) {
        if (tabs == null || tabs.isEmpty()) return tabs;

        try {
            List<TabRule> hidden = new ArrayList<>();
            for (TabRule rule : TAB_RULES) {
                if (SettingsRegistry.getBooleanOrDefault(SETTING_PREFIX + rule.key, false)) {
                    hidden.add(rule);
                }
            }
            if (hidden.isEmpty()) return tabs;

            List<Object> filtered = new ArrayList<>(tabs.size());
            for (Object tab : tabs) {
                if (tab != null && isHidden(tab, hidden)) continue;
                filtered.add(tab);
            }
            if (filtered.size() == tabs.size()) return tabs;
            return filtered;
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to customize NewX explore tabs", exception);
            return tabs;
        }
    }

    private static boolean isHidden(Object tab, List<TabRule> hidden) {
        Set<String> values = collectStrings(tab);
        String representation = String.valueOf(tab);
        for (TabRule rule : hidden) {
            for (String value : values) {
                if (matches(value, rule)) return true;
            }
            if (matchesRepresentation(representation, rule)) return true;
        }
        return false;
    }

    private static boolean matches(String value, TabRule rule) {
        String trimmed = value.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        for (String alias : rule.idAliases) {
            if (normalized.equals(alias)
                    || normalized.endsWith("_" + alias)
                    || normalized.endsWith("-" + alias)
                    || normalized.endsWith("/" + alias)) {
                return true;
            }
        }
        for (String title : rule.titleAliases) {
            if (trimmed.equalsIgnoreCase(title)) return true;
        }
        return false;
    }

    /** Fallback for when reflective field access yields nothing (e.g. UrpTimeline's toString). */
    private static boolean matchesRepresentation(String representation, TabRule rule) {
        for (String alias : rule.idAliases) {
            if (representation.contains("=" + alias + ",")
                    || representation.contains("=" + alias + ")")
                    || representation.contains("=" + alias + "]")) {
                return true;
            }
        }
        for (String title : rule.titleAliases) {
            if (representation.contains("title=" + title + ",")) return true;
        }
        return false;
    }

    /**
     * Collects the string fields of the item itself plus the string fields of
     * one level of nested app objects (the scribe config), without depending
     * on obfuscated field names.
     */
    private static Set<String> collectStrings(Object item) {
        Set<String> values = new LinkedHashSet<>();
        collectStrings(item, values, 0);
        return values;
    }

    private static void collectStrings(Object item, Set<String> values, int depth) {
        if (item == null || depth > 1) return;
        Field[] fields;
        try {
            fields = item.getClass().getDeclaredFields();
        } catch (Exception ignored) {
            return;
        }
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(item);
                if (value instanceof String string) {
                    values.add(string);
                } else if (value != null
                        && depth == 0
                        && value.getClass().getName().startsWith("com.x.")) {
                    collectStrings(value, values, depth + 1);
                }
            } catch (Exception ignored) {
                // Skip fields that cannot be read and keep the rest.
            }
        }
    }
}
