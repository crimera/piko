package app.crimera.patches.twitter.misc.customize.replySorting

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private val replySortingInvokeClassFinderFingerprint =
    fingerprint {
        custom { it, _ ->
            it.definingClass == "Lcom/twitter/tweetview/focal/ui/replysorting/ReplySortingViewDelegateBinder;"
        }
    }

private val replySortingLastSelectedFinderFingerprint =
    fingerprint {
        strings(
            "controller_data",
            "reply_sorting_enabled",
            "reply_sorting",
        )
    }

@Suppress("unused")
val defaultReplySortingPatch =
    bytecodePatch(
        name = "Customize default reply sorting",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val replySortingInvokeClass =
                replySortingInvokeClassFinderFingerprint.classDef.fields
                    .first()
                    .type
            val method = classBy { it.type == replySortingInvokeClass }!!.mutableClass.methods.first()
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
            settingsStatusLoadFingerprint.enableSettings("defaultReplySortFilter")
        }
    }
