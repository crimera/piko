/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.developerOptions

import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchFirst
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

internal const val EXTENSION_CLASS_DESCRIPTOR = "$ENTITY_CLASS/DeveloperOptions;"
internal const val ITEM_CLASS_DESCRIPTOR = "$ENTITY_CLASS/DeveloperOptionsItem;"

internal object GetUniversalIdHelperClassExtension : Fingerprint(
    name = "getUniversalIdHelperClass",
    definingClass = ITEM_CLASS_DESCRIPTOR,
)

internal object GetQuickExperimentHelperClassExtension : Fingerprint(
    name = "getQuickExperimentHelperClass",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetExperimentItemHelperClassExtension : Fingerprint(
    name = "getExperimentItemHelperClass",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetAllExperimentsClassExtension : Fingerprint(
    name = "getAllExperiments",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object ExperimentsValueBuilderFingerprint : Fingerprint(
    strings = listOf("default[after mc dispose]", "default[before mc init]", "override", "server"),
)

internal object GetAllExperimentsFingerprint : Fingerprint(
    classFingerprint = ExperimentsValueBuilderFingerprint,
    returnType = "Ljava/util/List;",
)

internal object ExperimentsGetMobileConfigSpecifier : Fingerprint(
    returnType = "I",
    parameters = listOf(),
    strings = listOf("ExperimentParameter"),
    filters = listOf(
        fieldAccess(definingClass = "this", type = "J", opcode = Opcode.IGET_WIDE, location = MatchFirst()),
        // Universal id helper.
        methodCall(parameters = listOf("J"), returnType = "I", opcode = Opcode.INVOKE_STATIC, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.RETURN, location = MatchAfterImmediately()),
    ),
)
