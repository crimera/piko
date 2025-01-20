package crimera.patches.twitter.misc.shareMenu.hooks

import app.revanced.patcher.Match
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.Reference

val ShareMenuButtonHook =
    fingerprint {
        strings(
            "None",
            "Favorite",
            "Retweet",
            "Reply",
            "SendToTweetViewSandbox",
            "SendToSpacesSandbox",
            "ViewDebugDialog",
        )
        returns("V")
        opcodes(
            Opcode.NEW_INSTANCE,
            Opcode.MOVE_OBJECT,
        )
        custom { methodDef, _ ->
            methodDef.name == "a" && methodDef.parameters.size == 4
        }
    }

fun Match.buttonReference(buttonName: String): Reference? {
    this.stringMatches?.forEach { match ->
        val str = match.string
        if (str == buttonName) {
            val ref = this.mutableMethod.getInstruction<ReferenceInstruction>(match.index + 3).reference
            return ref
        }
    }
    return null
}
