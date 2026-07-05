/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.resourceMappingPatch

// Credits: brosssh
// https://github.com/brosssh/morphe-patches/commit/6a781ef8e0951ad5aa898fa17d094cfbfa5dd9fb

// The hash code of the field of interest. It is used as the key of a hashmap
internal const val notesTag = "enable_media_notes_production"
internal val hashedFieldInteger = notesTag.hashCode()

internal object FeedResponseMediaParserFingerprint : Fingerprint(
    filters =
        listOf(
            string(notesTag),
            literal(hashedFieldInteger),
        ),
    returnType = "Ljava/lang/Boolean;",
)

internal object LiveTreeGetOptionalBooleanFingerprint : Fingerprint(
    name = "getOptionalBooleanValueByHashCode",
    definingClass = "Lcom/instagram/pando/livetree/LiveTreeJNI;",
)

@Suppress("unused")
val hideReshareButtonPatch =
    bytecodePatch(
        name = "Hide reshare button",
        description = "Hides the reshare button from both posts and reels.",
    ) {
        dependsOn(settingsPatch, resourceMappingPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            val PREF_CALL = "$PREF_CALL_DESCRIPTOR->hideReshareButton()Z"

            FeedResponseMediaParserFingerprint.method.apply {
                addInstructionsWithLabels(
                    0,
                    """
                    $PREF_CALL
                    move-result v0
                    if-eqz v0, :piko
                    sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                    return-object v0
                    """.trimMargin(),
                    ExternalLabel("piko", getInstruction(0)),
                )
            }

            // If it's trying to get the value for our field of interest via the Pando native library,
            // force the value to false instead
            LiveTreeGetOptionalBooleanFingerprint.method.addInstructions(
                0,
                """
                const v0, $hashedFieldInteger
                if-ne p1, v0, :nopatch
                $PREF_CALL
                move-result v0
                if-eqz v0, :nopatch
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
                :nopatch
                nop
        """,
            )

            enableSettings("hideReshareButton")
        }
    }
