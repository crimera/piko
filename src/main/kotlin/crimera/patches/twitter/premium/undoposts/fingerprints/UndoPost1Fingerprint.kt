package crimera.patches.twitter.premium.undoposts.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.Opcode

object UndoPost1Fingerprint :MethodFingerprint(
    returnType = "Z",
    strings = listOf(
        "subscriptions_feature_1003",
        "allow_undo_replies",
        "allow_undo_tweet"
    ),
    opcodes = listOf(
        Opcode.MOVE_RESULT_OBJECT
    )


)