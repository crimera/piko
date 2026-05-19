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

object PostOnSingleTapConfirmedFingerprint : Fingerprint(
    strings = listOf("open_cmon_interstitial"),
    name = "onSingleTapConfirmed",
)

@Suppress("unused")
val disableDoubleTapOnPostPatch =
    bytecodePatch(
        description = "Disable double tap like on post",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            PostOnSingleTapConfirmedFingerprint.apply {
                classDef.methods.first { it.name == "onDoubleTap" }.apply {
                    addInstructions(
                        0,
                        DOUBLE_TAP_PREF_DESCRIPTOR.format("disableDoubleTapPost"),
                    )
                }
            }
        }
    }
