package app.crimera.patches.instagram.entity.mediadata

import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

val mediaDataPatch = bytecodePatch(
    description = "This patch is used for decoding obfuscated code of the media data",
) {
    compatibleWith("com.instagram.android")

    execute {

        ReelsMentionDoubleTapFingerprint.method.apply {
            val secondInvokeStaticMethodData = instructions.filter { it.opcode == Opcode.INVOKE_STATIC }[1].methodExtractor()
            GetHelperClassExtensionFingerprint.changeFirstString(secondInvokeStaticMethodData.definingClass)
            GetMentionSetExtensionFingerprint.changeFirstString(secondInvokeStaticMethodData.name)
        }
    }
}
