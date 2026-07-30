/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.sensitivemediasettings

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.value.ArrayEncodedValue
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue

// Credits to @Cradlesofashes

private object sensitiveMediaSettingsPatchFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/json/core/JsonTweetWithVisibilityResults\$\$JsonObjectMapper;",
    name = "parse",
    returnType = "Ljava/lang/Object",
)

@Suppress("unused")
val sensitiveMediaPatch =
    bytecodePatch(
        name = "Show sensitive media",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val TIMELINE_ENTRY_DESCRIPTOR = "$PATCHES_DESCRIPTOR/TimelineEntry"

            val methods = sensitiveMediaSettingsPatchFingerprint.method
            val instructions = methods.instructions

            val returnObj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

            val jsonClass = classDefBy {
                it.type == "Lcom/tweet/model/json/core/JsonTestWithVisibilityResults;"
            }!!

            val mediaVisibilityField = jsonClass.fields.first { field ->
                field.annotations.any { annotation ->
                    annotation.type.endsWith("JsonField;") &&
                    annotation.elements.any { element ->
                        element.name == "name" &&
                        (element.value as? ArrayEncodedValue)?.value?.any {
                            (it as? StringEncodedValue)?.value == "media_visibility_results"
                        } == true
                    }
                }
            }

            val fieldName = mediaVisibilityField.name

            methods.addInstructions(
                returnObj,
                """
                const-string v0, "$fieldName"
                invoke-static {p1, v0}, $TIMELINE_ENTRY_DESCRIPTOR;->sensitiveMedia(Lcom/twitter/model/json/core/JsonTestWithVisibilityResults;Ljava/lang/String;)Lcom/twitter/model/json/core/JsonTestWithVisibilityResults;
                move-result-object p1
                """.trimIndent(),
            )
            enableSettings("showSensitiveMedia")
        }
    }
