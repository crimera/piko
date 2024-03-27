package crimera.patches.twitter.timeline.foryou.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.Opcode

object HideForYouFingerprint : MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "selectedTabStateRepo",
        "pinnedTimelinesRepo",
        "releaseCompletable",
        ),
    opcodes = listOf(
        Opcode.CONST_16,
    ),
)