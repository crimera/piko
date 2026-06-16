/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.overflowMenuButton.posts

import app.crimera.patches.instagram.entity.decoder.CURRENT_MEDIA_FIELD
import app.crimera.patches.instagram.entity.decoder.MEDIA_ADD_INFO_CLASS_NAME
import app.crimera.patches.instagram.misc.download.FeedButtonOnClickFingerprint
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.FEED_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.patches.instagram.utils.Constants.FRAGMENT_ACTIVITY
import app.crimera.patches.instagram.utils.Constants.MEDIA_OPTIONS_CLASS
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags

@Suppress("unused")
val hookOverflowMenuButtonOnClickPatch =
    bytecodePatch(
        description = "Hooks feed overflow menu on click.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            FeedButtonOnClickFingerprint.apply {
                val classDef = classDef
                method.apply {

                    val classFields = classDef.fields

                    val appActivityField = classFields.first { it.type == FRAGMENT_ACTIVITY }
                    val userSessionField = classFields.first { it.type == USER_SESSION_CLASS }

                    val getMediaObjectMethod =
                        classDef.methods.first {
                            AccessFlags.FINAL.isSet(it.accessFlags) && it.implementation?.registerCount == 1
                        }

                    val mediaExtraDataField = classDef.fields.first { it.type == MEDIA_ADD_INFO_CLASS_NAME }

                    addInstructionsWithLabels(
                        0,
                        """
                        move-object/from16 v1, p1
                        invoke-static {v1}, $FEED_OVERFLOW_MENU_BUTTON_CLASS->isCustomButtonPressed($MEDIA_OPTIONS_CLASS)Z
                        move-result v0
                        if-eqz v0, :piko
                        
                        move-object/from16 v0, p0
                        iget-object v5, v0, $appActivityField
                        iget-object v6, v0, $userSessionField
                        invoke-static {v0}, $getMediaObjectMethod
                        move-result-object v2
                        iget-object v4, v0, $mediaExtraDataField
                        iget v4, v4, $CURRENT_MEDIA_FIELD
                        
                        invoke-static {v1, v6, v5, v2, v4}, $FEED_OVERFLOW_MENU_BUTTON_CLASS->customButtonOnClick($MEDIA_OPTIONS_CLASS $USER_SESSION_CLASS Landroid/content/Context;Ljava/lang/Object;I)V
                        return-void
                        
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(0)),
                    )
                }
            }
        }
    }
