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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

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
            MainActivityFingerprint.classDef.apply {
                require(methods.none { it.name == "onCreate" }) {
                    "Main activity already has an onCreate method"
                }

                // Add missing method. Could override superclass onCreate but
                // then extension hook is called multiple times by unrealted activities.
                methods += ImmutableMethod(
                    type,
                    "onCreate",
                    listOf(ImmutableMethodParameter("Landroid/os/Bundle;", null, null)),
                    "V",
                    AccessFlags.PUBLIC.value,
                    null,
                    null,
                    MutableMethodImplementation(3)
                ).toMutable().apply {
                    addInstructions(
                        0,
                        """
                            invoke-super {p0, p1}, ${superclass}->onCreate(Landroid/os/Bundle;)V
                            invoke-static {p0}, ${Constants.PATCHES_DESCRIPTOR}/Changelogs;->showChangelog(Landroid/app/Activity;)V
                            return-void
                        """
                    )
                }
            }

            enableSettings("showChangelogsPatchEnabled")
        }
    }
