/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.entity.extMedia

import app.crimera.patches.twitter.utils.Constants.ENTITY_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

private const val ENTITY_EXT_MEDIA_DEFINING_CLASS = "${ENTITY_DESCRIPTOR}ExtMediaEntities"

internal object ExtMediaGetVideosFingerprint : Fingerprint(
    definingClass = ENTITY_EXT_MEDIA_DEFINING_CLASS,
    name = "getVideos",
)

internal object ExtMediaHighResolutionFingerprint : Fingerprint(
    definingClass = ENTITY_EXT_MEDIA_DEFINING_CLASS,
    name = "getHighestResolution",
)

internal object ExtMediaGetImageFingerprint : Fingerprint(
    definingClass = ENTITY_EXT_MEDIA_DEFINING_CLASS,
    name = "getImageUrl",
)

internal object ExtMediaGetImageMethodFinder : Fingerprint(
    definingClass = "Lcom/twitter/model/json/unifiedcard/JsonAppStoreData;",
    strings =
        listOf(
            "type",
            "id",
        ),
)

// Also required for download patch.
object MediaOptionSheetMediaListVideoDownloaderImplDownloadMethodFingerprint : Fingerprint(
    returnType = "Z",
    strings = listOf("url", "video_download"),
    custom = { _, classDef ->
        classDef.startsWith("Lcom/twitter/tweetview/core/ui/mediaoptionssheet/")
    },
)

internal object MediaResolutionToStringFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/util/math",
    name = "toString",
    strings =
        listOf(
            "Size(width=",
            ", height=",
            ")",
        ),
)

internal object ExtMediaGetSensitiveMediaCategoriesFingerprint : Fingerprint(
    name = "getSensitiveMediaCategories",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/util/Set;",
    custom = { _, classDef ->
        classDef.type.startsWith("Lcom/twitter/model/core/entity/")
    },
)
