package app.crimera.patches.twitter.models

import app.revanced.patcher.fingerprint

internal fun entityMethodFingerprint(
    className: String,
    methodName: String,
) = fingerprint {
    custom { method, classDef ->
        method.name == methodName &&
            classDef.type == "Lapp/revanced/integrations/twitter/model/$className;"
    }
}

// --------------- Tweet
internal val tweetObjectFingerprint = fingerprint { strings("https://x.com/%1\$s/status/%2\$d") }

internal val tweetUsernameFingerprint = entityMethodFingerprint("Tweet", "getTweetUsername")

internal val tweetProfileNameFingerprint = entityMethodFingerprint("Tweet", "getTweetProfileName")

internal val tweetUserIdFingerprint = entityMethodFingerprint("Tweet", "getTweetUserId")

internal val tweetMediaFingerprint = entityMethodFingerprint("Tweet", "getMedias")

internal val tweetInfoFingerprint = entityMethodFingerprint("Tweet", "getTweetInfo")

internal val tweetLongTextFingerprint = entityMethodFingerprint("Tweet", "getLongText")

internal val tweetShortTextFingerprint = entityMethodFingerprint("Tweet", "getShortText")

internal val getUserNameMethodCaller =
    fingerprint {
        returns("V")
        strings(
            "Ref_ID (Tweet ID)",
            "Name",
            "User Name",
        )
    }

internal val tweetMediaEntityClassFingerprint = fingerprint { strings("EntityList{mEntities=") }

internal val longTweetObjectFingerprint = fingerprint { strings("NoteTweet(id=", ", text=") }

// --------------- Extended Media Entity
internal val extMediaHighResVideoMethodFinder =
    fingerprint {
        strings(
            "long_press_menu",
            "null cannot be cast to non-null type com.twitter.model.dm.attachment.DMMediaAttachment",
        )
    }

internal val extMediaHighResVideoFingerprint = entityMethodFingerprint("ExtMediaHighResVideoFingerprint", "getHighResVideo")

internal val extMediaGetImageMethodFinder =
    fingerprint {
        strings("type", "id")
        custom { _, classDef ->
            classDef.toString().contains("Lcom/twitter/model/json/unifiedcard/JsonAppStoreData;")
        }
    }

internal val extMediaGetImageFingerprint = entityMethodFingerprint("ExtMediaEntities", "getImageUrl")

// --------------- TweetInfo
internal val tweetInfoObjectFingerprint =
    fingerprint {
        strings(
            "flags",
            "lang",
            "supplemental_language",
        )
        custom { methodDef, classDef ->
            methodDef.parameters.size == 2 && classDef.contains("/tdbh/")
        }
    }

internal val tweetLangFingerprint = entityMethodFingerprint("TweetInfo", "getLang")
