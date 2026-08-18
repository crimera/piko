package app.morphe.extension.xlite.misc;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public final class ReplySortingResolverTest {
    private enum SortingMode {
        Recency,
        Relevance,
        Likes,
    }

    @Test
    public void fallsBackToRelevanceWhenSettingsAreUnavailable() {
        assertSame(
                SortingMode.Relevance,
                ReplySortingResolver.getEnumDefault(SortingMode.class)
        );
    }

    @Test
    public void rejectsNonEnumTargetClasses() {
        assertNull(ReplySortingResolver.getEnumDefault(String.class));
    }
}
