package app.crimera.patches.instagram.misc.stories.viewstorymention

import app.crimera.patches.instagram.entity.dialogbox.instagramDialogBoxPatch
import app.crimera.patches.instagram.entity.mediadata.mediaDataPatch
import app.crimera.patches.instagram.entity.userdata.userDataPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.misc.stories.AddStoryButtonFingerprint
import app.crimera.patches.instagram.misc.stories.handleStoryButtonPatch
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val viewStoryMentionsPatch =
    bytecodePatch(
        name = "View story mentions",
        description = "Add option to view visible and hidden story mentions.",
    ) {
        dependsOn(settingsPatch, handleStoryButtonPatch, userDataPatch, mediaDataPatch, instagramDialogBoxPatch)

        compatibleWith("com.instagram.android")

        execute {

            AddStoryButtonFingerprint.method.apply {
                val mediaObjectFieldName = instructions.filter { it.opcode == Opcode.IGET_OBJECT }[1].fieldExtractor().name
                GetMediaObjectFromReelItemExtensionFingerprint.changeFirstString(mediaObjectFieldName)
            }
            enableSettings("viewStoryMentions")
        }
    }
