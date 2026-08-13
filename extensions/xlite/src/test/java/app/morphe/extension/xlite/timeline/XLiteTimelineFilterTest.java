package app.morphe.extension.xlite.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.xlite.postfilter.PostFilterRule;
import app.morphe.extension.xlite.postfilter.PostFilterRuleStore;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class XLiteTimelineFilterTest {
    private static final FakeModelAccess MODELS = new FakeModelAccess();

    @Test
    public void disabledFilterReturnsOriginalList() {
        List<Object> items = items(module("who-to-follow-1", item(post("safe"))));
        assertSame(items, XLiteTimelineFilter.filterWhoToFollow(items, false, MODELS));
    }

    @Test
    public void unchangedFilterReturnsOriginalList() {
        List<Object> items = items(post("safe"));
        assertSame(items, XLiteTimelineFilter.filterPromotedItems(items, true, MODELS));
    }

    @Test
    public void removesWhoToFollowModuleByEntryId() {
        List<Object> filtered = filterWhoToFollow(module("who-to-follow-1", item(post("safe"))));
        assertTrue(filtered.isEmpty());
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

        assertSame(input, XLiteTimelineFilter.filterPromotedItems(input, true, MODELS));
    }

    @Test
    public void removesPromotedEntryIdPatterns() {
        FakePost promoted = post("buy now");
        promoted.entryId = "promoted-tweet-123";
        FakePost ad = post("sponsored");
        ad.entryId = "timeline-ad-456";

        Object filtered = XLiteTimelineFilter.filterPromotedItems(
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

        Object filtered = XLiteTimelineFilter.filterPromotedItems(input, true, MODELS);
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
        List<Object> filtered = (List<Object>) XLiteTimelineFilter.filterPromotedItems(
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

        Object filtered = XLiteTimelineFilter.filterPromotedItems(
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

        Object filtered = XLiteTimelineFilter.filterPostsByKeyword(input, true, snapshot, MODELS);
        assertEquals(List.of(input.get(0)), filtered);
    }

    @Test
    public void removesSelectedAiDisclosureSource() {
        FakePost userMarked = post("generated");
        userMarked.disclosure = new FakeDisclosure(true, AiSource.UserMarked);
        FakePost autoDetected = post("detected");
        autoDetected.disclosure = new FakeDisclosure(true, AiSource.AutoDetected);

        Object filtered = XLiteTimelineFilter.filterAiGeneratedPosts(
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

        Object filtered = XLiteTimelineFilter.filterAiGeneratedPosts(
                items(unclassified),
                Set.of("SourceNotIdentified"),
                MODELS
        );
        assertEquals(List.of(), filtered);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> filterWhoToFollow(Object... values) {
        return (List<Object>) XLiteTimelineFilter.filterWhoToFollow(items(values), true, MODELS);
    }

    private static List<Object> items(Object... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static FakePost post(String text) {
        return new FakePost(text);
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

        private FakePost(String text) {
            this.text = text;
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

    private static final class FakeModelAccess extends TimelineModelAccess {
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
        @Override Object getContentDisclosure(Object post) { return ((FakePost) post).disclosure; }
        @Override boolean hasAiGeneratedDisclosure(Object disclosure) {
            return ((FakeDisclosure) disclosure).aiGenerated;
        }
        @Override Object getAiDetectionSource(Object disclosure) {
            return ((FakeDisclosure) disclosure).source;
        }
    }
}
