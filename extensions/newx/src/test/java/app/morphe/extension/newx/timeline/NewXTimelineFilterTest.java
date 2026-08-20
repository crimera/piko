package app.morphe.extension.newx.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.newx.postfilter.PostFilterRule;
import app.morphe.extension.newx.postfilter.PostFilterRuleStore;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class NewXTimelineFilterTest {
    private static final FakeModelAccess MODELS = new FakeModelAccess();
    private static final ThrowingModelAccess THROWING_MODELS = new ThrowingModelAccess();
    private static final UnsupportedDisclosureModelAccess UNSUPPORTED_DISCLOSURE_MODELS =
            new UnsupportedDisclosureModelAccess();

    @Test
    public void disabledFilterReturnsOriginalList() {
        List<Object> items = items(module("who-to-follow-1", item(post("safe"))));
        assertSame(items, NewXTimelineFilter.filterWhoToFollow(items, false, MODELS));
    }

    @Test
    public void unchangedFilterReturnsOriginalList() {
        List<Object> items = items(post("safe"));
        assertSame(items, NewXTimelineFilter.filterPromotedItems(items, true, MODELS));
    }

    @Test
    public void removesWhoToFollowModuleByEntryId() {
        List<Object> filtered = filterWhoToFollow(module("who-to-follow-1", item(post("safe"))));
        assertTrue(filtered.isEmpty());
    }

    @Test
    public void disabledDiscoverMoreFilterReturnsOriginalList() {
        List<Object> items = items(module("tweetdetailrelatedtweets", item(post("recommended"))));
        assertSame(items, NewXTimelineFilter.filterDiscoverMore(items, false, MODELS));
    }

    @Test
    public void removesDiscoverMoreModuleByEntryId() {
        List<Object> filtered = filterDiscoverMore(module("tweetdetailrelatedtweets", item(post("recommended"))));
        assertTrue(filtered.isEmpty());
    }

    @Test
    public void removesDiscoverMoreModuleWithEntryIdSuffix() {
        List<Object> filtered = filterDiscoverMore(module("tweetdetailrelatedtweets-123", item(post("recommended"))));
        assertTrue(filtered.isEmpty());
    }

    @Test
    public void keepsSimilarDiscoverMoreEntryIds() {
        FakeModule module = module("tweetdetailrelatedtweetsx", item(post("kept")));
        List<Object> input = items(module);

        assertSame(input, NewXTimelineFilter.filterDiscoverMore(input, true, MODELS));
    }

    @Test
    public void removesNestedModuleAndReconstructsParent() {
        FakeModule outer = module(
                "conversationthread-1",
                item(module("who-to-follow-1", item(post("suggested")))),
                item(post("kept"))
        );

        List<Object> filtered = filterWhoToFollow(outer);
        FakeModule filteredOuter = (FakeModule) filtered.get(0);

        assertNotSame(outer, filteredOuter);
        assertEquals(1, filteredOuter.children.size());
        assertEquals("kept", ((FakePost) filteredOuter.children.get(0).item).text);
    }

    @Test
    public void keepsOrdinaryThreePartConversationEntryId() {
        FakeModule conversation = module("conversationthread-123-456", item(post("kept")));
        List<Object> input = items(conversation);

        assertSame(input, NewXTimelineFilter.filterPromotedItems(input, true, MODELS));
    }

    @Test
    public void removesPromotedEntryIdPatterns() {
        FakePost promoted = post("buy now");
        promoted.entryId = "promoted-tweet-123";
        FakePost ad = post("sponsored");
        ad.entryId = "timeline-ad-456";

        Object filtered = NewXTimelineFilter.filterPromotedItems(
                items(promoted, ad, post("kept")),
                true,
                MODELS
        );
        assertEquals(1, ((List<?>) filtered).size());
    }

    @Test
    public void removesPromotedPostAndRtbAd() {
        FakePost promoted = post("buy now");
        promoted.promotedMetadata = new Object();
        List<Object> input = items(promoted, new FakeRtbAd(), post("kept"));

        Object filtered = NewXTimelineFilter.filterPromotedItems(input, true, MODELS);
        assertEquals(List.of(input.get(2)), filtered);
    }

    @Test
    public void removesFilteredPostIdFromVerticalConversationMetadata() {
        FakePost promoted = post("promoted");
        promoted.id = "post-1";
        promoted.promotedMetadata = new Object();
        FakePost kept = post("kept");
        kept.id = "post-2";
        FakeModule module = module("conversationthread-1", item(promoted), item(kept));
        module.displayType = new FakeVerticalConversation(List.of("post-1", "post-2"));

        @SuppressWarnings("unchecked")
        List<Object> filtered = (List<Object>) NewXTimelineFilter.filterPromotedItems(
                items(module),
                true,
                MODELS
        );
        FakeModule filteredModule = (FakeModule) filtered.get(0);
        FakeVerticalConversation displayType = (FakeVerticalConversation) filteredModule.displayType;

        assertEquals(List.of("post-2"), displayType.postIds);
    }

    @Test
    public void removesPromotedClientEventInfo() {
        FakePost promoted = post("buy now");
        promoted.clientEventInfo = "ClientEventInfo(component=promoted_content)";

        Object filtered = NewXTimelineFilter.filterPromotedItems(
                items(promoted, post("kept")),
                true,
                MODELS
        );
        assertEquals(1, ((List<?>) filtered).size());
    }

    @Test
    public void removesMainTextKeywordMatchOnly() {
        List<Object> input = items(post("safe"), post("contains blocked phrase"));
        PostFilterRule rule = new PostFilterRule("content", "blocked", true, false, true);
        PostFilterRuleStore.Snapshot snapshot = PostFilterRuleStore.snapshotOf(List.of(rule));

        Object filtered = NewXTimelineFilter.filterPostsByKeyword(input, true, snapshot, MODELS);
        assertEquals(List.of(input.get(0)), filtered);
    }

    @Test
    public void keepsReplyToUserMatchingContentKeywordWhenBodyIsClean() {
        FakePost reply = reply("@rezero This is a clean take", "steven", "rezero");

        Object filtered = filterByContentKeyword(items(reply), "rezero");
        assertEquals(List.of(reply), filtered);
    }

    @Test
    public void removesPostWithContentKeywordInBody() {
        FakePost body = post("this mentions rezero in the body");

        Object filtered = filterByContentKeyword(items(body), "rezero");
        assertTrue(((List<?>) filtered).isEmpty());
    }

    @Test
    public void removesPostWithMidTextMentionContainingKeyword() {
        FakePost midText = post("hello @rezero world");
        midText.mentions = List.of(new FakeMention(6, 13, "rezero"));

        Object filtered = filterByContentKeyword(items(midText), "rezero");
        assertTrue(((List<?>) filtered).isEmpty());
    }

    @Test
    public void removesPostWhenLeadingMentionIsFollowedByKeywordBody() {
        FakePost reply = reply("@steven rezero is great", "steven", "steven");

        Object filtered = filterByContentKeyword(items(reply), "rezero");
        assertTrue(((List<?>) filtered).isEmpty());
    }

    @Test
    public void removesPostWhenAuthorMatchesUsernameRule() {
        FakePost post = post("clean body");
        post.authorScreenName = "rezero";

        Object filtered = filterByUsernameKeyword(items(post), "rezero");
        assertTrue(((List<?>) filtered).isEmpty());
    }

    @Test
    public void keepsReplyToUserMatchingUsernameRuleWhenAuthorDiffers() {
        FakePost reply = reply("@rezero clean body", "steven", "rezero");

        Object filtered = filterByUsernameKeyword(items(reply), "rezero");
        assertEquals(List.of(reply), filtered);
    }

    @Test
    public void stripsAllLeadingMentionsFromMultiTargetReply() {
        FakePost reply = reply("@u1 @u2 body", "steven", "u1", "u2");

        assertEquals(List.of(reply), filterByContentKeyword(items(reply), "u1"));
        assertEquals(List.of(reply), filterByContentKeyword(items(reply), "u2"));
        assertTrue(((List<?>) filterByContentKeyword(items(reply), "body")).isEmpty());
    }

    @Test
    public void handlesNullEmptyAndMentionOnlyPosts() {
        FakePost nullText = post(null);
        FakePost emptyText = post("");
        FakePost mentionsOnly = reply("@u1 @u2", "steven", "u1", "u2");
        List<Object> input = items(nullText, emptyText, mentionsOnly);

        Object filtered = filterByContentKeyword(input, "u1");
        assertEquals(input, filtered);
    }

    @Test
    public void stripsLeadingMentionsWithInterstitialWhitespace() {
        FakePost spaced = reply("@u1  @u2 body", "steven", "u1", "u2");

        Object filtered = filterByContentKeyword(items(spaced), "u2");
        assertEquals(List.of(spaced), filtered);
    }

    @Test
    public void removesSelectedAiDisclosureSource() {
        FakePost userMarked = post("generated");
        userMarked.disclosure = new FakeDisclosure(true, AiSource.UserMarked);
        FakePost autoDetected = post("detected");
        autoDetected.disclosure = new FakeDisclosure(true, AiSource.AutoDetected);

        Object filtered = NewXTimelineFilter.filterAiGeneratedPosts(
                items(userMarked, autoDetected),
                Set.of("UserMarked"),
                MODELS
        );
        assertEquals(List.of(autoDetected), filtered);
    }

    @Test
    public void removesUnclassifiedAiDisclosureWhenSelected() {
        FakePost unclassified = post("generated");
        unclassified.disclosure = new FakeDisclosure(true, null);

        Object filtered = NewXTimelineFilter.filterAiGeneratedPosts(
                items(unclassified),
                Set.of("SourceNotIdentified"),
                MODELS
        );
        assertEquals(List.of(), filtered);
    }

    @Test
    public void preservesPostWhenDisclosureModelVariantIsUnsupported() {
        FakePost tombstone = post("tombstone");
        FakeModule module = module("conversationthread-1", item(tombstone));
        List<Object> input = items(module);

        Object result = NewXTimelineFilter.filterAiGeneratedPosts(
                input,
                Set.of("SourceNotIdentified"),
                UNSUPPORTED_DISCLOSURE_MODELS
        );

        assertSame(input, result);
        FakeModule preserved = (FakeModule) ((List<?>) result).get(0);
        assertSame(tombstone, preserved.children.get(0).item);
    }

    @Test
    public void preservesMalformedAndUnknownItems() {
        Object unknown = new Object();
        FakeModule mixed = module(
                "conversationthread-1",
                item(post("inner-a")),
                item(unknown),
                item(new FakeRtbAd()),
                item(post("inner-b"))
        );
        List<Object> input = items(post("safe"), unknown, null, mixed, new FakeRtbAd(), post("kept"));

        List<?> result = (List<?>) NewXTimelineFilter.filterPromotedItems(input, true, MODELS);

        assertEquals(5, result.size());
        assertSame(input.get(0), result.get(0));
        assertSame(input.get(1), result.get(1));
        assertNull(result.get(2));

        FakeModule filteredMixed = (FakeModule) result.get(3);
        assertNotSame(mixed, filteredMixed);
        assertEquals(3, filteredMixed.children.size());
        assertSame(mixed.children.get(0), filteredMixed.children.get(0));
        assertSame(mixed.children.get(1), filteredMixed.children.get(1));
        assertSame(mixed.children.get(3), filteredMixed.children.get(2));

        assertSame(input.get(5), result.get(4));
    }

    @Test
    public void keepsItemWhenModelAccessThrows() {
        FakePost throwing = post("throwing");
        FakePost safe = post("safe");
        List<Object> input = items(throwing, new FakeRtbAd(), safe);

        List<?> result = (List<?>) NewXTimelineFilter.filterPromotedItems(input, true, THROWING_MODELS);

        assertEquals(2, result.size());
        assertSame(throwing, result.get(0));
        assertSame(safe, result.get(1));
    }

    @Test
    public void keepsModuleChildWhenModelAccessThrows() {
        FakeModule conversation = module(
                "conversationthread-1",
                item(post("throwing")),
                item(new FakeRtbAd()),
                item(post("ok"))
        );

        List<?> result = (List<?>) NewXTimelineFilter.filterPromotedItems(
                items(conversation),
                true,
                THROWING_MODELS
        );
        FakeModule filtered = (FakeModule) result.get(0);

        assertNotSame(conversation, filtered);
        assertEquals(2, filtered.children.size());
        assertSame(conversation.children.get(0), filtered.children.get(0));
        assertSame(conversation.children.get(2), filtered.children.get(1));
    }

    @Test
    public void preservesIdentityOfUnchangedPrefixAndSuffix() {
        FakePost p0 = post("p0");
        FakePost p1 = post("p1");
        FakePost p2 = post("p2");
        FakePost p3 = post("p3");
        List<Object> input = items(p0, p1, new FakeRtbAd(), p2, p3);

        List<?> result = (List<?>) NewXTimelineFilter.filterPromotedItems(input, true, MODELS);

        assertEquals(4, result.size());
        assertNotSame(input, result);
        assertSame(p0, result.get(0));
        assertSame(p1, result.get(1));
        assertSame(p2, result.get(2));
        assertSame(p3, result.get(3));
    }

    @Test
    public void keepsUnchangedNestedModuleInstance() {
        FakeModule inner = module("conversationthread-1", item(post("a")), item(post("b")));
        FakeModule outer = module("conversationthread-2", item(inner), item(new FakeRtbAd()));

        List<?> result = (List<?>) NewXTimelineFilter.filterPromotedItems(items(outer), true, MODELS);
        FakeModule filteredOuter = (FakeModule) result.get(0);

        assertNotSame(outer, filteredOuter);
        assertEquals(1, filteredOuter.children.size());
        assertSame(inner, filteredOuter.children.get(0).item);
    }

    @Test
    public void unchangedNonListIterableReturnsSameInstance() {
        FakeIterable input = new FakeIterable(items(post("a"), post("b")));

        assertSame(input, NewXTimelineFilter.filterPromotedItems(input, true, MODELS));
    }

    @Test
    public void changedNonListIterableBuildsListInOrder() {
        FakeIterable input = new FakeIterable(items(post("a"), new FakeRtbAd(), post("b"), post("c")));

        Object filtered = NewXTimelineFilter.filterPromotedItems(input, true, MODELS);

        assertTrue(filtered instanceof List);
        List<?> result = (List<?>) filtered;
        assertEquals(3, result.size());
        assertSame(input.values.get(0), result.get(0));
        assertSame(input.values.get(2), result.get(1));
        assertSame(input.values.get(3), result.get(2));
    }

    @Test
    public void filtersRealisticLargeTimeline() {
        List<Object> input = new ArrayList<>();
        int postCount = 0;
        final int total = 5000;
        for (int i = 0; i < total; i++) {
            switch (i % 50) {
                case 5:
                    input.add(new FakeRtbAd());
                    break;
                case 13:
                    input.add(module("who-to-follow-" + i, item(post("suggested-" + i))));
                    break;
                case 29:
                    input.add(module(
                            "conversationthread-" + i,
                            item(post("a-" + i)),
                            item(new FakeRtbAd()),
                            item(post("b-" + i))
                    ));
                    break;
                default:
                    input.add(post("post-" + postCount));
                    postCount++;
                    break;
            }
        }
        assertEquals(4700, postCount);

        Object promotedResult = NewXTimelineFilter.filterPromotedItems(input, true, MODELS);
        assertEquals(4900, ((List<?>) promotedResult).size());

        List<?> result = (List<?>) NewXTimelineFilter.filterWhoToFollow(promotedResult, true, MODELS);
        assertEquals(4800, result.size());
        assertSame(input.get(0), result.get(0));

        int standalonePosts = 0;
        int conversationModules = 0;
        int expectedPostNumber = 0;
        for (Object entry : result) {
            if (entry instanceof FakeModule) {
                FakeModule module = (FakeModule) entry;
                conversationModules++;
                assertTrue(module.entryId.startsWith("conversationthread-"));
                assertEquals(2, module.children.size());
                assertTrue(((FakePost) module.children.get(0).item).text.startsWith("a-"));
                assertTrue(((FakePost) module.children.get(1).item).text.startsWith("b-"));
            } else {
                assertTrue(entry instanceof FakePost);
                assertEquals("post-" + expectedPostNumber, ((FakePost) entry).text);
                expectedPostNumber++;
                standalonePosts++;
            }
        }
        assertEquals(100, conversationModules);
        assertEquals(4700, standalonePosts);
        assertEquals(4700, expectedPostNumber);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> filterDiscoverMore(Object... values) {
        return (List<Object>) NewXTimelineFilter.filterDiscoverMore(items(values), true, MODELS);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> filterWhoToFollow(Object... values) {
        return (List<Object>) NewXTimelineFilter.filterWhoToFollow(items(values), true, MODELS);
    }

    private static List<Object> items(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static FakePost post(String text) {
        return new FakePost(text);
    }

    private static FakePost reply(String text, String authorScreenName, String... mentionScreenNames) {
        FakePost post = new FakePost(text);
        post.authorScreenName = authorScreenName;
        List<FakeMention> mentions = new ArrayList<>();
        int searchFrom = 0;
        for (String screenName : mentionScreenNames) {
            String token = "@" + screenName;
            int start = text.indexOf(token, searchFrom);
            if (start < 0) {
                throw new IllegalArgumentException("Mention " + token + " not found in " + text);
            }
            mentions.add(new FakeMention(start, start + token.length(), screenName));
            searchFrom = start + token.length();
        }
        post.mentions = mentions;
        return post;
    }

    private static Object filterByContentKeyword(List<Object> input, String phrase) {
        PostFilterRule rule = new PostFilterRule("content-" + phrase, phrase, true, false, true);
        return NewXTimelineFilter.filterPostsByKeyword(
                input,
                true,
                PostFilterRuleStore.snapshotOf(List.of(rule)),
                MODELS
        );
    }

    private static Object filterByUsernameKeyword(List<Object> input, String phrase) {
        PostFilterRule rule = new PostFilterRule("username-" + phrase, phrase, false, true, true);
        return NewXTimelineFilter.filterPostsByKeyword(
                input,
                true,
                PostFilterRuleStore.snapshotOf(List.of(rule)),
                MODELS
        );
    }

    private static FakeModuleItem item(Object value) {
        return new FakeModuleItem(value, false);
    }

    private static FakeModule module(String entryId, FakeModuleItem... children) {
        return new FakeModule(entryId, new ArrayList<>(Arrays.asList(children)));
    }

    private enum AiSource {
        UserMarked,
        AutoDetected
    }

    private static final class FakePost {
        private final String text;
        private String id = "post-id";
        private String entryId = "post-1";
        private Object promotedMetadata;
        private Object clientEventInfo;
        private FakeDisclosure disclosure;
        private String authorScreenName;
        private List<FakeMention> mentions;

        private FakePost(String text) {
            this.text = text;
        }
    }

    private static final class FakeMention {
        private final int start;
        private final int end;
        private final String screenName;

        private FakeMention(int start, int end, String screenName) {
            this.start = start;
            this.end = end;
            this.screenName = screenName;
        }
    }

    private static final class FakeRtbAd {
    }

    private static final class FakeDisclosure {
        private final boolean aiGenerated;
        private final AiSource source;

        private FakeDisclosure(boolean aiGenerated, AiSource source) {
            this.aiGenerated = aiGenerated;
            this.source = source;
        }
    }

    private static final class FakeModuleItem {
        private final Object item;
        private final boolean dispensable;

        private FakeModuleItem(Object item, boolean dispensable) {
            this.item = item;
            this.dispensable = dispensable;
        }
    }

    private static final class FakeModule {
        private final String entryId;
        private final List<FakeModuleItem> children;
        private Object displayType;

        private FakeModule(String entryId, List<FakeModuleItem> children) {
            this.entryId = entryId;
            this.children = children;
        }
    }

    private static final class FakeVerticalConversation {
        private final List<?> postIds;

        private FakeVerticalConversation(List<?> postIds) {
            this.postIds = postIds;
        }
    }

    private static class FakeModelAccess extends TimelineModelAccess {
        @Override boolean isModuleItem(Object value) { return value instanceof FakeModuleItem; }
        @Override boolean isPost(Object value) { return value instanceof FakePost; }
        @Override boolean isModule(Object value) { return value instanceof FakeModule; }
        @Override boolean isRtbImageAd(Object value) { return value instanceof FakeRtbAd; }
        @Override Object getModuleItem(Object wrapper) { return ((FakeModuleItem) wrapper).item; }
        @Override boolean isModuleItemDispensable(Object wrapper) {
            return ((FakeModuleItem) wrapper).dispensable;
        }
        @Override Object copyModuleItem(Object wrapper, Object item, boolean dispensable) {
            return new FakeModuleItem(item, dispensable);
        }
        @Override List<?> getModuleChildren(Object module) { return ((FakeModule) module).children; }
        @Override Object getModuleDisplayType(Object module) { return ((FakeModule) module).displayType; }
        @Override Object copyModule(Object module, List<?> children, Object displayType) {
            List<FakeModuleItem> copied = new ArrayList<>();
            for (Object child : children) copied.add((FakeModuleItem) child);
            FakeModule copy = new FakeModule(((FakeModule) module).entryId, copied);
            copy.displayType = displayType;
            return copy;
        }
        @Override Object getPostId(Object post) { return ((FakePost) post).id; }
        @Override boolean isVerticalConversation(Object displayType) {
            return displayType instanceof FakeVerticalConversation;
        }
        @Override List<?> getVerticalConversationPostIds(Object displayType) {
            return ((FakeVerticalConversation) displayType).postIds;
        }
        @Override Object copyVerticalConversation(Object displayType, List<?> postIds) {
            return new FakeVerticalConversation(postIds);
        }
        @Override String getModuleEntryId(Object module) { return ((FakeModule) module).entryId; }
        @Override String getPostEntryId(Object post) { return ((FakePost) post).entryId; }
        @Override Object getPostPromotedMetadata(Object post) {
            return ((FakePost) post).promotedMetadata;
        }
        @Override Object getPostClientEventInfo(Object post) {
            return ((FakePost) post).clientEventInfo;
        }
        @Override String getPostText(Object post) { return ((FakePost) post).text; }
        @Override List<?> getPostMentions(Object post) { return ((FakePost) post).mentions; }
        @Override int getMentionStartIdx(Object mention) { return ((FakeMention) mention).start; }
        @Override int getMentionEndIdx(Object mention) { return ((FakeMention) mention).end; }
        @Override String getMentionScreenName(Object mention) {
            return ((FakeMention) mention).screenName;
        }
        @Override String getPostAuthorScreenName(Object post) {
            return ((FakePost) post).authorScreenName;
        }
        @Override Object getContentDisclosure(Object post) { return ((FakePost) post).disclosure; }
        @Override boolean hasAiGeneratedDisclosure(Object disclosure) {
            return ((FakeDisclosure) disclosure).aiGenerated;
        }
        @Override Object getAiDetectionSource(Object disclosure) {
            return ((FakeDisclosure) disclosure).source;
        }
    }

    private static final class UnsupportedDisclosureModelAccess extends FakeModelAccess {
        @Override
        Object getContentDisclosure(Object post) {
            throw new ClassCastException("unsupported post-result variant");
        }
    }

    private static final class FakeIterable implements Iterable<Object> {
        private final List<Object> values;

        private FakeIterable(List<Object> values) {
            this.values = values;
        }

        @Override
        public Iterator<Object> iterator() {
            return values.iterator();
        }
    }

    private static final class ThrowingModelAccess extends FakeModelAccess {
        @Override
        String getPostEntryId(Object post) {
            if (post instanceof FakePost) {
                FakePost fake = (FakePost) post;
                if ("throwing".equals(fake.text)) {
                    throw new IllegalStateException("entry id unavailable");
                }
            }
            return super.getPostEntryId(post);
        }
    }
}
