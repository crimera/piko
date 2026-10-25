package app.crimera.patches.newx.misc.inlineactions

// Guards the emitted composition predicate against field-owner/register mistakes and broken
// iterator control flow, which can produce verifier failures or hide downloadable media.

import app.crimera.bytecode.fieldReference
import app.crimera.patches.newx.models.ResolvedNewXInlineMediaModels
import app.crimera.patches.newx.models.resolveInlineGifVariantsField
import app.crimera.patches.newx.models.resolveInlineMediaVariantContentTypeField
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InlineMediaPredicatePatchTest {
    @Test
    fun `GIF variants resolver follows terminal packed label and rejects another matching field`() {
        val owner = "Lapp/crimera/test/Gif;"
        val variantsType = "Lapp/crimera/test/ImmutableList;"
        val expectedField = fieldReference("$owner->variants:$variantsType")

        assertEquals(
            expectedField.toString(),
            resolveInlineGifVariantsField(
                gifToStringFixture(owner, variantsType),
                owner,
                variantsType,
            ).toString(),
        )
        assertFailsWith<PatchException> {
            resolveInlineGifVariantsField(
                gifToStringFixture(owner, variantsType, includeExtraSameType = true),
                owner,
                variantsType,
            )
        }
    }

    @Test
    fun `variant content type schema resolves only the other validated string field`() {
        val owner = "Lapp/crimera/test/Variant;"
        val urlField = fieldReference("$owner->url:Ljava/lang/String;")
        val contentTypeField = fieldReference("$owner->contentType:Ljava/lang/String;")

        assertEquals(
            contentTypeField.toString(),
            resolveInlineMediaVariantContentTypeField(
                variantToStringFixture(owner),
                owner,
                urlField,
            ).toString(),
        )
        assertFailsWith<PatchException> {
            resolveInlineMediaVariantContentTypeField(
                variantToStringFixture(owner, includeExtraString = true),
                owner,
                urlField,
            )
        }
        assertFailsWith<PatchException> {
            resolveInlineMediaVariantContentTypeField(
                variantToStringFixture(owner, bitrateType = "Ljava/lang/Long;"),
                owner,
                urlField,
            )
        }
    }

    @Test
    fun `composition predicate uses typed fields and paired iterator calls`() {
        val models = ResolvedNewXInlineMediaModels(
            sharedMediaBaseDescriptor = "Lapp/crimera/test/MediaBase;",
            imageMediaDescriptor = "Lapp/crimera/test/Image;",
            imageUrlField = fieldReference("Lapp/crimera/test/Image;->imageUrl:Ljava/lang/String;"),
            gifMediaDescriptor = "Lapp/crimera/test/Gif;",
            gifVariantsField = fieldReference("Lapp/crimera/test/Gif;->variants:Ljava/util/List;"),
            videoMediaDescriptor = "Lapp/crimera/test/Video;",
            videoVariantsField = fieldReference("Lapp/crimera/test/Video;->variants:Ljava/util/List;"),
            variantDescriptor = "Lapp/crimera/test/Variant;",
            variantUrlField = fieldReference("Lapp/crimera/test/Variant;->url:Ljava/lang/String;"),
            variantContentTypeField = fieldReference("Lapp/crimera/test/Variant;->contentType:Ljava/lang/String;"),
        )
        val method = predicateStub()

        val patched = patchCompositionMediaPredicate(method, models)
        val emitted = patched.instructions.toList()
        val fields = emitted.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
            instruction.getReference<FieldReference>()?.toString()
        }
        assertEquals(
            setOf(
                models.imageUrlField.toString(),
                models.gifVariantsField.toString(),
                models.videoVariantsField.toString(),
                models.variantUrlField.toString(),
                models.variantContentTypeField.toString(),
            ),
            fields.toSet(),
        )

        val checkedTypes = emitted.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.INSTANCE_OF) return@mapNotNull null
            instruction.getReference<TypeReference>()?.type
        }
        assertTrue(checkedTypes.containsAll(listOf(
            models.imageMediaDescriptor,
            models.gifMediaDescriptor,
            models.videoMediaDescriptor,
            models.variantDescriptor,
        )), checkedTypes.toString())
        // Native variant fields are resolved as List; the emitted interface cast and iterator loop
        // use List's inherited Iterable contract without requiring the patch-time resolver to infer it.
        assertTrue(emitted.any { instruction ->
            instruction.opcode == Opcode.CHECK_CAST &&
                instruction.getReference<TypeReference>()?.type == "Ljava/lang/Iterable;"
        })

        val typedFieldByModel = mapOf(
            models.imageMediaDescriptor to models.imageUrlField,
            models.gifMediaDescriptor to models.gifVariantsField,
            models.videoMediaDescriptor to models.videoVariantsField,
        )
        val typedCasts = emitted.mapIndexedNotNull { index, instruction ->
            val type = instruction.getReference<TypeReference>()?.type ?: return@mapIndexedNotNull null
            if (instruction.opcode != Opcode.CHECK_CAST || type !in typedFieldByModel) {
                return@mapIndexedNotNull null
            }
            index to instruction
        }
        assertEquals(typedFieldByModel.size, typedCasts.size)
        typedCasts.forEach { (index, instruction) ->
            val receiver = (instruction as OneRegisterInstruction).registerA
            val fieldRead = emitted[index + 1]
            assertEquals(Opcode.IGET_OBJECT, fieldRead.opcode)
            assertEquals(
                typedFieldByModel.getValue(instruction.getReference<TypeReference>()!!.type).toString(),
                fieldRead.getReference<FieldReference>()?.toString(),
            )
            assertEquals(receiver, (fieldRead as TwoRegisterInstruction).registerB)

            val receiverCopy = emitted.take(index).mapIndexedNotNull { copyIndex, candidate ->
                val copy = candidate as? TwoRegisterInstruction ?: return@mapIndexedNotNull null
                if (
                    candidate.opcode in setOf(
                        Opcode.MOVE_OBJECT,
                        Opcode.MOVE_OBJECT_FROM16,
                        Opcode.MOVE_OBJECT_16,
                    ) && copy.registerA == receiver
                ) {
                    copyIndex to copy
                } else {
                    null
                }
            }.lastOrNull()?.second
            assertEquals(patched.p0Register, receiverCopy?.registerB, "typed receiver source")
        }

        val code = emitted
        val importantCalls = code.mapIndexedNotNull { index, instruction ->
            val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
            if (reference.name !in setOf("isHttpUrl", "iterator", "hasNext", "next", "isHttpMp4Variant")) {
                return@mapIndexedNotNull null
            }
            index to reference
        }
        assertEquals(
            setOf("isHttpUrl", "iterator", "hasNext", "next", "isHttpMp4Variant"),
            importantCalls.map { it.second.name }.toSet(),
        )
        importantCalls.forEach { (index, reference) ->
            val expectedResult = if (reference.returnType == "Z") Opcode.MOVE_RESULT else Opcode.MOVE_RESULT_OBJECT
            assertEquals(expectedResult, code.getOrNull(index + 1)?.opcode, reference.toString())
        }

        assertTrue(code.any { it.opcode == Opcode.IF_EQZ })
        assertTrue(code.any { it.opcode == Opcode.IF_NEZ })
        assertTrue(code.any { it.opcode == Opcode.GOTO })
        assertTrue(code.none { instruction ->
            instruction.getReference<MethodReference>()?.name == "toString"
        })
        val constantOpcodes = setOf(Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST, Opcode.CONST_HIGH16)
        assertTrue(code.any { instruction ->
            instruction.opcode in constantOpcodes &&
                (instruction as? NarrowLiteralInstruction)?.narrowLiteral == 0
        })
        assertTrue(code.any { instruction ->
            instruction.opcode in constantOpcodes &&
                (instruction as? NarrowLiteralInstruction)?.narrowLiteral == 1
        })
        val falseExits = code.indices.filter { index ->
            val instruction = code[index]
            val returned = code.getOrNull(index + 1)
            instruction.opcode in constantOpcodes &&
                (instruction as? NarrowLiteralInstruction)?.narrowLiteral == 0 &&
                returned?.opcode == Opcode.RETURN &&
                (instruction as OneRegisterInstruction).registerA ==
                (returned as OneRegisterInstruction).registerA
        }.toSet()
        var offset = 0
        val offsets = code.map { instruction -> offset.also { offset += instruction.codeUnits } }
        val indicesByOffset = offsets.withIndex().associate { it.value to it.index }
        assertTrue(code.withIndex().any { (index, instruction) ->
            instruction.opcode == Opcode.IF_EQZ &&
                indicesByOffset[offsets[index] + (instruction as OffsetInstruction).codeOffset] in falseExits
        }, "unsupported/exhausted paths must branch to a false return")
    }

    private fun predicateStub(): MutableMethod {
        val implementation = MethodImplementationBuilder(2)
        implementation.addInstruction("const/4 v0, 0".toInstruction())
        implementation.addInstruction("return v0".toInstruction())
        return MutableMethod(
            ImmutableMethod(
                "Lapp/crimera/test/InlineDownloadButton;",
                "isCompositionDownloadableMedia",
                listOf(ImmutableMethodParameter("Ljava/lang/Object;", emptySet(), null)),
                "Z",
                AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                implementation.methodImplementation,
            ),
        )
    }

    private fun gifToStringFixture(
        owner: String,
        variantsType: String,
        includeExtraSameType: Boolean = false,
    ): Method {
        val implementation = MethodImplementationBuilder(4)
        implementation.addInstruction("const-string v0, \", variants=\"".toInstruction())
        implementation.addInstruction("const-string v1, \"MediaContentGif(mediaId=\"".toInstruction())
        implementation.addInstruction(
            (
                "invoke-static {v1, v0}, Lapp/crimera/test/PackedBuilder;->build(" +
                    "Ljava/lang/String;Ljava/lang/String;)Ljava/lang/StringBuilder;"
                ).toInstruction(),
        )
        implementation.addInstruction("move-result-object v0".toInstruction())
        implementation.addInstruction("iget-object v1, v3, $owner->variants:$variantsType".toInstruction())
        implementation.addInstruction(
            "invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;"
                .toInstruction(),
        )
        if (includeExtraSameType) {
            implementation.addInstruction("iget-object v2, v3, $owner->other:$variantsType".toInstruction())
            implementation.addInstruction(
                "invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;"
                    .toInstruction(),
            )
        }
        implementation.addInstruction("const-string v1, \")\"".toInstruction())
        implementation.addInstruction(
            "invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;"
                .toInstruction(),
        )
        implementation.addInstruction(
            "invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;".toInstruction(),
        )
        implementation.addInstruction("move-result-object v0".toInstruction())
        implementation.addInstruction("return-object v0".toInstruction())
        return ImmutableMethod(
            owner,
            "toString",
            emptyList<ImmutableMethodParameter>(),
            "Ljava/lang/String;",
            AccessFlags.PUBLIC.value,
            emptySet<ImmutableAnnotation>(),
            emptySet<HiddenApiRestriction>(),
            implementation.methodImplementation,
        )
    }

    private fun variantToStringFixture(
        owner: String,
        includeExtraString: Boolean = false,
        bitrateType: String = "Ljava/lang/Integer;",
    ): Method {
        val implementation = MethodImplementationBuilder(6)
        implementation.addInstruction("const-string v0, \"MediaVariant(url=\"".toInstruction())
        implementation.addInstruction("const-string v1, \", bitRate=\"".toInstruction())
        implementation.addInstruction("const-string v2, \", contentType=\"".toInstruction())
        implementation.addInstruction("iget-object v4, v5, $owner->url:Ljava/lang/String;".toInstruction())
        implementation.addInstruction("iget-object v4, v5, $owner->bitrate:$bitrateType".toInstruction())
        implementation.addInstruction("iget-object v4, v5, $owner->contentType:Ljava/lang/String;".toInstruction())
        if (includeExtraString) {
            implementation.addInstruction("iget-object v4, v5, $owner->extra:Ljava/lang/String;".toInstruction())
        }
        implementation.addInstruction("const-string v0, \"result\"".toInstruction())
        implementation.addInstruction("return-object v0".toInstruction())
        return ImmutableMethod(
            owner,
            "toString",
            emptyList<ImmutableMethodParameter>(),
            "Ljava/lang/String;",
            AccessFlags.PUBLIC.value,
            emptySet<ImmutableAnnotation>(),
            emptySet<HiddenApiRestriction>(),
            implementation.methodImplementation,
        )
    }
}
