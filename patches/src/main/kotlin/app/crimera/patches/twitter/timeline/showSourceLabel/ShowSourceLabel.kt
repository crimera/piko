package app.crimera.patches.twitter.timeline.showSourceLabel

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.crimera.utils.extractDescriptors
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11n
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c

private object SourceLabelFingerprint : Fingerprint(
    filters = listOf(
        string("show_tweet_source_disabled")
    )
)

@Suppress("unused")
val showSourceLabel =
    bytecodePatch(
        name = "Show post source label",
        description = "Source label will be shown only on public posts",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            SourceLabelFingerprint.method.apply {

                val igetWide = instructions.first { it.opcode == Opcode.IGET_WIDE }
                val igetWideIns = igetWide as Instruction22c
                val idRef = igetWideIns.reference.extractDescriptors()[0]
                val idReg = igetWideIns.registerB

                val igetWideIndex = igetWide.location.index
                val igetWideP1Ins = instructions[igetWideIndex + 1] as Instruction35c
                val dummyReg1 = igetWideP1Ins.registerC
                val dummyReg2 = igetWideP1Ins.registerD

                val const4Ins = instructions[igetWideIndex - 1] as Instruction11n
                val const4Reg = const4Ins.registerA

                val apiMethod = "$PATCHES_DESCRIPTOR/tweet/TweetInfoAPI;"
                addInstructionsWithLabels(
                    igetWideIndex,
                    """
                    sget-boolean v$dummyReg1, $PREF_DESCRIPTOR;->SHOW_SRC_LBL:Z
                    if-eqz v$dummyReg1, :piko
                    invoke-virtual {v$idReg}, $idRef->getId()J
                    move-result-wide v$dummyReg1
                    
                    invoke-static {v$dummyReg1, v$dummyReg2}, $apiMethod->sendRequest(J)V
                    invoke-static {v$dummyReg1, v$dummyReg2}, $apiMethod->getTweetSource(J)Ljava/lang/String;
                    move-result-object v$const4Reg
                    """.trimIndent(),
                    ExternalLabel("piko", igetWide),
                )

                SettingsStatusLoadFingerprint.enableSettings("showSourceLabel")
            }
        }
    }
