/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.ads

import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal object DisableAdsFingerprint : Fingerprint(
    strings = listOf("Is ad pod"),
)

internal object ContextualFeedFilterFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(string("feed_contextual_self_profile")),
)

internal object FeedAdInsertionFingerprint : Fingerprint(
    returnType = "Z",
    filters = listOf(
        string("instagram_ad_async_ad_controller_action_success"),
        string("timeline_request"),
    ),
)

@Suppress("unused")
val disableAdsPatch =
    bytecodePatch(
        name = "Disable ads",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            settingsPatch,
            hookFlagsPatch,
        )
        execute {

            DisableAdsFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                    ${Constants.PREF_CALL_DESCRIPTOR}->disableAds()Z
                    move-result v0
                    return v0
                    """.trimIndent(),
                )
            }

            val mediaType = "Lcom/instagram/feed/media/Media;"
            val isAd = FeedAdInsertionFingerprint.matchAll(1..1).single().method.instructions
                .filter { it.opcode == Opcode.INVOKE_VIRTUAL || it.opcode == Opcode.INVOKE_VIRTUAL_RANGE }
                .mapNotNull { it.getReference<MethodReference>() }
                .filter { it.definingClass == mediaType && it.parameterTypes.isEmpty() && it.returnType == "Z" }
                .distinctBy { it.toString() }
                .singleOrNull() ?: throw PatchException("Missing unique feed ad predicate")

            // Following can receive ads in the response itself, bypassing the injection hook above.
            ContextualFeedFilterFingerprint.matchAll(1..1).single().method.apply {
                val code = instructions
                val cast = code.first()
                val itemType = cast.getReference<TypeReference>()?.type
                val parameterRegister = implementation!!.registerCount - 1
                if (AccessFlags.STATIC.isSet(accessFlags) || implementation!!.registerCount < 3 ||
                    cast.opcode != Opcode.CHECK_CAST || cast.registersUsed != listOf(parameterRegister) ||
                    itemType == null
                ) throw PatchException("Unexpected contextual feed item cast")

                val getMedia = code
                    .filter { it.opcode == Opcode.INVOKE_VIRTUAL || it.opcode == Opcode.INVOKE_VIRTUAL_RANGE }
                    .mapNotNull { it.getReference<MethodReference>() }
                    .filter { it.definingClass == itemType && it.parameterTypes.isEmpty() && it.returnType == mediaType }
                    .distinctBy { it.toString() }
                    .singleOrNull() ?: throw PatchException("Missing unique contextual feed media getter")

                addInstructionsWithLabels(
                    1,
                    """
                    ${Constants.PREF_CALL_DESCRIPTOR}->disableAds()Z
                    move-result v0
                    if-eqz v0, :piko_original
                    if-eqz p1, :piko_original
                    invoke-virtual/range {p1 .. p1}, $getMedia
                    move-result-object v0
                    if-eqz v0, :piko_original
                    invoke-virtual {v0}, $isAd
                    move-result v0
                    if-eqz v0, :piko_original
                    const/4 v0, 0x0
                    return v0
                    """.trimIndent(),
                    ExternalLabel("piko_original", code[1]),
                )
            }

            enableSettings("disableAds")
            addFlags("adsFlags")
        }
    }
