/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

internal object ProfileHeaderBinderFingerprint : Fingerprint(
    name = "bindView",
    returnType = "V",
    parameters = listOf("I", "Landroid/view/View;", "Ljava/lang/Object;", "Ljava/lang/Object;"),
    filters = listOf(string("ProfileHeaderBinderGroup.bindView")),
    custom = { method, _ -> !AccessFlags.STATIC.isSet(method.accessFlags) },
)

@Suppress("unused")
val disableHighlightsPatch =
    bytecodePatch(
        name = "Disable highlights",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            ProfileHeaderBinderFingerprint.matchAll(1..1).single().method.apply {
                val returnIndex = instructions.withIndex()
                    .filter { it.value.opcode == Opcode.RETURN_VOID }
                    .singleOrNull()?.index
                    ?: throw PatchException("Expected one profile header binder return")
                val extension = "$PATCHES_DESCRIPTOR/userprofile/ProfileHighlights;"

                // Replace the return so branches to it also pass through the visibility hook.
                replaceInstruction(
                    returnIndex,
                    "invoke-static/range {p2 .. p2}, $extension->afterBind(Landroid/view/View;)V",
                )
                addInstruction(returnIndex + 1, "return-void")
                addInstruction(
                    0,
                    "invoke-static/range {p2 .. p2}, $extension->beforeBind(Landroid/view/View;)V",
                )
            }
            enableSettings("disableHighlights")
        }
    }
