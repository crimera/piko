package crimera.patches.twitter.interaction.downloads.unlockdownloads.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.Opcode


object MediaEntityFingerprint:MethodFingerprint(
    opcodes = listOf(
        Opcode.IGET_BOOLEAN,
    ),

    customFingerprint = { it, _ ->
        it.definingClass == "Lcom/twitter/model/json/core/JsonMediaEntity;"
    }

)