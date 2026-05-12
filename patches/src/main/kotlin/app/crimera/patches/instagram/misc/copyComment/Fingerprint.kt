/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.copyComment

import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint

internal const val COMMENT_COPY_EXTENSION_CLASS = "${PATCHES_DESCRIPTOR}/comment/copy/"
internal const val COPY_BUTTON_EXTENSION_CLASS = "${COMMENT_COPY_EXTENSION_CLASS}CopyButton;"
internal const val INIT_COPY_BUTTON_EXTENSION_CLASS = "${COMMENT_COPY_EXTENSION_CLASS}InitCopyButton;"
internal const val HANDLE_COMMENT_BUTTON_EXTENSION_CLASS = "${COMMENT_COPY_EXTENSION_CLASS}HandleCommentButton;"

internal object InitCopyButtonInitExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = INIT_COPY_BUTTON_EXTENSION_CLASS,
)

internal object CheckOnCommentButtonClickExtensionFingerprint : Fingerprint(
    name = "checkOnCommentButtonClick",
    definingClass = HANDLE_COMMENT_BUTTON_EXTENSION_CLASS,
)

internal object AddCommentButtonFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    strings = listOf("instagram_share_comment_to_story_entrypoint_impression"),
)

internal object CommentButtonOnClickFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("select_comment_screen_delete_comments_tap", "comment_share_click"),
)

internal object CopyTextChatButtonToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    strings = listOf("CopyText"),
)
