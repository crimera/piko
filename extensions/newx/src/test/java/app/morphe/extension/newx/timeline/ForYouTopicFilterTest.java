package app.morphe.extension.newx.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.Test;

public class ForYouTopicFilterTest {
    @Test
    public void disabledFilterPreservesNativeTopics() {
        List<String> nativeTopics = Arrays.asList("-123", "-456");

        assertSame(
                nativeTopics,
                ForYouTopicFilter.resolveForYouTopicIds(
                        nativeTopics,
                        false,
                        new LinkedHashSet<>(Collections.singleton("123"))
                )
        );
    }

    @Test
    public void emptySelectionPreservesNativeTopics() {
        List<String> nativeTopics = Collections.singletonList("-123");

        assertSame(
                nativeTopics,
                ForYouTopicFilter.resolveForYouTopicIds(
                        nativeTopics,
                        true,
                        Collections.emptySet()
                )
        );
    }

    @Test
    public void configuredTopicsReplaceNativeTopicsWithPositiveIds() {
        List<String> nativeTopics = Collections.singletonList("-123");
        LinkedHashSet<String> configuredTopics = new LinkedHashSet<>(Arrays.asList("123", "789"));

        List<String> resolved = ForYouTopicFilter.resolveForYouTopicIds(
                nativeTopics,
                true,
                configuredTopics
        );

        assertEquals(Arrays.asList("123", "789"), resolved);
        assertNotSame(configuredTopics, resolved);
    }

    @Test
    public void parsesRuntimeHomeFilterOption() {
        ForYouTopicFilter.Topic topic = ForYouTopicFilter.parseTopicOptionText(
                "HomeFilterOption(identifier=123456789, displayName=Politics, " +
                        "tabDisplayName=Politics, iconName=politics)"
        );

        assertNotNull(topic);
        assertEquals("123456789", topic.getId());
        assertEquals("Politics", topic.getName());
    }

    @Test
    public void ignoresNegativeAndNonTopicOptions() {
        assertNull(ForYouTopicFilter.parseTopicOptionText(
                "HomeFilterOption(identifier=-123, displayName=Muted, tabDisplayName=Muted, iconName=null)"
        ));
        assertNull(ForYouTopicFilter.parseTopicOptionText("RegionOption(identifier=123, displayName=US)"));
    }

    @Test(expected = IllegalStateException.class)
    public void malformedTopicOptionFailsDuringCapture() {
        ForYouTopicFilter.captureTopicOptions("TOPIC", Collections.singletonList("unexpected"));
    }

    @Test(expected = IllegalStateException.class)
    public void nonIterableTopicOptionsFailDuringCapture() {
        ForYouTopicFilter.captureTopicOptions("TOPIC", "unexpected");
    }

    @Test(expected = IllegalStateException.class)
    public void emptyTopicOptionsFailDuringCapture() {
        ForYouTopicFilter.captureTopicOptions("TOPIC", Collections.emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedSerializedTopicIdFailsParsing() {
        ForYouTopicFilter.parseTopicIds("123,-456");
    }

    @Test(expected = IllegalStateException.class)
    public void invalidConfiguredTopicIdFailsResolution() {
        ForYouTopicFilter.resolveForYouTopicIds(
                Collections.singletonList("-123"),
                true,
                new LinkedHashSet<>(Arrays.asList("123", "-456"))
        );
    }
}
