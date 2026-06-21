/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.directItem

import app.crimera.utils.changeFirstString
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

/**
 * Resolves every obfuscated DirectItem field name at patch time and bakes it into the
 * {@code DirectItem} extension entity, so the runtime never has to discover field/method
 * names by reflection. See {@code mediaDataEntity} for the reference pattern.
 */
val directItemEntity =
    bytecodePatch(
        description = "Decodes obfuscated DirectItem (DM) field names at patch time.",
    ) {
        execute {
            DirectItemDispatchFingerprint.apply {
                // For a JSON key string in the dispatch method, the very next iput stores the
                // parsed value into that key's (obfuscated) field on the DirectItem base class.
                fun fieldAfter(key: String) =
                    method.run {
                        val insns = instructions.toList()
                        val keyIndex =
                            insns.indexOfFirst {
                                (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
                                    (it as ReferenceInstruction).reference.toString() == key
                            }
                        require(keyIndex >= 0) { "const-string '$key' not found in dispatch method" }
                        val iput =
                            insns.drop(keyIndex + 1).firstOrNull {
                                it.opcode.name.startsWith("iput", ignoreCase = true)
                            } ?: error("no iput after '$key'")
                        iput.fieldExtractor()
                    }

                val itemId = fieldAfter("item_id")
                GetItemIdExtension.changeFirstString(itemId.name)
                // The item_id field is declared on the DirectItem base class — reuse its binary
                // name rather than hardcoding the obfuscated class.
                GetBaseClassNameExtension.changeFirstString(itemId.definingClass)

                GetUserIdExtension.changeFirstString(fieldAfter("user_id").name)

                val textField = fieldAfter("text").name
                GetTextExtension.changeFirstString(textField)
                SetTextExtension.changeFirstString(textField)

                GetTimestampRawExtension.changeFirstString(fieldAfter("timestamp").name)

                val hideField = fieldAfter("hide_in_thread").name
                IsHideInThreadExtension.changeFirstString(hideField)
                SetHideInThreadExtension.changeFirstString(hideField)

                GetThreadKeyExtension.changeFirstString(fieldAfter("thread_key").name)

                // item_type enum: the dispatch only stores a raw integer code, but Instagram's own
                // logic uses an enum field on the base class. The item_type enum is the only enum
                // type with two fields on the base class (a primary + a vestigial copy); the
                // primary one sorts last by name. Resolve it without hardcoding the enum class.
                val baseDescriptor = extensionToClassName(itemId.definingClass)
                val baseClass = mutableClassDefBy { it.type == baseDescriptor }

                fun isEnumType(type: String) =
                    runCatching {
                        mutableClassDefBy { it.type == type }.superclass == "Ljava/lang/Enum;"
                    }.getOrDefault(false)

                val itemTypeField =
                    baseClass.fields
                        .filter { !AccessFlags.STATIC.isSet(it.accessFlags) && isEnumType(it.type) }
                        .groupBy { it.type }
                        .maxByOrNull { it.value.size }!!
                        .value
                        .maxByOrNull { it.name }!!
                        .name
                GetItemTypeExtension.changeFirstString(itemTypeField)
            }

            // DirectThreadKey is a stable (non-obfuscated) class but its thread-id field is
            // obfuscated; it is the first declared String instance field.
            val threadKeyClass =
                mutableClassDefBy { it.type == "Lcom/instagram/model/direct/DirectThreadKey;" }
            val threadIdField =
                threadKeyClass.fields
                    .first {
                        !AccessFlags.STATIC.isSet(it.accessFlags) && it.type == "Ljava/lang/String;"
                    }.name
            GetThreadIdExtension.changeFirstString(threadIdField)
        }
    }
