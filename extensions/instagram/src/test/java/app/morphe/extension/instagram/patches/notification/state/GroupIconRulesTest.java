package app.morphe.extension.instagram.patches.notification.state;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class GroupIconRulesTest {
    @Test
    public void bestCandidate_isTheNarrowestAtLeastTheIconSize() {
        assertEquals(1, GroupIconRules.bestCandidate(new int[]{1080, 320, 150, 640}, 192));
    }

    @Test
    public void bestCandidate_allSmaller_isTheWidest() {
        assertEquals(2, GroupIconRules.bestCandidate(new int[]{64, 100, 150}, 192));
    }

    @Test
    public void bestCandidate_noWidthsKnown_isTheFirst_andEmptyIsNone() {
        assertEquals(0, GroupIconRules.bestCandidate(new int[]{0, 0, 0}, 192));
        assertEquals(-1, GroupIconRules.bestCandidate(new int[]{}, 192));
    }

    @Test
    public void compositeOrder_skipsTheViewerAndDuplicates_numericOrder() {
        assertEquals(Arrays.asList("9", "30", "200"),
                GroupIconRules.compositeOrder(Arrays.asList("200", "100", "9", "30", "9", null, ""),
                        id -> id.equals("100")));
    }

    @Test
    public void compositeOrder_onlyTheViewer_isEmpty() {
        assertEquals(Collections.emptyList(),
                GroupIconRules.compositeOrder(Collections.singletonList("100"), id -> id.equals("100")));
    }
}
