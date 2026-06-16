/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.actionBar.dmActionBarButton.dmActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal object StorySeenUriBuilderFingerprint : Fingerprint(
    strings = listOf("media/seen/?reel=%s&live_vod=0"),
)

@Suppress("unused")
val viewStoriesAnonymouslyPatch =
    bytecodePatch(
        name = "View stories anonymously",
    ) {
        dependsOn(settingsPatch, interceptUriPatch, dmActionBarButtonPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            // Thanks to MyInsta.
            StorySeenUriBuilderFingerprint.classDef.methods.last { it.returnType == "Z" }.apply {
                val lastIfEqzIndex = instructions.last { it.opcode == Opcode.IF_EQZ }.location.index
                val returnIndex = indexOfFirstInstruction(lastIfEqzIndex, Opcode.RETURN)
                val seenRegister = getInstruction(returnIndex).registersUsed[0]

                addInstructions(
                    returnIndex,
                    """
                    invoke-static {v$seenRegister}, $LINKS_DESCRIPTOR->setStorySeen(Z)Z
                    move-result v$seenRegister
                    """.trimIndent(),
                )
            }

            enableSettings("viewStoriesAnonymously")
        }
    }
