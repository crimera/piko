/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.morphe.patcher.Fingerprint

internal object FeedButtonOnClickFingerprint : Fingerprint(
    parameters = listOf("Lcom/instagram/feed/media/mediaoption/MediaOption\$Option;"),
    strings = listOf("MediaOptionsOverflowHelper"),
    returnType = "V",
)

internal object AddReelButtonFingerprint : Fingerprint(
    strings = listOf("ClipsOrganicMediaItemViewMoreOptionsController", "reels"),
)

internal object GetDirectThreadMediaSaverModuleNameFingerprint : Fingerprint(
    strings = listOf("DirectThreadMediaSaver"),
    name = "getModuleName",
    returnType = "Ljava/lang/String;",
)

internal object MediaOptionsOverflowMenuCreatorConstructorFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("MediaOptionsOverflowMenuCreator"),
)

internal object SavedCollectionOptionsActionSheetFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("SavedCollectionOptionsActionSheet"),
)

internal object SavedCollectionPageRequestFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z", "Z"),
    strings = listOf("feed/collection/%s/"),
)

internal object AddCollectionMenuItemExtensionFingerprint : Fingerprint(
    name = "addNormalAction",
    definingClass = "$ENTITY_CLASS/InstagramActionSheetBuilder;",
)

internal object ReadCollectionSourceExtensionFingerprint : Fingerprint(
    name = "readSource",
    definingClass = "$DOWNLOAD_DESCRIPTOR/CollectionDownloadPatch;",
)

internal object ReadCollectionSourceStateExtensionFingerprint : Fingerprint(
    name = "readSourceState",
    definingClass = "$DOWNLOAD_DESCRIPTOR/CollectionDownloadPatch;",
)

internal object CollectionSourceHasCursorExtensionFingerprint : Fingerprint(
    name = "sourceHasCursor",
    definingClass = "$DOWNLOAD_DESCRIPTOR/CollectionDownloadPatch;",
)

internal object CollectionSourceCanLoadMoreExtensionFingerprint : Fingerprint(
    name = "sourceCanLoadMore",
    definingClass = "$DOWNLOAD_DESCRIPTOR/CollectionDownloadPatch;",
)

internal object CollectionStateHasMoreExtensionFingerprint : Fingerprint(
    name = "stateHasMore",
    definingClass = "$DOWNLOAD_DESCRIPTOR/CollectionDownloadPatch;",
)

internal object CollectionStateRequestAllowedExtensionFingerprint : Fingerprint(
    name = "stateRequestAllowed",
    definingClass = "$DOWNLOAD_DESCRIPTOR/CollectionDownloadPatch;",
)

internal object InvokeCollectionNextPageExtensionFingerprint : Fingerprint(
    name = "invokeLoadNext",
    definingClass = "$DOWNLOAD_DESCRIPTOR/CollectionDownloadPatch;",
)

internal object InvokeCollectionRefreshExtensionFingerprint : Fingerprint(
    name = "invokeRefresh",
    definingClass = "$DOWNLOAD_DESCRIPTOR/CollectionDownloadPatch;",
)
