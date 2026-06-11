/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment

import app.crimera.patches.instagram.utils.Constants.COMMENT_BUTTON_EXTENSION_CLASS
import app.morphe.patcher.Fingerprint

internal const val HANDLE_COMMENT_BUTTON_EXTENSION_CLASS = "${COMMENT_BUTTON_EXTENSION_CLASS}/HandleCommentButton;"

internal object AddCommentButtonFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    strings = listOf("instagram_share_comment_to_story_entrypoint_impression"),
)
