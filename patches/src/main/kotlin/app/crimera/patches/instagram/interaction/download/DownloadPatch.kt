package app.crimera.patches.instagram.interaction.download

import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "Lapp/revanced/extension/instagram/patches/DownloadPatch;"

val downloadPatch = bytecodePatch(
    name = "Download patch",
    description = "Adds the ability to download media",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith("com.instagram.android")

    execute {
        feedBottomSheet.method.apply {
            val targetInst = instructions.last { it.opcode == Opcode.RETURN_VOID }
            // val loc = targetInst.location.index
						val loc = instructions.filter { it.opcode == Opcode.GOTO }[2].location.index + 3

            addInstructionsWithLabels(
                loc,
                """
                invoke-virtual/range {v18 .. v18}, Landroidx/fragment/app/Fragment;->requireContext()Landroid/content/Context;
                move-result-object v12

                invoke-static {v12, v9}, $EXTENSION_CLASS->addDownloadButton(Landroid/content/Context;Ljava/lang/Object;)V
                """.trimIndent(),
                ExternalLabel("original_invoke", targetInst)
            )
        }
    }
}
