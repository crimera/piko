/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.shared

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags

private fun CharSequence.registerWidth(): Int = if (this == "J" || this == "D") 2 else 1

private fun MutableMethod.declaredParameterWidth(): Int =
    parameterTypes.sumOf { type -> type.registerWidth() }

internal fun parameterRegisterStart(method: MutableMethod): Int {
    val implementation =
        method.implementation
            ?: throw PatchException("${method.definingClass}->${method.name} has no implementation")
    val receiverWidth = if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1
    val start = implementation.registerCount - method.declaredParameterWidth() - receiverWidth
    if (start < 0) {
        throw PatchException(
            "Invalid register count for ${method.definingClass}->${method.name}",
        )
    }
    return start
}

internal fun declaredParameterRegister(
    method: MutableMethod,
    parameterIndex: Int,
): Int {
    if (parameterIndex !in method.parameterTypes.indices) {
        throw PatchException(
            "Invalid parameter index $parameterIndex for ${method.definingClass}->${method.name}",
        )
    }
    val receiverWidth = if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1
    return parameterRegisterStart(method) + receiverWidth +
        method.parameterTypes.take(parameterIndex).sumOf { type -> type.registerWidth() }
}
