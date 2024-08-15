package crimera.patches.twitter.premium.undoposts

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.Opcode

internal val undoPost1Fingerprint = fingerprint {
    returns("Z")
    strings(
        "subscriptions_feature_1003",
        "allow_undo_replies",
        "allow_undo_tweet"
    )
    opcodes(
        Opcode.MOVE_RESULT_OBJECT
    )
}

internal val undoPost2Fingerprint = fingerprint {
    returns("Z")
    strings(
        "userPreferences",
        "draftTweet",
        "subscriptions_feature_1003",
        "allow_undo_replies",
        "allow_undo_tweet"
    )
}

internal val undoPost3Fingerprint = fingerprint {
    returns("Ljava/lang/Object;")
    strings("subscriptions_feature_1003")
}

