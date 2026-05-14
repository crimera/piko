/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.misc.customize.postFontSize

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private object CustomiseNavBarFingerprint : Fingerprint(
    definingClass = "TextContentView;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
)

@Suppress("unused")
val customizePostFontSize =
    bytecodePatch(
        name = "Customise post font size",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val method = CustomiseNavBarFingerprint.method

            val index =
                method
                    .instructions
                    .last { it.opcode == Opcode.MOVE_RESULT }
                    .location.index
            method.addInstruction(index + 1, "sget p1, $PREF_DESCRIPTOR;->POST_FONT_SIZE:F")
            enableSettings("customPostFontSize")
        }
    }
