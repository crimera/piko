/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.settings

import app.crimera.patches.twitter.utils.versionCheckPatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.patch.patch

internal val checkCompatibilityPatch = patch(
    description = "Checks compatibility"
) {
    dependsOn(versionCheckPatch)

    execute {
        if (packageMetadata.versionName != "12.19.1-release.0") {
            throw PatchException(
                """

#####################################

Do not select the old X patches for NewX.

Only version 12.19.1-release.0 is compatible with "Old patches". Version ${packageMetadata.versionName} is a NewX.

Please deselect all "Old patches". Select only "NewX:" patches.

Morphe Manager remembers the selection of old patches. Reset to default from "Selections" (🔖) > "Recommended patches".

#####################################
"""
            )
        }
    }
}