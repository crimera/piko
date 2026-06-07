/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.decoder

import app.crimera.patches.instagram.entity.mediadata.AslSessionRelatedFingerprint
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import kotlin.properties.Delegates

var MEDIA_CLASS_NAME: String by Delegates.notNull()
    private set

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
            MEDIA_CLASS_NAME = AslSessionRelatedFingerprint.method.parameters[0].type

            EditMediaInfoGetCurrentMediaIdFingerprint.method.apply {
                CURRENT_MEDIA_FIELD =
                    getInstruction(
                        indexOfFirstInstruction(Opcode.IGET),
                    ).getReference<FieldReference>()!!
                MEDIA_ADD_INFO_CLASS_NAME = CURRENT_MEDIA_FIELD.definingClass
            }

            COMMENT_BUTTON_CLASS = CommentButtonOnClickFingerprint.method.parameters[0].type
        }
    }
