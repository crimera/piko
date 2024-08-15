package crimera.patches.twitter.misc.nativeDownloader

import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import app.revanced.patches.shared.misc.mapping.resourceMappingPatch
import app.revanced.patches.shared.misc.mapping.resourceMappings
import app.revanced.patches.shared.misc.mapping.get
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import crimera.patches.twitter.misc.settings.PATCHES_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal var fileIcomingIcon = -1L
    private set

private val nativeDownloaderResourcePatch = resourcePatch {
    dependsOn(resourceMappingPatch)

    execute {
        fileIcomingIcon = resourceMappings["drawable", "ic_vector_incoming"]
    }
}

@Suppress("unused")
val nativeDownloaderPatch = bytecodePatch(
    name = "Custom downloader",
) {
    dependsOn(settingsPatch, nativeDownloaderResourcePatch)
    compatibleWith("com.twitter.android")

    val nativeDownloaderFingerprintMatch by nativeDownloaderPatchFingerprint()
    val nativeDownloaderAlwaysIconMatch by nativeDownloaderAlwaysIconFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val DD = "${PATCHES_DESCRIPTOR}/NativeDownloader;"
        //one click func
        var strLoc: Int = 0
        nativeDownloaderFingerprintMatch.stringMatches?.forEach { match ->
            val str = match.string
            if (str.contains("tweetview?id=")) {
                strLoc = match.index
                return@forEach
            }
        }
        if (strLoc == 0) {
            throw PatchException("hook not found")
        }

        val method = nativeDownloaderFingerprintMatch.mutableMethod
        val instructions = method.instructions

        //inject func
        val inv_vir_ran_loc =
            instructions.filter { it.opcode == Opcode.INVOKE_VIRTUAL_RANGE && it.location.index > strLoc }[0].location.index
        val inv_vir_ran_reg = method.getInstruction<BuilderInstruction3rc>(inv_vir_ran_loc).startRegister

        val postObj = method.getInstruction<TwoRegisterInstruction>(strLoc + 2)
        val postObjReg = postObj.registerA
        val ctxReg = postObj.registerB

        method.addInstructions(
            strLoc + 3, """
            invoke-virtual/range{v$inv_vir_ran_reg .. v$inv_vir_ran_reg}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
            move-result-object v$ctxReg
            check-cast v$ctxReg, Landroid/app/Activity;
            invoke-static {v$ctxReg, v$postObjReg}, $DD->downloader(Landroid/content/Context;Ljava/lang/Object;)V
        """.trimIndent()
        )

        val filters = instructions.first { it.opcode == Opcode.GOTO_16 && it.location.index > strLoc }
        method.removeInstruction(filters.location.index - 1)


        val method2 = nativeDownloaderAlwaysIconMatch.mutableMethod

        //text func
        nativeDownloaderAlwaysIconMatch.stringMatches?.forEach { match ->
            val str = match.string
            if (str.contains("View in Tweet Sandbox")) {
                var loc = match.index
                var r = method2.getInstruction<OneRegisterInstruction>(loc).registerA
                method2.addInstructions(
                    loc + 1, """
                    invoke-static {},$DD->downloadString()Ljava/lang/String;
                    move-result-object v$r
                """.trimIndent()
                )
                return@forEach
            }
        }


        val allMethods = nativeDownloaderAlwaysIconMatch.mutableClass.methods
        val method3 = allMethods.first { it.returnType == "V" }
        val instructions3 = method3.instructions

        //icon
        val const_4 = instructions3.filter { it.opcode == Opcode.CONST }
        val loc = const_4[const_4.size - 7].location.index
        var r = method3.getInstruction<OneRegisterInstruction>(loc).registerA
        method3.addInstruction(
            loc + 1, """
            const v$r, $fileIcomingIcon
        """.trimIndent()
        )

        //show icon always
        val method4 = allMethods.first { it.name == "a" }
        val instructions4 = method4.instructions
        val ins_of = instructions4.first { it.opcode == Opcode.INSTANCE_OF }.location.index
        method4.removeInstruction(ins_of - 1)

        settingsStatusMatch.enableSettings("nativeDownloader")
    }
}