package app.morphe.extension.xlite.postfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import app.morphe.extension.xlite.timeline.XLiteTimelineFilter;

import com.x.models.ContextualPost;
import com.x.models.PostIdentifier;
import com.x.models.PostResult;
import com.x.models.TimelinePromotedMetadata;
import com.x.models.UserResult;
import com.x.models.articles.Article;
import com.x.models.cards.CardBindingValue;
import com.x.models.cards.LegacyCard;
import com.x.models.notes.NotePost;
import com.x.models.notes.NotePostResult;
import com.x.models.timelines.items.UrtTimelinePost;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PostFilterMatcherTest {
    @Test
    public void normalizeUsesNfkcAndLocaleRoot() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertEquals("fullwidth", PostFilterMatcher.normalize("ＦＵＬＬＷＩＤＴＨ"));
            assertEquals("i", PostFilterMatcher.normalize("I"));
            assertEquals(
                    PostFilterMatcher.normalize("é"),
                    PostFilterMatcher.normalize("e\u0301")
            );
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void parsesOneKeywordPerLineAndRemovesNormalizedDuplicates() {
        assertEquals(
                List.of("first", "second"),
                PostFilterMatcher.normalizedKeywords(" First \nsecond\nＦＩＲＳＴ\n\n")
        );
    }

    @Test
    public void matchesLiteralTextAndUsernameForms() {
        assertEquals("KEYWORD_MAIN_TEXT", reason(post("value [.*?] here", null), "[.*?]"));
        assertEquals("KEYWORD_MAIN_TEXT", reason(post("これは日本語です", null), "日本語"));
        assertEquals("KEYWORD_USERNAME", reason(post("safe", user("ExampleUser")), "@exampleuser"));
    }

    @Test
    public void matchesQuotedPostContent() {
        FakePost post = post("safe", user("safe_author"));
        post.postResult = contextual(
                postResult("safe", user("safe_author")),
                postResult("quoted phrase", user("quoted_author"))
        );

        assertEquals("KEYWORD_QUOTED_TEXT", reason(post, "quoted phrase"));
        assertEquals("KEYWORD_QUOTED_USERNAME", reason(post, "@quoted_author"));
    }

    @Test
    public void matchesLongNoteArticleAndCard() {
        FakePost notePost = post("safe", null);
        notePost.notePost = note("long note phrase");
        assertEquals("KEYWORD_NOTE", reason(notePost, "note phrase"));

        FakePost articlePost = post("safe", null);
        articlePost.article = article("article title", "preview body");
        assertEquals("KEYWORD_ARTICLE", reason(articlePost, "preview body"));

        FakePost cardPost = post("safe", null);
        cardPost.card = card("card title", "card description");
        assertEquals("KEYWORD_CARD", reason(cardPost, "description"));
    }

    @Test
    public void ignoresUnrelatedMetadata() {
        FakePost safe = post("safe", user("safe_author"));
        safe.entryId = "metadata-secret";
        assertNull(reason(safe, "secret"));
    }

    @Test
    public void timelineFilterReturnsOriginalListWhenDisabledOrUnchanged() {
        List<UrtTimelinePost> posts = List.of(post("safe", null));

        assertSame(posts, XLiteTimelineFilter.filterPostsByKeyword(posts, false, "safe"));
        assertSame(posts, XLiteTimelineFilter.filterPostsByKeyword(posts, true, "blocked"));
    }

    @Test
    public void timelineFilterRemovesMatchingPosts() {
        List<UrtTimelinePost> posts = List.of(
                post("safe", null),
                post("contains blocked phrase", null)
        );

        Object result = XLiteTimelineFilter.filterPostsByKeyword(posts, true, "blocked phrase");

        assertEquals(List.of(posts.get(0)), result);
    }

    private static String reason(UrtTimelinePost post, String keyword) {
        return PostFilterMatcher.findMatchReason(
                post,
                Collections.singletonList(PostFilterMatcher.normalize(keyword))
        );
    }

    private static FakePost post(String text, UserResult author) {
        FakePost post = new FakePost();
        post.text = text;
        post.author = author;
        post.postResult = postResult(text, author);
        return post;
    }

    private static PostResult postResult(String text, UserResult author) {
        return new FakePostResult(text, author);
    }

    private static ContextualPost contextual(PostResult effective, PostResult quote) {
        return new ContextualPost() {
            @Override public PostResult getDisplayQuotedPost() { return quote; }
            @Override public String getText() { return effective.getText(); }
            @Override public UserResult getAuthor() { return effective.getAuthor(); }
            @Override public NotePost getNotePost() { return null; }
            @Override public Article getArticle() { return null; }
            @Override public LegacyCard getLegacyCard() { return null; }
            @Override public PostIdentifier getId() { return null; }
        };
    }

    private static UserResult user(String screenName) {
        return () -> screenName;
    }

    private static NotePost note(String text) {
        NotePostResult.NotePost result = new NotePostResult.NotePost() {
            @Override public String getText() { return text; }
        };
        return new NotePost() {
            @Override public NotePostResult getNoteTweetResult() { return result; }
        };
    }

    private static Article article(String title, String preview) {
        return new Article() {
            @Override public String getTitle() { return title; }
            @Override public String getPreviewText() { return preview; }
        };
    }

    private static LegacyCard card(String title, String description) {
        CardBindingValue.StringValue titleValue = stringValue(title);
        CardBindingValue.StringValue descriptionValue = stringValue(description);
        return new LegacyCard() {
            @Override
            public Map<String, CardBindingValue> getBindingMap() {
                return Map.of("title", titleValue, "description", descriptionValue);
            }
        };
    }

    private static CardBindingValue.StringValue stringValue(String value) {
        return new CardBindingValue.StringValue() {
            @Override public String getValue() { return value; }
        };
    }

    private static class FakePost extends UrtTimelinePost {
        String text;
        UserResult author;
        PostResult postResult;
        NotePost notePost;
        Article article;
        LegacyCard card;
        String entryId = "post-1";

        @Override public String getEntryId() { return entryId; }
        @Override public TimelinePromotedMetadata getPromotedMetadata() { return null; }
        @Override public PostResult getPostResult() { return postResult; }
        @Override public String getText() { return text; }
        @Override public UserResult getAuthor() { return author; }
        @Override public PostIdentifier getId() { return null; }
        @Override public NotePost getNotePost() { return notePost; }
        @Override public Article getArticle() { return article; }
        @Override public LegacyCard getLegacyCard() { return card; }
    }

    private static final class FakePostResult implements PostResult {
        private final String text;
        private final UserResult author;

        FakePostResult(String text, UserResult author) {
            this.text = text;
            this.author = author;
        }

        @Override public String getText() { return text; }
        @Override public UserResult getAuthor() { return author; }
        @Override public NotePost getNotePost() { return null; }
        @Override public Article getArticle() { return null; }
        @Override public LegacyCard getLegacyCard() { return null; }
        @Override public PostIdentifier getId() { return null; }
    }
}
