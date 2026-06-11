/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.entity

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private const val ENTITY_TWEET_DEFINING_CLASS = "Lapp/morphe/extension/twitter/entity/Tweet"

// --------------- Tweet
internal object TweetObjectFingerprint : Fingerprint(
    filters =
        listOf(
            string("https://x.com/%1\$s/status/%2\$d"),
        ),
)

internal object TweetUsernameFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getTweetUsername",
)

internal object TweetProfileNameFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getTweetProfileName",
)

internal object TweetUserIdFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getTweetUserId",
)

internal object TweetMediaFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getMedias",
)

internal object TweetInfoFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getTweetInfo",
)

internal object TweetLongTextFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getLongText",
)

internal object TweetShortTextFingerprint : Fingerprint(
    definingClass = ENTITY_TWEET_DEFINING_CLASS,
    name = "getShortText",
)

internal object TweetNamesFingerprint : Fingerprint(
    classFingerprint = TweetObjectFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    returnType = "Ljava/lang/String;",
    filters = listOf(
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "this",
            returnType = "Ljava/lang/String;"
        ),
        opcode(
            opcode = Opcode.MOVE_RESULT_OBJECT,
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "this",
            returnType = "Ljava/lang/String;",
            location = MatchAfterImmediately()
        ),
        opcode(
            opcode = Opcode.MOVE_RESULT_OBJECT,
            location = MatchAfterImmediately()
        ),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
            returnType = "Ljava/lang/String;"
        ),
        opcode(
            opcode = Opcode.MOVE_RESULT_OBJECT,
            location = MatchAfterImmediately()
        ),
        opcode(
            opcode = Opcode.RETURN_OBJECT,
            location = MatchAfterImmediately()
        )
    )
)

internal object TweetMediaEntityClassFingerprint : Fingerprint(
    strings = listOf("EntityList{mEntities="),
)

internal object LongTweetObjectFingerprint : Fingerprint(
    strings =
        listOf(
            "NoteTweet(id=",
            ", text=",
        ),
)

internal object QuotedViewSetAccessibilityFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/tweetview/core/QuoteView;",
    name = "setAccessibility",
)

// --------------- Extended Media Entity
// Also required for download patch.
object MediaOptionSheetMediaListVideoDownloaderImplDownloadMethodFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf("url", "video_download"),
    custom = { _, classDef ->
        classDef.startsWith("Lcom/twitter/tweetview/core/ui/mediaoptionssheet/")
    },
)

internal object ExtMediaHighResVideoFingerprint : Fingerprint(
    definingClass = "Lapp/morphe/extension/twitter/entity/ExtMediaEntities",
    name = "getHighResVideo",
)

internal object ExtMediaGetImageFingerprint : Fingerprint(
    definingClass = "Lapp/morphe/extension/twitter/entity/ExtMediaEntities",
    name = "getImageUrl",
)

internal object ExtMediaGetImageMethodFinder : Fingerprint(
    definingClass = "Lcom/twitter/model/json/unifiedcard/JsonAppStoreData;",
    strings =
        listOf(
            "type",
            "id",
        ),
)

// --------------- TweetInfo
internal object TweetInfoObjectFingerprint : Fingerprint(
    strings =
        listOf(
            "flags",
            "lang",
            "supplemental_language",
        ),
    custom = { methodDef, classDef ->
        methodDef.parameters.size == 2 && classDef.contains("/tdbh/")
    },
)

internal object TweetLangFingerprint : Fingerprint(
    definingClass = "Lapp/morphe/extension/twitter/entity/TweetInfo;",
    name = "getLang",
)
