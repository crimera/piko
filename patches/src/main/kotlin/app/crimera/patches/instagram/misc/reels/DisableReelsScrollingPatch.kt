/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.reels

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private object ClipsViewPagerImplGetViewAtIndexFingerprint : Fingerprint(
    filters =
        listOf(
            string("ClipsViewPagerImpl.getViewAtIndex() index is out of bounds: "),
        ),
)

val disableReelsScrollingPatch =
    bytecodePatch(
        name = "Disable reels scrolling",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            val viewPagerField = ClipsViewPagerImplGetViewAtIndexFingerprint.classDef.fields.firstOrNull {
                it.type.startsWith("L") && it.type.endsWith("ViewPager;")
            } ?: throw PatchException("Failed to find ViewPager field in ${ClipsViewPagerImplGetViewAtIndexFingerprint.definingClass}")

            ClipsViewPagerImplGetViewAtIndexFingerprint.method.apply {
                val ifNez = instructions.firstOrNull { it.opcode == Opcode.IF_NEZ }
                    ?: throw PatchException("Failed to find IF_NEZ in ${ClipsViewPagerImplGetViewAtIndexFingerprint.definingClass}")

                replaceInstruction(
                    ifNez.location.index,
                    "goto :cond_0",
                )
            }
        }
    }
