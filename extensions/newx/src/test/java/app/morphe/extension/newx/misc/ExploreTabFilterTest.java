/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExploreTabFilterTest {
    private static final class FakeTab {
        final String title;
        final String timelineId;
        final String segmentedTimelineId;
        final String scribeSection;

        FakeTab(String title, String timelineId, String segmentedTimelineId, String scribeSection) {
            this.title = title;
            this.timelineId = timelineId;
            this.segmentedTimelineId = segmentedTimelineId;
            this.scribeSection = scribeSection;
        }
    }

    private static final ExploreTabFilter.TabFields FAKE_FIELDS = new ExploreTabFilter.TabFields() {
        @Override
        public String scribeSection(Object tab) {
            return ((FakeTab) tab).scribeSection;
        }

        @Override
        public String segmentedTimelineId(Object tab) {
            return ((FakeTab) tab).segmentedTimelineId;
        }

        @Override
        public String timelineId(Object tab) {
            return ((FakeTab) tab).timelineId;
        }

        @Override
        public String title(Object tab) {
            return ((FakeTab) tab).title;
        }
    };

    @After
    public void tearDown() {
        ExploreTabFilter.clearRegisteredRepositoriesForTest();
    }

    @Test
    public void emptyHiddenSetPreservesOriginalList() {
        List<Object> tabs = defaultTabs();

        assertSame(tabs, ExploreTabFilter.filter(tabs, Collections.emptySet(), FAKE_FIELDS));
    }

    @Test
    public void hidesTabsByLocaleIndependentScribeSection() {
        FakeTab forYou = new FakeTab("Para ti", "opaque-1", "tab-1", "for_you");
        FakeTab sports = new FakeTab("Deportes", "opaque-2", "tab-2", "explore_sports");
        FakeTab news = new FakeTab("Noticias", "opaque-3", "tab-3", "news");
        List<Object> tabs = Arrays.asList(forYou, sports, news);

        List<Object> filtered = ExploreTabFilter.filter(tabs, setOf("for_you", "sports"), FAKE_FIELDS);

        assertEquals(Collections.singletonList(news), filtered);
    }

    @Test
    public void hidesTabsBySegmentedTimelineIdSuffixAndCaseInsensitiveTitleFallback() {
        FakeTab trending = new FakeTab("トレンド", "opaque-1", "guide/explore-trending", null);
        FakeTab entertainment = new FakeTab("  entertainment ", "opaque-2", "opaque-seg", null);
        FakeTab custom = new FakeTab("Custom", "opaque-3", "custom", "custom");
        List<Object> tabs = Arrays.asList(trending, entertainment, custom);

        List<Object> filtered =
                ExploreTabFilter.filter(tabs, setOf("trending", "entertainment"), FAKE_FIELDS);

        assertEquals(Collections.singletonList(custom), filtered);
    }

    @Test
    public void hidesAllTabsReturnsEmptyList() {
        List<Object> tabs = defaultTabs();

        List<Object> filtered =
                ExploreTabFilter.filter(
                        tabs,
                        setOf("for_you", "trending", "news", "sports", "entertainment"),
                        FAKE_FIELDS);

        assertTrue(filtered.isEmpty());
    }

    @Test
    public void failsOpenWhenFieldExtractionThrows() {
        List<Object> tabs = defaultTabs();
        ExploreTabFilter.TabFields throwingFields = new ExploreTabFilter.TabFields() {
            @Override
            public String scribeSection(Object tab) {
                throw new IllegalStateException("boom");
            }

            @Override
            public String segmentedTimelineId(Object tab) {
                return null;
            }

            @Override
            public String timelineId(Object tab) {
                return null;
            }

            @Override
            public String title(Object tab) {
                return null;
            }
        };

        assertSame(tabs, ExploreTabFilter.filter(tabs, setOf("for_you"), throwingFields));
    }

    @Test
    public void exploreRepositoryRegistryTracksOnlyRegisteredInstances() {
        Object exploreRepo = new Object();
        Object otherRepo = new Object();

        assertFalse(ExploreTabFilter.isExploreRepository(exploreRepo));
        ExploreTabFilter.registerExploreRepository(exploreRepo);

        assertTrue(ExploreTabFilter.isExploreRepository(exploreRepo));
        assertFalse(ExploreTabFilter.isExploreRepository(otherRepo));
        assertFalse(ExploreTabFilter.isExploreRepository(null));
    }

    @Test
    public void clampSelectedIndexHandlesEmptyNegativeAndShrunkLists() {
        assertEquals(0, ExploreTabFilter.clampSelectedIndex(3, Collections.emptyList()));
        assertEquals(0, ExploreTabFilter.clampSelectedIndex(0, null));
        assertEquals(0, ExploreTabFilter.clampSelectedIndex(-1, defaultTabs()));
        assertEquals(2, ExploreTabFilter.clampSelectedIndex(2, defaultTabs()));
        assertEquals(1, ExploreTabFilter.clampSelectedIndex(4, Arrays.asList("a", "b")));
    }

    private static List<Object> defaultTabs() {
        return Arrays.asList(
                new FakeTab("For You", "t1", "for_you", "for_you"),
                new FakeTab("Trending", "t2", "trending", "trending"),
                new FakeTab("News", "t3", "news", "news"),
                new FakeTab("Sports", "t4", "sports", "sports"),
                new FakeTab("Entertainment", "t5", "entertainment", "entertainment"));
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
