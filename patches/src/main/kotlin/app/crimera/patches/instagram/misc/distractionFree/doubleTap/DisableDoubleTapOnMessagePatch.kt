/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree.doubleTap

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string

private object DisableDoubleTapOnMessageFingerprint : Fingerprint(
    filters =
        listOf(
            string("double_tap_to_like_message"),
        ),
)

val disableDoubleTapOnMessagePatch =
    bytecodePatch(
        name = "Disable double tap on message",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            DisableDoubleTapOnMessageFingerprint.classDef.methods.firstOrNull { it.name == "onDoubleTap" }?.apply {
                replaceInstructions(
                    listOf(
                        "const/4 v0, 0x1",
                        "return v0",
                    ),
                )
            } ?: throw PatchException("Failed to find onDoubleTap method in ${DisableDoubleTapOnMessageFingerprint.definingClass}")
        }
    }
