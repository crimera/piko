/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.lastInstruction
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

internal object AnalyticBlokBuildRelatedFingerprint : Fingerprint(
    strings = listOf("Evaluating BKBloksDataContribScreenScreenIgConstants.ANALYTICS_EXTRAS returned null. A map was expected"),
)

@Suppress("unused")
val disableAnalyticsPatch =
    bytecodePatch(
        name = "Disable analytics",
        description = "Block analytics that are sent to Instagram/Facebook servers.",
    ) {
        dependsOn(settingsPatch, interceptUriPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            // Thanks to MyInsta.
            AnalyticBlokBuildRelatedFingerprint.method.apply {
                val returnVoidIndex = indexOfFirstInstruction(Opcode.RETURN_VOID)

                val lastInvokeStaticB4Return = lastInstruction(returnVoidIndex, Opcode.INVOKE_STATIC)
                val invokeStaticIndex = lastInvokeStaticB4Return!!.location.index

                addInstruction(
                    invokeStaticIndex,
                    """
                    return-void
                    """.trimIndent(),
                )
            }

            addFlags("contactPermissionConsentFlags")

            enableSettings("disableAnalytics")
        }
    }
