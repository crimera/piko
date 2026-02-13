package app.crimera.patches.twitter.misc.customize.notificationtabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private object CustomiseNotificationTabsFingerprint : Fingerprint(
    strings = listOf(
        "android_ntab_verified_tab_enabled",
        "all",
        "verified",
        "super_followers",
    )
)

@Suppress("unused")
val customiseNotificationTabsPatch =
    bytecodePatch(
        name = "Customize notification tabs",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            CustomiseNotificationTabsFingerprint.method.apply {
                val strIndex =
                    instructions
                        .first {
                            it.opcode == Opcode.CONST_STRING &&
                                it.getReference<StringReference>()?.string == "mentions"
                        }.location.index

                val index = indexOfFirstInstruction(strIndex, Opcode.CHECK_CAST)

                val reg = (getInstruction(index) as OneRegisterInstruction).registerA

                addInstructions(
                    index + 1,
                    """
                    invoke-static {v$reg}, $CUSTOMISE_DESCRIPTOR;->notificationTabs(Ljava/util/List;)Ljava/util/List;
                    move-result-object v$reg
                    """.trimIndent(),
                )
                SettingsStatusLoadFingerprint.enableSettings("notificationTabCustomisation")
            }
        }
    }
