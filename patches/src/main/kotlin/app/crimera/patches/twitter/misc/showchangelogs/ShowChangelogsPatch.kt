/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.showchangelogs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException

private object MainActivityFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/app/main/MainActivity;",
)

@Suppress("unused")
val changelogsPatch =
    bytecodePatch(
        name = "Show changelogs",
        description = "Shows changelogs when new a patch is installed.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {

            val superClassName = MainActivityFingerprint.classDef.superclass
                ?: throw PatchException("MainActivity has no superclass")

            val superclassOnCreateMethod =
                mutableClassDefBy(superClassName)
                    .methods
                    .firstOrNull { it.name == "onCreate" }
                    ?: throw PatchException("Failed to find 'onCreate' in superclass ${superClassName} of MainActivity")

            superclassOnCreateMethod.addInstruction(
                0,
                "invoke-static {p0}, ${Constants.PATCHES_DESCRIPTOR}/Changelogs;->showChangelog(Landroid/app/Activity;)V",
            )
            enableSettings("showChangelogsPatchEnabled")
        }
    }
