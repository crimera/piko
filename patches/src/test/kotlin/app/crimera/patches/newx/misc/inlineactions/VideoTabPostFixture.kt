package app.crimera.patches.newx.misc.inlineactions

// Shared fixture corpus for the watermarked video-tab download tuple. Both the inline-media suite
// and the NewX resolver linter corpus resolve through the real entry point with these shapes.

import app.crimera.bytecode.fieldReference
import app.crimera.bytecode.methodReference
import app.morphe.patcher.util.getReference
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
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

internal const val VIDEO_TAB_OBJECT = "Ljava/lang/Object;"
internal const val VIDEO_TAB_STRING = "Ljava/lang/String;"
internal const val VIDEO_TAB_HANDLER = "Lapp/crimera/test/VideoTabHandler;"
internal const val VIDEO_TAB_EVENT = "Lapp/crimera/test/VideoTabEvent;"
internal const val VIDEO_TAB_DOWNLOADER = "Lapp/crimera/test/Downloader;"
internal const val VIDEO_TAB_WRAPPER = "Lapp/crimera/test/ProgressEventWrapper;"
internal const val VIDEO_TAB_WRAPPER_EVENT = "Lapp/crimera/test/ProgressEventPayload;"
internal const val VIDEO_TAB_TIMELINE_POST = "Lapp/crimera/test/TimelinePost;"
internal const val VIDEO_TAB_CANONICAL_POST = "Lapp/crimera/test/CanonicalPost;"
internal const val VIDEO_TAB_OTHER_POST = "Lapp/crimera/test/OtherPost;"
internal const val VIDEO_TAB_MEDIA_PAYLOAD = "Lapp/crimera/test/MediaPayload;"
internal const val VIDEO_TAB_CALLBACK = "Lapp/crimera/test/Callback;"
internal const val VIDEO_TAB_FLAGS = "Lapp/crimera/test/Flags;"
internal const val VIDEO_TAB_FLAG = "subscriptions_watermarked_video_download_enabled"
internal const val VIDEO_TAB_ACCESSOR = "timelineCanonicalPost"
internal const val VIDEO_TAB_DISPATCH =
    "$VIDEO_TAB_DOWNLOADER->dispatch($VIDEO_TAB_STRING$VIDEO_TAB_STRING$VIDEO_TAB_MEDIA_PAYLOAD$VIDEO_TAB_CALLBACK)V"
internal const val VIDEO_TAB_METADATA =
    "Lapp/crimera/test/MediaMetadata;->build($VIDEO_TAB_STRING$VIDEO_TAB_CANONICAL_POST)$VIDEO_TAB_MEDIA_PAYLOAD"
internal const val VIDEO_TAB_APPEND_OBJECT =
    "invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;"
internal const val VIDEO_TAB_APPEND_STRING =
    "invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;"

internal enum class VideoTabProducer { ACCESSOR, INLINED }

internal enum class VideoTabAliasOverwrite { NONE, RETURNING, REACHING }

internal enum class VideoTabEntry { NONE, SUCCESS_ARM, NULL_ARM, MERGE }

/** Selects the release 12.42 nullable-narrowing return shape, or one of its malformed variants. */
internal enum class VideoTabNullableAccessor {
    NONE,
    NARROWING,
    ZERO_ARM_NONZERO,
    WRONG_TEST_TYPE,
    NULL_ARM_OTHER_REGISTER,
}

/** Toggles for one handler shape; defaults are the release-shaped accessor producer. */
internal data class VideoTabShape(
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
    val nullableAccessor: VideoTabNullableAccessor = VideoTabNullableAccessor.NONE,
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

/** Resolves one shape through the real tuple resolver the patch uses. */
internal fun resolveVideoTabTuple(shape: VideoTabShape): ResolvedVideoTabPostTuple {
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
        if (shape.nullableAccessor == VideoTabNullableAccessor.NONE) {
            addInstruction("iget-object v0, v1, $VIDEO_TAB_WRAPPER->$field".toInstruction())
            addInstruction("iget-object v0, v0, $VIDEO_TAB_TIMELINE_POST->postResult:${postResultType(shape)}".toInstruction())
            if (shape.postResultCast) addInstruction("check-cast v0, $VIDEO_TAB_CANONICAL_POST".toInstruction())
            addInstruction("return-object v0".toInstruction())
        } else {
            // 12.42 lowers `post.postResult as? CanonicalPost` to this nullable diamond.
            val testType = when (shape.nullableAccessor) {
                VideoTabNullableAccessor.WRONG_TEST_TYPE -> VIDEO_TAB_OTHER_POST
                else -> VIDEO_TAB_CANONICAL_POST
            }
            addInstruction("iget-object v0, v1, $VIDEO_TAB_WRAPPER->$field".toInstruction())
            addInstruction("iget-object v0, v0, $VIDEO_TAB_TIMELINE_POST->postResult:${postResultType(shape)}".toInstruction())
            addInstruction("instance-of v1, v0, $testType".toInstruction())
            addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, 1, getLabel("nullableNullArm")))
            addInstruction("check-cast v0, $VIDEO_TAB_CANONICAL_POST".toInstruction())
            addInstruction("return-object v0".toInstruction())
            addLabel("nullableNullArm")
            val nullLiteral = if (shape.nullableAccessor == VideoTabNullableAccessor.ZERO_ARM_NONZERO) 1 else 0
            addInstruction("const/4 v1, 0x$nullLiteral".toInstruction())
            val nullReturn = if (shape.nullableAccessor == VideoTabNullableAccessor.NULL_ARM_OTHER_REGISTER) 0 else 1
            addInstruction("return-object v$nullReturn".toInstruction())
        }
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
