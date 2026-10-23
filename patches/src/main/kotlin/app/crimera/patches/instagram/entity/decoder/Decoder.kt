/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.decoder

import app.crimera.patches.instagram.utils.fieldReference
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import kotlin.properties.Delegates

const val MEDIA_CLASS_NAME = "Lcom/instagram/feed/media/Media;"

const val MEDIAEXT_CLASS_NAME = "Lcom/instagram/feed/media/MediaExtKt;"

const val USER_MODEL_CLASS_NAME = "Lcom/instagram/user/model/User;"

var MEDIA_ADD_INFO_CLASS_NAME: String by Delegates.notNull()
    private set

var CURRENT_MEDIA_FIELD: FieldReference by Delegates.notNull()
    private set

var COMMENT_BUTTON_CLASS: String by Delegates.notNull()
    private set

val decoderEntity =
    bytecodePatch(
        description = "This patch is used hold class and field names that are commonly used",
    ) {
        execute {
            CURRENT_MEDIA_FIELD = EditMediaInfoGetCurrentMediaIdFingerprint.instructionMatches[2].fieldReference
            MEDIA_ADD_INFO_CLASS_NAME = CURRENT_MEDIA_FIELD.definingClass

            COMMENT_BUTTON_CLASS = CommentButtonOnClickFingerprint.originalMethod.parameterTypes[0].toString()
        }
    }
