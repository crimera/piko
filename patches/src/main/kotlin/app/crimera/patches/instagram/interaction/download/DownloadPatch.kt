package app.crimera.patches.instagram.interaction.download

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.revanced.patcher.patch.bytecodePatch
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

            val loc = instructions
                .last { it.opcode == Opcode.INVOKE_STATIC }
                .location
                .index

            addInstructions(
                loc + 1,
                """
                    invoke-virtual/range {v18 .. v18}, Landroidx/fragment/app/Fragment;->requireContext()Landroid/content/Context;
                    move-result-object v12

                    invoke-static {v12, v9}, $EXTENSION_CLASS->addDownloadButton(Landroid/content/Context;Ljava/lang/Object;)V
                """.trimIndent(),
            )
        }
    }
}
