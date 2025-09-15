package app.revanced.patches.shared.misc.string

import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.transformation.transformInstructionsPatch
import app.revanced.util.getReference
import com.android.tools.smali.dexlib2.ReferenceType
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import kotlin.text.contains

fun replaceStringPatch(
    from: String,
    to: String,
) = bytecodePatch(
    description = "Replaces occurrences of '$from' with '$to' in string references.",
) {
    dependsOn(
        transformInstructionsPatch(
            filterMap = filterMap@{ _, _, instruction, instructionIndex ->
                if (instruction.opcode.referenceType != ReferenceType.STRING) return@filterMap null

                val stringReference = instruction.getReference<StringReference>()!!.string
                if (from !in stringReference) return@filterMap null

                Triple(instructionIndex, instruction as OneRegisterInstruction, stringReference)
            },
            transform = transform@{ mutableMethod, entry ->
                val (instructionIndex, instruction, stringReference) = entry

                val newString = stringReference.replace(from, to)
                mutableMethod.replaceInstruction(
                    instructionIndex,
                    "${instruction.opcode.name} v${instruction.registerA}, \"$newString\"",
                )
            },
        ),
    )
}
