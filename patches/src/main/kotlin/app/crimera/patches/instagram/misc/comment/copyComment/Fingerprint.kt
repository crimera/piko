/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment.copyComment

import app.crimera.patches.instagram.utils.Constants.COMMENT_BUTTON_EXTENSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.Opcode

internal const val COMMENT_COPY_EXTENSION_CLASS = "${COMMENT_BUTTON_EXTENSION_CLASS}/copyTextButton"
internal const val COPY_BUTTON_EXTENSION_CLASS = "${COMMENT_COPY_EXTENSION_CLASS}/CopyTextButton;"

internal const val INIT_COPY_BUTTON_EXTENSION_CLASS = "${COMMENT_COPY_EXTENSION_CLASS}/InitCopyTextButton;"

internal const val COPY_USERNAME_BUTTON_EXTENSION_CLASS = "${COMMENT_COPY_EXTENSION_CLASS}/CopyUsernameButton;"

internal fun commentButtonLabelFingerprint(stringClass: String) = Fingerprint(
    returnType = "V",
    filters = listOf(
        fieldAccess(definingClass = stringClass, type = "I", opcode = Opcode.IGET),
        methodCall(
            smali = "Landroid/content/Context;->getString(I)Ljava/lang/String;",
            location = MatchAfterImmediately(),
        ),
    ),
)

internal object CopyUsernameButtonExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = COPY_USERNAME_BUTTON_EXTENSION_CLASS,
)

internal object InitCopyUsernameButtonExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = "${COMMENT_COPY_EXTENSION_CLASS}/InitCopyUsernameButton;",
)

internal object InitCopyButtonInitExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = INIT_COPY_BUTTON_EXTENSION_CLASS,
)

internal object InitCopyButtonExtensionFingerprint : Fingerprint(
    name = "<init>",
    definingClass = COPY_BUTTON_EXTENSION_CLASS,
)

// Also being used for sale media button.
object CopyTextChatButtonToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    strings = listOf("CopyText"),
)
