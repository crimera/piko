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
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.matchSingle
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

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
        methodCall(returnType = "Ljava/lang/String;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        methodCall(smali = "Ljava/lang/String;->hashCode()I", location = MatchAfterImmediately()),
    ),
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

            FeedItemParseFromJsonFingerprint.matchSingle().let { match ->
                val keyIndex = match.instructionMatches[1].index
                val keyRegister = match.instructionMatches[1].getInstruction<OneRegisterInstruction>().registerA
                match.method.addInstructions(keyIndex + 1, Constants.JSONPARSER_CHECK_DESCRIPTOR.format(keyRegister, keyRegister))
            }

            enableSettings("hideSuggestedContent")
            addFlags("suggestedContentFlags")
        }
    }
