/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.timeline.sensitivemediasettings

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

// Credits to @Cradlesofashes

private object sensitiveMediaSettingsPatchFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/json/core/JsonSensitiveMediaWarning\$\$JsonObjectMapper;",
    name = "parse",
    returnType = "Ljava/lang/Object",
)

@Suppress("unused")
val sensitiveMediaPatch =
    bytecodePatch(
        name = "Show sensitive media",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val TIMELINE_ENTRY_DESCRIPTOR = "$PATCHES_DESCRIPTOR/TimelineEntry"

            val methods = sensitiveMediaSettingsPatchFingerprint.method
            val instructions = methods.instructions

            val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

            methods.addInstructions(
                returnObj,
                """
                invoke-static {p1}, $TIMELINE_ENTRY_DESCRIPTOR;->sensitiveMedia(Lcom/twitter/model/json/core/JsonSensitiveMediaWarning;)Lcom/twitter/model/json/core/JsonSensitiveMediaWarning;
                move-result-object p1
                """.trimIndent(),
            )
            enableSettings("showSensitiveMedia")
        }
    }
