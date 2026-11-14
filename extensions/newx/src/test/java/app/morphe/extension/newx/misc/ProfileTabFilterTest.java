package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public final class ProfileTabFilterTest {

    @Test
    public void reordersAndHidesConfiguredTabs() {
        ProfileTabsConfig config = new ProfileTabsConfig();
        config.saveOrder(Arrays.asList("Videos", "Posts", "Replies"));
        config.setHidden("Replies", true);
        List<Object> tabs = tabsOf("Posts", "AffiliatedUsers", "Replies", "Videos");

        List<Object> filtered = ProfileTabFilter.filter(tabs, resolver(), config);

        assertEquals(
                Arrays.asList("Videos", "Posts", "AffiliatedUsers"),
                idsOf(filtered)
        );
    }

    @Test
    public void keepsUpstreamTabsTheStoredOrderDoesNotKnow() {
        ProfileTabsConfig config = new ProfileTabsConfig();
        config.saveOrder(Arrays.asList("Videos", "Posts"));
        List<Object> tabs = tabsOf("Posts", "Articles", "Videos");

        List<Object> filtered = ProfileTabFilter.filter(tabs, resolver(), config);

        assertEquals(Arrays.asList("Videos", "Posts", "Articles"), idsOf(filtered));
    }

    @Test
    public void unresolvablePagesAreKeptInOriginalOrder() {
        ProfileTabsConfig config = new ProfileTabsConfig();
        config.setHidden("Posts", true);
        Object unknown = new Object();
        List<Object> tabs = new ArrayList<>(Arrays.asList(tab("Posts"), unknown, tab("Replies")));

        List<Object> filtered = ProfileTabFilter.filter(tabs, resolver(), config);

        assertEquals(2, filtered.size());
        assertEquals("Replies", ((FakePage) filtered.get(0)).id);
        assertSame(unknown, filtered.get(1));
    }

    @Test
    public void hidingEveryTabKeepsTheAppsFirstTab() {
        ProfileTabsConfig config = new ProfileTabsConfig();
        config.setHidden("Posts", true);
        config.setHidden("Replies", true);
        List<Object> tabs = tabsOf("Posts", "Replies");

        List<Object> filtered = ProfileTabFilter.filter(tabs, resolver(), config);

        assertEquals(1, filtered.size());
        assertSame(tabs.get(0), filtered.get(0));
    }

    @Test
    public void bridgeCastFailureReturnsTheInput() {
        ProfileTabsConfig config = new ProfileTabsConfig();
        List<Object> tabs = tabsOf("Posts", "Replies");

        List<Object> filtered = ProfileTabFilter.filter(tabs, page -> {
            throw new ClassCastException("bridge check-cast rejected the page");
        }, config);

        assertSame(tabs, filtered);
    }

    @Test
    public void unchangedConfigurationReturnsTheInputInstance() {
        ProfileTabsConfig config = new ProfileTabsConfig();
        List<Object> tabs = tabsOf("Posts", "Replies");

        List<Object> filtered = ProfileTabFilter.filter(tabs, resolver(), config);

        assertSame(tabs, filtered);
    }

    @Test
    public void nullAndEmptyInputsAreTolerated() {
        ProfileTabsConfig config = new ProfileTabsConfig();
        List<Object> empty = new ArrayList<>();

        assertNull(ProfileTabFilter.filter(null, resolver(), config));
        assertSame(empty, ProfileTabFilter.filter(empty, resolver(), config));
    }

    @Test
    public void storedOrderAppendsUnknownAvailableTabsInOriginalOrder() {
        ProfileTabsConfig config = new ProfileTabsConfig();
        config.saveOrder(Arrays.asList("Replies", "Dropped"));

        assertEquals(
                Arrays.asList("Replies", "Posts", "Videos"),
                config.orderedTabs(Arrays.asList("Posts", "Replies", "Videos"))
        );
        assertEquals(
                Arrays.asList("Replies", "Dropped"),
                config.storedOrder()
        );
    }

    @Test
    public void hiddenStateRoundTripsAndResetRestoresDefaults() {
        ProfileTabsConfig config = new ProfileTabsConfig();
        assertFalse(config.isHidden("Posts"));

        config.setHidden("Posts", true);
        assertTrue(config.isHidden("Posts"));

        config.setHidden("Posts", false);
        assertFalse(config.isHidden("Posts"));

        config.saveOrder(Arrays.asList("Videos", "Posts"));
        config.setHidden("Replies", true);
        config.reset();

        assertTrue("reset clears the stored order", config.storedOrder().isEmpty());
        assertTrue("reset clears the hidden set", config.hiddenTabs().isEmpty());
        List<String> appOrder = Arrays.asList("Posts", "Videos", "Replies");
        assertEquals(appOrder, config.orderedTabs(appOrder));
    }

    private static Function<Object, String> resolver() {
        return page -> page instanceof FakePage ? ((FakePage) page).id : null;
    }

    private static Object tab(String id) {
        return new FakePage(id);
    }

    private static List<Object> tabsOf(String... ids) {
        List<Object> tabs = new ArrayList<>(ids.length);
        for (String id : ids) tabs.add(new FakePage(id));
        return tabs;
    }

    private static List<String> idsOf(List<Object> tabs) {
        List<String> ids = new ArrayList<>(tabs.size());
        for (Object tab : tabs) ids.add(((FakePage) tab).id);
        return ids;
    }

    private static final class FakePage {
        private final String id;

        FakePage(String id) {
            this.id = id;
        }
    }
}
