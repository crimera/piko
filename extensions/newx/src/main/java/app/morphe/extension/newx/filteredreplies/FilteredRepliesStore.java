package app.morphe.extension.newx.filteredreplies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory thread-safe buffer for replies filtered out by NewX verified accounts check.
 * Maps conversation root post IDs to their filtered replies.
 */
public final class FilteredRepliesStore {
    private static final int MAX_THREADS = 50;
    private static final int MAX_REPLIES_PER_THREAD = 100;
    private static final int MAX_INDEXED_REPLIES = 500;

    /**
     * Represents an individual reply that was hidden due to its author's verification status.
     */
    public static final class FilteredReply {
        private final String postId;
        private final String authorScreenName;
        private final String authorId;
        private final String authorVerifiedType;
        private final String postText;
        private final long timestamp;

        public FilteredReply(
                String postId,
                String authorScreenName,
                String authorId,
                String authorVerifiedType,
                String postText,
                long timestamp
        ) {
            this.postId = postId != null ? postId : "";
            this.authorScreenName = authorScreenName != null ? authorScreenName : "";
            this.authorId = authorId != null ? authorId : "";
            this.authorVerifiedType = authorVerifiedType != null ? authorVerifiedType : "";
            this.postText = postText != null ? postText : "";
            this.timestamp = timestamp;
        }

        public String getPostId() {
            return postId;
        }

        public String getAuthorScreenName() {
            return authorScreenName;
        }

        public String getAuthorId() {
            return authorId;
        }

        public String getAuthorVerifiedType() {
            return authorVerifiedType;
        }

        public String getPostText() {
            return postText;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FilteredReply that)) return false;
            return Objects.equals(postId, that.postId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(postId);
        }
    }

    // LRU cache mapping rootPostId -> LinkedHashMap<postId, FilteredReply>
    private final LinkedHashMap<String, LinkedHashMap<String, FilteredReply>> threads =
            new LinkedHashMap<>(MAX_THREADS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, LinkedHashMap<String, FilteredReply>> eldest) {
                    return size() > MAX_THREADS;
                }
            };

    // Secondary LRU index mapping replyPostId -> rootPostId
    private final LinkedHashMap<String, String> replyToRoot =
            new LinkedHashMap<>(MAX_INDEXED_REPLIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_INDEXED_REPLIES;
                }
            };

    private static final FilteredRepliesStore INSTANCE = new FilteredRepliesStore();

    public static FilteredRepliesStore shared() {
        return INSTANCE;
    }

    private FilteredRepliesStore() {
    }

    public synchronized void record(String rootPostId, FilteredReply reply) {
        if (rootPostId == null || rootPostId.isEmpty() || reply == null) return;

        LinkedHashMap<String, FilteredReply> replies =
                threads.computeIfAbsent(rootPostId, k -> new LinkedHashMap<>());

        String replyId = reply.getPostId();
        if (replies.size() < MAX_REPLIES_PER_THREAD || replies.containsKey(replyId)) {
            replies.put(replyId, reply);
        }

        if (!replyId.isEmpty()) {
            replyToRoot.put(replyId, rootPostId);
        }
    }

    public synchronized List<FilteredReply> getReplies(String postId) {
        if (postId == null || postId.isEmpty()) return Collections.emptyList();

        LinkedHashMap<String, FilteredReply> map = threads.get(postId);
        if (map != null && !map.isEmpty()) {
            return new ArrayList<>(map.values());
        }

        String rootId = replyToRoot.get(postId);
        if (rootId != null) {
            map = threads.get(rootId);
            if (map != null && !map.isEmpty()) {
                return new ArrayList<>(map.values());
            }
        }

        return Collections.emptyList();
    }

    public synchronized boolean hasReplies(String postId) {
        return !getReplies(postId).isEmpty();
    }

    public synchronized int getCount(String postId) {
        return getReplies(postId).size();
    }

    public synchronized void clear() {
        threads.clear();
        replyToRoot.clear();
    }
}
