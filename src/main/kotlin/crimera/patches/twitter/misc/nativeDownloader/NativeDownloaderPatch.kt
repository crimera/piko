package crimera.patches.twitter.misc.nativeDownloader

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import crimera.patches.twitter.misc.nativeDownloader.fingerprints.NativeDownloaderAlwaysIcon
import crimera.patches.twitter.misc.nativeDownloader.fingerprints.NativeDownloaderResourcePatch
import crimera.patches.twitter.misc.nativeDownloader.fingerprints.NativeDownloaderPatchFingerprint
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint


@Patch(
    name = "Custom downloader",
    description = "",
    dependencies = [SettingsPatch::class,NativeDownloaderResourcePatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true
)
@Suppress("unused")
object NativeDownloaderPatch: BytecodePatch(
    setOf(NativeDownloaderPatchFingerprint, NativeDownloaderAlwaysIcon,SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = NativeDownloaderPatchFingerprint.result
            ?: throw PatchException("NativeDownloaderPatchFingerprint not found")

        val DD = "${SettingsPatch.PATCHES_DESCRIPTOR}/NativeDownloader;"
        //one click func
        var strLoc: Int = 0
        result.scanResult.stringsScanResult!!.matches.forEach{ match ->
            val str = match.string
            if(str.contains("tweetview?id=")){
                strLoc = match.index
                return@forEach
            }
        }
        if(strLoc==0){
            throw PatchException("hook not found")
        }

        val method = result.mutableMethod
        val instructions = method.getInstructions()

        //inject func
        val inv_vir_ran_loc = instructions.filter { it.opcode == Opcode.INVOKE_VIRTUAL_RANGE && it.location.index > strLoc}[0].location.index
        val inv_vir_ran_reg = method.getInstruction<BuilderInstruction3rc>(inv_vir_ran_loc).startRegister

        val postObj = method.getInstruction<TwoRegisterInstruction>(strLoc+2)
        val postObjReg = postObj.registerA
        val ctxReg = postObj.registerB

        method.addInstructions(strLoc+3,"""
            invoke-virtual/range{v$inv_vir_ran_reg .. v$inv_vir_ran_reg}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
            move-result-object v$ctxReg
            check-cast v$ctxReg, Landroid/app/Activity;
            invoke-static {v$ctxReg, v$postObjReg}, $DD->downloader(Landroid/content/Context;Ljava/lang/Object;)V
        """.trimIndent())

        val filters = instructions.first{ it.opcode == Opcode.GOTO_16 && it.location.index>strLoc}
        method.removeInstruction(filters.location.index-1)



        val result2 = NativeDownloaderAlwaysIcon.result
            ?: throw PatchException("DebugMenuFingerprint not found")

        val method2 = result2.mutableMethod

        //text func
        result2.scanResult.stringsScanResult!!.matches.forEach{ match ->
            val str = match.string
            if(str.contains("View in Tweet Sandbox")){
                var loc = match.index
                var r = method2.getInstruction<OneRegisterInstruction>(loc).registerA
                method2.addInstructions(loc+1,"""
                    invoke-static {},$DD->downloadString()Ljava/lang/String;
                    move-result-object v$r
                """.trimIndent())
                return@forEach
            }
        }


        val allMethods = result2.mutableClass.methods
        val method3 = allMethods.first { it.returnType == "V" }
        val instructions3 = method3.getInstructions()

            //icon
        val const_4 = instructions3.filter { it.opcode == Opcode.CONST }
        val loc = const_4[const_4.size-7].location.index
        var r = method3.getInstruction<OneRegisterInstruction>(loc).registerA
        method3.addInstruction(loc+1,"""
            const v$r, ${NativeDownloaderResourcePatch.fileIcomingIcon}
        """.trimIndent())

        //show icon always
        val method4 = allMethods.first { it.name == "a" }
        val instructions4 = method4.getInstructions()
        val ins_of = instructions4.first { it.opcode == Opcode.INSTANCE_OF }.location.index
        method4.removeInstruction(ins_of-1)



        SettingsStatusLoadFingerprint.enableSettings("nativeDownloader")
    }
}