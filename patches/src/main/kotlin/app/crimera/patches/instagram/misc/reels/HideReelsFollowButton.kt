/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.reels

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction

private const val PREF_CLASS_DESCRIPTOR = "Lapp/morphe/extension/instagram/utils/Pref;"

// AuthorInfoUseCase.shouldShowFollowButton(ClipsViewerConfig)
private object ReelsShouldShowFollowButtonFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "Z",
    strings = listOf("android_purge_26_q3_AuthorInfoUseCase_shouldShowFollowButton"),
)

private object ReelsInlineFollowButtonFingerprint : Fingerprint(
    strings = listOf(
        "android_purge_26_q3_AuthorInfoUseCase_shouldShowFollowButton",
        "android_purge_26_q3_FollowUseCase_getUiState",
    ),
    custom = { method, _ ->
        method.returnType.startsWith("L") &&
            "Lcom/instagram/clips/intf/ClipsViewerConfig;" in method.parameterTypes
    },
)

@Suppress("unused")
val hideReelsFollowButtonPatch =
    bytecodePatch(
        name = "Hide Reels follow button",
        description = "Removes the follow button from Reels.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        dependsOn(settingsPatch)

        execute {
            val standalone = ReelsShouldShowFollowButtonFingerprint.matchAll(0..1).singleOrNull()
            if (standalone != null) {
                standalone.method.apply {
                    val returns = instructions.indices.filter { instructions[it].opcode == Opcode.RETURN }
                    if (returns.isEmpty()) throw PatchException("Reels follow check has no return")
                    for (index in returns.asReversed()) {
                        val register = instructions[index].registersUsed.single()
                        addInstructionsAtControlFlowLabel(index, """
                            invoke-static/range {v$register .. v$register}, $PREF_CLASS_DESCRIPTOR->showReelsFollowButton(Z)Z
                            move-result v$register
                        """)
                    }
                }
            } else {
                val match = ReelsInlineFollowButtonFingerprint.matchAll(1..1).single()
                val method = match.method
                val code = method.instructions
                val start = match.stringMatches[0].index
                val end = match.stringMatches[1].index
                val mergeIndex = (start + 2 until end - 2).singleOrNull { index ->
                    val enabled = code[index] as? WideLiteralInstruction
                    val branch = code[index + 1] as? BuilderOffsetInstruction
                    val disabled = code[index + 2] as? WideLiteralInstruction
                    enabled?.wideLiteral == 1L && disabled?.wideLiteral == 0L &&
                        code[index].opcode in listOf(Opcode.CONST_4, Opcode.CONST_16) &&
                        code[index + 2].opcode in listOf(Opcode.CONST_4, Opcode.CONST_16) &&
                        code[index].registersUsed == code[index + 2].registersUsed &&
                        branch?.opcode == Opcode.IF_EQZ && branch.target.location.index == index + 3
                }?.plus(3) ?: throw PatchException("Expected one inlined Reels follow result")
                val register = code[mergeIndex - 1].registersUsed.single()
                method.addInstructionsAtControlFlowLabel(mergeIndex, """
                    invoke-static/range {v$register .. v$register}, $PREF_CLASS_DESCRIPTOR->showReelsFollowButton(Z)Z
                    move-result v$register
                """)
            }

            enableSettings("hideReelsFollowButton")
        }
    }
