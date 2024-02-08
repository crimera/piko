package crimera.patches.twitter.sensitivemediasettings.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.Opcode

object SensitiveMediaSettingsPatchFingerprint: MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "adult_content",
        "graphic_violence",
        "other"
    ),
    opcodes = listOf(
        Opcode.IPUT_BOOLEAN,
    ),
    customFingerprint = { it, _ ->
        it.definingClass == "Lcom/twitter/model/json/core/JsonSensitiveMediaWarning\$\$JsonObjectMapper;"
    }
)