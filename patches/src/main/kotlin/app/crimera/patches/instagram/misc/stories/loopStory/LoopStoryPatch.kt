/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.stories.loopStory

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import app.morphe.util.indexOfFirstStringInstruction
import app.morphe.util.indexOfFirstStringInstructionOrThrow
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal object StoryProgressCompletedFingerprint : Fingerprint(
    returnType = "V",
    definingClass = "Linstagram/features/stories/fragment/ReelViewerFragment;",
    strings = listOf("userSession"),
    parameters = listOf("Ljava/lang/Object;"),
)

@Suppress("unused")
val loopStoryPatch =
    bytecodePatch(
        name = "Loop story",
        description = "Replay the current story when it ends",
    ) {
        dependsOn(settingsPatch)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            StoryProgressCompletedFingerprint.method.apply {
                val entryCast = getInstruction(indexOfFirstInstructionOrThrow(Opcode.CHECK_CAST))
                val reelItemRegister = entryCast.registersUsed[0]
                val reelItemType = entryCast.getReference<TypeReference>()!!.type

                if (indexOfFirstStringInstruction("resume") < 0) {
                    val fragment = StoryProgressCompletedFingerprint.classDef
                    val playerField = fragment.fields.singleOrNull { it.name == "mVideoPlayer" }
                        ?: throw PatchException("Expected one story video player field")
                    val playerTypes = fragment.methods.flatMap { candidate ->
                        val code = candidate.instructions.toList()
                        code.mapIndexedNotNull { index, instruction ->
                            if (instruction.opcode != Opcode.IGET_OBJECT ||
                                instruction.getReference<FieldReference>()?.toString() != playerField.toString()
                            ) return@mapIndexedNotNull null
                            val cast = code.getOrNull(index + 2)
                            if (cast?.opcode != Opcode.CHECK_CAST ||
                                cast.registersUsed.single() != instruction.registersUsed[0]
                            ) return@mapIndexedNotNull null
                            cast.getReference<TypeReference>()!!.type
                        }
                    }.distinct()
                    val player = playerTypes.singleOrNull()?.let { classDefBy(it) }
                        ?: throw PatchException("Expected one concrete story video player")
                    if (playerField.type !in player.interfaces) {
                        throw PatchException("Story video player does not implement its field type")
                    }
                    val relativeSeek = player.methods.singleOrNull {
                        AccessFlags.PUBLIC.isSet(it.accessFlags) && !AccessFlags.STATIC.isSet(it.accessFlags) &&
                            it.parameterTypes == listOf("I") && it.returnType == "V" &&
                            classDefBy(playerField.type).methods.any { declaration ->
                                declaration.name == it.name && declaration.parameterTypes == it.parameterTypes &&
                                    declaration.returnType == it.returnType
                            }
                    } ?: throw PatchException("Expected one relative story seek method")
                    val absoluteSeek = relativeSeek.instructions.mapNotNull {
                        if (it.opcode != Opcode.INVOKE_STATIC) return@mapNotNull null
                        it.getReference<MethodReference>()?.takeIf { reference ->
                            reference.definingClass == player.type && reference.returnType == "V" &&
                                reference.parameterTypes == listOf(player.type, "I")
                        }
                    }.distinct().singleOrNull()
                        ?: throw PatchException("Expected one absolute story seek helper")
                    val seekMethod = player.methods.single { it.toString() == absoluteSeek.toString() }
                    if (!AccessFlags.PUBLIC.isSet(seekMethod.accessFlags) ||
                        !AccessFlags.STATIC.isSet(seekMethod.accessFlags) ||
                        seekMethod.instructions.none {
                            it.opcode == Opcode.INVOKE_VIRTUAL &&
                                it.getReference<MethodReference>()?.let { reference ->
                                    reference.returnType == "V" && reference.parameterTypes == listOf("I", "Z")
                                } == true
                        }
                    ) throw PatchException("Unexpected absolute story seek helper")
                    val currentItem = relativeSeek.instructions.mapNotNull {
                        it.getReference<FieldReference>()?.takeIf { field ->
                            it.opcode == Opcode.IGET_OBJECT && field.definingClass == player.type &&
                                field.type == reelItemType
                        }
                    }.distinct().singleOrNull() ?: throw PatchException("Expected one current video story field")
                    if (player.fields.none {
                            it.toString() == currentItem.toString() && AccessFlags.PUBLIC.isSet(it.accessFlags)
                        }
                    ) throw PatchException("Current video story field is not public")
                    val resume = classDefBy(playerField.type).methods.singleOrNull {
                        it.parameterTypes == listOf("Ljava/lang/String;", "Z") && it.returnType == "V"
                    } ?: throw PatchException("Expected one story video resume method")
                    val photoRestartIndex = instructions.indices.singleOrNull { index ->
                        getInstruction(index).let {
                            it.opcode == Opcode.IGET_OBJECT &&
                                it.getReference<FieldReference>()?.name == "mPhotoTimerController"
                        }
                    } ?: throw PatchException("Expected one story photo restart block")
                    val photoRegister = getInstruction(photoRestartIndex).registersUsed[0]
                    if (p0Register <= 3 || p0Register > 15 || reelItemRegister != p0Register + 1 ||
                        reelItemRegister > 15 || photoRegister != 2 ||
                        getInstruction(photoRestartIndex + 1).opcode != Opcode.CONST_4 ||
                        getInstruction(photoRestartIndex + 1).registersUsed.single() != 1 ||
                        getInstruction(photoRestartIndex + 6).opcode != Opcode.IF_NE ||
                        getInstruction(photoRestartIndex + 6).registersUsed != listOf(0, 3)
                    ) throw PatchException("Unexpected story photo restart registers")

                    addInstructionsWithLabels(
                        0,
                        """
                        ${PREF_CALL_DESCRIPTOR}->loopStory()Z
                        move-result v0
                        if-eqz v0, :piko
                        check-cast v$reelItemRegister, $reelItemType
                        iget-object v0, p0, $playerField
                        if-eqz v0, :piko_photo
                        instance-of v1, v0, ${player.type}
                        if-eqz v1, :piko
                        check-cast v0, ${player.type}
                        iget-object v1, v0, $currentItem
                        invoke-virtual {v$reelItemRegister, v1}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z
                        move-result v1
                        if-eqz v1, :piko_photo
                        const/4 v1, 0x0
                        invoke-static {v0, v1}, $absoluteSeek
                        const-string v1, "resume"
                        const/4 v2, 0x0
                        invoke-interface {v0, v1, v2}, $resume
                        :piko_photo
                        const/4 v3, 0x1
                        goto :piko_loop
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(0)),
                        ExternalLabel("piko_loop", getInstruction(photoRestartIndex)),
                    )
                    return@apply
                }

                val restartIndex =
                    indexOfFirstInstructionReversedOrThrow(
                        indexOfFirstStringInstructionOrThrow("resume"),
                        Opcode.INVOKE_STATIC,
                    )

                val seekInstruction =
                    getInstruction(
                        indexOfFirstInstructionOrThrow(restartIndex) {
                            opcode == Opcode.INVOKE_INTERFACE &&
                                getReference<MethodReference>()?.let { reference ->
                                    reference.returnType == "V" &&
                                        reference.parameterTypes.map(CharSequence::toString) ==
                                        listOf("I", "Z")
                                } == true
                        },
                    )
                val positionRegister = seekInstruction.registersUsed[1]
                val playingRegister = seekInstruction.registersUsed[2]

                check(
                    p0Register > 0 &&
                        reelItemRegister > 0 &&
                        positionRegister < p0Register &&
                        playingRegister < p0Register,
                ) {
                    "Story restart block does not keep its state in local registers"
                }

                addInstructionsWithLabels(
                    0,
                    """
                    ${PREF_CALL_DESCRIPTOR}->loopStory()Z
                    move-result v0
                    if-eqz v0, :piko
                    check-cast v$reelItemRegister, $reelItemType
                    const/4 v$playingRegister, 0x1
                    const/4 v$positionRegister, 0x0
                    goto :piko_loop
                    """.trimIndent(),
                    ExternalLabel("piko", getInstruction(0)),
                    ExternalLabel("piko_loop", getInstruction(restartIndex)),
                )
            }
            enableSettings("loopStory")
        }
    }
