package app.crimera.patches.twitter.misc.customize.replySorting

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private object ReplySortingInvokeClassFinderFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/tweetview/focal/ui/replysorting/ReplySortingViewDelegateBinder;"
)

private object replySortingLastSelectedFinderFingerprint : Fingerprint(
    strings = listOf(
        "controller_data",
        "reply_sorting_enabled",
        "reply_sorting",
    )
)

@Suppress("unused")
val defaultReplySortingPatch =
    bytecodePatch(
        name = "Customize default reply sorting",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val replySortingInvokeClass =
                ReplySortingInvokeClassFinderFingerprint.classDef.fields
                    .first()
                    .type
            val method = mutableClassDefBy(replySortingInvokeClass).methods.first()
            val instructions = method.instructions
            val loc = instructions.first { it.opcode == Opcode.SGET_OBJECT }.location.index
            val rClass = (method.getInstruction<ReferenceInstruction>(loc).reference as FieldReference).definingClass
            val r0 = method.getInstruction<OneRegisterInstruction>(loc).registerA
            method.addInstructions(
                loc + 1,
                """
                invoke-static {}, $PREF_DESCRIPTOR;->defaultReplySortFilter()Ljava/lang/String;
                move-result-object v$r0
                invoke-static{v0}, $rClass->valueOf(Ljava/lang/String;)$rClass
                move-result-object v$r0
                """.trimIndent(),
            )

            val method2 = replySortingLastSelectedFinderFingerprint.method
            val inst = method2.instructions

            inst
                .filter { it.opcode == Opcode.CONST_STRING }
                .first { it.getReference<StringReference>()?.string == "reply_sorting" }
                .apply {
                    var loc = location.index
                    var r = method2.getInstruction<OneRegisterInstruction>(loc - 1).registerA
                    method2.addInstructions(
                        loc,
                        """
                        invoke-static {v$r},$PREF_DESCRIPTOR;->setReplySortFilter(Ljava/lang/String;)V
                        """.trimIndent(),
                    )
                }
            SettingsStatusLoadFingerprint.enableSettings("defaultReplySortFilter")
        }
    }
