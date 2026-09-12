package app.morphe.extension.newx.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TimelineScrollPositionStoreTest {
    private enum TimelineType {
        FOR_YOU,
        FOLLOWING,
        RANKED_FOLLOWING,
        USER_PROFILE_POSTS_ONLY,
    }

    @Test
    public void homeKeysAreAllowedOnlyForForYouAndFollowing() {
        assertEquals(
                "FOR_YOU",
                TimelineScrollPositionStore.storageKey("FOR_YOU", null, true, false)
        );
        assertEquals(
                "FOLLOWING",
                TimelineScrollPositionStore.storageKey("FOLLOWING", null, true, false)
        );
        assertNull(
                TimelineScrollPositionStore.storageKey("RANKED_FOLLOWING", null, true, false)
        );
        assertNull(
                TimelineScrollPositionStore.storageKey("FOR_YOU", null, false, false)
        );
    }

    @Test
    public void inMemoryPositionsAreAllowedOnlyForHomeTimelines() {
        assertTrue(TimelineScrollPositionStore.useInMemoryPosition(TimelineType.FOR_YOU));
        assertTrue(TimelineScrollPositionStore.useInMemoryPosition(TimelineType.FOLLOWING));
        assertFalse(TimelineScrollPositionStore.useInMemoryPosition(TimelineType.RANKED_FOLLOWING));
        assertFalse(TimelineScrollPositionStore.useInMemoryPosition(TimelineType.USER_PROFILE_POSTS_ONLY));
    }

    @Test
    public void profileKeysRequireTheProfileToggleAndProfileId() {
        assertNull(
                TimelineScrollPositionStore.storageKey(
                        "USER_PROFILE_POSTS_ONLY",
                        "42",
                        true,
                        false
                )
        );
        assertEquals(
                "profile.USER_PROFILE_POSTS_ONLY.42",
                TimelineScrollPositionStore.storageKey(
                        "USER_PROFILE_POSTS_ONLY",
                        " 42 ",
                        true,
                        true
                )
        );
        assertNull(
                TimelineScrollPositionStore.storageKey(
                        "USER_PROFILE_POSTS_ONLY",
                        " ",
                        true,
                        true
                )
        );
    }
}
