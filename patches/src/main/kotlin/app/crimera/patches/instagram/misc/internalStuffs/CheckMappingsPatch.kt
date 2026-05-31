/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.internalStuffs

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

const val QE_FRAGMENT_DESCRIPTOR = "Lcom/instagram/debug/quickexperiment/QuickExperimentCategoriesFragment;"

internal object QuickExperimentCategoriesFragmentOnViewCreateFingerprint : Fingerprint(
    name = "onViewCreated",
    definingClass = QE_FRAGMENT_DESCRIPTOR,
)

@Suppress("unused")
val checkMappingsPatch =
    bytecodePatch(
        description = "This patch is used to check if the mappings file exists and if not alert the user",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {

            QuickExperimentCategoriesFragmentOnViewCreateFingerprint.method.apply {
                val ifGezIndex = indexOfFirstInstruction(Opcode.IF_GEZ)
                val targetIndex = ifGezIndex + 1
                val fragmentRegister = getInstruction(targetIndex).registersUsed[0]

                val firstGoto16 = getInstruction(indexOfFirstInstruction(Opcode.GOTO_16))

                addInstructionsWithLabels(
                    targetIndex,
                    """ 
                    invoke-static {v$fragmentRegister}, $DOWNLOAD_DESCRIPTOR/DownloadMapping;->checkMappings(Landroidx/fragment/app/Fragment;)V
                    goto: piko
                    """.trimIndent(),
                    ExternalLabel("piko", firstGoto16),
                )
            }
        }
    }
