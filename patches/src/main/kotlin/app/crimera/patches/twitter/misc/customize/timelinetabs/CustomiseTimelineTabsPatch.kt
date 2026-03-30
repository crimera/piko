/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.misc.customize.timelinetabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string

private object CustomiseTimelineTabsFingerprint : Fingerprint(
    parameters = listOf("I", "I"),
    filters =
        listOf(
            string("null cannot be cast to non-null type android.app.Activity"),
        ),
)

@Suppress("unused")
val customiseTimelineTabsPatch =
    bytecodePatch(
        name = "Customize timeline top bar",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            CustomiseTimelineTabsFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                     invoke-static {p1}, ${PREF_DESCRIPTOR};->timelineTab(I)I
                    move-result p1
                    """.trimIndent(),
                )

                enableSettings("timelineTabCustomisation")
            }
        }
    }
