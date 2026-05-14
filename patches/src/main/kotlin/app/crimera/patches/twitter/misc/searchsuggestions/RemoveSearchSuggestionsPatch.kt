/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.misc.searchsuggestions

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

private object SearchSuggestionFingerprint : Fingerprint(
    definingClass = "/search/provider/",
    returnType = "Ljava/util/Collection;",
    strings =
        listOf(
            "type",
            "query_id",
        ),
)

@Suppress("unused")
val RemoveSearchSuggestions =
    bytecodePatch(
        name = "Remove search suggestions",
        description = "Hide/Remove search suggestion in explore section",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            SearchSuggestionFingerprint.method.apply {

                val firstInstruction = getInstruction(0)

                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR;->RemoveSearchSuggestions()Z
                    move-result v0
                    if-eqz v0, :cond_1212
                    const v0, 0x0
                    return-object v0
                    """.trimIndent(),
                    ExternalLabel("cond_1212", firstInstruction),
                )

                enableSettings("removeSearchSuggestions")
            }
        }
    }
