package crimera.patches.twitter.misc.shareMenu.hooks

import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.Reference

object ShareMenuButtonHook : MethodFingerprint(
    strings =
        listOf(
            "None",
            "Favorite",
            "Retweet",
            "Reply",
            "SendToTweetViewSandbox",
            "SendToSpacesSandbox",
            "ViewDebugDialog",
        ),
    opcodes =
        listOf(
            Opcode.NEW_INSTANCE,
            Opcode.MOVE_OBJECT,
        ),
) {
    fun buttonReference(buttonName: String): Reference? {
        val method = result!!.mutableMethod

        result!!.scanResult.stringsScanResult!!.matches.forEach { match ->
            val str = match.string
            if (str == buttonName) {
                val ref = method.getInstruction<ReferenceInstruction>(match.index + 3).reference
                return ref
            }
        }
        return null
    }
}
