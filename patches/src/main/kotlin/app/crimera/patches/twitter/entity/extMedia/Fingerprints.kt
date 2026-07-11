/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.entity.extMedia

import app.crimera.patches.twitter.utils.Constants.ENTITY_DESCRIPTOR
import app.morphe.patcher.Fingerprint

private const val ENTITY_EXT_MEDIA_DEFINING_CLASS = "${ENTITY_DESCRIPTOR}ExtMediaEntities"

internal object ExtMediaHighResVideoFingerprint : Fingerprint(
    definingClass = ENTITY_EXT_MEDIA_DEFINING_CLASS,
    name = "getHighResVideo",
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
