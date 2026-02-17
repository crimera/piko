package app.crimera.patches.twitter.entity

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

private const val ENTITY_TWEET_DEFINING_CLASS = "Lapp/revanced/extension/twitter/entity/Tweet"

// --------------- Tweet
internal object TweetObjectFingerprint : Fingerprint(
    filters = listOf(
        string("https://x.com/%1\$s/status/%2\$d")
    )
)

internal object TweetUsernameFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getTweetUsername"
)

internal object TweetProfileNameFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getTweetProfileName"
)

internal object TweetUserIdFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getTweetUserId"
)

internal object TweetMediaFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getMedias"
)

internal object TweetInfoFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getTweetInfo"
)

internal object TweetLongTextFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getLongText"
)

internal object TweetShortTextFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getShortText"
)

internal object GetUserNameMethodCaller : Fingerprint(
    returnType = "V",
    strings = listOf(
        "Ref_ID (Tweet ID)",
        "Name",
        "User Name",
    )
)

internal object TweetMediaEntityClassFingerprint : Fingerprint(
    strings = listOf("EntityList{mEntities=")
)

internal object LongTweetObjectFingerprint : Fingerprint(
    strings = listOf(
        "NoteTweet(id=",
        ", text="
    )
)

internal object QuotedViewSetAccessibilityFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/tweetview/core/QuoteView;",
    name = "setAccessibility"
)

// --------------- Extended Media Entity
internal object ExtMediaHighResVideoMethodFinder : Fingerprint(
    strings = listOf(
        "long_press_menu",
        "null cannot be cast to non-null type com.twitter.model.dm.attachment.DMMediaAttachment",
    )
)

internal object ExtMediaHighResVideoFingerprint : Fingerprint(
    definingClass = "Lapp/revanced/extension/twitter/entity/ExtMediaEntities",
    name = "getHighResVideo"
)

internal object ExtMediaGetImageFingerprint : Fingerprint(
    definingClass = "Lapp/revanced/extension/twitter/entity/ExtMediaEntities",
    name = "getImageUrl"
)

internal object ExtMediaGetImageMethodFinder : Fingerprint(
    definingClass = "Lcom/twitter/model/json/unifiedcard/JsonAppStoreData;",
    strings = listOf(
        "type",
        "id"
    )
)

// --------------- TweetInfo
internal object TweetInfoObjectFingerprint : Fingerprint(
    strings = listOf(
        "flags",
        "lang",
        "supplemental_language",
    ),
    custom = { methodDef, classDef ->
        methodDef.parameters.size == 2 && classDef.contains("/tdbh/")
    }
)

internal object TweetLangFingerprint : Fingerprint(
    definingClass = "Lapp/revanced/extension/twitter/entity/TweetInfo;",
    name = "getLang"
)
