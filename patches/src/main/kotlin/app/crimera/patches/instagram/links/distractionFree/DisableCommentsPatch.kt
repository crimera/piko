/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.distractionFree

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val HIDE_COMMENT_BUTTONS = "$PREF_CALL_DESCRIPTOR->hideCommentButtons()Z"

private object FeedCommentStateFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(
        string("MediaUfiUiState(mediaForActionHandler="),
        string(", isCommentsEnabled="),
        string(", shouldShowCommentCountInUfi="),
    ),
)

private object ReelsCommentStateFingerprint : Fingerprint(
    filters = listOf(
        string("android_purge_26_q3_UfiUseCase_getCommentButtonAndCount"),
        string("ReelsCommentButton"),
    ),
    custom = { method, _ ->
        "Lcom/instagram/clips/intf/ClipsViewerConfig;" in method.parameterTypes &&
            method.returnType.startsWith("L")
    },
)

@Suppress("unused")
val disableCommentsPatch =
    bytecodePatch(
        name = "Disable comments",
        description = "Disables comments and hides comment buttons and counts on posts and reels.",
    ) {
        dependsOn(settingsPatch, interceptUriPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val feedMatch = FeedCommentStateFingerprint.matchSingle()
            val stateInstructions = feedMatch.method.implementation!!.instructions
            val commentFields = feedMatch.instructionMatches.drop(1).map { label ->
                val appendIndex = ((label.index + 1) until stateInstructions.size)
                    .takeWhile { stateInstructions[it].getReference<StringReference>() == null }
                    .singleOrNull {
                        stateInstructions[it].getReference<MethodReference>()?.toString() ==
                            "Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;"
                    } ?: throw PatchException("Could not resolve the feed comment state value")
                val appendRegisters = stateInstructions[appendIndex].registersUsed
                if (appendRegisters.size != 2) throw PatchException("Unexpected feed comment append registers")
                var register = appendRegisters[1]
                var before = appendIndex
                var field: FieldReference? = null
                // Large state objects move field values through temporary registers in toString().
                while (field == null) {
                    val readIndex = (before - 1 downTo 0).firstOrNull {
                        stateInstructions[it].opcode.setsRegister() &&
                            stateInstructions[it].registersUsed.firstOrNull() == register
                    } ?: throw PatchException("Could not trace the feed comment state field")
                    val read = stateInstructions[readIndex]
                    when (read.opcode) {
                        Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16 -> {
                            val registers = read.registersUsed
                            if (registers.size != 2) throw PatchException("Unexpected feed comment move registers")
                            register = registers[1]
                            before = readIndex
                        }
                        Opcode.IGET_BOOLEAN -> {
                            field = read.getReference<FieldReference>()
                            if (field?.type != "Z" || field.definingClass != feedMatch.classDef.type) {
                                throw PatchException("Unexpected feed comment state field")
                            }
                        }
                        else -> throw PatchException("Unexpected feed comment state instruction")
                    }
                }
                field
            }
            if (commentFields.distinctBy { it.toString() }.size != 2) {
                throw PatchException("Expected separate feed comment button and count fields")
            }
            val stateClass = mutableClassDefBy(feedMatch.classDef.type)
            for (field in commentFields) {
                val writes = stateClass.methods.filter { it.name == "<init>" }.flatMap { constructor ->
                    constructor.implementation?.instructions.orEmpty().mapIndexedNotNull { index, instruction ->
                        if (instruction.opcode == Opcode.IPUT_BOOLEAN &&
                            instruction.getReference<FieldReference>()?.toString() == field.toString()
                        ) constructor to index else null
                    }
                }
                val (constructor, index) = writes.singleOrNull()
                    ?: throw PatchException("Expected one feed comment state initialization, found ${writes.size}")
                val write = constructor.getInstruction(index)
                val registers = write.registersUsed
                if (registers.size != 2) throw PatchException("Unexpected feed comment field registers")
                val scratch = constructor.findFreeRegister(index, registers)
                if (scratch > 255) throw PatchException("No encodable register for the comment setting")
                constructor.addInstructionsWithLabels(
                    index,
                    """
                    $HIDE_COMMENT_BUTTONS
                    move-result v$scratch
                    if-eqz v$scratch, :original
                    const/4 v${registers[0]}, 0x0
                    """.trimIndent(),
                    ExternalLabel("original", write),
                )
            }

            val reelsMatch = ReelsCommentStateFingerprint.matchSingle()
            val reelsMethod = reelsMatch.method
            val reelsInstructions = reelsMethod.implementation!!.instructions
            val logIndex = reelsMatch.instructionMatches[1].index
            val hiddenIndex = logIndex + 2
            val hidden = reelsInstructions.getOrNull(hiddenIndex)
            val hiddenField = hidden?.getReference<FieldReference>()
            val stateRegister = hidden?.registersUsed?.singleOrNull()
            val mergeIndex = hiddenIndex + 1
            val reset = reelsInstructions.getOrNull(mergeIndex)
            val scratch = reset?.registersUsed?.singleOrNull()
            if (hidden?.opcode != Opcode.SGET_OBJECT || hiddenField == null ||
                hiddenField.type != hiddenField.definingClass || stateRegister == null ||
                reset?.opcode != Opcode.CONST_4 || (reset as? WideLiteralInstruction)?.wideLiteral != 0L ||
                scratch == null || scratch == stateRegister
            ) {
                throw PatchException("Could not resolve the hidden Reels comment state")
            }
            // Button, button-with-count and hidden paths all join here. Retarget their
            // branches to the hook, then let Instagram initialize the scratch register.
            reelsMethod.addInstructionsAtControlFlowLabel(
                mergeIndex,
                """
                $HIDE_COMMENT_BUTTONS
                move-result v$scratch
                if-eqz v$scratch, :piko_comments_original
                sget-object v$stateRegister, $hiddenField
                :piko_comments_original
                nop
                """.trimIndent(),
            )

            enableSettings("disableComments")
        }
    }
