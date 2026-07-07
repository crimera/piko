/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.utsavrajput.patches.instagram.misc.stories.viewstorymention

import app.utsavrajput.patches.instagram.entity.dialogbox.instagramDialogBoxEntity
import app.utsavrajput.patches.instagram.entity.mediadata.mediaDataEntity
import app.utsavrajput.patches.instagram.entity.userdata.userDataEntity
import app.utsavrajput.patches.instagram.entity.videoData.videoDataEntity
import app.utsavrajput.patches.instagram.misc.settings.settingsPatch
import app.utsavrajput.patches.instagram.misc.stories.handleStoryButtonPatch
import app.utsavrajput.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.utsavrajput.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val viewStoryMentionsPatch =
    bytecodePatch(
        name = "View story mentions",
        description = "Add option to view visible and hidden story mentions.",
    ) {
        dependsOn(settingsPatch, handleStoryButtonPatch, userDataEntity, mediaDataEntity, instagramDialogBoxEntity, videoDataEntity)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            enableSettings("viewStoryMentions")
        }
    }
