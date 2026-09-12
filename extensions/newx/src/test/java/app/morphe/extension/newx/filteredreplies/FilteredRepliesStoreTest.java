package app.morphe.extension.newx.filteredreplies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public final class FilteredRepliesStoreTest {
    private final FilteredRepliesStore store = FilteredRepliesStore.shared();

    @Before
    public void setUp() {
        store.clear();
    }

    @Test
    public void recordsAndRetrievesRepliesForRootPost() {
        FilteredRepliesStore.FilteredReply reply1 = new FilteredRepliesStore.FilteredReply(
                "reply-1",
                "user1",
                "id-1",
                "User",
                "Great point!",
                1000L
        );
        FilteredRepliesStore.FilteredReply reply2 = new FilteredRepliesStore.FilteredReply(
                "reply-2",
                "user2",
                "id-2",
                "Business",
                "Check our product",
                2000L
        );

        store.record("root-100", reply1);
        store.record("root-100", reply2);

        assertTrue(store.hasReplies("root-100"));
        assertEquals(2, store.getCount("root-100"));

        List<FilteredRepliesStore.FilteredReply> replies = store.getReplies("root-100");
        assertEquals(2, replies.size());
        assertEquals("reply-1", replies.get(0).getPostId());
        assertEquals("user1", replies.get(0).getAuthorScreenName());
        assertEquals("User", replies.get(0).getAuthorVerifiedType());
        assertEquals("Great point!", replies.get(0).getPostText());
        assertEquals("reply-2", replies.get(1).getPostId());
        assertEquals("user2", replies.get(1).getAuthorScreenName());
        assertEquals("Business", replies.get(1).getAuthorVerifiedType());
    }

    @Test
    public void retrievesRepliesByReplyPostId() {
        FilteredRepliesStore.FilteredReply reply = new FilteredRepliesStore.FilteredReply(
                "reply-42",
                "author_a",
                "uid-1",
                "User",
                "Replying here",
                5000L
        );

        store.record("root-thread-99", reply);

        // Can query directly with reply post ID
        assertTrue(store.hasReplies("reply-42"));
        List<FilteredRepliesStore.FilteredReply> fromReplyId = store.getReplies("reply-42");
        assertEquals(1, fromReplyId.size());
        assertEquals("reply-42", fromReplyId.get(0).getPostId());
    }

    @Test
    public void retrievesRepliesByAnyPostInTheConversation() {
        FilteredRepliesStore.FilteredReply reply = new FilteredRepliesStore.FilteredReply(
                "reply-42",
                "author_a",
                "uid-1",
                "User",
                "Replying here",
                5000L
        );

        store.associatePostWithRoot("root-thread-99", "visible-post-7");
        store.record("root-thread-99", reply);

        List<FilteredRepliesStore.FilteredReply> fromVisiblePost =
                store.getReplies("visible-post-7");
        assertEquals(1, fromVisiblePost.size());
        assertEquals("reply-42", fromVisiblePost.get(0).getPostId());
    }

    @Test
    public void mergesPreviouslyCapturedRepliesAlongIncrementalParentChain() {
        FilteredRepliesStore.FilteredReply reply = new FilteredRepliesStore.FilteredReply(
                "hidden-child", "blue", "blue-id", "User", "hidden", 5000L
        );

        store.record("module-root", reply);
        store.associatePostWithRoot("module-root", "hidden-child");
        store.associatePostWithParent("hidden-child", "visible-parent");
        store.associatePostWithParent("visible-parent", "focal-post");

        assertEquals(1, store.getCount("focal-post"));
        assertEquals("hidden-child", store.getReplies("focal-post").get(0).getPostId());
        assertEquals(1, store.getCount("visible-parent"));
        assertEquals(1, store.getCount("module-root"));
    }

    @Test
    public void tracksAuthorshipAndParentEdges() {
        store.notePostAuthorship("post-1", "author-a", "post-0");
        store.notePostAuthorship("post-0", "author-a", null);
        store.notePostAuthorship("self-loop", "author-b", "self-loop");
        store.notePostAuthorship(null, "author-c", "post-0");

        assertEquals("author-a", store.authorIdFor("post-1"));
        assertEquals("post-0", store.parentIdFor("post-1"));
        assertNull(store.parentIdFor("post-0"));
        assertNull(store.parentIdFor("self-loop"));
        assertNull(store.authorIdFor("unknown"));
    }

    @Test
    public void deduplicatesByPostId() {
        FilteredRepliesStore.FilteredReply reply = new FilteredRepliesStore.FilteredReply(
                "reply-1",
                "user1",
                "id-1",
                "User",
                "Initial text",
                1000L
        );
        FilteredRepliesStore.FilteredReply updated = new FilteredRepliesStore.FilteredReply(
                "reply-1",
                "user1",
                "id-1",
                "User",
                "Updated text",
                2000L
        );

        store.record("root-1", reply);
        store.record("root-1", updated);

        assertEquals(1, store.getCount("root-1"));
        assertEquals("Updated text", store.getReplies("root-1").get(0).getPostText());
    }

    @Test
    public void returnsEmptyForUnknownPost() {
        assertFalse(store.hasReplies("nonexistent"));
        assertEquals(0, store.getCount("nonexistent"));
        assertTrue(store.getReplies("nonexistent").isEmpty());
    }

    @Test
    public void clearEmptiesStore() {
        FilteredRepliesStore.FilteredReply reply = new FilteredRepliesStore.FilteredReply(
                "r-1", "u-1", "id-1", "User", "text", 100L
        );
        store.record("root-1", reply);

        store.clear();

        assertFalse(store.hasReplies("root-1"));
        assertFalse(store.hasReplies("r-1"));
        assertEquals(0, store.getCount("root-1"));
    }
}
