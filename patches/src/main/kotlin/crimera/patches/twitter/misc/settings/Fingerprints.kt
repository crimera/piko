package crimera.patches.twitter.misc.settings

import app.revanced.patcher.Fingerprint
import app.revanced.patcher.Match
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.PatchException
import app.revanced.util.exception


internal val settingsFingerprint = fingerprint {
    returns("V")
    strings("pref_proxy")
    custom { method, _ ->
        method.name == "<clinit>"
    }
}

internal val authorizeAppActivity = fingerprint {
    custom { method, _ ->
        method.definingClass == "Lcom/twitter/android/AuthorizeAppActivity;" &&
                method.name == "onCreate"
    }
}
internal val integrationsUtilsFingerprint = fingerprint {
    returns("V")
    custom { method, _ ->
        method.definingClass == "Lapp/revanced/integrations/shared/Utils;" && method.name == "load"
    }
}

internal val settingsStatusLoadFingerprint = fingerprint {
    custom { method, _ ->
        method.definingClass.endsWith("Lapp/revanced/integrations/twitter/settings/SettingsStatus;") &&
                method.name == "load"
    }
}

fun Match.enableSettings(functionName: String) {
    this.mutableMethod.addInstruction(
        0,
        "${SSTS_DESCRIPTOR}->$functionName()V"
    )
}
