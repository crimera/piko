/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.navigation;

import android.content.Intent;

import app.morphe.extension.instagram.settings.SettingsRestart;
import app.morphe.extension.instagram.utils.Pref;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings("unused")
public final class NavigationBarPatch {
    private static final String CONFIG_VERSION = "v2";
    private static final String STARTUP_TAB_EXTRA =
            "MainActivityAccountHelper.STARTUP_TAB";
    private static final List<Tab> DEFAULT_ORDER = Collections.unmodifiableList(
            Arrays.asList(
                    Tab.HOME,
                    Tab.REELS,
                    Tab.MESSAGES,
                    Tab.SEARCH,
                    Tab.PROFILE,
                    Tab.CREATE,
                    Tab.NOTIFICATIONS
            )
    );
    private static final Set<Tab> DEFAULT_VISIBLE = Collections.unmodifiableSet(
            EnumSet.of(Tab.HOME, Tab.REELS, Tab.MESSAGES, Tab.SEARCH, Tab.PROFILE)
    );
    private static final int MAX_TABS = 7;
    private static volatile NotificationsVisibility notificationsVisibility =
            NotificationsVisibility.UNKNOWN;

    private NavigationBarPatch() {
    }

    public enum Tab {
        HOME("home", "piko_navigation_tab_home", "tab_home_drawable", "fragment_feed", "FEED"),
        REELS("reels", "piko_navigation_tab_reels", "tab_clips_drawable", "fragment_clips", "CLIPS"),
        MESSAGES("messages", "piko_navigation_tab_messages", "tab_direct_drawable", "fragment_direct_tab", "DIRECT"),
        SEARCH("search", "piko_navigation_tab_search", "tab_search_drawable", "fragment_search", "SEARCH"),
        PROFILE("profile", "piko_navigation_tab_profile", "tab_profile_drawable", "fragment_profile", "PROFILE"),
        NOTIFICATIONS("notifications", "piko_navigation_tab_notifications", "tab_activity_heart_drawable", "fragment_news", "NEWS"),
        CREATE("create", "piko_navigation_tab_create", "tab_camera_drawable", "fragment_share", "SHARE", "CREATION");

        private final String key;
        private final String labelName;
        private final String iconName;
        private final String fragmentName;
        private final Set<String> enumNames;

        Tab(
                String key,
                String labelName,
                String iconName,
                String fragmentName,
                String... enumNames
        ) {
            this.key = key;
            this.labelName = labelName;
            this.iconName = iconName;
            this.fragmentName = fragmentName;
            this.enumNames = Collections.unmodifiableSet(
                    new java.util.HashSet<>(Arrays.asList(enumNames)));
        }

        public String key() {
            return key;
        }

        public String labelName() {
            return labelName;
        }

        public String iconName() {
            return iconName;
        }

        public static Tab fromKey(String key) {
            for (Tab tab : values()) {
                if (tab.key.equals(key)) return tab;
            }
            return null;
        }

        private static Tab from(String enumName, String fragmentName) {
            for (Tab tab : values()) {
                if (tab.fragmentName.equals(fragmentName) && tab.enumNames.contains(enumName)) {
                    return tab;
                }
            }
            return null;
        }
    }

    public enum NotificationsVisibility {
        UNKNOWN,
        VISIBLE,
        HIDDEN
    }

    public static final class Config {
        private final List<Tab> order;
        private final Set<Tab> visible;
        private final Tab startup;

        private Config(List<Tab> order, Set<Tab> visible, Tab startup) {
            this.order = Collections.unmodifiableList(new ArrayList<>(order));
            this.visible = Collections.unmodifiableSet(EnumSet.copyOf(visible));
            this.startup = startup;
        }

        public List<Tab> order() {
            return order;
        }

        public Set<Tab> visible() {
            return visible;
        }

        public Tab startup() {
            return startup;
        }
    }

    public static Config defaultConfig() {
        return new Config(DEFAULT_ORDER, DEFAULT_VISIBLE, Tab.HOME);
    }

    private static Config resolveConfig(
            String stored,
            boolean legacyPresent,
            Set<Tab> legacyVisible
    ) {
        if (stored == null || stored.isEmpty()) {
            return legacyPresent ? normalize(DEFAULT_ORDER, legacyVisible, Tab.HOME) : defaultConfig();
        }
        String versionPrefix = CONFIG_VERSION + "|";
        if (stored.startsWith(versionPrefix)) {
            return parseStoredConfig(stored.substring(versionPrefix.length()), false);
        }
        return parseStoredConfig(stored, true);
    }

    private static Config parseStoredConfig(String stored, boolean disableOptionalTabs) {
        String[] sections = stored.split("\\|", -1);
        List<Tab> order = new ArrayList<>();
        EnumSet<Tab> visible = EnumSet.noneOf(Tab.class);
        if (sections.length > 0) {
            for (String entry : sections[0].split(",")) {
                String[] fields = entry.split(":", -1);
                Tab tab = fields.length == 2 ? Tab.fromKey(fields[0]) : null;
                if (tab == null || order.contains(tab)
                        || !("0".equals(fields[1]) || "1".equals(fields[1]))) continue;
                order.add(tab);
                if ("1".equals(fields[1])) visible.add(tab);
            }
        }
        for (Tab tab : DEFAULT_ORDER) {
            if (order.contains(tab)) continue;
            order.add(tab);
            if (DEFAULT_VISIBLE.contains(tab)) visible.add(tab);
        }
        if (disableOptionalTabs) {
            visible.remove(Tab.NOTIFICATIONS);
            visible.remove(Tab.CREATE);
        }
        return normalize(
                order,
                visible,
                sections.length > 1 ? Tab.fromKey(sections[1]) : null
        );
    }

    public static Config loadConfig() {
        return loadConfig(null);
    }

    private static Config loadConfig(Boolean nativeCreateVisible) {
        boolean legacyPresent = Pref.hasLegacyNavigationSettings();
        Set<Tab> legacyVisible = legacyPresent
                ? legacyVisibleTabs(Boolean.TRUE.equals(nativeCreateVisible))
                : Collections.emptySet();
        String stored = Pref.navigationTabs();
        Config config = resolveConfig(stored, legacyPresent, legacyVisible);
        // A shortcut can open settings before native tabs exist; keep legacy migration pending.
        if (nativeCreateVisible == null && legacyPresent
                && (stored == null || stored.isEmpty())) {
            return config;
        }
        String normalized = encodeConfig(config);
        if (!Objects.equals(stored, normalized)) Pref.setNavigationTabs(normalized);
        return config;
    }

    public static boolean saveConfig(List<Tab> order, Set<Tab> visible, Tab startup) {
        Config normalized = normalize(order, visible, startup);
        String previous = Pref.navigationTabs();
        String next = encodeConfig(normalized);
        if (Objects.equals(previous, next)) return true;
        if (!Pref.setNavigationTabs(next)) return false;
        SettingsRestart.markChanged(previous, next);
        return true;
    }

    public static NotificationsVisibility effectiveNotificationsVisibility() {
        return notificationsVisibility;
    }

    public static List<Object> transformNavigationTabs(
            List<Object> currentTabs,
            List<Object> allCandidates
    ) {
        List<Object> fallback = mutableCopy(currentTabs);
        try {
            List<Candidate> current = classify(currentTabs);
            if (current.size() > MAX_TABS) {
                updateNotifications(current);
                return fallback;
            }

            List<Candidate> unknown = new ArrayList<>();
            IdentityHashMap<Object, Boolean> seenCurrent = new IdentityHashMap<>();
            IdentityHashMap<Object, Boolean> used = new IdentityHashMap<>();
            for (Candidate candidate : current) {
                if (seenCurrent.put(candidate.value, Boolean.TRUE) != null) continue;
                if (candidate.tab == null) {
                    unknown.add(candidate);
                    used.put(candidate.value, Boolean.TRUE);
                }
            }

            List<Candidate> candidates = classify(allCandidates);
            List<Candidate> output = new ArrayList<>();
            int knownCapacity = Math.max(0, MAX_TABS - unknown.size());
            Config config = loadConfig(find(current, Tab.CREATE, new IdentityHashMap<>()) != null);
            for (Tab tab : config.order()) {
                if (output.size() == knownCapacity) break;
                if (!config.visible().contains(tab)) continue;
                Candidate candidate = find(current, tab, used);
                if (candidate == null) candidate = find(candidates, tab, used);
                if (candidate != null) {
                    output.add(candidate);
                    used.put(candidate.value, Boolean.TRUE);
                }
            }
            for (Candidate candidate : unknown) {
                output.add(Math.min(candidate.nativeIndex, output.size()), candidate);
            }
            if (output.isEmpty()) {
                Candidate safe = find(current, Tab.HOME, new IdentityHashMap<>());
                if (safe == null && !current.isEmpty()) safe = current.get(0);
                if (safe == null) return fallback;
                output.add(safe);
            }

            updateNotifications(output);
            List<Object> result = new ArrayList<>(output.size());
            for (Candidate candidate : output) result.add(candidate.value);
            return result;
        } catch (Throwable ignored) {
            notificationsVisibility = NotificationsVisibility.UNKNOWN;
            return fallback;
        }
    }

    public static boolean preflightStartup(Intent intent, boolean coldDefaultPath) {
        try {
            return intent != null
                    && coldDefaultPath
                    && !intent.hasExtra(STARTUP_TAB_EXTRA);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void applyStartupTab(
            Intent intent,
            boolean coldDefaultPath,
            List<Object> allCandidates
    ) {
        if (intent == null || !coldDefaultPath) return;
        try {
            if (intent.hasExtra(STARTUP_TAB_EXTRA)) return;
            List<Candidate> candidates = classify(allCandidates);
            Config config = loadConfig();
            Tab[] choices = startupChoices(config);
            for (Tab tab : choices) {
                Candidate candidate = find(candidates, tab, new IdentityHashMap<>());
                if (candidate != null && candidate.value instanceof Enum<?>) {
                    intent.putExtra(STARTUP_TAB_EXTRA, ((Enum<?>) candidate.value).name());
                    return;
                }
            }
        } catch (Throwable ignored) {
            // Preserve Instagram's native startup behavior.
        }
    }

    private static Config normalize(List<Tab> requestedOrder, Set<Tab> requestedVisible, Tab startup) {
        List<Tab> order = new ArrayList<>();
        if (requestedOrder != null) {
            for (Tab tab : requestedOrder) {
                if (tab != null && !order.contains(tab)) order.add(tab);
            }
        }
        for (Tab tab : Tab.values()) {
            if (!order.contains(tab)) order.add(tab);
        }

        EnumSet<Tab> visible = EnumSet.noneOf(Tab.class);
        if (requestedVisible != null) visible.addAll(requestedVisible);
        if (visible.isEmpty()) visible.add(Tab.HOME);
        startup = resolveStartupTab(order, visible, startup);
        return new Config(order, visible, startup);
    }

    public static Tab resolveStartupTab(List<Tab> order, Set<Tab> visible, Tab startup) {
        if (startup != null && startup != Tab.CREATE && visible.contains(startup)) return startup;
        if (visible.contains(Tab.HOME)) return Tab.HOME;
        for (Tab tab : order) {
            if (tab != Tab.CREATE && visible.contains(tab)) return tab;
        }
        return Tab.HOME;
    }

    private static EnumSet<Tab> legacyVisibleTabs(boolean nativeCreateVisible) {
        EnumSet<Tab> visible = EnumSet.copyOf(DEFAULT_VISIBLE);
        if (nativeCreateVisible && !Pref.hideNavigationCreate()) visible.add(Tab.CREATE);
        if (Pref.hideNavigationFeed()) visible.remove(Tab.HOME);
        if (Pref.hideNavigationReels()) visible.remove(Tab.REELS);
        if (Pref.hideNavigationDirect()) visible.remove(Tab.MESSAGES);
        if (Pref.hideNavigationSearch()) visible.remove(Tab.SEARCH);
        return visible;
    }

    private static String encodeConfig(Config config) {
        StringBuilder value = new StringBuilder(CONFIG_VERSION).append('|');
        for (Tab tab : config.order()) {
            if (value.charAt(value.length() - 1) != '|') value.append(',');
            value.append(tab.key()).append(':')
                    .append(config.visible().contains(tab) ? '1' : '0');
        }
        value.append('|').append(config.startup().key());
        return value.toString();
    }

    private static Tab[] startupChoices(Config config) {
        List<Tab> choices = new ArrayList<>();
        choices.add(config.startup());
        if (config.visible().contains(Tab.HOME) && !choices.contains(Tab.HOME)) choices.add(Tab.HOME);
        for (Tab tab : config.order()) {
            if (tab != Tab.CREATE && config.visible().contains(tab) && !choices.contains(tab)) choices.add(tab);
        }
        return choices.toArray(new Tab[0]);
    }

    private static List<Candidate> classify(List<Object> tabs) {
        if (tabs == null) throw new IllegalArgumentException();
        List<Candidate> result = new ArrayList<>();
        for (int index = 0; index < tabs.size(); index++) {
            Object value = tabs.get(index);
            if (!(value instanceof Enum<?>)) throw new IllegalArgumentException();
            Enum<?> enumValue = (Enum<?>) value;
            String fragmentName = navigationFragmentName(value);
            if (fragmentName == null) throw new IllegalArgumentException();
            result.add(new Candidate(
                    value,
                    Tab.from(enumValue.name(), fragmentName),
                    index
            ));
        }
        return result;
    }

    private static native String navigationFragmentName(Object value);

    private static Candidate find(
            List<Candidate> candidates,
            Tab tab,
            IdentityHashMap<Object, Boolean> used
    ) {
        for (Candidate candidate : candidates) {
            if (candidate.tab == tab && !used.containsKey(candidate.value)) return candidate;
        }
        return null;
    }

    private static void updateNotifications(List<Candidate> tabs) {
        for (Candidate candidate : tabs) {
            if (candidate.tab == Tab.NOTIFICATIONS) {
                notificationsVisibility = NotificationsVisibility.VISIBLE;
                return;
            }
        }
        notificationsVisibility = NotificationsVisibility.HIDDEN;
    }

    private static List<Object> mutableCopy(List<Object> tabs) {
        return tabs == null ? new ArrayList<>() : new ArrayList<>(tabs);
    }

    private static final class Candidate {
        private final Object value;
        private final Tab tab;
        private final int nativeIndex;

        private Candidate(Object value, Tab tab, int nativeIndex) {
            this.value = value;
            this.tab = tab;
            this.nativeIndex = nativeIndex;
        }
    }
}
