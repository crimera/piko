package crimera.patches.twitter.live.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.Opcode

object HideLiveThreadsFingerprint : MethodFingerprint(
    opcodes = listOf(
        Opcode.IGET_OBJECT,
    ),
     customFingerprint = {it,_ ->
         it.definingClass == "Lcom/twitter/fleets/api/json/JsonFleetsTimelineResponse;"
     }
)