package app.crimera.patches.twitter.timeline.showpollresults

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

internal val jsonCardInstanceDataFingerprint =
    fingerprint {
        strings(
            "binding_values",
        )

        custom { methodDef, classDef ->
            methodDef.name == "parseField" && classDef.type.endsWith("JsonCardInstanceData\$\$JsonObjectMapper;")
        }
    }

@Suppress("unused")
val showPollResultsPatch =
    bytecodePatch(
        name = "Show poll results",
        description = "Adds an option to show poll results without voting",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = jsonCardInstanceDataFingerprint.method

            val loc =
                method.instructions
                    .first { it.opcode == Opcode.MOVE_RESULT_OBJECT }
                    .location.index

            val pollDescriptor =
                "invoke-static {p2}, $PREF_DESCRIPTOR;->polls(Ljava/util/Map;)Ljava/util/Map;"

            method.addInstructions(
                loc + 1,
                """
                $pollDescriptor
                move-result-object p2
                """.trimIndent(),
            )

            settingsStatusLoadFingerprint.enableSettings("enableShowPollResults")
        }
    }
