package app.crimera.patches.twitter.misc.showchangelogs

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch

private object MainActivityFingerprint : Fingerprint(
    definingClass = "/main/MainActivity;"
)

@Suppress("unused")
val changelogsPatch =
    bytecodePatch(
        name = "Show changelogs",
        description = "Shows changelogs when new a patch is installed.",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val superClassName = MainActivityFingerprint.classDef.superclass!!

            val superclassOnCreateMethod = mutableClassDefBy(superClassName).methods
                .first { it.name == "onCreate" }

            superclassOnCreateMethod.addInstruction(
                0,
                "invoke-static {p0}, ${Constants.PATCHES_DESCRIPTOR}/Changelogs;->showChangelog(Landroid/app/Activity;)V"
            )
            SettingsStatusLoadFingerprint.enableSettings("showChangelogsPatchEnabled")
        }
    }
