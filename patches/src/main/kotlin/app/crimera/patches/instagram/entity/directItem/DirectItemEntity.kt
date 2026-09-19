/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.directItem

import app.crimera.utils.changeFirstString
import app.crimera.utils.changeString
import app.crimera.utils.changeStringAt
import app.crimera.utils.classNameToExtension
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.crimera.utils.MethodFieldMetadata
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
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
                fun fieldsAfter(key: String) =
                    mutableClassDefBy { it.type == method.definingClass }
                        .methods.flatMap { m ->
                            val insns = runCatching { m.instructions.toList() }.getOrNull()
                                ?: return@flatMap emptyList()
                            insns.indices.mapNotNull { keyIndex ->
                                val instruction = insns[keyIndex]
                                val isKey =
                                    (instruction.opcode == Opcode.CONST_STRING ||
                                        instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
                                        (instruction as ReferenceInstruction).reference.toString() == key
                                if (!isKey) return@mapNotNull null
                                insns.drop(keyIndex + 1).firstOrNull {
                                    it.opcode.name.startsWith("iput", ignoreCase = true)
                                }?.fieldExtractor()
                            }
                        }.distinctBy { Triple(it.definingClass, it.name, it.returnType) }

                fun fieldAfter(key: String): MethodFieldMetadata {
                    val matches = fieldsAfter(key)
                    if (matches.size != 1) {
                        throw PatchException(
                            "Expected one field assignment after '$key' in ${method.definingClass}, " +
                                "found ${matches.size}",
                        )
                    }
                    return matches.single()
                }

                fun stringFieldAfter(key: String): MethodFieldMetadata {
                    val matches = fieldsAfter(key).filter { it.returnType == "java.lang.String" }
                    if (matches.size != 1) {
                        throw PatchException(
                            "Expected one String field after '$key' in ${method.definingClass}, " +
                                "found ${matches.size}",
                        )
                    }
                    return matches.single()
                }

                val itemId = stringFieldAfter("item_id")
                val clientContext = stringFieldAfter("client_context")
                if (clientContext.definingClass != itemId.definingClass) {
                    throw PatchException("DirectItem identifiers resolve to different base classes")
                }
                GetItemIdExtension.changeFirstString(itemId.name)
                GetClientContextExtension.changeFirstString(clientContext.name)
                GetBaseClassNameExtension.changeFirstString(itemId.definingClass)

                GetUserIdExtension.changeFirstString(fieldAfter("user_id").name)

                val textField = fieldAfter("text").name
                GetTextExtension.changeString("baseTextField", textField)
                SetTextExtension.changeString("baseTextField", textField)

                GetTimestampRawExtension.changeFirstString(fieldAfter("timestamp").name)

                val hideField = fieldAfter("hide_in_thread").name
                IsHideInThreadExtension.changeFirstString(hideField)
                SetHideInThreadExtension.changeFirstString(hideField)

                IsSentByViewerExtension.changeFirstString(fieldAfter("is_sent_by_viewer").name)

                GetThreadKeyExtension.changeFirstString(fieldAfter("thread_key").name)

                // item_type + MQTT sub-text: best-effort, never fatal. If a future build restructures the
                // base/sub class so these can't resolve, skip and leave the placeholders — runtime reflection
                // then no-ops instead of aborting the patch (mirrors the media block below).
                runCatching {
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
                    GetTextExtension.changeString("subTextField", it)
                    SetTextExtension.changeString("subTextField", it)
                }
                }

                // Media resolution: best-effort, never fatal. Every supported DM media shape carries a
                // com.instagram.feed.media.Media object — the unobfuscated anchor that survives version
                // rotation. We resolve each shape's field by its stable JSON key on the deserializer.
                // Concrete item class (X/6fW) = the definingClass of those fields (a subclass of the
                // scalar base X/8t8, which is why media MUST be read off it, not the scalar base).
                //
                // NOTE: animated_media, story_share, xma and link are intentionally NOT resolved — their
                // URL sits in an obfuscated wrapper with no stable type/getter anchor (e.g. GifUrlImpl:
                // three indistinguishable String fields). Adding them would be fragile to maintain, so
                // those shapes degrade to a "[type]" label at runtime. See DirectItem.getMediaUrl().
                val mediaClass = "Lcom/instagram/feed/media/Media;"

                // First iput-object stored right after a JSON key in the (split) serializer/deserializer.
                fun iputFieldAfter(key: String) =
                    mutableClassDefBy { it.type == method.definingClass }
                        .methods.firstNotNullOfOrNull { m ->
                            val insns = runCatching { m.instructions.toList() }.getOrNull()
                                ?: return@firstNotNullOfOrNull null
                            val ki = insns.indexOfFirst {
                                (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
                                    (it as ReferenceInstruction).reference.toString() == key
                            }
                            if (ki < 0) return@firstNotNullOfOrNull null
                            insns.drop(ki + 1)
                                .firstOrNull { it.opcode.name.startsWith("iput-object", ignoreCase = true) }
                                ?.let { (it as ReferenceInstruction).reference as? FieldReference }
                        }

                runCatching {
                    // Item class is set once, from the first field that resolves (all share definingClass).
                    var itemClassSet = false
                    fun setItemClass(fr: FieldReference) {
                        if (!itemClassSet) {
                            // Class.forName needs the binary name (X.6fW), not the smali descriptor (LX/6fW;).
                            GetMediaClassNameExtension.changeFirstString(classNameToExtension(fr.definingClass))
                            itemClassSet = true
                        }
                    }

                    // Direct Media fields: type must be Media.
                    fun direct(key: String, fp: Fingerprint) = runCatching {
                        val f = iputFieldAfter(key) ?: return@runCatching
                        if (f.type != mediaClass) return@runCatching
                        setItemClass(f); fp.changeFirstString(f.name)
                    }
                    direct("media", FieldMediaExtension)
                    direct("media_share", FieldMediaShareExtension)
                    direct("raven_media", FieldRavenMediaExtension)

                    // Wrapped shapes: wrapper field on the item class, then the wrapper's single Media
                    // field, found BY TYPE (so the obfuscated wrapper name is never depended on).
                    fun wrapped(
                        key: String,
                        wrapperFp: Fingerprint,
                        innerFp: Fingerprint,
                    ) = runCatching {
                        val wf = iputFieldAfter(key) ?: return@runCatching
                        val inner = mutableClassDefBy { it.type == wf.type }
                            .fields.firstOrNull { it.type == mediaClass } ?: return@runCatching
                        setItemClass(wf)
                        wrapperFp.changeFirstString(wf.name)
                        innerFp.changeFirstString(inner.name)
                    }
                    wrapped("clip", FieldClipExtension, FieldClipMediaExtension)
                    wrapped("reel_share", FieldReelExtension, FieldReelMediaExtension)
                    wrapped("voice_media", FieldVoiceExtension, FieldVoiceMediaExtension)
                    // Disappearing media: carries the payload that raven_media leaves null.
                    wrapped("visual_media", FieldVisualExtension, FieldVisualMediaExtension)
                }

                // xma reshare permalink: best-effort, never fatal. An xma reshare (shared post/reel)
                // has no Media object; instead the item holds a List of xma elements (built by a
                // converter method) whose permalink is a String under JSON key "target_url". Resolve
                // the item's xma List field and the element's permalink field at patch time so the
                // runtime does named reflection only. If neither can be resolved on a future build,
                // xma degrades to the "[type]" label (see DirectItem).
                //
                // The permalink field is confirmed by the "target_url" -> String iput on the element
                // class; the item's xma List field is disambiguated (from three sibling List fields
                // that share the same converter) by following the xma_ dispatch keys' branch target.
                runCatching {
                    val itemClass = mutableClassDefBy { it.type == method.definingClass }

                    // Only the deserializer stores target_url with an iput; the serializer uses a call.
                    fun targetUrlField(parserType: String): FieldReference? {
                        val cls = runCatching { mutableClassDefBy { it.type == parserType } }.getOrNull()
                            ?: return null
                        return cls.methods.firstNotNullOfOrNull { m ->
                            val insns = runCatching { m.instructions.toList() }.getOrNull()
                                ?: return@firstNotNullOfOrNull null
                            val ki = insns.indexOfFirst {
                                (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
                                    (it as ReferenceInstruction).reference.toString() == "target_url"
                            }
                            if (ki < 0) return@firstNotNullOfOrNull null
                            insns.drop(ki + 1)
                                .takeWhile {
                                    it.opcode != Opcode.CONST_STRING && it.opcode != Opcode.CONST_STRING_JUMBO
                                }
                                .firstOrNull {
                                    it.opcode == Opcode.IPUT_OBJECT &&
                                        ((it as ReferenceInstruction).reference as? FieldReference)
                                            ?.type == "Ljava/lang/String;"
                                }?.let { (it as ReferenceInstruction).reference as FieldReference }
                        }
                    }

                    var xmaField: FieldReference? = null
                    var linkField: FieldReference? = null
                    run {
                        for (m in itemClass.methods) {
                            val insns = runCatching { m.instructions.toList() }.getOrNull() ?: continue
                            // The item stores four List<xma-element> fields, all built from the same
                            // converter (so all share "target_url"); only the one reached from the
                            // "xma_*" reshare keys is the field we want. Each xma_ key does
                            // equals -> if-nez -> shared handler block, so follow that branch to pin
                            // the correct block, then read its List iput + converter.
                            val xmaKeyIdx = insns.indexOfFirst {
                                (it.opcode == Opcode.CONST_STRING || it.opcode == Opcode.CONST_STRING_JUMBO) &&
                                    (it as ReferenceInstruction).reference.toString().startsWith("xma_")
                            }
                            if (xmaKeyIdx < 0) continue
                            val ifNez = insns.drop(xmaKeyIdx + 1).firstOrNull { it.opcode == Opcode.IF_NEZ } ?: continue
                            val targetAddr = ifNez.location.codeAddress + (ifNez as OffsetInstruction).codeOffset
                            val targetIdx = insns.indexOfFirst { it.location.codeAddress == targetAddr }
                            if (targetIdx < 0) continue
                            val block = insns.drop(targetIdx)
                            val listPut = block.firstOrNull {
                                it.opcode == Opcode.IPUT_OBJECT &&
                                    ((it as ReferenceInstruction).reference as? FieldReference)?.type == "Ljava/util/List;"
                            }?.let { (it as ReferenceInstruction).reference as FieldReference } ?: continue
                            // The call is declared on the abstract base, so take the parser class
                            // from the sget rather than the call itself.
                            val parseIdx = block.indexOfFirst {
                                it.opcode == Opcode.INVOKE_VIRTUAL &&
                                    ((it as ReferenceInstruction).reference as? MethodReference)
                                        ?.name == "parseFromJsonParser"
                            }
                            if (parseIdx < 0) continue
                            val parserType = block.take(parseIdx)
                                .lastOrNull { it.opcode == Opcode.SGET_OBJECT }
                                ?.let { (it as ReferenceInstruction).reference as? FieldReference }
                                ?.type ?: continue
                            val lf = targetUrlField(parserType) ?: continue
                            xmaField = listPut
                            linkField = lf
                            return@run
                        }
                    }
                    val xf = xmaField ?: error("xma converter not found in ${method.definingClass}")
                    FieldXmaExtension.changeFirstString(xf.name)
                    FieldXmaLinkExtension.changeFirstString(linkField!!.name)
                }
            }

            // Resolve the exact mThreadId access from the stable toString label.
            val threadKeyClass =
                mutableClassDefBy { it.type == "Lcom/instagram/model/direct/DirectThreadKey;" }
            val toStringMethod =
                threadKeyClass.methods.singleOrNull {
                    it.name == "toString" && it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;"
                } ?: throw PatchException("Expected one DirectThreadKey.toString() method")
            val threadInstructions = toStringMethod.instructions.toList()
            val threadIdFields =
                threadInstructions.indices.mapNotNull { labelIndex ->
                    val instruction = threadInstructions[labelIndex]
                    val isThreadIdLabel =
                        (instruction.opcode == Opcode.CONST_STRING ||
                            instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
                            (instruction as ReferenceInstruction).reference.toString()
                                .contains("mThreadId")
                    if (!isThreadIdLabel) return@mapNotNull null

                    threadInstructions.drop(labelIndex + 1).firstOrNull {
                        it.opcode == Opcode.IGET_OBJECT &&
                            ((it as ReferenceInstruction).reference as? FieldReference)?.let { field ->
                                field.definingClass ==
                                    "Lcom/instagram/model/direct/DirectThreadKey;" &&
                                    field.type == "Ljava/lang/String;"
                            } == true
                    }?.let { (it as ReferenceInstruction).reference as FieldReference }
                }.distinctBy { Triple(it.definingClass, it.name, it.type) }
            if (threadIdFields.size != 1) {
                throw PatchException(
                    "Expected one DirectThreadKey mThreadId field, " +
                        "found ${threadIdFields.size}",
                )
            }
            val threadIdField = threadIdFields.single()
            GetThreadIdExtension.changeFirstString(threadIdField.name)
        }
    }
