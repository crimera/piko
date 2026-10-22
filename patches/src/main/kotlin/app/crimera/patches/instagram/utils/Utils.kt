/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.utils

import app.crimera.patches.instagram.misc.settings.HookFlagsLoadFingerprint
import app.crimera.patches.instagram.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.instagram.utils.Constants.LOAD_FLAGS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.SSTS_DESCRIPTOR
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

context(patchContext: BytecodePatchContext)
fun enableSettings(functionName: String) {
    SettingsStatusLoadFingerprint.method.addInstruction(
        0,
        SSTS_DESCRIPTOR.format(functionName),
    )
}

context(patchContext: BytecodePatchContext)
fun addFlags(functionName: String) {
    HookFlagsLoadFingerprint.method.addInstruction(
        0,
        LOAD_FLAGS_DESCRIPTOR.format(functionName),
    )
}

/**
 * Method referenced by a matched method call, without resolving or modifying the called class.
 */
internal val Match.InstructionMatch.methodReference: MethodReference
    get() = instruction.getReference<MethodReference>()!!

/**
 * Field referenced by a matched field access, without resolving or modifying the field class.
 */
internal val Match.InstructionMatch.fieldReference: FieldReference
    get() = instruction.getReference<FieldReference>()!!

/**
 * Type referenced by a matched instruction such as a check-cast or new-instance.
 */
internal val Match.InstructionMatch.typeReference: String
    get() = instruction.getReference<TypeReference>()!!.type
