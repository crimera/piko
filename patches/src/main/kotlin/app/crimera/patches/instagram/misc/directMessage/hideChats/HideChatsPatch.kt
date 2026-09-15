package app.crimera.patches.instagram.misc.directMessage.hideChats

import app.crimera.patches.instagram.misc.userProfile.userProfileButtonPatch
import app.crimera.patches.instagram.misc.extension.hooks.instagramInitHook
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

private const val HOOK = "$PATCHES_DESCRIPTOR/dm/HiddenChats;"

@Suppress("unused")
val hideChatsPatch = bytecodePatch(
    name = "Hide chats",
    description = "Adds a button on a user's profile to hide their DM conversation from the inbox, and restore it from Piko settings.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(settingsPatch, userProfileButtonPatch)
    execute {
        instagramInitHook.fingerprint.method.apply {
            val idx = indexOfFirstInstruction(Opcode.INVOKE_SUPER)
            addInstruction(idx + 1, "invoke-static {}, $HOOK->init()V")
        }
        enableSettings("hiddenChats")
    }
}
