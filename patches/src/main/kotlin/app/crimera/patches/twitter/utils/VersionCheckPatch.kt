/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.utils

import app.morphe.patcher.patch.bytecodePatch
import kotlin.properties.Delegates

// Based on https://github.com/MorpheApp/morphe-patches/blob/main/patches/src/main/kotlin/app/morphe/patches/reddit/misc/version/VersionCheckPatch.kt

var is_11_92_stable_or_greater: Boolean by Delegates.notNull()
    private set
var is_11_88_stable_or_greater: Boolean by Delegates.notNull()
    private set
var is_11_69_stable_or_greater: Boolean by Delegates.notNull()
    private set

val versionCheckPatch =
    bytecodePatch {
        execute {
            val versionName = packageMetadata.versionName

            fun isEqualsOrGreaterThan(version: String): Boolean = versionName >= version

            // For share menu button enum init.
            is_11_92_stable_or_greater = isEqualsOrGreaterThan("11.92.0-release.0")
            // For Customize Navigation Bar items patch.
            is_11_88_stable_or_greater = isEqualsOrGreaterThan("11.88.0-release.0")
            // For XChat Subsystem patch.
            is_11_69_stable_or_greater = isEqualsOrGreaterThan("11.69.0-release.0")
        }
    }
