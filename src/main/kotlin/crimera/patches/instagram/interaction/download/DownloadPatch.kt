package crimera.patches.instagram.interaction.download

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.exception
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.instagram.interaction.download.fingerprints.*
import crimera.patches.instagram.interaction.download.fingerprints.DialogItemClickedFingerprint


@Patch(
    name = "Download patch",
    compatiblePackages = [CompatiblePackage("com.instagram.android")],
    requiresIntegrations = true
)
object DownloadPatch : BytecodePatch(
    setOf(
        DialogItemClickedFingerprint,
        MediaOptionsSheetFingerprint,
        FeedBottomSheetFingerprint,
        FeedItemIconsFingerprint,
        FeedItemClassFingerprint,
        FeedOptionItemIconClassNameHookFingerprint,
        FeedItemClassNameHookFingerprint
    )
) {
    val log = """
        const-string v0, "BRUH"
        const-string v1, "Sheet opened"
        
       invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    """

    private fun String.toJavaClass() = this.replace("/".toRegex(), ".").removePrefix("L").removeSuffix(";")

    private fun setFeedItemClassName() {
        val downloadPatchHooksClass = FeedItemClassNameHookFingerprint.result?.mutableMethod
            ?: throw FeedItemClassNameHookFingerprint.exception

        val feedItemClassName = FeedItemClassFingerprint.result?.classDef?.toString()?.toJavaClass()
            ?: throw FeedItemClassFingerprint.exception

        downloadPatchHooksClass.replaceInstruction(0, "const-string v0, \"$feedItemClassName\"")
    }

    private fun setFeedIconClassName() {
        val feedIconHookMethod = FeedOptionItemIconClassNameHookFingerprint.result?.mutableMethod
            ?: throw FeedOptionItemIconClassNameHookFingerprint.exception

        val feedItemIconsClassName = FeedItemIconsFingerprint.result?.classDef?.toString()?.toJavaClass()
            ?: throw FeedItemIconsFingerprint.exception

        feedIconHookMethod.replaceInstruction(0, "const-string v0, \"$feedItemIconsClassName\"")
    }

    override fun execute(context: BytecodeContext) {
        setFeedItemClassName()
        setFeedIconClassName()

//        For real media options sheet
        FeedBottomSheetFingerprint.result?.mutableMethod?.let { method ->
            val loc = method.getInstructions().filter { it.opcode == Opcode.GOTO }[2].location.index + 3
            method.addInstructions(
                loc,
//                TODO make the register dynamic
                """
                  invoke-static {v5}, Lapp/revanced/integrations/instagram/patches/DownloadPatch;->addDownloadButton(Ljava/util/List;)V
                """
            )
        } ?: throw FeedBottomSheetFingerprint.exception

        DialogItemClickedFingerprint.result?.mutableMethod?.let { method ->
            val loc = method.getInstructions().first { it.opcode == Opcode.PACKED_SWITCH }.location.index + 1

            method.replaceInstruction(
//                TODO should make registers dynamic
                method.getInstructions().filter { it.opcode == Opcode.CONST_4 }[1].location.index,
                "const/4 v0, 0x0"
            )

/*            Fingerprints
              2Tf: strings - "Music Overlay Not Found"

              only one fingerprint {
                3Pw: strings - "shouldShowFeedDelaySkipPill"
                is a function of 3Pw
                3Pw->Aj5:get the first function that returns an int and that has only one register
              }

              0wU: class have more than 2 fields, and has the method getDeviceSession
 */
            method.addInstructions(
                loc,
                """
                    const/16 v4, 0x8f

                    if-ne v2, v4, :cond_0

                    iget-object v5, v0, LX/Ikk;->A04:Landroidx/fragment/app/FragmentActivity;

                    iget-object v2, v0, LX/Ikk;->A0D:LX/2Tf;

                    iget-object v4, v0, LX/Ikk;->A0G:LX/3Pw;

                    invoke-virtual {v4}, LX/3Pw;->Aj5()I

                    move-result v4

                    iget-object v6, v0, LX/Ikk;->A09:LX/0wU;

                    invoke-static {v2, v4, v6, v5}, Lme/bluepapilte/DownloadPosts;->downloadSpecificPosts(Ljava/lang/Object;ILjava/lang/Object;Landroid/app/Activity;)V

                    return-void 
                """
            )

//            Log when a button is clicked
            method.addInstructions(
                0,
                log
            )

        } ?: throw DialogItemClickedFingerprint.exception
    }
}