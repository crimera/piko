/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.distractionFree.doubleTap

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

internal object ReelOnFlingFingerprint : Fingerprint(
    parameters = listOf("Landroid/view/MotionEvent;", "Landroid/view/MotionEvent;", "F", "F"),
    name = "onFling",
    filters =
        OpcodesFilter.opcodesToFilters(
            Opcode.IF_EQZ,
            Opcode.IF_EQZ,
        ),
)

@Suppress("unused")
val disableDoubleTapOnReelPatch =
    bytecodePatch(
        description = "Disable double tap like on reels",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            ReelOnFlingFingerprint.apply {
                classDef.methods.first { it.name == "onDoubleTap" }.apply {
                    addInstructions(
                        0,
                        DOUBLE_TAP_PREF_DESCRIPTOR.format("disableDoubleTapReel"),
                    )
                }
            }
        }
    }
