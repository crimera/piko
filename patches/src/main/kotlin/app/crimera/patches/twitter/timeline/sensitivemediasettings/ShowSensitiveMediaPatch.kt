package app.crimera.patches.twitter.timeline.sensitivemediasettings

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

// Credits to @Cradlesofashes

private object sensitiveMediaSettingsPatchFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/json/core/JsonSensitiveMediaWarning\$\$JsonObjectMapper;",
    name = "parse",
    returnType = "Ljava/lang/Object",
)

@Suppress("unused")
val sensitiveMediaPatch =
    bytecodePatch(
        name = "Show sensitive media",
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
            SettingsStatusLoadFingerprint.enableSettings("showSensitiveMedia")
        }
    }
