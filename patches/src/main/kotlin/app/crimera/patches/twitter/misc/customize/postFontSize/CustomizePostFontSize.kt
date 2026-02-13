package app.crimera.patches.twitter.misc.customize.postFontSize

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private object CustomiseNavBarFingerprint : Fingerprint(
    definingClass = "TextContentView;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
)

@Suppress("unused")
val customizePostFontSize =
    bytecodePatch(
        name = "Customise post font size",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = CustomiseNavBarFingerprint.method

            val index =
                method
                    .instructions
                    .last { it.opcode == Opcode.MOVE_RESULT }
                    .location.index
            method.addInstruction(index + 1, "sget p1, $PREF_DESCRIPTOR;->POST_FONT_SIZE:F")
            SettingsStatusLoadFingerprint.enableSettings("customPostFontSize")
        }
    }
