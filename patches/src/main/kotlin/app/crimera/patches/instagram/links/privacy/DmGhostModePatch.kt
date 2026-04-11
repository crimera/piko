/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.links.privacy

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

internal object MarkThreadSeenFingerprint : Fingerprint(
    strings = listOf("mark_thread_seen-"),
    returnType = "V",
    custom = { method, _ ->
        method.parameterTypes.any { it == "Lcom/instagram/common/session/UserSession;" } &&
            method.parameterTypes.size >= 5
    },
)

// Feature inspiration and core logic adapted from MyInsta by bluepapilte.
@Suppress("unused")
val dmGhostModePatch =
    bytecodePatch(
        name = "DM ghost mode",
        description = "Prevents DM read receipts from being sent, so other users won't see you've read their messages.",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            MarkThreadSeenFingerprint.method.apply {
                addInstructionsWithLabels(
                    0,
                    """
                    ${Constants.PREF_CALL_DESCRIPTOR}->dmGhostMode()Z
                    move-result v0
                    if-eqz v0, :original
                    return-void
                    """.trimIndent(),
                    ExternalLabel("original", getInstruction(0)),
                )
            }

            enableSettings("dmGhostMode")
        }
    }
