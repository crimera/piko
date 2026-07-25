/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.postfilter;

import com.x.models.ContextualPost;
import com.x.models.PostResult;
import com.x.models.UserResult;
import com.x.models.articles.Article;
import com.x.models.cards.CardBindingValue;
import com.x.models.cards.LegacyCard;
import com.x.models.notes.NotePost;
import com.x.models.notes.NotePostResult;
import com.x.models.timelines.items.UrtTimelinePost;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.twitter.Pref;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PostFilterMatcher {
    private PostFilterMatcher() {}

    public static boolean isActive() {
        return !PostFilterPreferences.getNormalizedKeywords().isEmpty();
    }

    public static boolean shouldHide(UrtTimelinePost post) {
        return findMatchReason(post) != null;
    }

    public static String findMatchReason(UrtTimelinePost post) {
        return findMatchReason(post, PostFilterPreferences.getNormalizedKeywords());
    }

    static String findMatchReason(UrtTimelinePost post, List<String> keywords) {
        if (post == null || keywords == null || keywords.isEmpty()) return null;

        try {
            if (matches(post.getText(), keywords)) return "KEYWORD_MAIN_TEXT";
            if (matchesAuthor(post.getAuthor(), keywords)) return "KEYWORD_USERNAME";

            PostResult postResult = post.getPostResult();
            if (postResult instanceof ContextualPost) {
                PostResult quotedPost = ((ContextualPost) postResult).getDisplayQuotedPost();
                if (quotedPost != null) {
                    if (matches(quotedPost.getText(), keywords)) return "KEYWORD_QUOTED_TEXT";
                    if (matchesAuthor(quotedPost.getAuthor(), keywords)) return "KEYWORD_QUOTED_USERNAME";
                }
            }

            NotePost notePost = post.getNotePost();
            if (notePost != null) {
                NotePostResult noteResult = notePost.getNoteTweetResult();
                if (noteResult instanceof NotePostResult.NotePost
                        && matches(((NotePostResult.NotePost) noteResult).getText(), keywords)) {
                    return "KEYWORD_NOTE";
                }
            }

            Article article = post.getArticle();
            if (article != null) {
                if (matches(article.getTitle(), keywords)) return "KEYWORD_ARTICLE";
                if (matches(article.getPreviewText(), keywords)) return "KEYWORD_ARTICLE";
            }

            LegacyCard card = post.getLegacyCard();
            if (card != null) {
                Map<String, CardBindingValue> bindings = card.getBindingMap();
                if (matchesCardBinding(bindings, "title", keywords)) return "KEYWORD_CARD";
                if (matchesCardBinding(bindings, "description", keywords)) return "KEYWORD_CARD";
            }
        } catch (RuntimeException exception) {
            logExtractionException(exception);
            return null;
        }

        return null;
    }

    private static void logExtractionException(RuntimeException exception) {
        try {
            if (Pref.serverResponseLogging()) PikoUtils.logger(exception);
        } catch (RuntimeException | LinkageError ignored) {
            // Logging must not turn a fail-open content extraction into a timeline failure.
        }
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static boolean matchesAuthor(UserResult author, List<String> keywords) {
        if (author == null) return false;
        String screenName = author.getScreenName();
        if (matches(screenName, keywords)) return true;
        return screenName != null && matches("@" + screenName, keywords);
    }

    private static boolean matchesCardBinding(
            Map<String, CardBindingValue> bindings,
            String key,
            List<String> keywords) {
        if (bindings == null) return false;
        CardBindingValue value = bindings.get(key);
        if (!(value instanceof CardBindingValue.StringValue)) return false;
        return matches(((CardBindingValue.StringValue) value).getValue(), keywords);
    }

    private static boolean matches(String candidate, List<String> keywords) {
        if (candidate == null || candidate.isEmpty()) return false;
        String normalized = normalize(candidate);
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) return true;
        }
        return false;
    }
}
