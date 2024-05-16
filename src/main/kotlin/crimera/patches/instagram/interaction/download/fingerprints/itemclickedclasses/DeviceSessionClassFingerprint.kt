package crimera.patches.instagram.interaction.download.fingerprints.itemclickedclasses

import app.revanced.patcher.fingerprint.MethodFingerprint

object DeviceSessionClassFingerprint: MethodFingerprint(
    customFingerprint = { methodDef, classDef ->
        methodDef.name == "getDeviceSession" &&
                classDef.fields.count() > 2
    }
)