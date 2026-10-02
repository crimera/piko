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
        CONVERSATION,
        USER_PROFILE_POSTS_ONLY,
    }

    @Test
    public void homeKeysAreAllowedForForYouFollowingAndRankedFollowing() {
        assertEquals(
                "FOR_YOU",
                TimelineScrollPositionStore.storageKey("FOR_YOU", null, true, false)
        );
        assertEquals(
                "FOLLOWING",
                TimelineScrollPositionStore.storageKey("FOLLOWING", null, true, false)
        );
        assertEquals(
                "RANKED_FOLLOWING",
                TimelineScrollPositionStore.storageKey("RANKED_FOLLOWING", null, true, false)
        );
        assertNull(
                TimelineScrollPositionStore.storageKey("FOR_YOU", null, false, false)
        );
    }

    @Test
    public void inMemoryPositionsAreAllowedForEveryTimelineExceptProfiles() {
        assertTrue(TimelineScrollPositionStore.useInMemoryPosition(TimelineType.FOR_YOU));
        assertTrue(TimelineScrollPositionStore.useInMemoryPosition(TimelineType.FOLLOWING));
        assertTrue(TimelineScrollPositionStore.useInMemoryPosition(TimelineType.RANKED_FOLLOWING));
        // Conversation threads have no persistent key, so the native holder must survive.
        assertTrue(TimelineScrollPositionStore.useInMemoryPosition(TimelineType.CONVERSATION));
        assertFalse(TimelineScrollPositionStore.useInMemoryPosition(TimelineType.USER_PROFILE_POSTS_ONLY));
        // X shares one in-memory slot across all lists, so restored lists must bypass it; with
        // restore off the native holder is the only in-session position and must survive.
        assertFalse(TimelineScrollPositionStore.useInMemoryPosition("LIST_POSTS", true));
        assertTrue(TimelineScrollPositionStore.useInMemoryPosition("LIST_POSTS", false));
    }

    @Test
    public void perIdTimelineKeysRequireTheTimelineToggleAndAnId() {
        assertEquals(
                "timeline.LIST_POSTS123",
                TimelineScrollPositionStore.storageKey("LIST_POSTS", " LIST_POSTS123 ", true, false)
        );
        assertEquals(
                "timeline.TOPIC456",
                TimelineScrollPositionStore.storageKey("TOPIC", "TOPIC456", true, false)
        );
        assertNull(
                TimelineScrollPositionStore.storageKey("LIST_POSTS", "LIST_POSTS123", false, true)
        );
        // A bare type name carries no id and would make every list share one position.
        assertNull(
                TimelineScrollPositionStore.storageKey("LIST_POSTS", "LIST_POSTS", true, false)
        );
        assertNull(TimelineScrollPositionStore.storageKey("LIST_POSTS", null, true, false));
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
