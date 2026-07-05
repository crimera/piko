/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.utils

import app.morphe.patcher.patch.bytecodePatch
import kotlin.properties.Delegates

// Based on https://github.com/MorpheApp/morphe-patches/blob/main/patches/src/main/kotlin/app/morphe/patches/reddit/misc/version/VersionCheckPatch.kt

// For hooks links on modern share sheet.
var is_11_40_or_greater: Boolean by Delegates.notNull()
    private set

// For XChat Subsystem patch.
var is_11_70_or_greater: Boolean by Delegates.notNull()
    private set

// For Check compatibility patch.
var is_11_82_or_greater: Boolean by Delegates.notNull()
    private set

// For Customize Navigation Bar items patch.
var is_11_88_or_greater: Boolean by Delegates.notNull()
    private set

// For share menu button enum init.
var is_11_92_or_greater: Boolean by Delegates.notNull()
    private set

// For blocking redirecting X Lite patch.
var is_11_98_or_greater: Boolean by Delegates.notNull()
    private set

val versionCheckPatch =
    bytecodePatch {
        execute {
            val versionCode = packageMetadata.versionCode.toInt()

            fun isEqualsOrGreaterThan(version: Int): Boolean = versionCode >= version

            is_11_40_or_greater = isEqualsOrGreaterThan(311400000)
            is_11_70_or_greater = isEqualsOrGreaterThan(311700000)
            // 11.82.0-beta.1 (311820101) does not have libpairipcore.so, but 11.82.0-release.0 (31182000) has libpairipcore.so.
            is_11_82_or_greater = versionCode == 311820000 || isEqualsOrGreaterThan(311830000)
            is_11_88_or_greater = isEqualsOrGreaterThan(311880000)
            is_11_92_or_greater = isEqualsOrGreaterThan(311920000)

            is_11_98_or_greater = isEqualsOrGreaterThan(311980000)
        }
    }
