package crimera.patches.twitter.recommendedusers.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.Opcode

object HideRecommendedUsersFingerprint: MethodFingerprint(
    opcodes = listOf(
        Opcode.IGET_OBJECT,
    ),

    customFingerprint = { it, _ ->
        it.definingClass == "Lcom/twitter/model/json/people/JsonProfileRecommendationModuleResponse;"
    }
)