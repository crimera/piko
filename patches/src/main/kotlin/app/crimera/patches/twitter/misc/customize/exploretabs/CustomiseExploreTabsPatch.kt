/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.misc.customize.exploretabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private object CustomiseExploreTabsFingerprint : Fingerprint(
    definingClass = "JsonPageTabs;",
    filters = listOf(
        opcode(Opcode.NEW_INSTANCE)
    )
)

@Suppress("unused")
val customiseExploreTabsPatch =
    bytecodePatch(
        name = "Customize explore tabs",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val method = CustomiseExploreTabsFingerprint.method

            val instructions = method.instructions

            val index = instructions.first { it.opcode == Opcode.IGET_OBJECT }.location.index

            method.addInstructions(
                index + 1,
                """
                invoke-static {v1}, $CUSTOMISE_DESCRIPTOR;->exploretabs(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                move-result-object v1
                """.trimIndent(),
            )
            SettingsStatusLoadFingerprint.enableSettings("exploreTabCustomisation")
        }
    }
