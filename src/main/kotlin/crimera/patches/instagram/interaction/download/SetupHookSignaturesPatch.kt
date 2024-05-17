package crimera.patches.instagram.interaction.download

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.util.exception
import crimera.patches.instagram.interaction.download.fingerprints.FeedItemClassFingerprint
import crimera.patches.instagram.interaction.download.fingerprints.FeedItemIconsFingerprint
import crimera.patches.instagram.interaction.download.fingerprints.hook.FeedItemClassNameHookFingerprint
import crimera.patches.instagram.interaction.download.fingerprints.hook.FeedOptionItemIconClassNameHookFingerprint

object SetupHookSignaturesPatch : BytecodePatch(
    setOf(
        FeedItemClassNameHookFingerprint,
        FeedItemClassFingerprint,
        FeedOptionItemIconClassNameHookFingerprint,
        FeedItemIconsFingerprint
    )
) {
    private fun String.toClassName() =
        this.replace("/".toRegex(), ".").removePrefix("L").removeSuffix(";")

    private fun setFeedItemClassName() {
        val downloadPatchHooksClass = FeedItemClassNameHookFingerprint.result?.mutableMethod
            ?: throw FeedItemClassNameHookFingerprint.exception

        val feedItemClassName = FeedItemClassFingerprint.result?.classDef?.toString()?.toClassName()
            ?: throw FeedItemClassFingerprint.exception

        downloadPatchHooksClass.replaceInstruction(0, "const-string v0, \"$feedItemClassName\"")
    }

    private fun setFeedIconClassName() {
        val feedIconHookMethod = FeedOptionItemIconClassNameHookFingerprint.result?.mutableMethod
            ?: throw FeedOptionItemIconClassNameHookFingerprint.exception

        val feedItemIconsClassName = FeedItemIconsFingerprint.result?.classDef?.toString()?.toClassName()
            ?: throw FeedItemIconsFingerprint.exception

        feedIconHookMethod.replaceInstruction(0, "const-string v0, \"$feedItemIconsClassName\"")
    }

    override fun execute(context: BytecodeContext) {
        setFeedItemClassName()
        setFeedIconClassName()
    }

}