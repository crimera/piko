/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.moreOptionsOnPost

import app.crimera.patches.instagram.entity.decoder.CURRENT_MEDIA_FIELD
import app.crimera.patches.instagram.entity.decoder.MEDIA_ADD_INFO_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.download.FeedButtonOnClickFingerprint
import app.crimera.patches.instagram.misc.overflowMenuButton.addOverflowMenuButtonAttributes
import app.crimera.patches.instagram.misc.overflowMenuButton.debugOverflowButton.debugOverflowMenuButtonPatch
import app.crimera.patches.instagram.misc.overflowMenuButton.hookOverflowMenuButton
import app.crimera.patches.instagram.misc.overflowMenuButton.reels.hookReelOverflowMenuButton
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.FEED_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.patches.instagram.utils.Constants.FRAGMENT_ACTIVITY
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags

@Suppress("unused")
val moreOptionsOnPostPatch =
    bytecodePatch(
        name = "More options on post",
        description = "Adds more options on post, like copy description by long pressing on post",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, decoderEntity, hookOverflowMenuButton, debugOverflowMenuButtonPatch, hookReelOverflowMenuButton)
        execute {

            val EXTENSION_CLASS_DESCRIPTOR = "$PATCHES_DESCRIPTOR/feed/MoreOptionsOnPostPatch;"

            addOverflowMenuButtonAttributes("PIKO_MORE_POST_OPTION", "morePostOptionOverflowButton")

            FeedButtonOnClickFingerprint.method.apply {
                val classDef = FeedButtonOnClickFingerprint.classDef
                val classFields = classDef.fields

                val appActivityField = classFields.first { it.type == FRAGMENT_ACTIVITY }

                val getMediaObjectMethod =
                    classDef.methods.first {
                        AccessFlags.FINAL.isSet(it.accessFlags) && it.implementation?.registerCount == 1
                    }

                val mediaExtraDataField = classDef.fields.first { it.type == MEDIA_ADD_INFO_CLASS_NAME }

                addInstructionsWithLabels(
                    0,
                    """
                    move-object/from16 v1, p1
                    invoke-static {v1}, $FEED_OVERFLOW_MENU_BUTTON_CLASS->isMoreOptionsOnPostButton(Lcom/instagram/feed/media/mediaoption/MediaOption${'$'}Option;)Z
                    move-result v0
                    if-eqz v0, :piko
                    
                    move-object/from16 v0, p0
                    iget-object v5, v0, $appActivityField
                    invoke-static {v0}, $getMediaObjectMethod
                    move-result-object v2
                    iget-object v4, v0, $mediaExtraDataField
                    iget v4, v4, $CURRENT_MEDIA_FIELD
                    
                    invoke-static {v5, v2, v4}, $EXTENSION_CLASS_DESCRIPTOR->postMoreOptions(Landroid/content/Context;Ljava/lang/Object;I)V
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko", getInstruction(0)),
                )
            }

            enableSettings("moreOptionsOnPost")
        }
    }
