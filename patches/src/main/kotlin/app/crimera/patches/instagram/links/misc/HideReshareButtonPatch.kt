/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.matchSingle
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

// Credits: brosssh
// https://github.com/brosssh/morphe-patches/commit/6a781ef8e0951ad5aa898fa17d094cfbfa5dd9fb

// The hash code of the field of interest. It is used as the key of a hashmap
internal const val notesTag = "enable_media_notes_production"
internal val hashedFieldInteger = notesTag.hashCode()

internal object FeedResponseMediaParserFingerprint : Fingerprint(
    filters =
        listOf(
            string(notesTag),
            literal(hashedFieldInteger),
        ),
    returnType = "Ljava/lang/Boolean;",
)

internal object LiveTreeGetOptionalBooleanFingerprint : Fingerprint(
    name = "getOptionalBooleanValueByHashCode",
    definingClass = "Lcom/instagram/pando/livetree/LiveTreeJNI;",
)

@Suppress("unused")
val hideReshareButtonPatch =
    bytecodePatch(
        name = "Hide reshare button",
        description = "Hides the reshare button from both posts and reels.",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            val PREF_CALL = "$PREF_CALL_DESCRIPTOR->hideReshareButton()Z"

            FeedResponseMediaParserFingerprint.method.apply {
                addInstructionsWithLabels(
                    0,
                    """
                    $PREF_CALL
                    move-result v0
                    if-eqz v0, :piko
                    sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                    return-object v0
                    """.trimMargin(),
                    ExternalLabel("piko", getInstruction(0)),
                )
            }

            // If it's trying to get the value for our field of interest via the Pando native library,
            // force the value to false instead
            LiveTreeGetOptionalBooleanFingerprint.method.addInstructions(
                0,
                """
                const v0, $hashedFieldInteger
                if-ne p1, v0, :nopatch
                $PREF_CALL
                move-result v0
                if-eqz v0, :nopatch
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
                :nopatch
                nop
        """,
            )

            // The ALV2 feed state bypasses the Media/Pando getters above. Feed renderers
            // use this state field to hide the entire repost control, including its count.
            val stateMatch =
                Fingerprint(
                    name = "toString",
                    returnType = "Ljava/lang/String;",
                    parameters = emptyList(),
                    filters = listOf(string(", isRepostButtonEnabled=")),
                ).matchSingle()
            val stateInstructions = stateMatch.method.implementation!!.instructions
            val labelIndex = stateMatch.instructionMatches.single().index
            val appendIndex =
                ((labelIndex + 1) until stateInstructions.size)
                    .takeWhile { stateInstructions[it].getReference<StringReference>() == null }
                    .singleOrNull {
                        stateInstructions[it].getReference<MethodReference>()?.toString() ==
                            "Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;"
                    } ?: throw PatchException("Could not resolve the feed repost state value")
            val appendRegisters = stateInstructions[appendIndex].registersUsed
            if (appendRegisters.size != 2) throw PatchException("Unexpected feed repost append registers")
            val valueRegister = appendRegisters[1]
            val fieldRead =
                stateInstructions.take(appendIndex).lastOrNull {
                    it.opcode.setsRegister() && it.registersUsed.firstOrNull() == valueRegister
                }
            val stateField = fieldRead?.getReference<FieldReference>()
            if (fieldRead?.opcode != Opcode.IGET_BOOLEAN || stateField?.type != "Z" ||
                stateField.definingClass != stateMatch.classDef.type
            ) {
                throw PatchException("Could not resolve the feed repost state field")
            }
            val stateClass = mutableClassDefBy(stateMatch.classDef.type)
            val writes =
                stateClass.methods.filter { it.name == "<init>" }.flatMap { constructor ->
                    constructor.implementation?.instructions.orEmpty().mapIndexedNotNull { index, instruction ->
                        if (instruction.opcode == Opcode.IPUT_BOOLEAN &&
                            instruction.getReference<FieldReference>()?.toString() == stateField.toString()
                        ) {
                            constructor to index
                        } else {
                            null
                        }
                    }
                }
            val (constructor, writeIndex) =
                writes.singleOrNull() ?: throw PatchException("Expected one feed repost state initialization, found ${writes.size}")
            val write = constructor.getInstruction(writeIndex)
            val writeRegisters = write.registersUsed
            if (writeRegisters.size != 2) throw PatchException("Unexpected feed repost field registers")
            val scratch = constructor.findFreeRegister(writeIndex, writeRegisters)
            if (scratch > 255) throw PatchException("No encodable register for the feed repost setting")
            constructor.addInstructionsWithLabels(
                writeIndex,
                """
                $PREF_CALL
                move-result v$scratch
                if-eqz v$scratch, :original
                const/4 v${writeRegisters[0]}, 0x0
                """.trimIndent(),
                ExternalLabel("original", write),
            )

            enableSettings("hideReshareButton")
        }
    }
