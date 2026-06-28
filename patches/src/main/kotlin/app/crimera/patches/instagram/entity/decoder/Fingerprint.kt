/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.decoder

import app.crimera.patches.instagram.utils.Constants.EDIT_MEDIA_INFO_FRAGMENT_CLASS
import app.morphe.patcher.Fingerprint

// Also used to in description extraction in MediaEntity
object EditMediaInfoGetCurrentMediaIdFingerprint : Fingerprint(
    definingClass = EDIT_MEDIA_INFO_FRAGMENT_CLASS,
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
)

object CommentButtonOnClickFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("select_comment_screen_delete_comments_tap", "comment_share_click"),
)

internal object UserTagInfoDictInitFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/schemas/UserTagInfoDict;",
    name = "<init>",
)

object ReelsInlineQualitySurveyRelatedFingerprint : Fingerprint(
    strings = listOf("reels_inline_quality_survey"),
)
