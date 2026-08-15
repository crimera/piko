package app.morphe.extension.xlite.misc;

import app.morphe.extension.xlite.utils.ToStringParser;

/** Resolves legacy card short URLs through the post's URL entities. */
public final class CanonicalUrlResolver {
    private static final String URL_ENTITY_PREFIX = "UrlEntity(";

    private CanonicalUrlResolver() {
    }

    public static String resolve(Object post, String cardUrl) {
        if (post == null || cardUrl == null) return cardUrl;

        String canonicalPost = ToStringParser.fieldValue(post.toString(), "canonicalPost");
        if (canonicalPost == null) return cardUrl;

        String entityList = ToStringParser.fieldValue(canonicalPost, "entityList");
        if (entityList == null) return cardUrl;

        String urls = ToStringParser.fieldValue(entityList, "urls");
        if (urls == null) return cardUrl;

        for (int start = urls.indexOf(URL_ENTITY_PREFIX);
             start >= 0;
             start = urls.indexOf(URL_ENTITY_PREFIX, start + URL_ENTITY_PREFIX.length())) {
            String entity = urls.substring(start);
            String shortUrl = ToStringParser.fieldValue(entity, "url");
            if (!cardUrl.equals(shortUrl)) continue;

            String expandedUrl = ToStringParser.fieldValue(entity, "expandedUrl");
            return expandedUrl == null ? cardUrl : expandedUrl;
        }
        return cardUrl;
    }
}
