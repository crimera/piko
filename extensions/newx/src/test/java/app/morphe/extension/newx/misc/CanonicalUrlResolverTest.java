package app.morphe.extension.newx.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class CanonicalUrlResolverTest {
    private static final String POST =
            "ContextualPost(canonicalPost=CanonicalPost(id=2088451715343548553, " +
                    "text=https://t.co/8Llkkhl9JJ, " +
                    "entityList=PostEntityList(mentions=[], " +
                    "urls=[UrlEntity(displayUrl=pixiv.net/artworks/62233260, " +
                    "expandedUrl=https://www.pixiv.net/artworks/62233260, " +
                    "url=https://t.co/8Llkkhl9JJ)], media=[])))";

    @Test
    public void resolvesCardShortUrlFromPostEntity() {
        assertEquals(
                "https://www.pixiv.net/artworks/62233260",
                CanonicalUrlResolver.resolve(POST, "https://t.co/8Llkkhl9JJ")
        );
    }

    @Test
    public void preservesUnrelatedCardUrl() {
        assertEquals(
                "https://example.com/card",
                CanonicalUrlResolver.resolve(POST, "https://example.com/card")
        );
    }

    @Test
    public void nullInputIsPreserved() {
        assertNull(CanonicalUrlResolver.resolve(null, null));
    }
}
