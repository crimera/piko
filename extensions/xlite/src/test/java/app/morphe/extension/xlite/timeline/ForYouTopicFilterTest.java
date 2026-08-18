package app.morphe.extension.xlite.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
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
    public void configuredTopicsReplaceNativeTopics() {
        List<String> nativeTopics = Collections.singletonList("-123");
        LinkedHashSet<String> configuredTopics = new LinkedHashSet<>(Arrays.asList("123", "456"));

        List<String> resolved = ForYouTopicFilter.resolveForYouTopicIds(
                nativeTopics,
                true,
                configuredTopics
        );

        assertEquals(Arrays.asList("123", "456"), resolved);
        assertNotSame(configuredTopics, resolved);
    }
}
