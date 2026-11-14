/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2470
 *
 * Original code hard forked from:
 * https://github.com/ReVanced/revanced-patches/blob/724e6d61b2ecd868c1a9a37d465a688e83a74799/patches/src/main/kotlin/app/revanced/patches/all/misc/versioncode/ChangeVersionCodePatch.kt
 *
 * File-Specific License Notice (GPLv3 Section 7 Terms)
 *
 * This file is part of the Morphe project and is licensed under
 * the GNU General Public License version 3 (GPLv3), with the Additional
 * Terms under Section 7 described in the LICENSE file.
 *
 * https://www.gnu.org/licenses/gpl-3.0.html
 *
 * Section 7b: Notice Preservation
 * -------------------------------
 * This entire comment block must be preserved in all copies,
 * distributions, and derivative works of this file, in both
 * original and modified source forms.
 *
 * Portions of this software are provided "AS IS" by the Morphe software project.
 * Any express or implied warranties, including the implied warranties of
 * merchantability and fitness for a particular purpose, are disclaimed.
 */

package app.morphe.patches.all.misc.updates

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceXml
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode

import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import java.util.logging.Logger

private const val EXTENSION_CLASS = "Lapp/morphe/extension/all/versioncode/DisablePlayStoreUpdatesPatch;"

private var originalVersionCode: Int = 0

@Suppress("unused")
private val disablePlayStoreUpdatesResourcePatch = patch {
    execute {
        resourceXml("AndroidManifest.xml").use { manifest ->
            originalVersionCode = manifest.root.attribute("android:versionCode")!!.toInt()
        }
    }

    finalize {
        resourceXml("AndroidManifest.xml").use { manifest ->
            //  Max allowed by Play Store is 2100000000, but Android allows max int value.
            manifest.root.setAttribute("android:versionCode", Int.MAX_VALUE.toString())
        }
    }
}

@Suppress("unused")
internal val disablePlayStoreUpdatesPatch = patch(
    name = "Disable Play Store updates",
    description = "Disables Play Store updates by setting the version code to the maximum allowed. " +
            "This patch may cause unexpected issues with some apps and does not work if the " +
            "app is installed by root mounting",
    default = true
) {

    dependsOn(disablePlayStoreUpdatesResourcePatch)

    compatibleWith(COMPATIBILITY_X, COMPATIBILITY_INSTAGRAM)

    extendWith("extensions/all/versioncode/change-version-code.mpe")

    finalize {
        Fingerprint(
            definingClass = EXTENSION_CLASS,
            name = "originalVersionCode"
        ).method.returnEarly(originalVersionCode)

        val logger by lazy { Logger.getLogger(this::class.java.name) }

        Fingerprint(
            filters = listOf(
                fieldAccess(
                    opcode = Opcode.IGET,
                    smali = "Landroid/content/pm/PackageInfo;->versionCode:I"
                )
            ),
            custom = { _, classDef ->
                !classDef.type.startsWith("Lapp/morphe/extension")
            }
        ).matchAllMethodIndicesForEach(requireMatches = false) { index ->
            val instruction = this.getInstruction<TwoRegisterInstruction>(index)
            val moveResultRegister = instruction.registerA
            val packageInfoRegister = instruction.registerB
            val moveResultIndex = index + 1

            if (moveResultRegister >= 16) {
                val provider = getFreeRegisterProvider(
                    moveResultIndex,
                    moveResultRegister,
                    packageInfoRegister
                )
                if (!provider.hasFreeRegisters()) {
                    logger.warning("Method does not have enough free registers, version code may not be overridden for: $this")
                    return@matchAllMethodIndicesForEach
                }
                val free = provider.getFreeRegister()
                if (free >= 16) {
                    logger.warning("No 4-bit register available, version code may not be overridden for: $this")
                    return@matchAllMethodIndicesForEach
                }

                addInstruction(
                    moveResultIndex,
                    """
                        move-result v$free
                        move/from16 v$moveResultRegister, v$free
                    """
                )
            } else {
                addInstruction(
                    moveResultIndex,
                    "move-result v$moveResultRegister"
                )
            }

            replaceInstruction(
                index,
                "invoke-static/range { v$packageInfoRegister .. v$packageInfoRegister }, " +
                        "$EXTENSION_CLASS->getVersionCode(Landroid/content/pm/PackageInfo;)I"
            )
        }

        // Replace long version code, which is a combination of
        // regular version code and versionCodeMajor.
        Fingerprint(
            filters = listOf(
                methodCall(
                    opcode = Opcode.INVOKE_VIRTUAL,
                    smali = "Landroid/content/pm/PackageInfo;->getLongVersionCode()J"
                )
            ),
            custom = { _, classDef ->
                !classDef.type.startsWith("Lapp/morphe/extension")
            }
        ).matchAllMethodIndicesForEach(requireMatches = false) { index ->
            if (getInstruction(index + 1).opcode != Opcode.MOVE_RESULT_WIDE) {
                return@matchAllMethodIndicesForEach
            }
            val register = getInstruction<FiveRegisterInstruction>(index).registerC

            replaceInstruction(
                index,
                "invoke-static/range { v$register .. v$register }, " +
                        "$EXTENSION_CLASS->getVersionCodeLong(Landroid/content/pm/PackageInfo;)J"
            )
        }
    }
}
