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
    public void contentRuleDoesNotMatchUsername() {
        assertNull(reason(post("safe", user("blocked_user")), contentRule("blocked")));
        assertEquals(
                "KEYWORD_MAIN_TEXT",
                reason(post("contains blocked phrase", user("safe")), contentRule("blocked"))
        );
    }

    @Test
    public void usernameRuleMatchesMainAndQuotedAuthorsOnly() {
        assertEquals(
                "KEYWORD_USERNAME",
                reason(post("safe", user("ExampleUser")), usernameRule("@exampleuser"))
        );
        assertNull(reason(post("text says exampleuser", user("safe")), usernameRule("exampleuser")));

        FakePost quoted = post("safe", user("safe_author"));
        quoted.postResult = contextual(
                postResult("safe", user("safe_author")),
                postResult("safe", user("quoted_author"))
        );
        assertEquals(
                "KEYWORD_QUOTED_USERNAME",
                reason(quoted, usernameRule("quoted_author"))
        );
    }

    @Test
    public void contentRuleMatchesQuotedNoteArticleAndCardContent() {
        FakePost quoted = post("safe", null);
        quoted.postResult = contextual(postResult("safe", null), postResult("quoted phrase", null));
        assertEquals("KEYWORD_QUOTED_TEXT", reason(quoted, contentRule("quoted phrase")));

        FakePost notePost = post("safe", null);
        notePost.notePost = note("long note phrase");
        assertEquals("KEYWORD_NOTE", reason(notePost, contentRule("note phrase")));

        FakePost articlePost = post("safe", null);
        articlePost.article = article("article title", "preview body");
        assertEquals("KEYWORD_ARTICLE", reason(articlePost, contentRule("preview body")));

        FakePost cardPost = post("safe", null);
        cardPost.card = card("card title", "card description");
        assertEquals("KEYWORD_CARD", reason(cardPost, contentRule("description")));
    }

    @Test
    public void disabledRuleDoesNotMatch() {
        PostFilterRule disabled = new PostFilterRule("1", "blocked", true, true, false);
        assertNull(reason(post("blocked", user("blocked")), disabled));
    }

    @Test
    public void ignoresUnrelatedMetadata() {
        FakePost safe = post("safe", user("safe_author"));
        safe.entryId = "metadata-secret";
        assertNull(reason(safe, contentRule("secret")));
    }

    @Test
    public void timelineFilterReturnsOriginalListWhenDisabledOrUnchanged() {
        List<UrtTimelinePost> posts = List.of(post("safe", null));
        PostFilterRuleStore.Snapshot snapshot = snapshot(contentRule("safe"));

        assertSame(posts, XLiteTimelineFilter.filterPostsByKeyword(posts, false, snapshot));
        assertSame(
                posts,
                XLiteTimelineFilter.filterPostsByKeyword(
                        posts,
                        true,
                        snapshot(contentRule("blocked"))
                )
        );
    }

    @Test
    public void timelineFilterRemovesOnlyScopedMatches() {
        List<UrtTimelinePost> posts = List.of(
                post("safe", user("blocked")),
                post("contains blocked phrase", user("safe"))
        );

        Object contentResult = XLiteTimelineFilter.filterPostsByKeyword(
                posts,
                true,
                snapshot(contentRule("blocked"))
        );
        assertEquals(List.of(posts.get(0)), contentResult);

        Object usernameResult = XLiteTimelineFilter.filterPostsByKeyword(
                posts,
                true,
                snapshot(usernameRule("blocked"))
        );
        assertEquals(List.of(posts.get(1)), usernameResult);
    }

    private static String reason(UrtTimelinePost post, PostFilterRule rule) {
        return PostFilterMatcher.findMatchReason(post, snapshot(rule));
    }

    private static PostFilterRuleStore.Snapshot snapshot(PostFilterRule rule) {
        return PostFilterRuleStore.snapshotOf(List.of(rule));
    }

    private static PostFilterRule contentRule(String phrase) {
        return new PostFilterRule("content-" + phrase, phrase, true, false, true);
    }

    private static PostFilterRule usernameRule(String phrase) {
        return new PostFilterRule("username-" + phrase, phrase, false, true, true);
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
