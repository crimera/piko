/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment.copyComment

import app.crimera.patches.instagram.utils.Constants.COMMENT_BUTTON_EXTENSION_CLASS
import app.morphe.patcher.Fingerprint

internal const val COMMENT_COPY_EXTENSION_CLASS = "${COMMENT_BUTTON_EXTENSION_CLASS}/copyTextButton"
internal const val COPY_BUTTON_EXTENSION_CLASS = "${COMMENT_COPY_EXTENSION_CLASS}/CopyTextButton;"

internal const val INIT_COPY_BUTTON_EXTENSION_CLASS = "${COMMENT_COPY_EXTENSION_CLASS}/InitCopyTextButton;"

internal object InitCopyButtonInitExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = INIT_COPY_BUTTON_EXTENSION_CLASS,
)

internal object InitCopyButtonExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = COPY_BUTTON_EXTENSION_CLASS,
)

internal object CopyTextChatButtonToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    strings = listOf("CopyText"),
)
