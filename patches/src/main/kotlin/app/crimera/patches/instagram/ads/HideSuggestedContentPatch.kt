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
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.registersUsed

internal object FeedItemParseFromJsonFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
    strings =
        listOf(
            "clips_netego",
            "stories_netego",
            "bloks_netego",
            "suggested_igd_channels",
            "suggested_top_accounts",
            "suggested_users",
        ),
    filters = listOf(
        methodCall(smali = "Ljava/lang/String;->hashCode()I"),
    ),
)

@Suppress("unused")
val hideSuggestedContentPatch =
    patch(
        name = "Hide suggested content",
        description = "Hides suggested stories, reels, threads (Suggested posts will still be shown).",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {

            FeedItemParseFromJsonFingerprint.let {
                val keyHash = it.instructionMatches.single()
                val key = keyHash.instruction.registersUsed.single()
                it.method.addInstructions(keyHash.index, Constants.JSONPARSER_CHECK_DESCRIPTOR.format(key, key))
            }

            enableSettings("hideSuggestedContent")
            addFlags("suggestedContentFlags")
        }
    }
