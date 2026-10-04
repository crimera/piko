/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.shared.parameterRegisterStart
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val SUGGESTIONS = "$PATCHES_DESCRIPTOR/SuggestedProfiles;"
private const val LIST = "Ljava/util/List;"
private const val COLLECTION = "Ljava/util/AbstractCollection;"

private fun Instruction.calls(owner: String, name: String): Boolean {
    val reference = (this as? ReferenceInstruction)?.reference as? MethodReference
    return reference?.definingClass == owner && reference.name == name
}

context(context: BytecodePatchContext)
internal fun hideActivityFeedSuggestions() {
    val method = ActivityFeedSectionsFingerprint.matchAll(1..1).single().method
    val code = method.instructions
    val edits = mutableListOf<Pair<Int, () -> Unit>>()

    fun filterUsersAfter(index: Int, target: MutableMethod = method) {
        val register = target.instructions[index].registersUsed.firstOrNull()
            ?: throw PatchException("Missing activity feed users register")
        edits += index + 1 to {
            target.addInstructions(index + 1, """
                invoke-static/range {v$register .. v$register}, $SUGGESTIONS->filterActivityFeedUsers(Ljava/util/List;)Ljava/util/List;
                move-result-object v$register
            """.trimIndent())
        }
    }

    // The legacy response has consecutive follow-back, pending-request and suggested-user sections.
    val legacyReads = code.indices.filter { index ->
        val field = (code[index] as? ReferenceInstruction)?.reference as? FieldReference
        if (code[index].opcode != Opcode.IGET_OBJECT || field?.type != LIST ||
            field.definingClass !in method.parameterTypes.subList(4, 6)
        ) return@filter false
        val emptyIndex = index + if (code.getOrNull(index + 1)?.opcode == Opcode.IF_EQZ) 2 else 1
        val header = (code.getOrNull(emptyIndex + 4) as? ReferenceInstruction)?.reference as? MethodReference
        code.getOrNull(emptyIndex)?.calls(LIST, "isEmpty") == true &&
            code.getOrNull(emptyIndex + 1)?.opcode == Opcode.MOVE_RESULT &&
            code.getOrNull(emptyIndex + 2)?.opcode == Opcode.IF_NEZ &&
            header?.definingClass == method.definingClass &&
            header.parameterTypes == listOf(COLLECTION, "I") && header.returnType == "V"
    }
    if (legacyReads.size !in 2..3) throw PatchException("Expected legacy activity feed user sections")
    filterUsersAfter(legacyReads[0])
    if (legacyReads.size == 3) {
        filterUsersAfter(legacyReads[2])
    } else {
        val response = ((code[legacyReads[1]] as ReferenceInstruction).reference as FieldReference).definingClass
        val thirdRead = (legacyReads[1] + 1 until code.size).firstOrNull {
            val field = (code[it] as? ReferenceInstruction)?.reference as? FieldReference
            code[it].opcode == Opcode.IGET_OBJECT && field?.definingClass == response && field.type == LIST
        } ?: throw PatchException("Missing delegated suggested user section")
        val delegatedCall = (thirdRead + 4 until minOf(thirdRead + 15, code.size)).singleOrNull {
            val ref = (code[it] as? ReferenceInstruction)?.reference as? MethodReference
            ref?.definingClass == method.definingClass && ref.returnType == method.returnType &&
                ref.parameterTypes == listOf(method.parameterTypes[2], response, COLLECTION, LIST, "I", "Z", "Z")
        }
        if (!code[thirdRead + 1].calls(LIST, "isEmpty") ||
            code[thirdRead + 2].opcode != Opcode.MOVE_RESULT ||
            code[thirdRead + 3].opcode != Opcode.IF_NEZ || delegatedCall == null
        ) throw PatchException("Unexpected delegated suggested user section")
        filterUsersAfter(thirdRead)
    }

    val successReads = code.indices.filter { index ->
        val field = (code[index] as? ReferenceInstruction)?.reference as? FieldReference
        val cast = code.getOrNull(index + 1)
        val type = (cast as? ReferenceInstruction)?.reference as? TypeReference
        code[index].opcode == Opcode.IGET_OBJECT && field?.type == "Ljava/lang/Object;" &&
            cast?.opcode == Opcode.CHECK_CAST && type?.type in listOf(LIST, "Ljava/lang/Iterable;") &&
            code[index].registersUsed.firstOrNull() == cast.registersUsed.singleOrNull()
    }
    if (successReads.size !in 1..2 || (successReads.size == 2 &&
        (code[successReads[0]] as ReferenceInstruction).reference.toString() !=
        (code[successReads[1]] as ReferenceInstruction).reference.toString())
    ) throw PatchException("Expected both activity feed recommendation result paths")
    val iterableRead = successReads.singleOrNull {
        ((code[it + 1] as ReferenceInstruction).reference as TypeReference).type == "Ljava/lang/Iterable;"
    } ?: throw PatchException("Missing activity feed recommendation iterable")
    if (successReads.size == 2) {
        filterUsersAfter(successReads.single { it != iterableRead } + 1)
    } else {
        val resultField = (code[iterableRead] as ReferenceInstruction).reference.toString()
        val delegatedReads = context.mutableClassDefBy(method.definingClass).methods.flatMap { target ->
            if (target == method || target.returnType != method.returnType) return@flatMap emptyList()
            target.instructions.indices.mapNotNull { index ->
                val read = target.instructions[index]
                val cast = target.instructions.getOrNull(index + 1)
                if (read.opcode == Opcode.IGET_OBJECT &&
                    (read as? ReferenceInstruction)?.reference.toString() == resultField &&
                    cast?.opcode == Opcode.CHECK_CAST &&
                    ((cast as? ReferenceInstruction)?.reference as? TypeReference)?.type == LIST &&
                    read.registersUsed.firstOrNull() == cast.registersUsed.singleOrNull()
                ) target to index else null
            }
        }
        val (target, index) = delegatedReads.singleOrNull()
            ?: throw PatchException("Missing delegated activity feed recommendation result")
        filterUsersAfter(index + 1, target)
    }

    val pinnedRowIndex = code.indices.singleOrNull {
        ((code[it] as? ReferenceInstruction)?.reference as? StringReference)?.string == "friend_request_pinned_row"
    } ?: code.indices.singleOrNull {
        val reference = (code[it] as? ReferenceInstruction)?.reference as? MethodReference
        code[it].opcode == Opcode.INVOKE_VIRTUAL &&
            reference?.definingClass == method.parameterTypes[6] &&
            reference.parameterTypes.isEmpty() && reference.returnType.startsWith("L") &&
            context.classDefBy(reference.definingClass).methods.singleOrNull { candidate ->
                candidate.name == reference.name && candidate.parameterTypes == reference.parameterTypes
            }?.implementation?.instructions?.any { instruction ->
                ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string ==
                    "friend_request_pinned_row"
            } == true
    } ?: throw PatchException("Missing pinned follow request row")
    val emptyIndex = (iterableRead + 2 until pinnedRowIndex).firstOrNull { code[it].calls(LIST, "isEmpty") }
        ?: throw PatchException("Missing activity feed recommendation empty check")
    val usersRegister = code[emptyIndex].registersUsed.singleOrNull()
        ?: throw PatchException("Invalid activity feed recommendation list")
    val result = code[emptyIndex + 1]
    val branch = code[emptyIndex + 2] as? BuilderOffsetInstruction
        ?: throw PatchException("Missing activity feed recommendation skip branch")
    if (result.opcode != Opcode.MOVE_RESULT || branch.opcode != Opcode.IF_NEZ ||
        result.registersUsed != branch.registersUsed
    ) throw PatchException("Unexpected activity feed recommendation empty check")
    val skipSection = code[branch.target.location.index]

    val headerIndex = (emptyIndex + 3 until pinnedRowIndex).singleOrNull { code[it].calls(COLLECTION, "add") }
        ?: throw PatchException("Expected one recommendation header append")
    val next = code[headerIndex + 1]
    val parameters = parameterRegisterStart(method)
    if (next.opcode != Opcode.MOVE_OBJECT_FROM16 || next.registersUsed.size != 2 ||
        next.registersUsed[1] != parameters + 6
    ) throw PatchException("Missing pinned request setup after recommendation header")
    val headerScratch = next.registersUsed[0]
    // Pinned request construction reuses the parameter registers; use the native saved category.
    val categoryCopy = (0 until headerIndex).lastOrNull {
        code[it].opcode == Opcode.MOVE_OBJECT_FROM16 && code[it].registersUsed.getOrNull(1) == parameters + 8
    } ?: throw PatchException("Missing saved activity feed category")
    val categoryRegister = code[categoryCopy].registersUsed[0]
    edits += headerIndex to {
        val headerLocation = (code[headerIndex] as BuilderInstruction).location
        val headerLabels = headerLocation.labels.toList()
        method.addInstructionsWithLabels(headerIndex, """
            invoke-static/range {v$categoryRegister .. v$categoryRegister}, $SUGGESTIONS->hideActivityFeedSuggestions(Ljava/lang/String;)Z
            move-result v$headerScratch
            if-nez v$headerScratch, :piko_skip_suggestion_header
        """.trimIndent(), ExternalLabel("piko_skip_suggestion_header", next))
        // Existing header branches must enter the gate instead of jumping straight to the append.
        val gateLabels = (method.instructions[headerIndex] as BuilderInstruction).location.labels
        headerLocation.labels.clear()
        gateLabels.addAll(headerLabels)
    }

    val iteratorIndex = (pinnedRowIndex + 1 until code.size).firstOrNull {
        code[it].calls(LIST, "iterator") && code[it].registersUsed == listOf(usersRegister)
    } ?: throw PatchException("Missing recommendation iterator after pinned requests")
    val iteratorResult = code[iteratorIndex + 1]
    val loopResult = code[iteratorIndex + 3]
    if (iteratorResult.opcode != Opcode.MOVE_RESULT_OBJECT ||
        !code[iteratorIndex + 2].calls("Ljava/util/Iterator;", "hasNext") ||
        code[iteratorIndex + 2].registersUsed != iteratorResult.registersUsed ||
        loopResult.opcode != Opcode.MOVE_RESULT || loopResult.registersUsed != result.registersUsed
    ) throw PatchException("Unexpected activity feed recommendation loop")
    val loopScratch = loopResult.registersUsed.single()
    // Keep the pinned request row above, and skip only recommendations and their footer.
    edits += iteratorIndex to {
        method.addInstructionsWithLabels(iteratorIndex, """
            invoke-static/range {v$categoryRegister .. v$categoryRegister}, $SUGGESTIONS->hideActivityFeedSuggestions(Ljava/lang/String;)Z
            move-result v$loopScratch
            if-nez v$loopScratch, :piko_skip_suggestion_rows
        """.trimIndent(), ExternalLabel("piko_skip_suggestion_rows", skipSection))
    }
    edits.sortedByDescending { it.first }.forEach { it.second() }
}
