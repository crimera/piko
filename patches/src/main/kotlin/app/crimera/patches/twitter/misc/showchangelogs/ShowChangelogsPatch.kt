package app.crimera.patches.twitter.misc.showchangelogs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants
import app.crimera.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable

private val mainActivityFingerprint =
    fingerprint {
        custom { _, classDef ->
            classDef.type.endsWith("/main/MainActivity;")
        }
    }

@Suppress("unused")
val changelogsPatch =
    bytecodePatch(
        name = "Show changelogs",
        description = "Shows changelogs when new a patch is installed.",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val superClassName = mainActivityFingerprint.classDef.superclass!!

            val superclassOnCreateMethod = mutableClassDefBy(superClassName).methods
                .first { it.name == "onCreate" }

            superclassOnCreateMethod.addInstruction(
                0,
                "invoke-static {p0}, ${Constants.PATCHES_DESCRIPTOR}/Changelogs;->showChangelog(Landroid/app/Activity;)V"
            )
            settingsStatusLoadFingerprint.enableSettings("showChangelogsPatchEnabled")
        }
    }
