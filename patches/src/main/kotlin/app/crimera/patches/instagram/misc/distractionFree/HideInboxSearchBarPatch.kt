/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister

// Compose names each function in a trace string; its line number changes between versions, so only the start is matched.
private const val SEARCH_BAR_TRACE = "instagram.features.direct.inbox.feature.searchbar.ui."

// The two versions of the search bar of the inbox; the app picks one with an experiment.
private object SearchBarV1Fingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(string("${SEARCH_BAR_TRACE}SearchBarV1 (", StringComparisonType.STARTS_WITH)),
)

private object SearchBarV2Fingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(string("${SEARCH_BAR_TRACE}SearchBarV2 (", StringComparisonType.STARTS_WITH)),
)

@Suppress("unused")
val hideInboxSearchBarPatch =
    bytecodePatch(
        name = "Hide inbox search bar",
        description = "Hides the search bar at the top of the DM inbox",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            // A Compose function that returns before drawing anything leaves its row empty.
            listOf(SearchBarV1Fingerprint, SearchBarV2Fingerprint).forEach { fingerprint ->
                fingerprint.method.apply {
                    val register = findFreeRegister(0)

                    addInstructionsWithLabels(
                        0,
                        """
                        ${PREF_CALL_DESCRIPTOR}->hideInboxSearchBar()Z
                        move-result v$register
                        if-eqz v$register, :piko
                        return-void
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(0)),
                    )
                }
            }

            enableSettings("hideInboxSearchBar")
        }
    }
