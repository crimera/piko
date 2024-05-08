package crimera.patches.twitter.misc.integrations.fingerprints

import app.revanced.patcher.extensions.or
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patches.shared.misc.integrations.BaseIntegrationsPatch
import com.android.tools.smali.dexlib2.AccessFlags

object ReVancedUtilsPatchesVersionFingerprint: MethodFingerprint(
    accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    customFingerprint = { methodDef, classDef ->
        methodDef.name == "getPatchesReleaseVersion" &&
                classDef.type == BaseIntegrationsPatch.INTEGRATIONS_CLASS_DESCRIPTOR
    }
)