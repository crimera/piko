/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// Thanks to instafel (https://github.com/mamiiblt/instafel) - GRAY_1600 is a
// compiled constant in BasePrismColorsV2's <clinit>, not a color resource, so
// the colors.xml overrides in ThemePatch.kt don't reach it. It's the
// prism-black tone newer Compose screens use, so without this patch they stay
// dark gray instead of pure black in AMOLED mode.
private const val PRISM_COLORS_V2_CLASS = "Lcom/instagram/compose/core/theme/BasePrismColorsV2;"
private const val GRAY_1600_FIELD_NAME = "GRAY_1600"
private const val PURE_BLACK_LITERAL = "0xff000000L"

// Matches the const-wide that feeds GRAY_1600's sput-wide directly: a
// shl-long/2addr sits in between (packing the ARGB value into Compose's
// internal 64-bit color representation), so MatchAfterWithin(1) allows for
// exactly that one unmatched instruction and no more - this is what pins the
// const-wide to the one immediately preceding GRAY_1600 specifically, instead
// of matching the first const-wide in the method.
internal object ComposePrismBlackFingerprint : Fingerprint(
    definingClass = PRISM_COLORS_V2_CLASS,
    name = "<clinit>",
    filters = listOf(
        opcode(Opcode.CONST_WIDE),
        fieldAccess(
            definingClass = PRISM_COLORS_V2_CLASS,
            name = GRAY_1600_FIELD_NAME,
            opcode = Opcode.SPUT_WIDE,
            location = InstructionLocation.MatchAfterWithin(1),
        ),
    ),
)

internal val composePrismBlackPatch =
    bytecodePatch(
        name = "Fix Compose prism black",
        description = "Rewrites the GRAY_1600 constant in BasePrismColorsV2 so newer " +
            "Compose-based screens render true black instead of Instagram's stock dark " +
            "gray prism tone. Required for the AMOLED theme option in the Theme patch " +
            "to look correct on those screens.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val constWideMatch = ComposePrismBlackFingerprint.instructionMatches[0]
            val register = (constWideMatch.instruction as OneRegisterInstruction).registerA

            ComposePrismBlackFingerprint.method.replaceInstruction(
                constWideMatch.index,
                "const-wide v$register, $PURE_BLACK_LITERAL",
            )
        }
    }