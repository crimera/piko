/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.utils

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi.ARM64_V8A

object Constants {
    val INSTAGRAM_VERSION_NAME = "442.0.0.46.79"

    val COMPATIBILITY_INSTAGRAM =
        Compatibility(
            name = "Instagram",
            packageName = "com.instagram.android",
            apkFileType = ApkFileType.APKM,
            appIconColor = 0xFC483C,
            targets =
                listOf(
                    // Stable
                    AppTarget(
                        version = INSTAGRAM_VERSION_NAME,
                        versionCodes =
                            mapOf(
                                ARM64_V8A to 384810148,
                            ),
                    ),
                ),
        )
}
