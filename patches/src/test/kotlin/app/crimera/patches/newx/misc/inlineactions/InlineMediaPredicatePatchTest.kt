package app.crimera.patches.newx.misc.inlineactions

// Guards the emitted composition predicate against field-owner/register mistakes and broken
// iterator control flow, which can produce verifier failures or hide downloadable media.

import app.crimera.bytecode.fieldReference
import app.crimera.bytecode.methodReference
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
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val VIDEO_TAB_OBJECT = "Ljava/lang/Object;"
private const val VIDEO_TAB_STRING = "Ljava/lang/String;"
private const val VIDEO_TAB_HANDLER = "Lapp/crimera/test/VideoTabHandler;"
private const val VIDEO_TAB_EVENT = "Lapp/crimera/test/VideoTabEvent;"
private const val VIDEO_TAB_DOWNLOADER = "Lapp/crimera/test/Downloader;"
private const val VIDEO_TAB_WRAPPER = "Lapp/crimera/test/ProgressEventWrapper;"
private const val VIDEO_TAB_WRAPPER_EVENT = "Lapp/crimera/test/ProgressEventPayload;"
private const val VIDEO_TAB_TIMELINE_POST = "Lapp/crimera/test/TimelinePost;"
private const val VIDEO_TAB_CANONICAL_POST = "Lapp/crimera/test/CanonicalPost;"
private const val VIDEO_TAB_OTHER_POST = "Lapp/crimera/test/OtherPost;"
private const val VIDEO_TAB_MEDIA_PAYLOAD = "Lapp/crimera/test/MediaPayload;"
private const val VIDEO_TAB_CALLBACK = "Lapp/crimera/test/Callback;"
private const val VIDEO_TAB_FLAGS = "Lapp/crimera/test/Flags;"
private const val VIDEO_TAB_FLAG = "subscriptions_watermarked_video_download_enabled"
private const val VIDEO_TAB_ACCESSOR = "timelineCanonicalPost"
private const val VIDEO_TAB_DISPATCH =
    "$VIDEO_TAB_DOWNLOADER->dispatch($VIDEO_TAB_STRING$VIDEO_TAB_STRING$VIDEO_TAB_MEDIA_PAYLOAD$VIDEO_TAB_CALLBACK)V"
private const val VIDEO_TAB_METADATA =
    "Lapp/crimera/test/MediaMetadata;->build($VIDEO_TAB_STRING$VIDEO_TAB_CANONICAL_POST)$VIDEO_TAB_MEDIA_PAYLOAD"
private const val VIDEO_TAB_APPEND_OBJECT =
    "invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;"
private const val VIDEO_TAB_APPEND_STRING =
    "invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;"

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

    @Test
    fun `video-tab post tuple resolves accessor and inlined producers with or without unrelated post reads`() {
        listOf(
            VideoTabShape(),
            VideoTabShape(unrelatedReads = true),
            VideoTabShape(producer = VideoTabProducer.INLINED),
            VideoTabShape(producer = VideoTabProducer.INLINED, unrelatedReads = true),
        ).forEach { shape -> assertVideoTabTupleResolves(shape) }
    }

    @Test
    fun `video-tab post tuple accepts a high receiver whose accessor and dispatch invokes are range form`() {
        listOf(
            VideoTabShape(highReceiver = true),
            VideoTabShape(producer = VideoTabProducer.INLINED, highReceiver = true),
        ).forEach { shape -> assertVideoTabTupleResolves(shape) }
    }

    @Test
    fun `video-tab post tuple accepts a receiver alias overwritten only on a branch that returns`() {
        listOf(
            VideoTabShape(aliasOverwrite = VideoTabAliasOverwrite.RETURNING),
            VideoTabShape(
                producer = VideoTabProducer.INLINED,
                aliasOverwrite = VideoTabAliasOverwrite.RETURNING,
                unrelatedReads = true,
            ),
        ).forEach { shape -> assertVideoTabTupleResolves(shape) }
    }

    @Test
    fun `video-tab post tuple ignores harmless object moves before the guard and in the producer arm`() {
        listOf(
            VideoTabShape(harmlessMoves = true),
            VideoTabShape(producer = VideoTabProducer.INLINED, harmlessMoves = true, unrelatedReads = true),
        ).forEach { shape -> assertVideoTabTupleResolves(shape) }
    }

    @Test
    fun `video-tab post tuple keeps receiver identity through a receiver check-cast`() {
        listOf(
            VideoTabShape(receiverCheckCast = true),
            VideoTabShape(producer = VideoTabProducer.INLINED, receiverCheckCast = true),
        ).forEach { shape -> assertVideoTabTupleResolves(shape) }
    }

    @Test
    fun `video-tab post tuple rejects a payload overwritten by an array load before the watermark dispatch`() {
        listOf(
            VideoTabShape(payloadArrayOverwrite = true),
            VideoTabShape(producer = VideoTabProducer.INLINED, payloadArrayOverwrite = true),
        ).forEach { shape -> assertVideoTabTupleRejected(shape) }
    }

    @Test
    fun `video-tab post tuple rejects receiver overwrites that reach the download path`() {
        listOf(
            VideoTabShape(aliasOverwrite = VideoTabAliasOverwrite.REACHING),
            VideoTabShape(producer = VideoTabProducer.INLINED, aliasOverwrite = VideoTabAliasOverwrite.REACHING),
        ).forEach { shape -> assertVideoTabTupleRejected(shape) }
    }

    @Test
    fun `video-tab post tuple rejects wrong or missing field to metadata relations`() {
        listOf(
            VideoTabShape(producerField = "repost"),
            VideoTabShape(producer = VideoTabProducer.INLINED, producerField = "repost"),
            VideoTabShape(formatterPostField = "badPost"),
            VideoTabShape(formatterPostField = null),
            VideoTabShape(producer = VideoTabProducer.INLINED, staleMetadataPost = true),
            VideoTabShape(nullPayload = true),
        ).forEach { shape -> assertVideoTabTupleRejected(shape) }
    }

    @Test
    fun `video-tab post tuple rejects unexpected branch edges into the nullable producer block`() {
        listOf(
            VideoTabShape(entry = VideoTabEntry.SUCCESS_ARM),
            VideoTabShape(producer = VideoTabProducer.INLINED, entry = VideoTabEntry.SUCCESS_ARM),
            VideoTabShape(entry = VideoTabEntry.NULL_ARM),
            VideoTabShape(entry = VideoTabEntry.MERGE),
            VideoTabShape(producer = VideoTabProducer.INLINED, entry = VideoTabEntry.MERGE),
        ).forEach { shape -> assertVideoTabTupleRejected(shape) }
    }

    @Test
    fun `video-tab post tuple accepts an Object post result cast to the canonical post`() {
        listOf(
            VideoTabShape(postResultCast = true),
            VideoTabShape(producer = VideoTabProducer.INLINED, postResultCast = true),
        ).forEach { shape -> assertVideoTabTupleResolves(shape) }
    }

    private enum class VideoTabProducer { ACCESSOR, INLINED }

    private enum class VideoTabAliasOverwrite { NONE, RETURNING, REACHING }

    private enum class VideoTabEntry { NONE, SUCCESS_ARM, NULL_ARM, MERGE }

    /** Toggles for one handler shape; defaults are the release-shaped accessor producer. */
    private data class VideoTabShape(
        val producer: VideoTabProducer = VideoTabProducer.ACCESSOR,
        val highReceiver: Boolean = false,
        val unrelatedReads: Boolean = false,
        val aliasOverwrite: VideoTabAliasOverwrite = VideoTabAliasOverwrite.NONE,
        val entry: VideoTabEntry = VideoTabEntry.NONE,
        val producerField: String = "post",
        val formatterPostField: String? = "post",
        val staleMetadataPost: Boolean = false,
        val nullPayload: Boolean = false,
        val harmlessMoves: Boolean = false,
        val receiverCheckCast: Boolean = false,
        val postResultCast: Boolean = false,
        val payloadArrayOverwrite: Boolean = false,
    )

    private class VideoTabFixture(
        val method: Method,
        val handlerFields: List<ImmutableField>,
        val classes: Map<String, ClassDef>,
        val dispatchIndex: Int,
    )

    private val wrapperFieldTypes = mapOf(
        "event" to VIDEO_TAB_WRAPPER_EVENT,
        "post" to VIDEO_TAB_TIMELINE_POST,
        "repost" to VIDEO_TAB_TIMELINE_POST,
        "badPost" to VIDEO_TAB_OTHER_POST,
    )

    private fun assertVideoTabTupleResolves(shape: VideoTabShape) {
        val tuple = resolveVideoTabTuple(shape)
        assertEquals(
            fieldReference("$VIDEO_TAB_HANDLER->container:$VIDEO_TAB_WRAPPER").toString(),
            tuple.containerField.toString(),
            shape.toString(),
        )
        assertEquals(
            fieldReference("$VIDEO_TAB_WRAPPER->post:$VIDEO_TAB_TIMELINE_POST").toString(),
            tuple.postField.toString(),
            shape.toString(),
        )
    }

    private fun assertVideoTabTupleRejected(shape: VideoTabShape) {
        assertFailsWith<PatchException>(shape.toString()) { resolveVideoTabTuple(shape) }
    }

    private fun resolveVideoTabTuple(shape: VideoTabShape): ResolvedVideoTabPostTuple {
        val fixture = videoTabFixture(shape)
        return resolveVideoTabPostTuple(
            method = fixture.method,
            handlerFields = fixture.handlerFields,
            postDescriptor = VIDEO_TAB_TIMELINE_POST,
            postResultField = fieldReference("$VIDEO_TAB_TIMELINE_POST->postResult:${postResultType(shape)}"),
            watermarkedDispatch = methodReference(VIDEO_TAB_DISPATCH),
            watermarkedDispatchIndex = fixture.dispatchIndex,
            classFor = fixture.classes::get,
        )
    }

    private fun videoTabFixture(shape: VideoTabShape): VideoTabFixture {
        // The high layout moves `this` above v15: the handler must alias it with move-object/from16
        // and every invoke that touches the staged registers must be /range.
        val high = shape.highReceiver
        val receiver = if (high) 20 else 10
        val parameter = receiver + 1
        val downloader = if (high) 14 else 1
        val url = downloader + 1
        val mime = downloader + 2
        val post = 4
        val wrapper = 5
        val scratch = 8
        val payload = if (high) 17 else 6
        val callback = if (high) 18 else 7
        val implementation = MethodImplementationBuilder(parameter + 1).apply {
            addInstruction("move-object/from16 v0, v$receiver".toInstruction())
            // check-cast keeps the object identity, so the alias must survive it.
            if (shape.receiverCheckCast) addInstruction("check-cast v0, $VIDEO_TAB_HANDLER".toInstruction())
            when (shape.aliasOverwrite) {
                VideoTabAliasOverwrite.NONE -> Unit
                VideoTabAliasOverwrite.RETURNING -> {
                    addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, parameter, getLabel("resume")))
                    addInstruction("const/4 v0, 0x0".toInstruction())
                    addInstruction("return-void".toInstruction())
                }
                VideoTabAliasOverwrite.REACHING -> {
                    addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, parameter, getLabel("resume")))
                    addInstruction("const/4 v0, 0x0".toInstruction())
                }
            }
            addLabel("resume")
            addInstruction("iget-object v$downloader, v0, $VIDEO_TAB_HANDLER->downloader:$VIDEO_TAB_DOWNLOADER".toInstruction())
            addInstruction("const-string v$url, \"https://example.test/media.mp4\"".toInstruction())
            addInstruction("const-string v$mime, \"video/mp4\"".toInstruction())
            addInstruction("const-string v$post, \"$VIDEO_TAB_FLAG\"".toInstruction())
            addInstruction("invoke-static {v$post}, $VIDEO_TAB_FLAGS->enabled(Ljava/lang/String;)Z".toInstruction())
            addInstruction("move-result v$post".toInstruction())
            if (shape.unrelatedReads) {
                addInstruction("iget-object v$scratch, v0, $VIDEO_TAB_HANDLER->inlinePost:$VIDEO_TAB_TIMELINE_POST".toInstruction())
            }
            addInstruction("iget-object v$wrapper, v0, $VIDEO_TAB_HANDLER->container:$VIDEO_TAB_WRAPPER".toInstruction())
            if (shape.unrelatedReads) {
                addInstruction("iget-object v$scratch, v$wrapper, $VIDEO_TAB_WRAPPER->post:$VIDEO_TAB_TIMELINE_POST".toInstruction())
            }
            when (shape.entry) {
                VideoTabEntry.NONE -> Unit
                VideoTabEntry.SUCCESS_ARM -> addInstruction(
                    BuilderInstruction21t(Opcode.IF_NEZ, parameter, getLabel("successTail")),
                )
                VideoTabEntry.NULL_ARM -> addInstruction(
                    BuilderInstruction21t(Opcode.IF_EQZ, parameter, getLabel("nullArm")),
                )
                // An external edge straight into the merge skips the null guard and feeds an unchecked post.
                VideoTabEntry.MERGE -> addInstruction(
                    BuilderInstruction21t(Opcode.IF_NEZ, parameter, getLabel("metadataMerge")),
                )
            }
            // Unused object copies are dead data flow; they must not shift branch or producer matching.
            if (shape.harmlessMoves) addInstruction("move-object v9, v$wrapper".toInstruction())
            addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, wrapper, getLabel("nullArm")))
            when (shape.producer) {
                VideoTabProducer.ACCESSOR -> {
                    val call = if (high) {
                        "invoke-virtual/range {v$wrapper .. v$wrapper}"
                    } else {
                        "invoke-virtual {v$wrapper}"
                    }
                    addInstruction("$call, $VIDEO_TAB_WRAPPER->$VIDEO_TAB_ACCESSOR()$VIDEO_TAB_CANONICAL_POST".toInstruction())
                    addInstruction("move-result-object v$post".toInstruction())
                }
                VideoTabProducer.INLINED -> {
                    val field = "${shape.producerField}:${wrapperFieldTypes.getValue(shape.producerField)}"
                    addInstruction("iget-object v$post, v$wrapper, $VIDEO_TAB_WRAPPER->$field".toInstruction())
                    addInstruction(
                        "iget-object v$post, v$post, $VIDEO_TAB_TIMELINE_POST->postResult:${postResultType(shape)}"
                            .toInstruction(),
                    )
                    if (shape.postResultCast) addInstruction("check-cast v$post, $VIDEO_TAB_CANONICAL_POST".toInstruction())
                }
            }
            if (shape.harmlessMoves) addInstruction("move-object v9, v$post".toInstruction())
            addLabel("successTail")
            addInstruction(BuilderInstruction10t(Opcode.GOTO, getLabel("metadataMerge")))
            addLabel("nullArm")
            addInstruction("const/4 v$post, 0x0".toInstruction())
            addLabel("metadataMerge")
            val metadataPost = if (shape.staleMetadataPost) mime else post
            addInstruction("invoke-static {v$url, v$metadataPost}, $VIDEO_TAB_METADATA".toInstruction())
            addInstruction("move-result-object v$payload".toInstruction())
            if (shape.nullPayload) addInstruction("const/16 v$payload, 0x0".toInstruction())
            if (shape.payloadArrayOverwrite) {
                // A null array and zero index still make aget-object write the payload register.
                addInstruction("const/4 v8, 0x0".toInstruction())
                addInstruction("const/4 v9, 0x0".toInstruction())
                addInstruction("aget-object v$payload, v8, v9".toInstruction())
            }
            addInstruction("const/16 v$callback, 0x0".toInstruction())
            val dispatch = if (high) {
                "invoke-virtual/range {v$downloader .. v$callback}"
            } else {
                "invoke-virtual {v$downloader, v$url, v$mime, v$payload, v$callback}"
            }
            addInstruction("$dispatch, $VIDEO_TAB_DISPATCH".toInstruction())
            addInstruction("return-void".toInstruction())
        }.methodImplementation
        val handler = MutableMethod(
            ImmutableMethod(
                VIDEO_TAB_HANDLER,
                "onVideoTabDownload",
                listOf(ImmutableMethodParameter(VIDEO_TAB_EVENT, emptySet(), null)),
                "V",
                AccessFlags.PUBLIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                implementation,
            ),
        )
        val handlerFields = listOf(
            videoTabInstanceField(VIDEO_TAB_HANDLER, "container", VIDEO_TAB_WRAPPER),
            videoTabInstanceField(VIDEO_TAB_HANDLER, "downloader", VIDEO_TAB_DOWNLOADER),
            videoTabInstanceField(VIDEO_TAB_HANDLER, "inlinePost", VIDEO_TAB_TIMELINE_POST),
        )
        val timelinePost = ImmutableClassDef(
            VIDEO_TAB_TIMELINE_POST,
            AccessFlags.PUBLIC.value,
            VIDEO_TAB_OBJECT,
            emptyList(),
            null,
            emptySet(),
            listOf(videoTabInstanceField(VIDEO_TAB_TIMELINE_POST, "postResult", postResultType(shape))),
            emptyList(),
        )
        return VideoTabFixture(
            method = handler,
            handlerFields = handlerFields,
            classes = mapOf(VIDEO_TAB_WRAPPER to videoTabWrapper(shape), VIDEO_TAB_TIMELINE_POST to timelinePost),
            dispatchIndex = implementation.instructions.indexOfFirst { instruction ->
                instruction.getReference<MethodReference>()?.name == "dispatch"
            },
        )
    }

    private fun videoTabWrapper(shape: VideoTabShape): ImmutableClassDef {
        val fields = wrapperFieldTypes.map { (name, type) -> videoTabInstanceField(VIDEO_TAB_WRAPPER, name, type) }
        val methods = buildList<Method> {
            add(videoTabFormatter(shape))
            if (shape.producer == VideoTabProducer.ACCESSOR) add(videoTabAccessor(shape))
        }
        return ImmutableClassDef(
            VIDEO_TAB_WRAPPER,
            AccessFlags.PUBLIC.value,
            VIDEO_TAB_OBJECT,
            emptyList(),
            null,
            emptySet(),
            fields,
            methods,
        )
    }

    /** Mirrors the release `toString` shape: StringBuilder label, field append, `, post=` label, post append. */
    private fun videoTabFormatter(shape: VideoTabShape): ImmutableMethod {
        val implementation = MethodImplementationBuilder(3).apply {
            addInstruction("new-instance v0, Ljava/lang/StringBuilder;".toInstruction())
            addInstruction("const-string v1, \"ProgressEvent(event=\"".toInstruction())
            addInstruction("invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V".toInstruction())
            addInstruction("iget-object v1, v2, $VIDEO_TAB_WRAPPER->event:$VIDEO_TAB_WRAPPER_EVENT".toInstruction())
            addInstruction(VIDEO_TAB_APPEND_OBJECT.toInstruction())
            shape.formatterPostField?.let { field ->
                addInstruction("const-string v1, \", post=\"".toInstruction())
                addInstruction(VIDEO_TAB_APPEND_STRING.toInstruction())
                addInstruction(
                    "iget-object v1, v2, $VIDEO_TAB_WRAPPER->$field:${wrapperFieldTypes.getValue(field)}".toInstruction(),
                )
                addInstruction(VIDEO_TAB_APPEND_OBJECT.toInstruction())
            }
            addInstruction("const-string v1, \")\"".toInstruction())
            addInstruction(VIDEO_TAB_APPEND_STRING.toInstruction())
            addInstruction("invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;".toInstruction())
            addInstruction("move-result-object v1".toInstruction())
            addInstruction("return-object v1".toInstruction())
        }.methodImplementation
        return ImmutableMethod(
            VIDEO_TAB_WRAPPER,
            "toString",
            emptyList<ImmutableMethodParameter>(),
            VIDEO_TAB_STRING,
            AccessFlags.PUBLIC.value,
            emptySet<ImmutableAnnotation>(),
            emptySet<HiddenApiRestriction>(),
            implementation,
        )
    }

    /** Canonical accessor: wrapper.post -> timeline.postResult -> return, with no other reads or calls. */
    private fun videoTabAccessor(shape: VideoTabShape): ImmutableMethod {
        val implementation = MethodImplementationBuilder(2).apply {
            val field = "${shape.producerField}:${wrapperFieldTypes.getValue(shape.producerField)}"
            addInstruction("iget-object v0, v1, $VIDEO_TAB_WRAPPER->$field".toInstruction())
            addInstruction("iget-object v0, v0, $VIDEO_TAB_TIMELINE_POST->postResult:${postResultType(shape)}".toInstruction())
            if (shape.postResultCast) addInstruction("check-cast v0, $VIDEO_TAB_CANONICAL_POST".toInstruction())
            addInstruction("return-object v0".toInstruction())
        }.methodImplementation
        return ImmutableMethod(
            VIDEO_TAB_WRAPPER,
            VIDEO_TAB_ACCESSOR,
            emptyList<ImmutableMethodParameter>(),
            VIDEO_TAB_CANONICAL_POST,
            AccessFlags.PUBLIC.value,
            emptySet<ImmutableAnnotation>(),
            emptySet<HiddenApiRestriction>(),
            implementation,
        )
    }

    /** The release timeline stores the post as `Object` and the resolver casts it to the canonical type. */
    private fun postResultType(shape: VideoTabShape): String =
        if (shape.postResultCast) VIDEO_TAB_OBJECT else VIDEO_TAB_CANONICAL_POST

    private fun videoTabInstanceField(owner: String, name: String, type: String): ImmutableField =
        ImmutableField(owner, name, type, AccessFlags.PUBLIC.value, null, emptySet(), emptySet())

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
