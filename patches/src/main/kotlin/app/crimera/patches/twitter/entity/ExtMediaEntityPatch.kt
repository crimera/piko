package app.crimera.patches.twitter.entity

import app.crimera.utils.changeFirstString
import app.crimera.utils.getFieldName
import app.crimera.utils.getMethodName
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val extMediaEntityPatch =
    bytecodePatch(
        description = "For extended media entity reflection",
    ) {
        compatibleWith("com.twitter.android")

        execute {

            extMediaHighResVideoMethodFinder.stringMatches?.forEach { match ->
                val str = match.string
                if (str == "null cannot be cast to non-null type com.twitter.model.dm.attachment.DMMediaAttachment") {
                    val inst =
                        extMediaHighResVideoMethodFinder.method.instructions.first {
                            it.opcode == Opcode.INVOKE_VIRTUAL &&
                                it.location.index > match.index
                        }
                    val methodName = extMediaHighResVideoMethodFinder.method.getMethodName(inst.location.index)
                    extMediaHighResVideoFingerprint.method.changeFirstString(methodName)
                    return@forEach
                }
            }

            // ------------
            val imageFieldName =
                extMediaGetImageMethodFinder.method.getFieldName(
                    extMediaGetImageMethodFinder.method
                        .instructions
                        .last {
                            it.opcode ==
                                Opcode.IGET_OBJECT
                        }.location.index,
                )
            extMediaGetImageFingerprint.method.changeFirstString(imageFieldName)
// ------------
        }
    }
