/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.developerOptions

import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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

internal object ExperimentsGetMobileConfigSpecifier : Fingerprint(
    strings = listOf("ExperimentParameter"),
    returnType = "I",
    parameters = listOf(),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    custom = { method, _ ->
        val instructions = method.implementation?.instructions?.take(4).orEmpty()
        val field = instructions.getOrNull(0)?.getReference<FieldReference>()
        val converter = instructions.getOrNull(1)?.getReference<MethodReference>()
        val fieldRegisters = instructions.getOrNull(0)?.registersUsed.orEmpty()
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            instructions.map { it.opcode } == listOf(Opcode.IGET_WIDE, Opcode.INVOKE_STATIC, Opcode.MOVE_RESULT, Opcode.RETURN) &&
            field?.definingClass == method.definingClass && field.type == "J" &&
            converter?.parameterTypes?.map { it.toString() } == listOf("J") && converter.returnType == "I" &&
            fieldRegisters.size == 2 && fieldRegisters[1] == method.implementation!!.registerCount - 1 &&
            instructions[1].registersUsed == listOf(fieldRegisters[0], fieldRegisters[0] + 1)
    },
)
