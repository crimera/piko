package app.revanced.patches.shared.misc.integrations.fingerprint

import app.revanced.patcher.fingerprint.MethodFingerprint

object IntegrationsUtilsFingerprint:MethodFingerprint(
    returnType = "V",
    customFingerprint = {it,_->
        it.definingClass == "Lapp/revanced/integrations/shared/Utils;" && it.name == "load"
    }
)