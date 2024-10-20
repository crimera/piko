package crimera.patches.twitter.misc.nativeDownloader.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.Opcode

internal object GetIdFingerprint : MethodFingerprint(
    opcodes = listOf(
        Opcode.INVOKE_VIRTUAL, Opcode.MOVE_RESULT_WIDE, Opcode.RETURN_WIDE
    ), customFingerprint = { methodDef, _ ->
        methodDef.name == "getId"
    }, returnType = "J"
)