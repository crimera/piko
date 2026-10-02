package app.morphe.extension.newx.timeline;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public final class TimelineTabFilterTest {
    @Test
    public void showBothPreservesTheOriginalArray() {
        Object[] tabs = tabs();

        assertSame(tabs, TimelineTabFilter.filter(tabs, "show_both"));
    }

    @Test
    public void hideForYouPreservesFollowingAndOrder() {
        Object[] tabs = tabs();

        Object[] filtered = (Object[]) TimelineTabFilter.filter(tabs, "hide_for_you");

        assertArrayEquals(new Object[]{tabs[1]}, filtered);
    }

    @Test
    public void hideFollowingPreservesForYou() {
        Object[] tabs = tabs();

        Object[] filtered = (Object[]) TimelineTabFilter.filter(tabs, "hide_following");

        assertArrayEquals(new Object[]{tabs[0]}, filtered);
    }

    @Test
    public void routeNamesAreRecognizedDirectly() {
        Object[] tabs = new Object[]{"ForYou", "Following"};

        Object[] filtered = (Object[]) TimelineTabFilter.filter(tabs, "hide_for_you");

        assertArrayEquals(new Object[]{tabs[1]}, filtered);
    }

    @Test
    public void unknownVisibilityLeavesTheArrayUntouched() {
        Object[] tabs = tabs();

        assertSame(tabs, TimelineTabFilter.filter(tabs, "unexpected"));
    }

    private static Object[] tabs() {
        return new Object[]{
                new FakeTab("ForYou"),
                new FakeTab("Following"),
        };
    }

    private static final class FakeTab {
        private final String type;

        FakeTab(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return "Tab(homeTabType=" + type + ", title=null)";
        }
    }
}
