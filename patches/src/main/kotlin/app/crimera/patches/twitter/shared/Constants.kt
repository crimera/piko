package app.crimera.patches.twitter.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal object Constants {
    val COMPATIBILITY_X = Compatibility(
        name = "X",
        packageName = "com.twitter.android",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x000000,
        signatures = setOf(
            // APK
            "0fd9a0cfb07b65950997b4eaebdc53931392391aa406538a3b04073bc2ce2fe9",
            // APKM
            "45c53db089fb8d63a1c71154037c414b1b0646a141a73ae6e3bcbf8cd148402f"
        ),
        targets = listOf(
            // Any version
            AppTarget(version = null)
        )
    )
}