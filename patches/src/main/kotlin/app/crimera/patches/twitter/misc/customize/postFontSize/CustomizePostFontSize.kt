package app.crimera.patches.twitter.misc.customize.postFontSize

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private val customiseNavBarFingerprint =
    fingerprint {
        accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
        custom { methodDef, _ ->
            methodDef.definingClass.endsWith("TextContentView;")
        }
    }

@Suppress("unused")
val customizePostFontSize =
    bytecodePatch(
        name = "Customise post font size",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = customiseNavBarFingerprint.method

            val index =
                method
                    .instructions
                    .last { it.opcode == Opcode.MOVE_RESULT }
                    .location.index
            method.addInstruction(index + 1, "sget p1, $PREF_DESCRIPTOR;->POST_FONT_SIZE:F")
            settingsStatusLoadFingerprint.enableSettings("customPostFontSize")
        }
    }
