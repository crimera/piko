/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.links

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal object InAppBrowserFunctionFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf("Tracking.ARG_CLICK_SOURCE", "TrackingInfo.ARG_MODULE_NAME"),
)

@Suppress("unused")
val openLinksExternallyPatch =
    bytecodePatch(
        name = "Open links externally",
        description = "Changes links to always open in your external browser, instead of the in-app browser.",
        use = true,
    ) {

        dependsOn(settingsPatch)

        compatibleWith("com.instagram.android")

        execute {

            InAppBrowserFunctionFingerprint.let {
                val stringMatchIndex = it.stringMatches[1].index

                it.method.apply {
                    val urlResultObjIndex =
                        indexOfFirstInstructionOrThrow(
                            stringMatchIndex,
                            Opcode.MOVE_OBJECT_FROM16,
                        )

                    val urlRegister = getInstruction(urlResultObjIndex).registersUsed[0]
                    val freeRegister = findFreeRegister(urlResultObjIndex + 1)

                    addInstructionsWithLabels(
                        urlResultObjIndex + 1,
                        """
                        invoke-static/range { v$urlRegister .. v$urlRegister }, ${Constants.LINKS_DESCRIPTOR}->openExternally(Ljava/lang/String;)Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :piko
                        return v$freeRegister
                    """,
                        ExternalLabel("piko", instructions[urlResultObjIndex + 1]),
                    )

                    enableSettings("openLinksExternally")
                }
            }
        }
    }
