/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment.saveMediaComment

import app.crimera.patches.instagram.entity.commentDataEntity.CHAT_CONTEXT_BUTTON_SUPER_CLASS
import app.crimera.patches.instagram.misc.comment.copyComment.CopyTextChatButtonToStringFingerprint
import app.crimera.patches.instagram.utils.Constants.COMMENT_BUTTON_EXTENSION_CLASS
import app.morphe.patcher.Fingerprint

internal const val COMMENT_COPY_EXTENSION_CLASS = "${COMMENT_BUTTON_EXTENSION_CLASS}/saveMediaButton"
internal const val BUTTON_EXTENSION_CLASS = "${COMMENT_COPY_EXTENSION_CLASS}/SaveMediaButton;"

internal const val INIT_BUTTON_EXTENSION_CLASS = "${COMMENT_COPY_EXTENSION_CLASS}/InitSaveMediaButton;"

internal object InitSaveMediaButtonInitExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = INIT_BUTTON_EXTENSION_CLASS,
)

internal object InitSaveMediaButtonExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = BUTTON_EXTENSION_CLASS,
)

internal object SaveMediaChatButtonToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    strings = listOf("SaveMedia"),
    custom = { _, classDef ->
        classDef.superclass == CHAT_CONTEXT_BUTTON_SUPER_CLASS
    },
)

internal object SaveMediaChatButtonInitFingerprint : Fingerprint(
    classFingerprint = SaveMediaChatButtonToStringFingerprint,
    name = "<init>",
)
