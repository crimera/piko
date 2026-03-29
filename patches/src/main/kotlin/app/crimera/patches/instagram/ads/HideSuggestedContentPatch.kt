/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.ads

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal object FeedItemParseFromJsonFingerprint : Fingerprint(
    strings =
        listOf(
            "suggested_businesses",
            "clips_netego",
            "stories_netego",
            "in_feed_survey",
            "bloks_netego",
            "suggested_igd_channels",
            "suggested_top_accounts",
            "suggested_users",
        ),
    custom = { methodDef, _ ->
        methodDef.name.lowercase().contains("parsefromjson")
    },
)

@Suppress("unused")
val hideSuggestedContentPatch =
    bytecodePatch(
        name = "Hide suggested content",
        description = "Hides suggested stories, reels, threads (Suggested posts will still be shown).",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {

            FeedItemParseFromJsonFingerprint.method.apply {
                val strIndex = FeedItemParseFromJsonFingerprint.stringMatches[0].index
                val moveResultObjectInstruction =
                    instructions.last {
                        it.opcode == Opcode.MOVE_RESULT_OBJECT &&
                            it.location.index < strIndex
                    }
                val index = moveResultObjectInstruction.location.index
                val register = moveResultObjectInstruction.registersUsed[0]

                addInstructions(
                    index + 1,
                    """
                    ${Constants.JSONPARSER_CHECK_DESCRIPTOR.format(register,register)}
                    """.trimIndent(),
                )

                enableSettings("hideSuggestedContent")
            }
        }
    }
