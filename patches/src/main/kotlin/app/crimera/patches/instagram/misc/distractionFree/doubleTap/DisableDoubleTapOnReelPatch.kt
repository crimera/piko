/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree.doubleTap

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.matchSingle

internal object ReelOnDoubleTapFingerprint : Fingerprint(
    parameters = listOf("Landroid/view/MotionEvent;"),
    returnType = "Z",
    name = "onDoubleTap",
    strings = listOf("android_purge_26_q3_ClipsItemGestureDetector_onDoubleTap"),
)

@Suppress("unused")
val disableDoubleTapOnReelPatch =
    bytecodePatch(
        description = "Disable double tap like on reels",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            ReelOnDoubleTapFingerprint.matchSingle().method.addInstructions(
                0,
                DOUBLE_TAP_PREF_DESCRIPTOR.format("disableDoubleTapReel"),
            )
        }
    }
