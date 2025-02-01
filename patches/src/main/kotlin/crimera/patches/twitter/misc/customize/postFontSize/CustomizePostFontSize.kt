package crimera.patches.twitter.misc.customize.postFontSize

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.*
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val customizePostFontSizeFingerprint =
    fingerprint {
        accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
        opcodes(Opcode.NEW_INSTANCE)
        custom { it, _ ->
            it.definingClass.endsWith("TextContentView;")
        }
    }

@Suppress("unused")
val customiseExploreTabsPatch =
    bytecodePatch(
        name = "Customise post font size",
        use = true,
    ) {
        execute {
            compatibleWith("com.twitter.android")
            dependsOn(settingsPatch)

            val result1 by customizePostFontSizeFingerprint()

            val method1 = result1.mutableMethod

            val index1 =
                method1
                    .instructions
                    .last { it.opcode == Opcode.MOVE_RESULT }
                    .location.index
            method1.addInstruction(index1 + 1, "sget p1, ${PREF_DESCRIPTOR};->POST_FONT_SIZE:F")

//        SettingsStatusLoadFingerprint.enableSettings("customPostFontSize")
            val settingsStatusMatch by settingsStatusLoadFingerprint()
            settingsStatusMatch.enableSettings("customPostFontSize")
        }
    }
