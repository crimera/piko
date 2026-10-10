/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userfriendshipstatus

import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal const val EXTENSION_CLASS = "$ENTITY_CLASS/UserFriendshipStatus;"

internal object GetMappingsFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "getMappings",
)

internal fun Method.friendshipStatusParameterOrNull(): String? {
    if (!AccessFlags.PUBLIC.isSet(accessFlags) || !AccessFlags.STATIC.isSet(accessFlags) ||
        returnType != "Ljava/util/Map;"
    ) return null
    val statusType = parameterTypes.singleOrNull()?.toString()?.takeIf { it.startsWith("L") } ?: return null
    val implementation = implementation ?: return null
    val instructions = implementation.instructions.toList()
    val statusRegister = implementation.registerCount - 1
    val keys = setOf("blocking", "followed_by", "following", "incoming_request")
    val matchedKeys = instructions.withIndex().mapNotNull { (index, instruction) ->
        val key = instruction.getReference<StringReference>()?.string ?: return@mapNotNull null
        if (key !in keys) return@mapNotNull null
        val getter = instructions.getOrNull(index + 1) ?: return@mapNotNull null
        val reference = getter.getReference<MethodReference>() ?: return@mapNotNull null
        key.takeIf {
            getter.opcode in setOf(Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE) &&
                getter.registersUsed == listOf(statusRegister) && reference.definingClass == statusType &&
                reference.parameterTypes.isEmpty() && reference.returnType == "Ljava/lang/Boolean;"
        }
    }.toSet()
    return statusType.takeIf { matchedKeys == keys }
}
