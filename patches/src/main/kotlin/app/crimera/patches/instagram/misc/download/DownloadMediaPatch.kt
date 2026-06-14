/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.entity.originalSoundDataIntf.originalSoundDataIntfEntity
import app.crimera.patches.instagram.entity.trackDataIntf.trackDataIntfEntity
import app.crimera.patches.instagram.entity.videoData.videoDataEntity
import app.crimera.patches.instagram.misc.directMessage.saveAllMessages.saveAllMessagesPatch
import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.addOverflowMenuButtonAttributes
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.debugOverflowButton.debugOverflowMenuButtonPatch
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.hookOverflowMenuButton
import app.crimera.patches.instagram.misc.overflowMenuButton.reels.hookReelOverflowMenuButton
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.misc.stories.handleStoryButtonPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

@Suppress("unused")
val downloadMediaPatch =
    bytecodePatch(
        name = "Download media",
        description = "Adds ability to download posts, reels, stories and highlights",
    ) {
        dependsOn(
            settingsPatch,
            mediaDataEntity,
            videoDataEntity,
            handleStoryButtonPatch,
            originalSoundDataIntfEntity,
            trackDataIntfEntity,
            hookFlagsPatch,
            saveAllMessagesPatch,
            decoderEntity,
            hookOverflowMenuButton,
            debugOverflowMenuButtonPatch,
            hookReelOverflowMenuButton,
        )
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            addOverflowMenuButtonAttributes("PIKO_DOWNLOAD", "downloadOverflowButton")

            // DM media downloader.
            GetDirectThreadMediaSaverModuleNameFingerprint.apply {

                val appActivityField = classDef.fields.first { it.type == "Landroid/app/Activity;" }

                classDef.methods
                    .first { it.returnType == "V" && it.name != "<init>" }
                    .apply {
                        addInstructionsWithLabels(
                            0,
                            """
                            iget-object v0, p1, $appActivityField
                            move-object v1, p2
                            invoke-static {v0, v1}, $DOWNLOAD_DESCRIPTOR/MessageUtils;->messageDownloadCheck(Landroid/content/Context;Ljava/lang/Object;)Z
                            move-result v1
                            if-nez v1, :piko
                            return-void
                            """.trimIndent(),
                            ExternalLabel("piko", getInstruction(0)),
                        )
                    }
            }

            enableSettings("downloadMedia")
            addFlags("simpleOverflowMenuFlags")
        }
    }
