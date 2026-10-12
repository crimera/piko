/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment.saveMediaComment

import app.crimera.patches.instagram.utils.Constants.COMMENT_BUTTON_EXTENSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

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

internal object ChatButtonActionsFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf("COPY_TEXT", "save_media"),
    filters = listOf(
        string("SAVE_MEDIA"),
        methodCall(opcode = Opcode.INVOKE_STATIC, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        // Save media action.
        fieldAccess(opcode = Opcode.SPUT_OBJECT, location = MatchAfterImmediately()),
    ),
)
