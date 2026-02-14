package app.crimera.patches.twitter.entity

import app.crimera.utils.changeFirstString
import app.crimera.utils.getFieldName
import app.crimera.utils.getMethodName
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

val extMediaEntityPatch =
    bytecodePatch(
        description = "For extended media entity reflection",
    ) {
        execute {
            ExtMediaHighResVideoMethodFinder.stringMatches?.forEach { match ->
                val str = match.string
                if (str == "null cannot be cast to non-null type com.twitter.model.dm.attachment.DMMediaAttachment") {
                    val inst =
                        ExtMediaHighResVideoMethodFinder.method.instructions.first {
                            it.opcode == Opcode.INVOKE_VIRTUAL &&
                                it.location.index > match.index
                        }
                    val methodName = ExtMediaHighResVideoMethodFinder.getMethodName(inst.location.index)
                    ExtMediaHighResVideoFingerprint.changeFirstString(methodName)
                    return@forEach
                }
            }

            // ------------
            val imageFieldName =
                ExtMediaGetImageMethodFinder.getFieldName(
                    ExtMediaGetImageMethodFinder.method
                        .instructions
                        .last {
                            it.opcode ==
                                Opcode.IGET_OBJECT
                        }.location.index,
                )
            ExtMediaGetImageFingerprint.changeFirstString(imageFieldName)
// ------------
        }
    }
