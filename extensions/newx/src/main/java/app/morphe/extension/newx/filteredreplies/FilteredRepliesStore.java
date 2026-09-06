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

    // Secondary LRU index mapping any post in a conversation -> rootPostId.
    private final LinkedHashMap<String, String> postToRoot =
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

        String resolvedRootId = resolveRoot(rootPostId);

        LinkedHashMap<String, FilteredReply> replies =
                threads.computeIfAbsent(resolvedRootId, k -> new LinkedHashMap<>());

        String replyId = reply.getPostId();
        if (replies.size() < MAX_REPLIES_PER_THREAD || replies.containsKey(replyId)) {
            replies.put(replyId, reply);
        }

        if (!replyId.isEmpty()) {
            postToRoot.put(replyId, resolvedRootId);
        }
    }

    /**
     * Associates a visible post from a conversation module with that module's root.
     *
     * The post-options presenter supplies the selected post ID, which can be different
     * from the root ID used by the conversation module. Keeping this alias lets the menu
     * resolve the same buffered replies regardless of which post in the conversation was
     * used to open it.
     */
    public synchronized void associatePostWithRoot(String rootPostId, String postId) {
        if (rootPostId == null || rootPostId.isEmpty() || postId == null || postId.isEmpty()) return;

        String resolvedRoot = resolveRoot(rootPostId);
        String existingRoot = resolveRoot(postId);
        if (!resolvedRoot.equals(existingRoot)) {
            if (postToRoot.containsKey(postId)) {
                mergeRoots(existingRoot, resolvedRoot);
                resolvedRoot = existingRoot;
            } else {
                mergeRoots(resolvedRoot, existingRoot);
            }
        }
        postToRoot.put(postId, resolvedRoot);
    }

    /**
     * Connects a post to its immediate replied-to post, merging any reply buffers already
     * discovered for either side. Parent links can arrive over several incremental timeline
     * updates, so merging must also migrate replies captured before the full chain was known.
     */
    public synchronized void associatePostWithParent(String postId, String parentPostId) {
        if (postId == null || postId.isEmpty()
                || parentPostId == null || parentPostId.isEmpty()
                || postId.equals(parentPostId)) {
            return;
        }

        String parentRoot = resolveRoot(parentPostId);
        String postRoot = resolveRoot(postId);
        if (!parentRoot.equals(postRoot)) {
            mergeRoots(parentRoot, postRoot);
        }
        postToRoot.put(postId, parentRoot);
    }

    public synchronized List<FilteredReply> getReplies(String postId) {
        if (postId == null || postId.isEmpty()) return Collections.emptyList();

        String rootId = resolveRoot(postId);
        LinkedHashMap<String, FilteredReply> map = threads.get(rootId);
        if (map != null && !map.isEmpty()) {
            return new ArrayList<>(map.values());
        }

        return Collections.emptyList();
    }

    private String resolveRoot(String postId) {
        String current = postId;
        for (int depth = 0; depth < MAX_INDEXED_REPLIES; depth++) {
            String next = postToRoot.get(current);
            if (next == null || next.isEmpty() || next.equals(current)) return current;
            current = next;
        }
        return postId;
    }

    private void mergeRoots(String preferredRoot, String mergedRoot) {
        if (preferredRoot.equals(mergedRoot)) return;

        LinkedHashMap<String, FilteredReply> mergedReplies = threads.remove(mergedRoot);
        if (mergedReplies != null && !mergedReplies.isEmpty()) {
            LinkedHashMap<String, FilteredReply> preferredReplies =
                    threads.computeIfAbsent(preferredRoot, ignored -> new LinkedHashMap<>());
            for (Map.Entry<String, FilteredReply> entry : mergedReplies.entrySet()) {
                if (preferredReplies.size() >= MAX_REPLIES_PER_THREAD
                        && !preferredReplies.containsKey(entry.getKey())) {
                    continue;
                }
                preferredReplies.put(entry.getKey(), entry.getValue());
            }
        }

        List<String> aliases = new ArrayList<>(postToRoot.keySet());
        for (String alias : aliases) {
            if (mergedRoot.equals(postToRoot.get(alias))) {
                postToRoot.put(alias, preferredRoot);
            }
        }
        postToRoot.put(mergedRoot, preferredRoot);
    }

    public synchronized boolean hasReplies(String postId) {
        return !getReplies(postId).isEmpty();
    }

    public synchronized int getCount(String postId) {
        return getReplies(postId).size();
    }

    public synchronized void clear() {
        threads.clear();
        postToRoot.clear();
    }
}
