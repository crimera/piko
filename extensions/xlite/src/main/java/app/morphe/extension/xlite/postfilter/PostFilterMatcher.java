package app.morphe.extension.xlite.postfilter;

import com.x.models.ContextualPost;
import com.x.models.PostResult;
import com.x.models.UserResult;
import com.x.models.articles.Article;
import com.x.models.cards.CardBindingValue;
import com.x.models.cards.LegacyCard;
import com.x.models.notes.NotePost;
import com.x.models.notes.NotePostResult;
import com.x.models.timelines.items.UrtTimelinePost;

import java.util.List;
import java.util.Map;

import app.morphe.extension.shared.Logger;

public final class PostFilterMatcher {
    private PostFilterMatcher() {
    }

    public static String findMatchReason(
            UrtTimelinePost post,
            PostFilterRuleStore.Snapshot snapshot
    ) {
        if (post == null || snapshot == null || !snapshot.hasEnabledRules()) return null;

        List<String> contentPhrases = snapshot.contentPhrases();
        List<String> usernamePhrases = snapshot.usernamePhrases();
        try {
            if (matches(post.getText(), contentPhrases)) return "KEYWORD_MAIN_TEXT";
            if (matchesAuthor(post.getAuthor(), usernamePhrases)) return "KEYWORD_USERNAME";

            PostResult postResult = post.getPostResult();
            if (postResult instanceof ContextualPost contextualPost) {
                PostResult quotedPost = contextualPost.getDisplayQuotedPost();
                if (quotedPost != null) {
                    if (matches(quotedPost.getText(), contentPhrases)) {
                        return "KEYWORD_QUOTED_TEXT";
                    }
                    if (matchesAuthor(quotedPost.getAuthor(), usernamePhrases)) {
                        return "KEYWORD_QUOTED_USERNAME";
                    }
                }
            }

            NotePost notePost = post.getNotePost();
            if (notePost != null) {
                NotePostResult noteResult = notePost.getNoteTweetResult();
                if (noteResult instanceof NotePostResult.NotePost content
                        && matches(content.getText(), contentPhrases)) {
                    return "KEYWORD_NOTE";
                }
            }

            Article article = post.getArticle();
            if (article != null) {
                if (matches(article.getTitle(), contentPhrases)) return "KEYWORD_ARTICLE";
                if (matches(article.getPreviewText(), contentPhrases)) return "KEYWORD_ARTICLE";
            }

            LegacyCard card = post.getLegacyCard();
            if (card != null) {
                Map<String, CardBindingValue> bindings = card.getBindingMap();
                if (matchesCardBinding(bindings, "title", contentPhrases)) return "KEYWORD_CARD";
                if (matchesCardBinding(bindings, "description", contentPhrases)) {
                    return "KEYWORD_CARD";
                }
            }
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Failed to inspect an X-Lite post for filtering", exception);
        }

        return null;
    }

    public static String normalize(String value) {
        return PostFilterRule.normalize(value);
    }

    private static boolean matchesAuthor(UserResult author, List<String> phrases) {
        if (author == null || phrases.isEmpty()) return false;
        String screenName = author.getScreenName();
        if (matches(screenName, phrases)) return true;
        return screenName != null && matches("@" + screenName, phrases);
    }

    private static boolean matchesCardBinding(
            Map<String, CardBindingValue> bindings,
            String key,
            List<String> phrases
    ) {
        if (bindings == null) return false;
        CardBindingValue value = bindings.get(key);
        if (!(value instanceof CardBindingValue.StringValue stringValue)) return false;
        return matches(stringValue.getValue(), phrases);
    }

    private static boolean matches(String candidate, List<String> phrases) {
        if (candidate == null || candidate.isEmpty() || phrases.isEmpty()) return false;
        String normalized = normalize(candidate);
        for (String phrase : phrases) {
            if (normalized.contains(phrase)) return true;
        }
        return false;
    }
}
