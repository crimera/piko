package app.crimera.patches.twitter.misc.customize.timelinetabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.Fingerprint
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.util.indexOfFirstInstruction
import app.revanced.util.indexOfFirstLiteralInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c

private val customiseTimelineTabsLegacyFingerprint =
    fingerprint {
        returns("V")
        strings(
            "selectedTabStateRepo",
            "pinnedTimelinesRepo",
            "releaseCompletable",
        )
        opcodes(Opcode.CONST_16)
    }

private val customiseTimelineTabsNewerFingerprint =
    fingerprint {
        returns("Ljava/lang/Object;")
        parameters("Ljava/lang/Object;")
        strings(
            "it",
        )
        custom { method, classDef ->
            method.name == "invoke" && classDef.type.contains("/tabbed/pinnedtimelines")
        }
    }

@Suppress("unused")
val customiseTimelineTabsPatch =
    bytecodePatch(
        name = "Customize timeline top bar",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            fun Fingerprint.invoke() {
                var c11 = method.indexOfFirstLiteralInstruction(0x11L)

                val c = method.indexOfFirstInstruction(c11, Opcode.CONST_16)
                val v4 = method.getInstruction<OneRegisterInstruction>(c).registerA
                val v5 = method.getInstruction<OneRegisterInstruction>(c + 1).registerA

                val arrLoc = method.indexOfFirstInstruction(c11, Opcode.FILLED_NEW_ARRAY)
                val r = method.getInstruction<Instruction35c>(arrLoc)
                val r3 = r.registerC
                val r11 = r.registerD

                method.addInstructionsWithLabels(
                    arrLoc,
                    """
                    invoke-static {}, $PREF_DESCRIPTOR;->timelineTab()I
                    move-result v$v4
                    
                    const/16 v$v5, 0x1
                    if-ne v$v4,v$v5, :no
                    move-object v$r3,v$r11
                    goto :escape
                    :no
                    const/16 v$v5, 0x2
                    if-ne v$v4,v$v5, :escape
                    move-object v$r11,v$r3
                    goto :escape
                    """.trimIndent(),
                    ExternalLabel("escape", method.instructions[arrLoc]),
                )
                settingsStatusLoadFingerprint.enableSettings("timelineTabCustomisation")
            }

            try {
                customiseTimelineTabsLegacyFingerprint.invoke()
            } catch (_: Exception) {
                // Fallback - since 11.46.xx
                customiseTimelineTabsNewerFingerprint.invoke()
            }
        }
    }
