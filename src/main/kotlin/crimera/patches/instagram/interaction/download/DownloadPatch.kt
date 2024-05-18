package crimera.patches.instagram.interaction.download

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.util.exception
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.instagram.interaction.download.fingerprints.DialogItemClickedFingerprint
import crimera.patches.instagram.interaction.download.fingerprints.FeedBottomSheetFingerprint
import crimera.patches.instagram.interaction.download.fingerprints.itemclickedclasses.DeviceSessionClassFingerprint
import crimera.patches.instagram.interaction.download.fingerprints.itemclickedclasses.MediaViewFingerprint
import crimera.patches.instagram.interaction.download.fingerprints.itemclickedclasses.PostMediaFingerprint
import crimera.patches.instagram.misc.integrations.IntegrationsPatch

@Patch(
        name = "Download patch",
        compatiblePackages = [CompatiblePackage("com.instagram.android")],
        requiresIntegrations = true,
        dependencies = [SetupHookSignaturesPatch::class, IntegrationsPatch::class]
)
object DownloadPatch :
        BytecodePatch(
                setOf(
                        DialogItemClickedFingerprint,
                        FeedBottomSheetFingerprint,
                        PostMediaFingerprint,
                        MediaViewFingerprint,
                        DeviceSessionClassFingerprint
                )
        ) {
    override fun execute(context: BytecodeContext) {
        FeedBottomSheetFingerprint.result?.mutableMethod?.let { method ->
            val loc =
                    method.getInstructions().filter { it.opcode == Opcode.GOTO }[2].location.index +
                            3
            method.addInstructions(
                    loc,
                    // TODO make the register dynamic
                    """
                  invoke-static {v5}, Lapp/revanced/integrations/instagram/patches/download/DownloadPatch;->addDownloadButton(Ljava/util/List;)V
                """
            )
        }
                ?: throw FeedBottomSheetFingerprint.exception

        DialogItemClickedFingerprint.result?.mutableMethod?.let { method ->
            val loc =
                    method.getInstructions()
                            .first { it.opcode == Opcode.PACKED_SWITCH }
                            .location
                            .index

            method.replaceInstruction(
                    // TODO should make registers dynamic
                    method.getInstructions().filter { it.opcode == Opcode.CONST_4 }[1]
                            .location
                            .index,
                    "const/4 v0, 0x0"
            )

            val itemClickedClass = method.definingClass
            val postMediaClass =
                    PostMediaFingerprint.result?.method?.definingClass
                            ?: throw PostMediaFingerprint.exception

            val mediaViewResult =
                    MediaViewFingerprint.result?.method ?: throw MediaViewFingerprint.exception
            val getMediaListSizeMethod =
                    MediaViewFingerprint.result?.classDef?.methods
                            ?.first {
                                it.returnType == "I" && it.implementation?.registerCount == 2
                            }
                            ?.name
                            ?: throw MediaViewFingerprint.exception

            val deviceSessionClass =
                    DeviceSessionClassFingerprint.result?.method?.definingClass
                            ?: throw DeviceSessionClassFingerprint.exception

            method.addInstructionsWithLabels(
                    loc,
                    """
                    const/16 v4, 0x60
                    if-ne v2, v4, :cond_0
                    
                    iget-object v2, v0, $itemClickedClass->A0D:$postMediaClass
                    
                    iget-object v4, v0, $itemClickedClass->A0G:${mediaViewResult.definingClass}
                    invoke-virtual {v4}, ${mediaViewResult.definingClass}->$getMediaListSizeMethod()I
                    move-result v4
                    
                    iget-object v6, v0, $itemClickedClass->A09:$deviceSessionClass
                    iget-object v5, v0, $itemClickedClass->A04:Landroidx/fragment/app/FragmentActivity;
                    
                    invoke-static {v2, v4, v6, v5}, Lapp/revanced/integrations/instagram/patches/download/DownloadPatch;->downloadPost(Ljava/lang/Object;ILjava/lang/Object;Landroid/app/Activity;)V
                    
                    const-string v0, "BRUH"
                    const-string v1, "Download clicked"
                    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

                    return-void 
                """,
                    ExternalLabel("cond_0", method.getInstruction(loc))
            )
        }
                ?: throw DialogItemClickedFingerprint.exception
    }
}

