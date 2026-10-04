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
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object FeedItemParseFromJsonFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    returnType = "Ljava/lang/Object;",
    strings =
        listOf(
            "clips_netego",
            "stories_netego",
            "bloks_netego",
            "suggested_igd_channels",
            "suggested_top_accounts",
            "suggested_users",
        ),
    custom = { method, _ -> method.parameterTypes.size == 1 },
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

            FeedItemParseFromJsonFingerprint.matchAll(1..1).single().method.apply {
                val code = instructions
                val hashIndex = code.indexOfFirst {
                    it.getReference<MethodReference>()?.toString() == "Ljava/lang/String;->hashCode()I"
                }
                val keyIndex = (0 until hashIndex).lastOrNull { code[it].opcode == Opcode.MOVE_RESULT_OBJECT }
                    ?: throw PatchException("Missing feed JSON key read")
                val reader = code.getOrNull(keyIndex - 1)?.getReference<MethodReference>()
                val keyRegister = code[keyIndex].registersUsed.single()
                if (reader?.returnType != "Ljava/lang/String;" ||
                    code[hashIndex].registersUsed != listOf(keyRegister) || keyRegister > 15
                ) throw PatchException("Unexpected feed JSON key register")
                addInstructions(keyIndex + 1, Constants.JSONPARSER_CHECK_DESCRIPTOR.format(keyRegister, keyRegister))
            }

            enableSettings("hideSuggestedContent")
            addFlags("suggestedContentFlags")
        }
    }
