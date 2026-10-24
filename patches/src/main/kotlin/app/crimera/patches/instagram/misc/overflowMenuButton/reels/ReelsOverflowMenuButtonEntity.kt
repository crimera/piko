/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.overflowMenuButton.reels

import app.crimera.patches.instagram.misc.overflowMenuButton.posts.AddReelButtonExtensionFingerprint
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.FeedReplaceAudioDialogHelperFingerprint
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.methodReference
import app.crimera.utils.changeFirstString
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val reelsOverflowMenuButtonEntity =
    bytecodePatch(
        description = "Entity class for reels overflow menu button",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            AddReelButtonExtensionFingerprint.changeFirstString(
                FeedReplaceAudioDialogHelperFingerprint.instructionMatches.last().methodReference.name,
            )
        }
    }
