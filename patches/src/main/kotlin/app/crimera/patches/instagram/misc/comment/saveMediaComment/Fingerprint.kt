/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment.saveMediaComment

import app.crimera.patches.instagram.entity.commentDataEntity.CHAT_CONTEXT_BUTTON_SUPER_CLASS
import app.crimera.patches.instagram.utils.Constants.COMMENT_BUTTON_EXTENSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

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
    strings = listOf("SAVE_MEDIA", "COPY_TEXT", "save_media"),
    custom = { _, classDef -> classDef.superclass == "Ljava/lang/Enum;" },
)

internal class SaveMediaChatButtonInitFingerprint(action: FieldReference) : Fingerprint(
    name = "<init>",
    parameters = emptyList(),
    custom = { method, classDef ->
        classDef.superclass == CHAT_CONTEXT_BUTTON_SUPER_CLASS && method.implementation?.instructions?.any { instruction ->
            instruction.opcode == Opcode.SGET_OBJECT && instruction.getReference<FieldReference>()?.let {
                it.definingClass == action.definingClass && it.name == action.name && it.type == action.type
            } == true
        } == true
    },
)
