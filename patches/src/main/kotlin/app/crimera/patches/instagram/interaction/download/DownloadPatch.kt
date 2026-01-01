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
            addInstructions(
                0,
                """
                invoke-static {}, $EXTENSION_CLASS->addDownloadButton()V
                """.trimIndent(),
            )
        }
    }
}
