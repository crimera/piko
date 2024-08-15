package crimera.patches.twitter.timeline.showpollresults

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val jsonCardInstanceDataFingerprint = fingerprint {
    strings("binding_values")
}

@Suppress("unused")
val showPollResultsPatch = bytecodePatch(
    name = "Show poll results",
    description = "Adds an option to show poll results without voting",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val result by jsonCardInstanceDataFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {

        val method = result.mutableMethod

        val loc = method.instructions.first { it.opcode == Opcode.MOVE_RESULT_OBJECT }.location.index

        val pollDescriptor =
            "invoke-static {p2}, ${PREF_DESCRIPTOR};->polls(Ljava/util/Map;)Ljava/util/Map;"

        method.addInstructions(
            loc + 1,
            """
                $pollDescriptor
                move-result-object p2
            """.trimIndent()
        )

        settingsStatusMatch.enableSettings("enableShowPollResults")
    }
}