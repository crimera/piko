/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.directItem

import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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
                // Scan all methods: v426 deserializer is A00, v430+ moved it to unsafeParseFromJson.
                fun fieldAfter(key: String) =
                    mutableClassDefBy { it.type == method.definingClass }
                        .methods.firstNotNullOfOrNull { m ->
                            val insns = runCatching { m.instructions.toList() }.getOrNull()
                                ?: return@firstNotNullOfOrNull null
                            val keyIndex =
                                insns.indexOfFirst {
                                    (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
                                        (it as ReferenceInstruction).reference.toString() == key
                                }
                            if (keyIndex < 0) return@firstNotNullOfOrNull null
                            insns.drop(keyIndex + 1).firstOrNull {
                                it.opcode.name.startsWith("iput", ignoreCase = true)
                            }?.fieldExtractor()
                        } ?: error("no iput after '$key' in ${method.definingClass}")

                val itemId = fieldAfter("item_id")
                GetItemIdExtension.changeFirstString(itemId.name)
                GetBaseClassNameExtension.changeFirstString(itemId.definingClass)

                GetUserIdExtension.changeFirstString(fieldAfter("user_id").name)

                val textField = fieldAfter("text").name
                GetTextExtension.changeStringAt(0, textField)
                SetTextExtension.changeStringAt(0, textField)

                GetTimestampRawExtension.changeFirstString(fieldAfter("timestamp").name)

                val hideField = fieldAfter("hide_in_thread").name
                IsHideInThreadExtension.changeFirstString(hideField)
                SetHideInThreadExtension.changeFirstString(hideField)

                IsSentByViewerExtension.changeFirstString(fieldAfter("is_sent_by_viewer").name)

                GetThreadKeyExtension.changeFirstString(fieldAfter("thread_key").name)

                // item_type: the only enum with 2+ fields on the base class; primary sorts last by name.
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

                // MQTT items store text in an Object field on the subclass, set after the item-type setter.
                val itemTypeEnum = baseClass.fields.first { it.name == itemTypeField }.type
                val subClass = mutableClassDefBy { it.superclass == baseDescriptor }
                val subTextField =
                    subClass.methods.firstNotNullOfOrNull { m ->
                        val insns = runCatching { m.instructions.toList() }.getOrNull()
                            ?: return@firstNotNullOfOrNull null
                        insns.indices.firstNotNullOfOrNull fn@{ i ->
                            val insn = insns[i]
                            if (insn.opcode != Opcode.INVOKE_VIRTUAL) return@fn null
                            val ref = (insn as ReferenceInstruction).reference as? MethodReference
                            if (ref == null || ref.parameterTypes.size != 1 ||
                                ref.parameterTypes[0].toString() != itemTypeEnum
                            ) {
                                return@fn null
                            }
                            insns.drop(i + 1).take(3)
                                .firstOrNull { it.opcode.name.startsWith("iput", true) }
                                ?.let { (it as ReferenceInstruction).reference as? FieldReference }
                                ?.takeIf { it.type == "Ljava/lang/Object;" }
                                ?.name
                        }
                    }
                subTextField?.let {
                    GetTextExtension.changeStringAt(1, it)
                    SetTextExtension.changeStringAt(1, it)
                }

                // Media field: best-effort, never fatal. v430+: iput-object directly in dispatch body; v426: inside a setter.
                val mediaClass = "Lcom/instagram/feed/media/Media;"
                runCatching {
                    method.run {
                        val insns = instructions.toList()
                        val mediaKeyIndex =
                            insns.indexOfFirst {
                                (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
                                    (it as ReferenceInstruction).reference.toString() == "media"
                            }
                        require(mediaKeyIndex >= 0) { "const-string 'media' not found" }
                        val tail = insns.drop(mediaKeyIndex + 1)

                        tail.firstOrNull {
                            it.opcode.name.startsWith("iput-object", ignoreCase = true) &&
                                (it as ReferenceInstruction).reference
                                    .let { r -> r is FieldReference && r.type == mediaClass }
                        }?.fieldExtractor()
                            ?: run {
                                // v426: iput-object is inside a setter called with the Media arg.
                                val setterRef =
                                    tail.asSequence()
                                        .mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
                                        .firstOrNull { r -> r.parameterTypes.any { it.toString() == mediaClass } }
                                        ?: error("no Media iput or setter after 'media' key")
                                val setter =
                                    mutableClassDefBy { it.type == setterRef.definingClass }
                                        .methods.first {
                                            it.name == setterRef.name &&
                                                it.parameterTypes.map(Any::toString) ==
                                                setterRef.parameterTypes.map(Any::toString)
                                        }
                                setter.instructions
                                    .first { it.opcode.name.startsWith("iput-object", ignoreCase = true) }
                                    .fieldExtractor()
                            }
                    }
                }.onSuccess { GetMediaObjectExtension.changeFirstString(it.name) }
            }

            // DirectThreadKey is stable but its thread-id field is obfuscated: first String instance field.
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
