/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

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

internal object DirectThreadMediaSaveFingerprint : Fingerprint(
    classFingerprint = GetDirectThreadMediaSaverModuleNameFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "L", "L", "L", "Z"),
    strings = listOf("android.permission.WRITE_EXTERNAL_STORAGE"),
)

internal object MediaOptionsOverflowMenuCreatorConstructorFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("MediaOptionsOverflowMenuCreator"),
)
