package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public final class PollResultsFormatterTest {
    @Test
    public void formatsUnfinalizedWrappedPollValues() {
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("counts_are_final", new FakeValue("BooleanValue(value=false)"));
        bindings.put("choice1_label", new FakeValue("StringValue(value=Tea)"));
        bindings.put("choice1_count", new FakeValue("StringValue(value=1)"));
        bindings.put("choice2_count", new FakeValue("StringValue(value=3)"));

        assertEquals("Tea - 25%", PollResultsFormatter.formatLabelValue(1, bindings));
    }

    @Test
    public void supportsDirectJavaValues() {
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("choice1_label", "Coffee");
        bindings.put("choice1_count", 2);
        bindings.put("choice2_count", 1);

        assertEquals("Coffee - 67%", PollResultsFormatter.formatLabelValue(1, bindings));
    }

    @Test
    public void leavesFinalPollsUntouched() {
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("counts_are_final", new FakeValue("BooleanValue(value=true)"));
        bindings.put("choice1_label", new FakeValue("StringValue(value=Tea)"));
        bindings.put("choice1_count", new FakeValue("StringValue(value=1)"));

        assertNull(PollResultsFormatter.formatLabelValue(1, bindings));
    }

    @Test
    public void malformedCountsFallBackToTheOriginalHelper() {
        Map<String, Object> bindings = new HashMap<>();
        bindings.put("choice1_label", new FakeValue("StringValue(value=Tea)"));
        bindings.put("choice1_count", new FakeValue("StringValue(value=unknown)"));

        assertNull(PollResultsFormatter.formatLabelValue(1, bindings));
    }

    @Test
    public void disabledSettingFallsBackToTheOriginalHelper() {
        assertNull(PollResultsFormatter.formatLabel(1, "label", Map.of(
                "choice1_label", "Tea",
                "choice1_count", 1
        )));
    }

    private static final class FakeValue {
        private final String representation;

        FakeValue(String representation) {
            this.representation = representation;
        }

        @Override
        public String toString() {
            return representation;
        }
    }
}
