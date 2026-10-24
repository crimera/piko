/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.developerOptions

import app.crimera.patches.instagram.utils.methodReference
import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.matchSingle

val developerOptionsEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of developer options items",
    ) {
        execute {
            GetQuickExperimentHelperClassExtension.changeFirstString(
                classNameToExtension(ExperimentsValueBuilderFingerprint.originalClassDef.type),
            )
            GetAllExperimentsClassExtension.changeFirstString(GetAllExperimentsFingerprint.originalMethod.name)

            ExperimentsGetMobileConfigSpecifier.matchSingle().let {
                GetExperimentItemHelperClassExtension.changeFirstString(classNameToExtension(it.originalClassDef.type))
                GetUniversalIdHelperClassExtension.changeFirstString(
                    classNameToExtension(it.instructionMatches[1].methodReference.definingClass),
                )
            }
        }
    }
