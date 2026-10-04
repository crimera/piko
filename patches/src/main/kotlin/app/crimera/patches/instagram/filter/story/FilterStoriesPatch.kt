/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.filter.story

import app.crimera.patches.instagram.entity.reelResponseItem.reelResponseItemEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

// Heavily based on @brosssh work.
// https://github.com/brosssh/instagram-morphe-patches-library/blob/dev/patch-library/src/main/kotlin/app/morphe/library/instagram/patches/FilterStoriesListPatch.kt

@Suppress("unused")
val filterStoriesPatch =
    bytecodePatch(
        name = "Filter stories",
        description = "Filter stories to hide based on different categories",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, reelResponseItemEntity, userDataEntity)
        execute {

            StoryResponseJsonParserFingerprint.matchAll(1..1).single().method.apply {
                val code = instructions.toList()
                val trayIndex = code.withIndex().singleOrNull { (_, instruction) ->
                    instruction.getReference<StringReference>()?.string == "tray"
                }?.index ?: throw PatchException("Expected one story tray field")
                val nextFieldIndex = (trayIndex + 1 until code.size).firstOrNull {
                    code[it].getReference<StringReference>() != null
                } ?: code.size
                val appendIndex = (trayIndex + 4 until nextFieldIndex).singleOrNull { index ->
                    val append = code[index].getReference<MethodReference>()
                    val arguments = code[index].registersUsed
                    val guard = code[index - 1]
                    val result = code[index - 2]
                    val parser = code[index - 3].getReference<MethodReference>()
                    code[index].opcode == Opcode.INVOKE_VIRTUAL &&
                        append?.definingClass == "Ljava/util/AbstractCollection;" &&
                        append.name == "add" && append.parameterTypes == listOf("Ljava/lang/Object;") &&
                        append.returnType == "Z" && arguments.size == 2 &&
                        guard.opcode == Opcode.IF_EQZ && guard.registersUsed == listOf(arguments[1]) &&
                        result.opcode == Opcode.MOVE_RESULT_OBJECT && result.registersUsed == guard.registersUsed &&
                        parser?.name == "parseFromJsonParser" && parser.returnType == "Ljava/lang/Object;"
                } ?: throw PatchException("Expected one parsed story tray item append")
                val guard = getInstruction<BuilderOffsetInstruction>(appendIndex - 1)
                val itemRegister = guard.registersUsed.single()
                val continuation = guard.target.location.instruction
                    ?: throw PatchException("Missing story tray loop continuation")

                // Reuse the native null-item continuation; field order and loop layout can change.
                addInstructionsWithLabels(
                    appendIndex,
                    """
                    invoke-static/range {v$itemRegister .. v$itemRegister}, $PATCHES_DESCRIPTOR/filter/story/FilterStory;->filter(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$itemRegister
                    if-eqz v$itemRegister, :piko
                    """.trimIndent(),
                    ExternalLabel("piko", continuation),
                )
                enableSettings("storyFilters")
            }
        }
    }
