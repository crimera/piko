package app.morphe.extension.newx.timeline;

import static org.junit.Assert.assertTrue;

import app.morphe.extension.newx.filteredreplies.FilteredRepliesStore;
import app.morphe.extension.newx.misc.InlineActionFilter;
import app.morphe.extension.newx.postfilter.PostFilterMatcher;
import app.morphe.extension.newx.postfilter.PostFilterRule;
import app.morphe.extension.newx.postfilter.PostFilterRuleStore;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Runtime baseline guard. Deterministic bridge-call counts per timeline-filter
 * feature; ms lines are informational only (stub methods, noisy). Re-run with:
 * ./gradlew :extensions:newx:testDebugUnitTest
 * --tests "app.morphe.extension.newx.timeline.TimelineFilterBaselineTest"
 * then read BASELINE lines from the test XML system-out. */
public class TimelineFilterBaselineTest {
    private static final int MEASURE_ITERS = 5;

    private enum VerifiedType { Business, Government, User, Unknown, NotVerified }
    private enum AiSource { UserMarked, AutoDetected }

    private static final class Post {
        String text = "plain post";
        String id = "post-id";
        String entryId = "post-1";
        Object promotedMetadata;
        Object clientEventInfo;
        Disclosure disclosure;
        String authorScreenName = "author";
        String authorId = "author-id";
        VerifiedType verifiedType;
    }

    private static final class Disclosure {
        final boolean aiGenerated;
        final AiSource source;
        Disclosure(boolean aiGenerated, AiSource source) {
            this.aiGenerated = aiGenerated;
            this.source = source;
        }
    }

    private static final class Module {
        final String entryId;
        final List<Item> children;
        Module(String entryId, List<Item> children) {
            this.entryId = entryId;
            this.children = children;
        }
    }

    private static final class Item {
        final Object item;
        Item(Object item) { this.item = item; }
    }

    private static final class Action {
        final String name;
        Action(String name) { this.name = name; }
        @Override public String toString() {
            return "InlineActionEntry(actionType=" + name + ", isEnabled=true)";
        }
    }

    private static final class CountingAccess extends TimelineModelAccess {
        int postText;
        int mentions;
        int authorName;
        int authorId;
        int verifiedType;
        int moduleEntryId;
        int postEntryId;
        int disclosure;
        int aiCheck;
        int aiSource;

        void reset() {
            postText = 0;
            mentions = 0;
            authorName = 0;
            authorId = 0;
            verifiedType = 0;
            disclosure = 0;
            aiCheck = 0;
            aiSource = 0;
            moduleEntryId = 0;
            postEntryId = 0;
        }

        int total() {
            return postText + mentions + authorName + authorId + verifiedType
                    + moduleEntryId + postEntryId + disclosure + aiCheck + aiSource;
        }

        @Override boolean isModuleItem(Object value) { return value instanceof Item; }
        @Override boolean isPost(Object value) { return value instanceof Post; }
        @Override boolean isModule(Object value) { return value instanceof Module; }
        @Override Object getModuleItem(Object wrapper) { return ((Item) wrapper).item; }
        @Override boolean isModuleItemDispensable(Object wrapper) { return false; }
        @Override Object copyModuleItem(Object wrapper, Object item, boolean dispensable) {
            return new Item(item);
        }
        @Override List<?> getModuleChildren(Object module) { return ((Module) module).children; }
        @Override Object getModuleDisplayType(Object module) { return null; }
        @Override Object copyModule(Object module, List<?> children, Object displayType) {
            List<Item> copied = new ArrayList<>();
            for (Object child : children) copied.add((Item) child);
            return new Module(((Module) module).entryId, copied);
        }
        @Override Object getPostId(Object post) { return ((Post) post).id; }
        @Override String getModuleEntryId(Object module) {
            moduleEntryId++;
            return ((Module) module).entryId;
        }
        @Override String getPostEntryId(Object post) {
            postEntryId++;
            return ((Post) post).entryId;
        }
        @Override Object getPostPromotedMetadata(Object post) {
            return ((Post) post).promotedMetadata;
        }
        @Override Object getPostClientEventInfo(Object post) { return null; }
        @Override String getPostText(Object post) {
            postText++;
            return ((Post) post).text;
        }
        @Override List<?> getPostMentions(Object post) {
            mentions++;
            return Collections.emptyList();
        }
        @Override String getPostAuthorScreenName(Object post) {
            authorName++;
            return ((Post) post).authorScreenName;
        }
        @Override Object getPostAuthorVerifiedType(Object post) {
            verifiedType++;
            return ((Post) post).verifiedType;
        }
        @Override String getPostAuthorId(Object post) {
            authorId++;
            return ((Post) post).authorId;
        }
        @Override Object getContentDisclosure(Object post) {
            disclosure++;
            return ((Post) post).disclosure;
        }
        @Override boolean hasAiGeneratedDisclosure(Object disclosure) {
            aiCheck++;
            return ((Disclosure) disclosure).aiGenerated;
        }
        @Override Object getAiDetectionSource(Object disclosure) {
            aiSource++;
            return ((Disclosure) disclosure).source;
        }
    }

    private final CountingAccess access = new CountingAccess();
    private List<Object> posts;
    private List<Object> modules;

    @Before
    public void setUp() {
        FilteredRepliesStore.shared().clear();
        posts = buildPosts();
        modules = buildModules();
    }

    private static Post plainPost(int index) {
        Post post = new Post();
        post.text = "plain post number " + index;
        post.id = "post-" + index;
        post.entryId = "post-" + index;
        post.authorScreenName = "author" + index;
        post.authorId = "author-id-" + index;
        return post;
    }

    private List<Object> buildPosts() {
        List<Object> items = new ArrayList<>(2000);
        for (int index = 0; index < 1500; index++) items.add(plainPost(index));
        for (int index = 0; index < 100; index++) {
            Post post = plainPost(1500 + index);
            post.entryId = "promoted-tweet-" + index;
            items.add(post);
        }
        for (int index = 0; index < 50; index++) {
            Post post = plainPost(1600 + index);
            post.promotedMetadata = new Object();
            items.add(post);
        }
        for (int index = 0; index < 100; index++) {
            Post post = plainPost(1650 + index);
            post.text = "this post contains blocked phrase number " + index;
            items.add(post);
        }
        for (int index = 0; index < 100; index++) {
            Post post = plainPost(1750 + index);
            post.authorScreenName = "spamuser" + index;
            items.add(post);
        }
        for (int index = 0; index < 30; index++) {
            Post post = plainPost(1850 + index);
            post.disclosure = new Disclosure(true, AiSource.UserMarked);
            items.add(post);
        }
        for (int index = 0; index < 15; index++) {
            Post post = plainPost(1880 + index);
            post.disclosure = new Disclosure(true, AiSource.AutoDetected);
            items.add(post);
        }
        for (int index = 0; index < 5; index++) {
            Post post = plainPost(1895 + index);
            post.disclosure = new Disclosure(true, null);
            items.add(post);
        }
        for (int index = 0; index < 100; index++) items.add(plainPost(1900 + index));
        // Verified mix across the first 400 plain posts.
        for (int index = 0; index < 300; index++) {
            ((Post) items.get(index)).verifiedType = VerifiedType.User;
        }
        for (int index = 300; index < 400; index++) {
            ((Post) items.get(index)).verifiedType = VerifiedType.Business;
        }
        ((Post) items.get(7)).authorScreenName = "whitelisted7";
        ((Post) items.get(8)).authorScreenName = "whitelisted8";
        return items;
    }

    private List<Object> buildModules() {
        List<Object> items = new ArrayList<>(200);
        for (int index = 0; index < 150; index++) {
            items.add(module("module-" + index));
        }
        for (int index = 0; index < 20; index++) {
            items.add(module("who-to-follow-" + index));
        }
        for (int index = 0; index < 10; index++) {
            items.add(module("tweetdetailrelatedtweets-" + index));
        }
        for (int index = 0; index < 20; index++) {
            items.add(module("conversationthread-root-" + index + "-x"));
        }
        return items;
    }

    private static Module module(String entryId) {
        List<Item> children = new ArrayList<>(4);
        for (int child = 0; child < 4; child++) children.add(new Item(plainPost(child)));
        return new Module(entryId, children);
    }

    private interface FilterRun {
        Object run();
    }

    private void bench(String name, List<Object> input, FilterRun run) {
        // Warmup.
        run.run();
        run.run();
        FilteredRepliesStore.shared().clear();
        long[] samples = new long[MEASURE_ITERS];
        int removed = 0;
        int calls = 0;
        for (int iter = 0; iter < MEASURE_ITERS; iter++) {
            access.reset();
            FilteredRepliesStore.shared().clear();
            long start = System.nanoTime();
            Object out = run.run();
            samples[iter] = System.nanoTime() - start;
            if (iter == 0) {
                removed = input.size() - ((List<?>) out).size();
                calls = access.total();
            }
        }
        java.util.Arrays.sort(samples);
        double medianMs = samples[MEASURE_ITERS / 2] / 1_000_000.0;
        System.out.println("BASELINE feature=" + name
                + " items=" + input.size()
                + " removed=" + removed
                + " bridgeCalls=" + calls
                + " medianMs=" + String.format(java.util.Locale.ROOT, "%.2f", medianMs));
        assertTrue(name + " must remove something", removed > 0);
    }

    @Test
    public void benchPromoted() {
        bench("promoted", posts, () ->
                NewXTimelineFilter.filterPromotedItems(posts, true, access));
    }

    @Test
    public void benchWhoToFollow() {
        bench("who-to-follow", modules, () ->
                NewXTimelineFilter.filterWhoToFollow(modules, true, access));
    }

    @Test
    public void benchDiscoverMore() {
        bench("discover-more", modules, () ->
                NewXTimelineFilter.filterDiscoverMore(modules, true, access));
    }

    @Test
    public void benchKeywordContent() {
        PostFilterRule rule = new PostFilterRule("baseline-content", "blocked", true, false, true);
        PostFilterRuleStore.Snapshot snapshot =
                PostFilterRuleStore.snapshotOf(List.of(rule));
        bench("keyword-content", posts, () ->
                NewXTimelineFilter.filterPostsByKeyword(posts, true, snapshot, access));
    }

    @Test
    public void benchKeywordUsername() {
        PostFilterRule rule = new PostFilterRule("baseline-username", "spamuser", false, true, true);
        PostFilterRuleStore.Snapshot snapshot =
                PostFilterRuleStore.snapshotOf(List.of(rule));
        bench("keyword-username", posts, () ->
                NewXTimelineFilter.filterPostsByKeyword(posts, true, snapshot, access));
    }

    @Test
    public void benchAiGenerated() {
        Set<String> sources = new HashSet<>();
        sources.add("UserMarked");
        sources.add("AutoDetected");
        sources.add("SourceNotIdentified");
        bench("ai-generated", posts, () ->
                NewXTimelineFilter.filterAiGeneratedPosts(posts, sources, access));
    }

    @Test
    public void benchVerifiedType() {
        Set<String> types = new HashSet<>();
        types.add("User");
        types.add("Business");
        Set<String> whitelist = new HashSet<>();
        whitelist.add("whitelisted7");
        whitelist.add("whitelisted8");
        bench("verified-type", posts, () ->
                NewXTimelineFilter.filterPostsByVerifiedType(
                        posts, types, true, true, whitelist, access));
    }

    @Test
    public void benchInlineActions() {
        List<Object> actions = new ArrayList<>();
        String[] names = {"Reply", "Retweet", "Favorite", "Share", "Bookmark", "ViewCounts",
                "Unfavorite", "UndoRetweet", "AddToBookmarks", "RemoveFromBookmarks",
                "Analytics", "Mute"};
        for (String name : names) actions.add(new Action(name));
        Set<String> hidden = Set.of("Reply", "Retweet", "Share", "AddRemoveBookmarks");
        // Warmup.
        for (int warm = 0; warm < 100; warm++) {
            InlineActionFilter.filter(new ArrayList<>(actions), hidden, new Object());
        }
        int iters = 2000;
        long start = System.nanoTime();
        int removedTotal = 0;
        for (int iter = 0; iter < iters; iter++) {
            List<?> out = InlineActionFilter.filter(new ArrayList<>(actions), hidden, new Object());
            removedTotal += actions.size() - out.size();
        }
        double totalMs = (System.nanoTime() - start) / 1_000_000.0;
        System.out.println("BASELINE feature=inline-actions"
                + " items=" + actions.size()
                + " removedPerRun=" + (removedTotal / iters)
                + " runs=" + iters
                + " totalMs=" + String.format(java.util.Locale.ROOT, "%.2f", totalMs));
        assertTrue("inline actions must hide something", removedTotal > 0);
    }

    @Test
    public void benchMatcherDirect() {
        List<String> phrases = new ArrayList<>();
        for (int index = 0; index < 20; index++) phrases.add("blocked" + index);
        List<PostFilterRule> rules = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            rules.add(new PostFilterRule("m" + index, "blocked" + index, true, false, true));
        }
        PostFilterRuleStore.Snapshot snapshot = PostFilterRuleStore.snapshotOf(rules);
        List<String> texts = new ArrayList<>(2000);
        for (int index = 0; index < 2000; index++) {
            texts.add(index % 20 == 0
                    ? "post with blocked" + (index % 20) + " inside"
                    : "ordinary post body number " + index);
        }
        for (int warm = 0; warm < 2; warm++) {
            for (String text : texts) PostFilterMatcher.findMatchReason(text, null, snapshot);
        }
        long[] samples = new long[MEASURE_ITERS];
        int hits = 0;
        for (int iter = 0; iter < MEASURE_ITERS; iter++) {
            long start = System.nanoTime();
            int runHits = 0;
            for (String text : texts) {
                if (PostFilterMatcher.findMatchReason(text, null, snapshot) != null) runHits++;
            }
            samples[iter] = System.nanoTime() - start;
            if (iter == 0) hits = runHits;
        }
        java.util.Arrays.sort(samples);
        double medianMs = samples[MEASURE_ITERS / 2] / 1_000_000.0;
        System.out.println("BASELINE feature=matcher-direct"
                + " items=" + texts.size()
                + " phrases=" + phrases.size()
                + " hits=" + hits
                + " medianMs=" + String.format(java.util.Locale.ROOT, "%.2f", medianMs));
        assertTrue("matcher must hit", hits > 0);
    }
}
