package app.crimera.patches.newx.timeline

import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.valueReachesRegister
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal data class ResumePositionValidation(
    val itemIndexField: FieldReference,
    val matcher: MethodReference,
)

/** Resolve the same list-index / entry-id / sort-index validation whether R8 extracts or inlines it. */
internal fun resolveResumePositionValidation(
    postLoad: Method,
    resumeType: String,
    classByType: (String) -> ClassDef?,
): ResumePositionValidation? {
    val external = requireAtMostOne(
        "NewX extracted resume validator in $postLoad",
        postLoad.implementation!!.instructions.mapNotNull { instruction ->
            val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
            reference.takeIf {
                instruction.opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) &&
                    reference.parameterTypes.map(CharSequence::toString) == listOf(resumeType, "Ljava/util/List;") &&
                    reference.returnType == "Z"
            }
        }.distinctBy(MethodReference::toString),
    )
    val source = if (external == null) postLoad else requireExactlyOne(
        "NewX extracted resume validator $external",
        classByType(external.definingClass)?.methods?.filter { it.toString() == external.toString() }.orEmpty(),
    )
    val code = source.implementation!!.instructions.toList()
    val lookups = code.indices.filter { index ->
        val reference = code[index].getReference<MethodReference>()
        code[index].opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) &&
            reference?.definingClass?.startsWith("Lkotlin/collections/") == true &&
            reference.name == "getOrNull" &&
            reference.parameterTypes.map(CharSequence::toString) == listOf("Ljava/util/List;", "I") &&
            reference.returnType == "Ljava/lang/Object;"
    }
    if (external == null && lookups.isEmpty()) return null
    val lookup = requireExactlyOne("NewX resume validator list lookup in $source", lookups)
    val indexRead = code.fieldValueBefore(lookup, code[lookup].registersUsed[1])
        ?: throw PatchException("NewX resume list index has no proven field source in $source")
    if (indexRead.field.definingClass != resumeType || indexRead.field.type != "I") {
        throw PatchException("NewX resume list index is not read from the resume model: ${indexRead.field}")
    }
    val result = code.getOrNull(lookup + 1) as? OneRegisterInstruction
    if (result?.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException("NewX resume list lookup has no object result in $source")
    }
    val offsets = code.runningFold(0) { offset, instruction -> offset + instruction.codeUnits }
    val matchers = code.indices.mapNotNull { index ->
        if (index <= lookup + 1 || code[index].opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)) {
            return@mapNotNull null
        }
        val reference = code[index].getReference<MethodReference>() ?: return@mapNotNull null
        val parameters = reference.parameterTypes.map(CharSequence::toString)
        if (parameters.size != 3 || parameters[1] != "Ljava/lang/String;" || parameters[2] != "J" ||
            reference.returnType != "Z"
        ) return@mapNotNull null
        val arguments = code[index].registersUsed
        val entryId = code.fieldValueBefore(index, arguments[1]) ?: return@mapNotNull null
        val sortIndex = code.fieldValueBefore(index, arguments[2]) ?: return@mapNotNull null
        if (entryId.field.definingClass != resumeType || entryId.field.type != "Ljava/lang/String;" ||
            sortIndex.field.definingClass != resumeType || sortIndex.field.type != "J"
        ) return@mapNotNull null
        val castsLookupItem = (lookup + 2 until index).any { castIndex ->
            val cast = code[castIndex] as? OneRegisterInstruction ?: return@any false
            code[castIndex].opcode == Opcode.CHECK_CAST &&
                code[castIndex].getReference<TypeReference>()?.type == parameters[0] &&
                code.valueReachesRegister(lookup + 1, result.registerA, castIndex, cast.registerA) &&
                code.valueReachesRegister(castIndex, cast.registerA, index, arguments[0])
        }
        if (!castsLookupItem ||
            !code.valueReachesRegister(indexRead.index, indexRead.receiver, entryId.index, entryId.receiver) ||
            !code.valueReachesRegister(indexRead.index, indexRead.receiver, sortIndex.index, sortIndex.receiver)
        ) return@mapNotNull null
        // A null-item guard may leave this block; no edge may enter or loop inside the consumed-value window.
        if (code.indices.any { branchIndex ->
                val branch = code[branchIndex] as? OffsetInstruction ?: return@any false
                val target = offsets[branchIndex] + branch.codeOffset
                target > offsets[lookup] && target <= offsets[index]
            } || source.implementation!!.tryBlocks.isNotEmpty()
        ) return@mapNotNull null
        reference
    }
    return ResumePositionValidation(
        indexRead.field,
        requireExactlyOne("NewX resume entry-id/sort-index matcher in $source", matchers),
    )
}

private data class ResumeFieldRead(val index: Int, val receiver: Int, val field: FieldReference)

/** Backwards through a branch-free value definition, including integer/object register staging. */
private fun List<Instruction>.fieldValueBefore(before: Int, register: Int): ResumeFieldRead? {
    var tracked = register
    for (index in before - 1 downTo 0) {
        val instruction = this[index]
        if (instruction is OffsetInstruction) return null
        val destination = (instruction as? OneRegisterInstruction)?.registerA ?: continue
        if (!instruction.opcode.setsRegister()) continue
        if (instruction.opcode.setsWideRegister() && destination + 1 == tracked) return null
        if (destination != tracked) continue
        if (instruction.opcode in setOf(
                Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16,
                Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16,
            )
        ) {
            tracked = (instruction as TwoRegisterInstruction).registerB
            continue
        }
        if (instruction.opcode !in setOf(Opcode.IGET, Opcode.IGET_OBJECT, Opcode.IGET_WIDE)) return null
        return ResumeFieldRead(index, (instruction as TwoRegisterInstruction).registerB,
            instruction.getReference<FieldReference>() ?: return null)
    }
    return null
}
