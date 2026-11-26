/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.stories.fixStoryPosition

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.patch

private const val REEL_VIEWER_FRAGMENT = "Linstagram/features/stories/fragment/ReelViewerFragment;"
private const val STORY_POSITION_DESCRIPTOR = "$PATCHES_DESCRIPTOR/story/StoryPosition;"

private object ReelViewerOnViewCreatedFingerprint : Fingerprint(
    definingClass = REEL_VIEWER_FRAGMENT,
    name = "onViewCreated",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
    returnType = "V",
)

@Suppress("unused")
val fixStoryPositionPatch = patch(
    name = "Fix story position",
    description = "Keeps story media above the reply bar when a story is opened outside the home feed.",
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(settingsPatch)

    execute {
        // The modal story viewer already sits below the status bar, and each story page adds its own
        // status bar spacer on top of that. The extension removes the doubled offset.
        ReelViewerOnViewCreatedFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p1 .. p1}, $STORY_POSITION_DESCRIPTOR->onViewCreated(Landroid/view/View;)V",
        )
        enableSettings("fixStoryPosition")
    }
}
