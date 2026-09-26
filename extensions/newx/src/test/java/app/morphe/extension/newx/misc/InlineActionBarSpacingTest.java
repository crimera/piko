package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public final class InlineActionBarSpacingTest {
    private static final int AVAILABLE = 1000;
    private static final int MIN_GAP = 20;
    private static final int CLASSIC_GAP = 24;
    private static final int COUNTED_FLOOR = 200;

    @Test
    public void countedSlotsShareTheRowAndIconOnlySlotsKeepTheirWidth() {
        // Three counted actions (floor-sized, like short counts) and two icon-only ones.
        ArrayList<Integer> slots = new ArrayList<>(Arrays.asList(200, 200, 200, 60, 60));

        InlineActionBarSpacing.applyClassicSpacing(
                slots, AVAILABLE, MIN_GAP, COUNTED_FLOOR, CLASSIC_GAP);

        // leftovers: 1000 - 120 icon-only - 4 gaps of 24 - one reserved gap 24 = 760 => 253 each
        assertEquals(253, slots.get(0).intValue());
        assertEquals(253, slots.get(1).intValue());
        assertEquals(253, slots.get(2).intValue());
        assertEquals(60, slots.get(3).intValue());
        assertEquals(60, slots.get(4).intValue());
    }

    @Test
    public void shareNeverDropsBelowTheCountedFloor() {
        // Far too little room: the app's own floor has to win, otherwise the row collapses.
        ArrayList<Integer> slots = new ArrayList<>(Arrays.asList(200, 200, 200, 60, 60, 60));

        InlineActionBarSpacing.applyClassicSpacing(
                slots, 400, MIN_GAP, COUNTED_FLOOR, CLASSIC_GAP);

        assertEquals(200, slots.get(0).intValue());
        assertEquals(200, slots.get(1).intValue());
        assertEquals(200, slots.get(2).intValue());
    }

    @Test
    public void rowsWithoutCountedActionsAreLeftAlone() {
        ArrayList<Integer> slots = new ArrayList<>(Arrays.asList(60, 60));

        InlineActionBarSpacing.applyClassicSpacing(
                slots, AVAILABLE, MIN_GAP, COUNTED_FLOOR, CLASSIC_GAP);

        assertEquals(Arrays.asList(60, 60), slots);
    }

    @Test
    public void uncapturedFloorAndEmptyListsAreIgnored() {
        ArrayList<Integer> slots = new ArrayList<>(Arrays.asList(200, 200));

        // -1 is the sentinel the injected seeding uses when the floor was never captured.
        InlineActionBarSpacing.applyClassicSpacing(slots, AVAILABLE, MIN_GAP, -1, CLASSIC_GAP);
        assertEquals(Arrays.asList(200, 200), slots);

        ArrayList<Integer> empty = new ArrayList<>();
        InlineActionBarSpacing.applyClassicSpacing(empty, AVAILABLE, MIN_GAP, COUNTED_FLOOR, CLASSIC_GAP);
        assertEquals(0, empty.size());
    }
}
