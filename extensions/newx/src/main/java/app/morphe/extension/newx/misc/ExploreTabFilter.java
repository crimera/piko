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
 * <p>{@code UrpComponent} and {@code UrpPageFragment} are shared by multiple URP surfaces
 * (Explore, Profile Connections, Connect, Community Notes, Topics). The patch registers the
 * {@code UrpRepository} instance created for {@code ExploreComponent} and filters only updates
 * belonging to that repository, reading {@code UrpTimeline} and {@code ScribeConfig} fields
 * through patch-injected bytecode bridges (no runtime reflection).
 *
 * <p>Selected page indices are clamped before {@code Pages} construction so shrinking the tab
 * list mid-session (e.g. when a user defers restarting) never violates Decompose's bounds check.
 * Hiding every tab is allowed and yields an empty list; filtering fails open only on exceptions.
 */
public final class ExploreTabFilter {
    private static final String SETTING_HIDDEN_TABS = "newx.explore.hidden_tabs";

    private static final Object REPOSITORY_LOCK = new Object();
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
                    new String[]{"news", "explore_news"},
                    new String[]{"News", "ニュース"}),
            new TabRule(
                    "sports",
                    new String[]{"sports", "explore_sports"},
                    new String[]{"Sports", "スポーツ"}),
            new TabRule(
                    "entertainment",
                    new String[]{"entertainment", "explore_entertainment"},
                    new String[]{"Entertainment", "エンターテイメント"}),
    };

    interface TabFields {
        @Nullable
        String scribeSection(Object tab);

        @Nullable
        String segmentedTimelineId(Object tab);

        @Nullable
        String timelineId(Object tab);

        @Nullable
        String title(Object tab);
    }

    private static final TabFields BRIDGE_FIELDS = new TabFields() {
        @Override
        @Nullable
        public String scribeSection(Object tab) {
            Object scribeConfig = scribeConfigOf(tab);
            return scribeConfig == null ? null : scribeSectionOf(scribeConfig);
        }

        @Override
        @Nullable
        public String segmentedTimelineId(Object tab) {
            return segmentedTimelineIdOf(tab);
        }

        @Override
        @Nullable
        public String timelineId(Object tab) {
            return timelineIdOf(tab);
        }

        @Override
        @Nullable
        public String title(Object tab) {
            return titleOf(tab);
        }
    };

    private ExploreTabFilter() {
    }

    /** Hook entry point: called from ExploreComponent before creating its UrpComponent. */
    public static void registerExploreRepository(@Nullable Object repository) {
        if (repository == null) return;
        synchronized (REPOSITORY_LOCK) {
            EXPLORE_REPOSITORIES.add(repository);
        }
    }

    static boolean isExploreRepository(@Nullable Object repository) {
        if (repository == null) return false;
        synchronized (REPOSITORY_LOCK) {
            return EXPLORE_REPOSITORIES.contains(repository);
        }
    }

    static void clearRegisteredRepositoriesForTest() {
        synchronized (REPOSITORY_LOCK) {
            EXPLORE_REPOSITORIES.clear();
        }
    }

    /** Hook entry point: filters tabs only when the owning UrpComponent belongs to Explore. */
    @Nullable
    public static List<Object> filterForComponent(
            @Nullable Object urpComponent,
            @Nullable List<Object> tabs
    ) {
        if (urpComponent == null || tabs == null || tabs.isEmpty()) return tabs;
        try {
            if (!isExploreRepository(repositoryOf(urpComponent))) return tabs;
            Set<String> hiddenKeys = SettingsRegistry.getStringSetOrDefault(SETTING_HIDDEN_TABS);
            return filter(tabs, hiddenKeys, BRIDGE_FIELDS);
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to customize NewX explore tabs", exception);
            return tabs;
        }
    }

    /**
     * Hook entry point: clamps a Decompose Pages selectedIndex to the current list bounds so
     * shrinking the Explore tab list without a full restart cannot throw IllegalArgumentException.
     */
    public static int clampSelectedIndex(int selectedIndex, @Nullable List<?> tabs) {
        if (tabs == null || tabs.isEmpty()) return 0;
        if (selectedIndex < 0) return 0;
        int lastIndex = tabs.size() - 1;
        return Math.min(selectedIndex, lastIndex);
    }

    /** Package-private so unit tests can drive rules and field extraction without the app. */
    @Nullable
    static List<Object> filter(
            @Nullable List<Object> tabs,
            @Nullable Set<String> hiddenKeys,
            TabFields fields
    ) {
        if (tabs == null || tabs.isEmpty() || hiddenKeys == null || hiddenKeys.isEmpty()) {
            return tabs;
        }

        try {
            List<TabRule> hidden = new ArrayList<>(hiddenKeys.size());
            for (TabRule rule : TAB_RULES) {
                if (hiddenKeys.contains(rule.key)) {
                    hidden.add(rule);
                }
            }
            if (hidden.isEmpty()) return tabs;

            List<Object> filtered = new ArrayList<>(tabs.size());
            for (Object tab : tabs) {
                if (tab != null && isHidden(tab, hidden, fields)) continue;
                filtered.add(tab);
            }
            return filtered.size() == tabs.size() ? tabs : filtered;
        } catch (Exception exception) {
            NewXLogger.printException(() -> "Failed to customize NewX explore tabs", exception);
            return tabs;
        }
    }

    private static boolean isHidden(Object tab, List<TabRule> hidden, TabFields fields) {
        String scribeSection = fields.scribeSection(tab);
        String segmentedTimelineId = fields.segmentedTimelineId(tab);
        String timelineId = fields.timelineId(tab);
        String title = fields.title(tab);

        for (TabRule rule : hidden) {
            if (matchesId(scribeSection, rule)
                    || matchesId(segmentedTimelineId, rule)
                    || matchesId(timelineId, rule)
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

    // Patch-time bridge placeholders: bodies are replaced with direct field reads on the resolved
    // NewX UrpComponent, UrpTimeline, and ScribeConfig classes.

    @Nullable
    static Object repositoryOf(Object urpComponent) {
        return null;
    }

    @Nullable
    static String titleOf(Object urpTimeline) {
        return null;
    }

    @Nullable
    static String timelineIdOf(Object urpTimeline) {
        return null;
    }

    @Nullable
    static String segmentedTimelineIdOf(Object urpTimeline) {
        return null;
    }

    @Nullable
    static Object scribeConfigOf(Object urpTimeline) {
        return null;
    }

    @Nullable
    static String scribeSectionOf(Object scribeConfig) {
        return null;
    }
}
