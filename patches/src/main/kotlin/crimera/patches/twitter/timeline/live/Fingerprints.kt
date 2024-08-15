package crimera.patches.twitter.timeline.live

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.Opcode

val hideLiveThreadsFingerprint = fingerprint {
    opcodes(Opcode.IGET_OBJECT)
    custom { it, _ ->
        it.definingClass == "Lcom/twitter/fleets/api/json/JsonFleetsTimelineResponse;"
    }
}
