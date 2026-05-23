/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.ads

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
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

            FeedItemParseFromJsonFingerprint.apply {
                val strIndex = stringMatches[0].index
                method.apply {
                    val indexOfFirstString = indexOfFirstInstruction(Opcode.CONST_STRING_JUMBO)

                    val jsonParserKeyInstruction =
                        instructions.last {
                            it.opcode == Opcode.MOVE_RESULT_OBJECT &&
                                it.location.index < indexOfFirstString
                        }
                    val moveResultObjectIndex = jsonParserKeyInstruction.location.index
                    val strRegister = jsonParserKeyInstruction.registersUsed[0]

                    addInstructions(
                        moveResultObjectIndex + 1,
                        """
                        ${Constants.JSONPARSER_CHECK_DESCRIPTOR.format(strRegister,strRegister)}
                        """.trimIndent(),
                    )
                }
            }

            enableSettings("hideSuggestedContent")
            addFlags("suggestedContentFlags")
        }
    }
