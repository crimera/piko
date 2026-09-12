package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class InlineActionFilterTest {
    @Test
    public void filterHidesPreparedActions() {
        List<?> actions = new ArrayList<>(List.of(action("Reply"), action("Favorite")));
        InlineActionFilter.prepareHiddenActions(Set.of("Reply"));
        InlineActionFilter.preparePresenter(new Object());

        List<?> result = InlineActionFilter.filter(actions);

        assertEquals(1, result.size());
        assertSame(actions.get(1), result.get(0));
    }

    @Test
    public void filterClearsPreparedStateAfterConsumption() {
        List<?> actions = new ArrayList<>(List.of(action("Reply")));
        InlineActionFilter.prepareHiddenActions(Set.of("Reply"));
        InlineActionFilter.preparePresenter(new Object());
        InlineActionFilter.filter(actions);

        // No preparation must be left behind after consumption, or stale hidden-action
        // state would leak across renders.
        assertEquals(1, InlineActionFilter.filter(actions).size());
    }

    @Test
    public void repeatedRecompositionDoesNotRetainPreparedState() {
        List<?> actions = new ArrayList<>(List.of(action("Reply"), action("Favorite")));

        for (int pass = 0; pass < 5; pass++) {
            InlineActionFilter.prepareHiddenActions(Set.of("Reply"));
            InlineActionFilter.preparePresenter(new Object());
            assertEquals(1, InlineActionFilter.filter(actions).size());
        }

        // Every pass consumed exactly one prepared set; nothing lingers.
        assertEquals(actions, InlineActionFilter.filter(actions));
    }

    @Test
    public void filterWithoutPreparationLeavesActionsUntouched() {
        List<?> actions = new ArrayList<>(List.of(action("Reply"), action("Favorite")));

        // No prepared state: nothing to hide, and the disabled download hook keeps the list.
        assertEquals(actions, InlineActionFilter.filter(actions));
    }

    private static Object action(String name) {
        return new FakeInlineAction(name);
    }

    private static final class FakeInlineAction {
        private final String name;

        FakeInlineAction(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "InlineActionEntry(actionType=" + name + ", isEnabled=true)";
        }
    }
}