package app.crimera.patches.instagram.entity.userdata

import app.crimera.utils.changeFirstString
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

val userDataPatch = bytecodePatch(
    description = "This patch is used for decoding obfuscated code of the user data",
) {
    compatibleWith("com.instagram.android")

    execute {

        GetMatrixCursorFingerprint.method.apply {
            val additionalUserInfoFieldName =
                instructions.filter { it.opcode == Opcode.IGET_OBJECT }[1].fieldExtractor().name
            GetAdditionalUserInfoExtensionFingerprint.changeFirstString(additionalUserInfoFieldName)

            val invokeInterfaceList = instructions.filter { it.opcode == Opcode.INVOKE_INTERFACE }
            GetUsernameExtensionFingerprint.changeFirstString(invokeInterfaceList[0].methodExtractor().name)
            GetFullNameExtensionFingerprint.changeFirstString(invokeInterfaceList[1].methodExtractor().name)
        }
    }
}
