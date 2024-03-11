package crimera.patches.twitter.misc.hidecommunitynotes.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

object HideCommunityNoteFingerprint : MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "article",
        "ext_birdwatch_pivot",
        "birdwatch_pivot",
    ),
    customFingerprint = {it,_ ->
        it.definingClass =="Lcom/twitter/api/model/json/core/JsonApiTweet\$\$JsonObjectMapper;"
    }

)