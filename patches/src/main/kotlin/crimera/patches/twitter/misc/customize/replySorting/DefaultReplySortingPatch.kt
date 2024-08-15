package crimera.patches.twitter.misc.customize.replySorting

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import crimera.patches.twitter.misc.customize.timelinetabs.replySortingInvokeClassFinderFingerprint
import crimera.patches.twitter.misc.customize.timelinetabs.replySortingLastSelectedFinderFingerprint
import crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

// TODO: make a separate extensions package
@Suppress("unused")
val defaultReplySortingPatch = bytecodePatch(
    name = "Customize reply sort filter",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val result by replySortingInvokeClassFinderFingerprint()
    val result2 by replySortingLastSelectedFinderFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute { context ->
        val replySortingInvokeClass = result.classDef.fields.first().type
        val method = context.classByType(replySortingInvokeClass)?.mutableClass?.directMethods?.first()
            ?: throw PatchException("Reply sorting failed")
        val instructions = method.instructions
        val loc = instructions.first { it.opcode == Opcode.SGET_OBJECT }.location.index
        val rClass = (method.getInstruction<ReferenceInstruction>(loc).reference as FieldReference).definingClass
        val r0 = method.getInstruction<OneRegisterInstruction>(loc).registerA
        method.addInstructions(
            loc + 1,
            """
                invoke-static {}, ${PREF_DESCRIPTOR};->defaultReplySortFilter()Ljava/lang/String;
                move-result-object v$r0
                invoke-static{v0}, $rClass->valueOf(Ljava/lang/String;)$rClass
                move-result-object v$r0
            """.trimIndent()
        )

        val method2 = result2.mutableMethod
        result2.stringMatches?.forEach { match ->
            val str = match.string
            if (str.equals("reply_sorting")) {
                var loc = match.index
                var r = method2.getInstruction<OneRegisterInstruction>(loc - 1).registerA
                method2.addInstructions(
                    loc, """
                    invoke-static {v$r},${PREF_DESCRIPTOR};->setReplySortFilter(Ljava/lang/String;)V
                """.trimIndent()
                )
                return@forEach
            }
        } ?: throw PatchException("Could not find method2")

        settingsStatusMatch.enableSettings("defaultReplySortFilter")
    }
}