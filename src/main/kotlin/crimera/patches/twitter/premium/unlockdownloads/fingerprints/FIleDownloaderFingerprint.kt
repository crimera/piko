package crimera.patches.twitter.premium.unlockdownloads.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.Opcode

internal object FIleDownloaderFingerprint : MethodFingerprint(
    returnType = "Z",
    strings = listOf(
        "mediaEntity", "url"
    ),
    opcodes = listOf(Opcode.IF_EQZ)
)