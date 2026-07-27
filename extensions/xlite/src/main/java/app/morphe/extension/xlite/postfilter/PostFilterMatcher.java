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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.morphe.extension.shared.Logger;

public final class PostFilterMatcher {
    private static volatile String cachedSource = "";
    private static volatile List<String> cachedNormalized = Collections.emptyList();

    private PostFilterMatcher() {
    }

    public static List<String> normalizedKeywords(String keywordLines) {
        String source = keywordLines == null ? "" : keywordLines;
        if (source.equals(cachedSource)) return cachedNormalized;
        return refreshKeywords(source);
    }

    public static String findMatchReason(UrtTimelinePost post, List<String> keywords) {
        if (post == null || keywords == null || keywords.isEmpty()) return null;

        try {
            if (matches(post.getText(), keywords)) return "KEYWORD_MAIN_TEXT";
            if (matchesAuthor(post.getAuthor(), keywords)) return "KEYWORD_USERNAME";

            PostResult postResult = post.getPostResult();
            if (postResult instanceof ContextualPost contextualPost) {
                PostResult quotedPost = contextualPost.getDisplayQuotedPost();
                if (quotedPost != null) {
                    if (matches(quotedPost.getText(), keywords)) return "KEYWORD_QUOTED_TEXT";
                    if (matchesAuthor(quotedPost.getAuthor(), keywords)) {
                        return "KEYWORD_QUOTED_USERNAME";
                    }
                }
            }

            NotePost notePost = post.getNotePost();
            if (notePost != null) {
                NotePostResult noteResult = notePost.getNoteTweetResult();
                if (noteResult instanceof NotePostResult.NotePost content
                        && matches(content.getText(), keywords)) {
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
            Logger.printException(() -> "Failed to inspect an X-Lite post for filtering", exception);
        }

        return null;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static synchronized List<String> refreshKeywords(String source) {
        if (source.equals(cachedSource)) return cachedNormalized;

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String keyword : source.split("\\r\\n|\\n|\\r")) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty()) normalized.add(normalize(trimmed));
        }

        cachedSource = source;
        cachedNormalized = Collections.unmodifiableList(new ArrayList<>(normalized));
        return cachedNormalized;
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
            List<String> keywords
    ) {
        if (bindings == null) return false;
        CardBindingValue value = bindings.get(key);
        if (!(value instanceof CardBindingValue.StringValue stringValue)) return false;
        return matches(stringValue.getValue(), keywords);
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
