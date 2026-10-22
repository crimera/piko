/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.commentDataEntity

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal const val EXTENSION_CLASS_DESCRIPTOR = "$ENTITY_CLASS/CommentData;"

internal object GetTextExtension : Fingerprint(
    name = "getText",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetGifMediaExtension : Fingerprint(
    name = "getGifMedia",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetGifUrlMediaExtension : Fingerprint(
    name = "getGifUrl",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetWebpUrlMediaExtension : Fingerprint(
    name = "getWebpUrl",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetGifTagMediaExtension : Fingerprint(
    name = "getGifTag",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetGifCreatorNameMediaExtension : Fingerprint(
    name = "getGifCreatorName",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetImageMediaExtension : Fingerprint(
    name = "getImageMedia",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetCommentUserDataExtension : Fingerprint(
    name = "getCommentUserData",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

// --------------------

internal object RandomGetCommentObjectMediaFingerprint : Fingerprint(
    strings = listOf(
        string("commentId="),
        string("parentCommentId=", StringComparisonType.CONTAINS),
        string("sourceMediaId=", StringComparisonType.CONTAINS),
        string("photo_comment_child_missing_overlay_info"),
    ),
    filters = listOf(
        // Comment photo wrapper.
        methodCall(
            name = "<init>",
            parameters = listOf("L", "Lcom/instagram/common/gallery/Medium;", MEDIA_CLASS_NAME, "Ljava/lang/String;"),
        ),
    ),
)

/**
 * Converts the comment GIF, assigning the GIF fields in order.
 */
internal object CommentGifConverterFingerprint : Fingerprint(
    classFingerprint = RandomGetCommentObjectMediaFingerprint,
    parameters = listOf("L"),
    filters = listOf(
        // GIF tag.
        fieldAccess(type = "Ljava/lang/String;", opcode = Opcode.IPUT_OBJECT),
        fieldAccess(opcode = Opcode.IPUT, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IPUT, location = MatchAfterImmediately()),
        // GIF URL.
        fieldAccess(opcode = Opcode.IPUT_OBJECT, location = MatchAfterImmediately()),
        // WebP URL.
        fieldAccess(opcode = Opcode.IPUT_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IPUT_BOOLEAN, location = MatchAfterImmediately()),
        // GIF creator name.
        fieldAccess(opcode = Opcode.IPUT_OBJECT, location = MatchAfterImmediately()),
    ),
)

internal object CommentShareClickFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("select_comment_screen_delete_comments_tap"),
    filters = listOf(
        string("comment_share_click"),
        // Comment text check, right after the text is read.
        opcode(Opcode.IF_EQZ),
    ),
)
