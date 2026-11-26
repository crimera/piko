/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.newx.misc;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.crimera.settings.SettingsRegistry;
import app.morphe.extension.newx.settings.NewXLogger;

/**
 * Filters {@code UrpTimeline} lists for the NewX Explore screen only.
 *
 * <p>{@code UrpComponent} is shared by multiple URP surfaces (Explore, Profile Connections,
 * Connect, Community Notes). Only repositories registered from {@code ExploreComponent} are
 * filtered, and fields are read through patch-injected bytecode bridges (no runtime reflection).
 */
public final class ExploreTabFilter {
    private static final String SETTING_HIDDEN_TABS = "newx.explore.hidden_tabs";

    private static final Set<Object> EXPLORE_REPOSITORIES =
            Collections.newSetFromMap(new WeakHashMap<>());

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
            new TabRule("for_you", new String[]{"for_you", "foryou", "explore_for_you"}, new String[]{"For You", "話題を検索"}),
            new TabRule("trending", new String[]{"trending", "explore_trending"}, new String[]{"Trending", "トレンド"}),
            new TabRule("news", new String[]{"news", "explore_news"}, new String[]{"News", "ニュース"}),
            new TabRule("sports", new String[]{"sports", "explore_sports"}, new String[]{"Sports", "スポーツ"}),
            new TabRule("entertainment", new String[]{"entertainment", "explore_entertainment"}, new String[]{"Entertainment", "エンターテイメント"}),
    };

    private ExploreTabFilter() {
    }

    public static void registerExploreRepository(@Nullable Object repository) {
        if (repository == null) return;
        synchronized (EXPLORE_REPOSITORIES) {
            EXPLORE_REPOSITORIES.add(repository);
        }
    }

    private static boolean isExploreRepository(@Nullable Object repository) {
        if (repository == null) return false;
        synchronized (EXPLORE_REPOSITORIES) {
            return EXPLORE_REPOSITORIES.contains(repository);
        }
    }

    @Nullable
    public static List<Object> filterForComponent(
            @Nullable Object urpComponent,
            @Nullable List<Object> tabs
    ) {
        if (urpComponent == null || tabs == null || tabs.isEmpty()) return tabs;
        try {
            if (!isExploreRepository(repositoryOf(urpComponent))) return tabs;

            Set<String> hiddenKeys = SettingsRegistry.getStringSetOrDefault(SETTING_HIDDEN_TABS);
            if (hiddenKeys.isEmpty()) return tabs;

            List<TabRule> hidden = new ArrayList<>(hiddenKeys.size());
            for (TabRule rule : TAB_RULES) {
                if (hiddenKeys.contains(rule.key)) hidden.add(rule);
            }
            if (hidden.isEmpty()) return tabs;

            List<Object> filtered = new ArrayList<>(tabs.size());
            for (Object tab : tabs) {
                if (tab != null && isHidden(tab, hidden)) continue;
                filtered.add(tab);
            }
            return filtered.size() == tabs.size() ? tabs : filtered;
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to customize NewX explore tabs", exception);
            return tabs;
        }
    }

    /** Clamps Decompose's Pages selectedIndex so mid-session list shrinkage never crashes. */
    public static int clampSelectedIndex(int selectedIndex, @Nullable List<?> tabs) {
        if (tabs == null || tabs.isEmpty() || selectedIndex < 0) return 0;
        return Math.min(selectedIndex, tabs.size() - 1);
    }

    private static boolean isHidden(Object tab, List<TabRule> hidden) {
        Object scribeConfig = scribeConfigOf(tab);
        String scribeSection = scribeConfig == null ? null : scribeSectionOf(scribeConfig);
        String segmentedId = segmentedTimelineIdOf(tab);
        String title = titleOf(tab);

        for (TabRule rule : hidden) {
            if (matchesId(scribeSection, rule)
                    || matchesId(segmentedId, rule)
                    || matchesTitle(title, rule)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesId(@Nullable String rawValue, TabRule rule) {
        if (rawValue == null) return false;
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return false;
        for (String alias : rule.idAliases) {
            if (normalized.equals(alias)
                    || normalized.endsWith("_" + alias)
                    || normalized.endsWith("-" + alias)
                    || normalized.endsWith("/" + alias)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesTitle(@Nullable String rawTitle, TabRule rule) {
        if (rawTitle == null) return false;
        String trimmed = rawTitle.trim();
        if (trimmed.isEmpty()) return false;
        for (String alias : rule.titleAliases) {
            if (trimmed.equalsIgnoreCase(alias)) return true;
        }
        return false;
    }

    // Patch-time bridge placeholders replaced with direct field reads.
    @Nullable static Object repositoryOf(Object urpComponent) { return null; }
    @Nullable static String titleOf(Object urpTimeline) { return null; }
    @Nullable static String segmentedTimelineIdOf(Object urpTimeline) { return null; }
    @Nullable static Object scribeConfigOf(Object urpTimeline) { return null; }
    @Nullable static String scribeSectionOf(Object scribeConfig) { return null; }
}
