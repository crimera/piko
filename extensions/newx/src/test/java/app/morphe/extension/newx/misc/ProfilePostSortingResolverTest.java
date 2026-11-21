package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ProfilePostSortingResolverTest {
    @Test
    public void fallsBackToLatestWhenSettingsAreUnavailable() {
        assertEquals(Boolean.FALSE, ProfilePostSortingResolver.getDefault());
    }
}
