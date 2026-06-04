/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment.debugComment

import app.crimera.patches.instagram.utils.Constants.COMMENT_BUTTON_EXTENSION_CLASS
import app.morphe.patcher.Fingerprint

internal const val DEBUG_COMMENT_EXTENSION_CLASS = "${COMMENT_BUTTON_EXTENSION_CLASS}/debugButton"
internal const val DEBUG_BUTTON_EXTENSION_CLASS = "${DEBUG_COMMENT_EXTENSION_CLASS}/DebugButton;"

internal const val INIT_DEBUG_BUTTON_EXTENSION_CLASS = "${DEBUG_COMMENT_EXTENSION_CLASS}/InitDebugButton;"

internal object InitDebugButtonInitExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = INIT_DEBUG_BUTTON_EXTENSION_CLASS,
)

internal object InitDebugButtonExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = DEBUG_BUTTON_EXTENSION_CLASS,
)

internal object ViewSourcesChatButtonToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    strings = listOf("ViewSources"),
)
