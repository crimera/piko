/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal object InAppBrowserFunctionFingerprint : Fingerprint(
    returnType = "Z",
    filters = listOf(
        string("TrackingInfo.ARG_MODULE_NAME"),
        string("Tracking.ARG_CLICK_SOURCE"),
        // A Bundle can be moved between the key and the call, so the url is moved after the call.
        methodCall(smali = "Landroid/os/BaseBundle;->putString(Ljava/lang/String;Ljava/lang/String;)V"),
        opcode(Opcode.MOVE_OBJECT_FROM16),
    ),
)

@Suppress("unused")
val openLinksExternallyPatch =
    bytecodePatch(
        name = "Open links externally",
        description = "Changes links to always open in your external browser, instead of the in-app browser.",
        default = true,
    ) {

        dependsOn(settingsPatch)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            InAppBrowserFunctionFingerprint.let {
                val urlResultObjIndex = it.instructionMatches.last().index

                it.method.apply {
                    val urlRegister = it.instructionMatches.last().getInstruction<OneRegisterInstruction>().registerA
                    val freeRegister = findFreeRegister(urlResultObjIndex + 1)

                    addInstructionsWithLabels(
                        urlResultObjIndex + 1,
                        """
                        invoke-static/range { v$urlRegister .. v$urlRegister }, ${Constants.LINKS_DESCRIPTOR}->openExternally(Ljava/lang/String;)Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :piko
                        return v$freeRegister
                    """,
                        ExternalLabel("piko", getInstruction(urlResultObjIndex + 1)),
                    )

                    enableSettings("openLinksExternally")
                }
            }
        }
    }
