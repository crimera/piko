/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.overflowMenuButton.debugOverflowButton

import app.crimera.patches.instagram.entity.decoder.MEDIA_ADD_INFO_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.download.FeedButtonOnClickFingerprint
import app.crimera.patches.instagram.misc.overflowMenuButton.addOverflowMenuButtonAttributes
import app.crimera.patches.instagram.misc.overflowMenuButton.hookOverflowMenuButton
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.FEED_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.patches.instagram.utils.Constants.FRAGMENT_ACTIVITY
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags

@Suppress("unused")
val debugOverflowMenuButtonPatch =
    bytecodePatch(
        description = "Adds debug overflow menu button",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, decoderEntity, hookOverflowMenuButton)
        execute {

            addOverflowMenuButtonAttributes("PIKO_DEBUG", "debugOverflowButton")

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
                    invoke-static {v1}, $FEED_OVERFLOW_MENU_BUTTON_CLASS->isDebugButton(Lcom/instagram/feed/media/mediaoption/MediaOption${'$'}Option;)Z
                    move-result v0
                    if-eqz v0, :piko
                    
                    move-object/from16 v0, p0
                    iget-object v5, v0, $appActivityField
                    invoke-static {v0}, $getMediaObjectMethod
                    move-result-object v2
                    
                    invoke-static {v5, v2}, Lapp/morphe/extension/crimera/ObjectBrowser;->browseObject(Landroid/content/Context;Ljava/lang/Object;)V
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko", getInstruction(0)),
                )
            }

            enableSettings("moreOptionsOnPost")
        }
    }
