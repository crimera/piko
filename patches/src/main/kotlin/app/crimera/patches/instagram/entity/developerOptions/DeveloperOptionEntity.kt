/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.entity.developerOptions

import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.patch.bytecodePatch

val developerOptionsEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of developer options items",
    ) {
        execute {
            ExperimentsLongPressItemBuilder.apply {
                GetQuickExperimentHelperClassExtension.changeFirstString(classNameToExtension(classDef.type))

                val getAllExperimentsMethodName = classDef.methods.first { it.returnType == "Ljava/util/List;" }.name
                GetAllExperimentsClassExtension.changeFirstString(getAllExperimentsMethodName)
            }

            val experimentsItemClassName = classNameToExtension(ExperimentsGetMobileConfigSpecifier.classDef.type)
            GetExperimentItemHelperClassExtension.changeFirstString(experimentsItemClassName)
        }
    }
