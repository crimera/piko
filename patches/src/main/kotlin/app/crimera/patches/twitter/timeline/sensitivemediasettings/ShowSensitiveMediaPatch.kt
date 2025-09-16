package app.crimera.patches.twitter.timeline.sensitivemediasettings

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

// Credits to @Cradlesofashes

internal val sensitiveMediaSettingsPatchFingerprint =
    fingerprint {
        returns("Ljava/lang/Object")

        custom { it, _ ->
            it.definingClass == "Lcom/twitter/model/json/core/JsonSensitiveMediaWarning\$\$JsonObjectMapper;" && it.name == "parse"
        }
    }

@Suppress("unused")
val sensitiveMediaPatch =
    bytecodePatch(
        name = "Show sensitive media",
        description = "Shows sensitive media",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val TIMELINE_ENTRY_DESCRIPTOR = "$PATCHES_DESCRIPTOR/TimelineEntry"

            val methods = sensitiveMediaSettingsPatchFingerprint.method
            val instructions = methods.instructions

            val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

            methods.addInstructions(
                returnObj,
                """
                invoke-static {p1}, $TIMELINE_ENTRY_DESCRIPTOR;->sensitiveMedia(Lcom/twitter/model/json/core/JsonSensitiveMediaWarning;)Lcom/twitter/model/json/core/JsonSensitiveMediaWarning;
                move-result-object p1
                """.trimIndent(),
            )
            settingsStatusLoadFingerprint.method.enableSettings("showSensitiveMedia")
        }
    }
