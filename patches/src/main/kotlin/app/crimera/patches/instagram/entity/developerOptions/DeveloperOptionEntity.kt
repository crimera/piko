/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.developerOptions

import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val developerOptionsEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of developer options items",
    ) {
        execute {
            ExperimentsValueBuilderFingerprint.apply {
                GetQuickExperimentHelperClassExtension.changeFirstString(classNameToExtension(classDef.type))

                val getAllExperimentsMethod = classDef.methods.firstOrNull { it.returnType == "Ljava/util/List;" }
                    ?: throw PatchException("Failed to find getAllExperimentsMethod in ${classDef.type}")
                GetAllExperimentsClassExtension.changeFirstString(getAllExperimentsMethod.name)
            }

            ExperimentsGetMobileConfigSpecifier.apply {
                GetExperimentItemHelperClassExtension.changeFirstString(classNameToExtension(classDef.type))
                method.apply {
                    val invokeStaticIndex = indexOfFirstInstruction(Opcode.INVOKE_STATIC)
                    if (invokeStaticIndex < 0) throw PatchException("Failed to find INVOKE_STATIC in ExperimentsGetMobileConfigSpecifier")
                    val getUniversalIdInstructionData = getInstruction(invokeStaticIndex).methodExtractor()
                    GetUniversalIdHelperClassExtension.changeFirstString(getUniversalIdInstructionData.definingClass)
                }
            }
        }
    }
