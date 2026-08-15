package app.morphe.extension.xlite.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class ToStringParserTest {

    // Realistic subset of the obfuscated app's post toString, including a
    // repost whose canonical mirrors the original media with a sourceInfo.
    private static final String REPOST_POST =
            "ContextualPost(canonicalPost=CanonicalPost(id=2088336364039184458, " +
                    "text=RT @chachironi3: Bedtime https://t.co/eNhSVoMg8b, " +
                    "media=[MediaContentImage(mediaId=2088181248732905472, " +
                    "imageUrl=https://pbs.twimg.com/media/HPq1yDVbQAArk67.jpg, " +
                    "sourceInfo=SourceInfo(sourcePostIdentifier=2088279482146898407, " +
                    "sourceUserIdentifier=1355825594030727172, " +
                    "sourceUserDisplayName=chachironi, " +
                    "sourceUserAvatarUrl=https://pbs.twimg.com/profile_images/a.jpg, " +
                    "sourceUserVerifiedType=NotVerified), isDownloadable=true)], " +
                    "entityList=PostEntityList(mentions=[MentionEntity(userId=1355825594030727172, " +
                    "startIdx=3, endIdx=15, screenName=chachironi3)], urls=[], " +
                    "media=[MediaEntity(id=2088181248732905472)], hashtags=[HashtagEntity(text=drawkun)]), " +
                    "author=MinimalUser(id=1, screenName=pokorakun, name=pokorakun), " +
                    "legacyCard=null, rePostedPost=null)";

    private static final String STRUCTURED_REPOST =
            "ContextualPost(canonicalPost=CanonicalPost(id=2088334976651792559, " +
                    "author=MinimalUser(id=9, screenName=retweeter, name=Retweeter), " +
                    "media=[MediaContentImage(mediaId=1, sourceInfo=null)]), " +
                    "quotedPost=null, rePostedPost=RePostedPost(" +
                    "canonicalPost=CanonicalPost(id=2088221458740969716, " +
                    "author=MinimalUser(id=1423483994084048906, screenName=hige_hurai, " +
                    "name=Hige Hurai), media=[]), quotedPost=null), " +
                    "tweetInterstitial=null)";

    @Test
    public void extractsPrimitiveField() {
        assertEquals("2088336364039184458", ToStringParser.fieldValue(REPOST_POST, "id"));
    }

    @Test
    public void extractsNestedObjectField() {
        String sourceInfo = ToStringParser.fieldValue(REPOST_POST, "sourceInfo");
        assertEquals("2088279482146898407", ToStringParser.fieldValue(sourceInfo, "sourcePostIdentifier"));
        assertEquals("chachironi", ToStringParser.fieldValue(sourceInfo, "sourceUserDisplayName"));
    }

    @Test
    public void extractsFieldFromObjectInsideList() {
        String media = ToStringParser.fieldValue(REPOST_POST, "media");
        assertEquals(2088181248732905472L, Long.parseLong(
                ToStringParser.fieldValue(media, "mediaId").replaceAll("[^0-9]", "")
        ));
        assertEquals(
                "https://pbs.twimg.com/media/HPq1yDVbQAArk67.jpg",
                ToStringParser.fieldValue(media, "imageUrl")
        );
    }

    @Test
    public void extractsFieldFromNestedAuthor() {
        String author = ToStringParser.fieldValue(REPOST_POST, "author");
        assertEquals("pokorakun", ToStringParser.fieldValue(author, "screenName"));
    }

    @Test
    public void objectValueSurvivesInnerCommas() {
        String sourceInfo = ToStringParser.fieldValue(REPOST_POST, "sourceInfo");
        assertEquals("chachironi", ToStringParser.fieldValue(sourceInfo, "sourceUserDisplayName"));
        assertEquals(
                "https://pbs.twimg.com/profile_images/a.jpg",
                ToStringParser.fieldValue(sourceInfo, "sourceUserAvatarUrl")
        );
        assertNull(ToStringParser.fieldValue(REPOST_POST, "legacyCard"));
    }

    @Test
    public void extractsFirstMentionScreenNameFromEntityList() {
        String entityList = ToStringParser.fieldValue(REPOST_POST, "entityList");
        String mentions = ToStringParser.fieldValue(entityList, "mentions");
        assertEquals("chachironi3", ToStringParser.fieldValue(mentions, "screenName"));
    }

    @Test
    public void extractsOriginalAuthorFromStructuredRepost() {
        String repostedPost = ToStringParser.fieldValue(STRUCTURED_REPOST, "rePostedPost");
        String originalPost = ToStringParser.fieldValue(repostedPost, "canonicalPost");
        String author = ToStringParser.fieldValue(originalPost, "author");
        assertEquals("2088221458740969716", ToStringParser.fieldValue(originalPost, "id"));
        assertEquals("hige_hurai", ToStringParser.fieldValue(author, "screenName"));
    }

    @Test
    public void missingOrNullFieldsYieldsNull() {
        assertNull(ToStringParser.fieldValue(REPOST_POST, "missingField"));
        assertNull(ToStringParser.fieldValue(REPOST_POST, "legacyCard"));
        assertNull(ToStringParser.fieldValue(null, "id"));
        assertNull(ToStringParser.fieldValue(REPOST_POST, null));
    }

    @Test
    public void listAndObjectValuesArriveWhole() {
        String media = ToStringParser.fieldValue(REPOST_POST, "media");
        assertEquals("[MediaContentImage(", media.substring(0, "[MediaContentImage(".length()));
        assertEquals(']', media.charAt(media.length() - 1));
        String sourceInfo = ToStringParser.fieldValue(REPOST_POST, "sourceInfo");
        assertEquals("SourceInfo(", sourceInfo.substring(0, "SourceInfo(".length()));
        assertEquals(
                "SourceInfo(sourcePostIdentifier=2088279482146898407, " +
                        "sourceUserIdentifier=1355825594030727172, " +
                        "sourceUserDisplayName=chachironi, " +
                        "sourceUserAvatarUrl=https://pbs.twimg.com/profile_images/a.jpg, " +
                        "sourceUserVerifiedType=NotVerified)",
                sourceInfo
        );
    }
}
