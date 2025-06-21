package crimera.patches.twitter.timeline.hideCommunityBadge

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.or
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11x
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.misc.shareMenu.nativeDownloader.exception

object BadgeUIFingerprint : MethodFingerprint(
    returnType = "Ljava/lang/Object;",
    customFingerprint = { methodDef, classDef ->
        classDef.type.contains("/tweetview/core/ui/badge") &&
            methodDef.name == "invoke"
    },
    opcodes =
        listOf(
            Opcode.CHECK_CAST,
            Opcode.INVOKE_STATIC,
            Opcode.IGET_OBJECT,
        ),
    accessFlags = AccessFlags.PUBLIC or AccessFlags.FINAL,
)

@Patch(
    name = "Hide community badges",
    description = "",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    requiresIntegrations = true,
)
@Suppress("unused")
object HideCommunityBadge : BytecodePatch(
    setOf(SettingsStatusLoadFingerprint, BadgeUIFingerprint),
) {
    override fun execute(context: BytecodeContext) {
        val result = BadgeUIFingerprint.result ?: throw BadgeUIFingerprint.exception

        val method = result.mutableMethod

        println(result.classDef)

        val instructions = method.getInstructions()

        val moveResObj = instructions.first { it.opcode == Opcode.MOVE_RESULT }
        val moveResIns = moveResObj as Instruction11x
        val reg = moveResIns.registerA
        val index = moveResObj.location.index + 1

        method.addInstruction(
            index,
            "sget-boolean v$reg, ${SettingsPatch.PREF_DESCRIPTOR};->HIDE_COMM_BADGE:Z",
        )

        SettingsStatusLoadFingerprint.enableSettings("hideCommBadge")
    }
}
