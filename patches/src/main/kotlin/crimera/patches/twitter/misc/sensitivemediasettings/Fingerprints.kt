package crimera.patches.twitter.misc.sensitivemediasettings

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.Opcode

internal val sensitiveMediaSettingsPatchFingerprint = fingerprint {
    returns("V")
    strings(
        "adult_content",
        "graphic_violence",
        "other"
    )
    opcodes(Opcode.IPUT_BOOLEAN)
    custom { method, _ ->
        method.definingClass == "Lcom/twitter/model/json/core/JsonSensitiveMediaWarning\$\$JsonObjectMapper;"
    }
}
