/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.developerOptions

import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val developerOptionsEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of developer options items",
    ) {
        execute {
            ExperimentsValueBuilderFingerprint.apply {
                GetQuickExperimentHelperClassExtension.changeFirstString(classNameToExtension(classDef.type))

                val getAllExperimentsMethodName = classDef.methods.first { it.returnType == "Ljava/util/List;" }.name
                GetAllExperimentsClassExtension.changeFirstString(getAllExperimentsMethodName)
            }

            ExperimentsGetMobileConfigSpecifier.apply {
                GetExperimentItemHelperClassExtension.changeFirstString(classNameToExtension(classDef.type))
                method.apply {
                    val getUniversalIdInstructionData = getInstruction(indexOfFirstInstruction(Opcode.INVOKE_STATIC)).methodExtractor()
                    GetUniversalIdHelperClassExtension.changeFirstString(getUniversalIdInstructionData.definingClass)
                }
            }
        }
    }
