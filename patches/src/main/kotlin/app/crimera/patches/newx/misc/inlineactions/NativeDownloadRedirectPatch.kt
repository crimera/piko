package app.crimera.patches.newx.misc.inlineactions

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.bytecode.Target
import app.crimera.bytecode.fieldReference
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.models.newXTimelineModelResolutionPatch
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.models.ModelFieldAccessor
import app.crimera.patches.newx.models.readModelAccessor
import app.crimera.patches.newx.models.resolvedNewXTimelineModels
import app.crimera.patches.newx.models.resolveFieldAccessor
import app.crimera.patches.newx.premium.NewXVideoTabDownloadHandlerFingerprint
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.settings.group
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.settings.toggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.common.INTEGER_LITERAL_OPCODES
import app.crimera.patches.common.OBJECT_MOVE_OPCODES
import app.crimera.patches.common.destinationRegisterOrNull
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.scopedMatchAll
import app.crimera.patches.common.valueReachesRegister
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.Field
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val EXTENSION = "Lapp/morphe/extension/newx/misc/NativeDownloadRouter;"
private const val NATIVE_DOWNLOAD_ROUTE =
    "$EXTENSION->route(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Z"
private const val REDIRECT_SETTING = "newx.content.inline_download.redirect_native"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val CONTINUATION_DESCRIPTOR = "Lkotlin/coroutines/jvm/internal/ContinuationImpl;"
private const val WATERMARKED_DOWNLOAD_FLAG = "subscriptions_watermarked_video_download_enabled"
private val KOTLIN_UNIT_FIELD = fieldReference("Lkotlin/Unit;->a:Lkotlin/Unit;")

/**
 * The photo/video resolver maps a media URL to its content type. It is the semantic anchor for
 * the downloader class: the only `(String)String` mapper in the package whose switch returns
 * image MIME types.
 */
private object DownloadMimeResolverFingerprint : Fingerprint(
    definingClass = "Lcom/x/urt/items/post/",
    returnType = STRING_DESCRIPTOR,
    parameters = listOf(STRING_DESCRIPTOR),
    filters =
        listOf(
            string("image/webp"),
            string("image/png"),
            string("image/gif"),
            string("image/jpeg"),
            string("image/bmp"),
        ),
    custom = { method, _ -> AccessFlags.STATIC.isSet(method.accessFlags) },
)

private data class ResolvedNewXNativeDownloader(
    val descriptor: String,
    val mimeResolver: MethodReference,
    val postField: FieldReference,
    val videoDownload: MutableMethod,
    val nativeDispatchIndex: Int,
    /** The watermarked-video dispatch shared by the timeline and the video-tab handlers. */
    val watermarkedDispatch: MethodReference,
    val photoDownload: MutableMethod,
)

private sealed interface NativePostSource {
    val thisRegister: Int
    val field: FieldReference
}

/** The post timeline item is read directly from a field on the downloader. */
private data class DirectPostSource(
    override val thisRegister: Int,
    override val field: FieldReference,
) : NativePostSource

/** The handler's `this` holds a video-tab state object whose field is the timeline item. */
private data class HandlerPostSource(
    override val thisRegister: Int,
    val containerField: FieldReference,
    val accessor: ModelFieldAccessor,
) : NativePostSource {
    override val field: FieldReference get() = accessor.field
}

internal data class ResolvedVideoTabPostTuple(
    val containerField: FieldReference,
    val postField: FieldReference,
)

private data class NativeDownloadHook(
    val method: MutableMethod,
    val insertionIndex: Int,
    val post: NativePostSource,
    val urlRegister: Int,
    val mimeRegister: Int,
    /** Suspend downloader methods return `Unit` objects; video-tab handlers return void. */
    val returnsObject: Boolean,
)

@Suppress("unused")
val newXNativeDownloadRedirectPatch =
    bytecodePatch(
        name = "NewX: Redirect downloads to chosen folder",
        description =
            "Saves media downloaded with NewX's own download buttons into the folders chosen " +
                "for the download feature, using the configured filename template.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(
            newXInlineDownloadButtonPatch,
            newXTimelineModelResolutionPatch,
            newXExtensionPatch,
        )

        newXSettings {
            category(Categories.POST_ACTIONS_MEDIA) {
                group(Groups.INLINE_DOWNLOAD) {
                    toggle(
                        id = REDIRECT_SETTING,
                        strings = settingStrings("piko_newx_inline_download_redirect_native"),
                        order = 380,
                        defaultValue = true,
                    )
                }
            }
        }

        execute {
            val downloader = resolveNativeDownloader()
            val postDescriptor = resolvedNewXTimelineModels().postDescriptor
            val hooks =
                buildList {
                    add(resolvePhotoDownloadHook(downloader))
                    add(resolveVideoDownloadHook(downloader))
                    addAll(resolveVideoTabHooks(downloader, postDescriptor))
                }
            // Installs mutate the instruction list; later hooks in the same method must land
            // first so earlier insertion indexes stay valid.
            hooks.sortedByDescending(NativeDownloadHook::insertionIndex).forEach(NativeDownloadHook::install)
        }
    }

context(context: BytecodePatchContext)
private fun resolveNativeDownloader(): ResolvedNewXNativeDownloader {
    val mimeResolverMatch =
        requireExactlyOne(
            "NewX download MIME resolver",
            DownloadMimeResolverFingerprint.scopedMatchAll(),
        )
    val downloader = mimeResolverMatch.originalClassDef
    val mutableDownloader = context.mutableClassDefBy(downloader.type)

    val videoDownload =
        requireExactlyOne(
            "NewX video download method in ${downloader.type}",
            mutableDownloader.methods.filter { method ->
                method.isDownloaderCoroutine(downloader.type) &&
                    method.hasString(WATERMARKED_DOWNLOAD_FLAG)
            },
        )
    val nativeDispatch =
        requireExactlyOne(
            "NewX native download dispatch in $videoDownload",
            videoDownload.nativeDispatchCandidates(),
        )
    val watermarkedDispatch =
        requireExactlyOne(
            "NewX watermarked download dispatch in $videoDownload",
            videoDownload.watermarkedDispatchCandidates(),
        )
    val mimeResolver = mimeResolverMatch.originalMethod
    val photoDownload =
        requireExactlyOne(
            "NewX photo download method in ${downloader.type}",
            mutableDownloader.methods.filter { method ->
                method != videoDownload &&
                    method.isDownloaderCoroutine(downloader.type) &&
                    method.callsMethod(mimeResolver) &&
                    method.callsMethod(nativeDispatch.second)
            },
        )

    val postField =
        requireExactlyOne(
            "NewX downloader timeline-post field in ${downloader.type}",
            mutableDownloader.fields.filter { field ->
                !AccessFlags.STATIC.isSet(field.accessFlags) &&
                    field.type == resolvedNewXTimelineModels().postDescriptor
            },
        )

    return ResolvedNewXNativeDownloader(
        descriptor = downloader.type,
        mimeResolver = mimeResolver,
        postField = postField,
        videoDownload = videoDownload,
        nativeDispatchIndex = nativeDispatch.first,
        watermarkedDispatch = watermarkedDispatch,
        photoDownload = photoDownload,
    )
}

private fun Method.isDownloaderCoroutine(ownerDescriptor: String): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    if (returnType != OBJECT_DESCRIPTOR || parameters.lastOrNull() != CONTINUATION_DESCRIPTOR) return false
    return if (AccessFlags.STATIC.isSet(accessFlags)) {
        parameters.size == 3 && parameters[0] == ownerDescriptor
    } else {
        parameters.size == 2 && definingClass == ownerDescriptor
    }
}

private fun Method.hasString(value: String): Boolean =
    implementation?.instructions?.any { instruction ->
        instruction.getReference<StringReference>()?.string == value
    } == true

private fun MutableMethod.callsMethod(reference: MethodReference): Boolean =
    instructions.any { instruction ->
        instruction.getReference<MethodReference>()?.toString() == reference.toString()
    }

/**
 * The native dispatch helper is the single static call taking a network owner, the resolved URL
 * and MIME type, the request map, the two optional headers, and a callback. The parameter shape
 * survives the network class and callback churn across releases.
 */
private fun MutableMethod.nativeDispatchCandidates(): List<Pair<Int, MethodReference>> =
    instructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.INVOKE_STATIC &&
            instruction.opcode != Opcode.INVOKE_STATIC_RANGE
        ) {
            return@mapIndexedNotNull null
        }
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        val parameters = reference.parameterTypes.map(CharSequence::toString)
        (index to reference).takeIf {
            reference.returnType == "V" &&
                parameters.size == 8 &&
                parameters[1] == STRING_DESCRIPTOR &&
                parameters[2] == STRING_DESCRIPTOR
        }
    }

/**
 * The watermarked path writes the URL and MIME type into the download manager, followed by the
 * watermark status and the callback. No other call in the download coroutine takes two leading
 * strings and returns void.
 */
private fun MutableMethod.watermarkedDispatchCandidates(): List<MethodReference> =
    instructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.INVOKE_VIRTUAL &&
            instruction.opcode != Opcode.INVOKE_INTERFACE
        ) {
            return@mapNotNull null
        }
        val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
        val parameters = reference.parameterTypes.map(CharSequence::toString)
        reference.takeIf {
            reference.returnType == "V" &&
                parameters.size == 4 &&
                parameters[0] == STRING_DESCRIPTOR &&
                parameters[1] == STRING_DESCRIPTOR
        }
    }.distinctBy(MethodReference::toString)

private fun resolvePhotoDownloadHook(
    downloader: ResolvedNewXNativeDownloader,
): NativeDownloadHook {
    val method = downloader.photoDownload
    val mimeCalls = method.instructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.INVOKE_STATIC) return@mapIndexedNotNull null
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        index.takeIf { reference.toString() == downloader.mimeResolver.toString() }
    }
    val mimeCallIndex =
        requireExactlyOne(
            "NewX photo MIME resolution in $method",
            mimeCalls,
        )
    val urlRegister =
        method.instructions[mimeCallIndex].registersUsed.singleOrNull()
            ?: throw PatchException(
                "NewX photo MIME resolution has an unexpected argument shape: " +
                    method.instructions[mimeCallIndex],
            )
    val mimeMoveResult = method.instructions[mimeCallIndex + 1]
    if (mimeMoveResult.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException(
            "NewX photo MIME resolution does not produce an object in $method: $mimeMoveResult",
        )
    }
    val mimeRegister = (mimeMoveResult as OneRegisterInstruction).registerA

    return NativeDownloadHook(
        method = method,
        insertionIndex = mimeCallIndex + 2,
        post = DirectPostSource(method.receiverRegister(), downloader.postField),
        urlRegister = urlRegister,
        mimeRegister = mimeRegister,
        returnsObject = true,
    )
}

private fun resolveVideoDownloadHook(
    downloader: ResolvedNewXNativeDownloader,
): NativeDownloadHook {
    val method = downloader.videoDownload
    val instructions = method.instructions
    val dispatchRegisters = instructions[downloader.nativeDispatchIndex].registersUsed
    if (dispatchRegisters.size < 3) {
        throw PatchException(
            "NewX native download dispatch has an unexpected register range in $method",
        )
    }
    val urlRegister =
        traceRegisterBackward(instructions, downloader.nativeDispatchIndex, dispatchRegisters[1])
    val mimeRegister =
        traceRegisterBackward(instructions, downloader.nativeDispatchIndex, dispatchRegisters[2])

    return NativeDownloadHook(
        method = method,
        insertionIndex = downloadBranchStart(instructions, urlRegister, downloader.nativeDispatchIndex, method),
        post = DirectPostSource(method.receiverRegister(), downloader.postField),
        urlRegister = urlRegister,
        mimeRegister = mimeRegister,
        returnsObject = true,
    )
}

context(context: BytecodePatchContext)
private fun resolveVideoTabHooks(
    downloader: ResolvedNewXNativeDownloader,
    postDescriptor: String,
): List<NativeDownloadHook> {
    val method =
        requireExactlyOne(
            "NewX video-tab download handler",
            NewXVideoTabDownloadHandlerFingerprint.scopedMatchAll(),
        ).method
    return buildList {
        val nativeDispatch =
            requireExactlyOne(
                "NewX video-tab native download dispatch in $method",
                method.nativeDispatchCandidates(),
            )
        val nativeRegisters = method.instructions[nativeDispatch.first].registersUsed
        if (nativeRegisters.size < 3) {
            throw PatchException(
                "NewX video-tab native download dispatch has an unexpected register range in $method",
            )
        }
        val watermarkedIndex =
            requireExactlyOne(
                "NewX video-tab watermarked download dispatch in $method",
                method.instructions.mapIndexedNotNull { index, instruction ->
                    val reference = instruction.getReference<MethodReference>()
                        ?: return@mapIndexedNotNull null
                    index.takeIf {
                        reference.toString() == downloader.watermarkedDispatch.toString()
                    }
                },
            )
        val watermarkedRegisters = method.instructions[watermarkedIndex].registersUsed
        if (watermarkedRegisters.size < 3) {
            throw PatchException(
                "NewX video-tab watermarked dispatch has an unexpected register range in $method",
            )
        }
        val post =
            resolveVideoTabPost(
                method = method,
                postDescriptor = postDescriptor,
                postResultField = resolvedNewXTimelineModels().postResultField,
                watermarkedDispatch = downloader.watermarkedDispatch,
                watermarkedDispatchIndex = watermarkedIndex,
                nativeDispatchIndex = nativeDispatch.first,
            )
        add(
            NativeDownloadHook(
                method = method,
                insertionIndex = nativeDispatch.first,
                post = post,
                urlRegister = nativeRegisters[1],
                mimeRegister = nativeRegisters[2],
                returnsObject = false,
            ),
        )
        add(
            NativeDownloadHook(
                method = method,
                insertionIndex = watermarkedIndex,
                post = post,
                urlRegister = watermarkedRegisters[1],
                mimeRegister = watermarkedRegisters[2],
                returnsObject = false,
            ),
        )
    }
}

/**
 * The download branch starts at the target of the last null check on the resolved URL before the
 * dispatch. Earlier branches test the same register for media type; the download-start guard is
 * by definition the final use of that register before the dispatch.
 * // newx-resolver-lint: allow instruction-order last-candidate because the download-start
 * guard is by definition the final use of the URL register before the dispatch call.
 */
private fun downloadBranchStart(
    instructions: List<Instruction>,
    register: Int,
    dispatchIndex: Int,
    method: MutableMethod,
): Int {
    val candidates =
        instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode != Opcode.IF_NEZ) return@mapIndexedNotNull null
            val branch = instruction as? BuilderOffsetInstruction ?: return@mapIndexedNotNull null
            val checked = (instruction as? OneRegisterInstruction)?.registerA
                ?: return@mapIndexedNotNull null
            if (checked != register || index >= dispatchIndex) return@mapIndexedNotNull null
            val target = branch.target.location.index
            (index to target).takeIf { target in (index + 1)..<dispatchIndex }
        }.maxByOrNull { candidate -> candidate.second }
            ?: throw PatchException("NewX download URL guard was not found in $method")
    return candidates.second
}

/**
 * Resolves the post source for both video-tab dispatches. The receiver must still be the handler
 * at each dispatch, because the injected hook re-reads the wrapper from it at that point.
 */
context(context: BytecodePatchContext)
private fun resolveVideoTabPost(
    method: MutableMethod,
    postDescriptor: String,
    postResultField: FieldReference,
    watermarkedDispatch: MethodReference,
    watermarkedDispatchIndex: Int,
    nativeDispatchIndex: Int,
): HandlerPostSource {
    val handlerClass = context.mutableClassDefBy(method.definingClass)
    val tuple = resolveVideoTabPostTuple(
        method = method,
        handlerFields = handlerClass.fields,
        postDescriptor = postDescriptor,
        postResultField = postResultField,
        watermarkedDispatch = watermarkedDispatch,
        watermarkedDispatchIndex = watermarkedDispatchIndex,
        classFor = { type -> context.classDefByOrNull(type) },
    )
    val postAccessor = context.mutableClassDefBy(tuple.containerField.type).resolveFieldAccessor(
        tuple.postField,
        "video-tab timeline post",
    )
    for (dispatchIndex in listOf(nativeDispatchIndex, watermarkedDispatchIndex)) {
        if (!method.provesParameterValue(dispatchIndex, method.p0Register, method.p0Register)) {
            throw PatchException(
                "NewX video-tab handler receiver is not live at download dispatch $dispatchIndex in $method",
            )
        }
    }
    return HandlerPostSource(method.p0Register, tuple.containerField, postAccessor)
}

/**
 * Resolves the actual wrapper-to-post tuple, independently of whether R8 inlines its accessor.
 *
 * Candidate source reads and metadata calls are not bounded by a policy flag. The nullable
 * diamond that feeds the watermarked dispatch is the joint proof, so unrelated reads or repeated
 * flag queries cannot change the single valid tuple.
 */
internal fun resolveVideoTabPostTuple(
    method: Method,
    handlerFields: Collection<Field>,
    postDescriptor: String,
    postResultField: FieldReference,
    watermarkedDispatch: MethodReference,
    watermarkedDispatchIndex: Int,
    classFor: (String) -> ClassDef?,
): ResolvedVideoTabPostTuple {
    val instructions = method.implementation?.instructions?.toList()
        ?: throw PatchException("NewX video-tab handler has no implementation: $method")
    if (method.implementation?.tryBlocks?.isNotEmpty() == true) {
        throw PatchException("NewX video-tab nullable source proof does not model try blocks in $method")
    }
    if (instructions.any { instruction -> instruction.opcode in SWITCH_OPCODES }) {
        throw PatchException("NewX video-tab nullable source proof does not model switch payloads in $method")
    }
    val branchTargets = instructions.branchTargetIndices()
    val instanceFields = handlerFields.filter { field ->
        !AccessFlags.STATIC.isSet(field.accessFlags)
    }
    val dispatchParameters = watermarkedDispatch.parameterTypes.map(CharSequence::toString)
    val payloadType = dispatchParameters.getOrNull(2)
        ?: throw PatchException("NewX watermarked dispatch has no payload parameter: $watermarkedDispatch")
    val postMetadataCalls = instructions.mapIndexedNotNull { index, instruction ->
        if (index >= watermarkedDispatchIndex) return@mapIndexedNotNull null
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        val arguments = instruction.registersUsed
        (index to reference).takeIf {
            reference.returnType == payloadType &&
                reference.parameterTypes.size > 1 &&
                arguments.size == reference.parameterTypes.size &&
                reference.parameterTypes[1].toString().startsWith("L")
        }
    }
    val containerReads = instructions.mapIndexedNotNull { index, instruction ->
        if (index >= watermarkedDispatchIndex || instruction.opcode != Opcode.IGET_OBJECT) {
            return@mapIndexedNotNull null
        }
        val field = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
        field.takeIf { candidate ->
            instanceFields.any { it.toString() == candidate.toString() } &&
                candidate.type.startsWith("L")
        }?.let { field to index }
    }

    val candidatePaths = buildList {
        for ((containerField, containerReadIndex) in containerReads) {
            val wrapperClass = classFor(containerField.type) ?: continue
            // The formatter is optional on arbitrary handler fields; only a unique match counts.
            val wrapperToStrings = requireAtMostOne(
                "ProgressEvent formatter in ${containerField.type}",
                wrapperClass.methods.filter { candidate ->
                    candidate.name == "toString" && candidate.parameterTypes.isEmpty() &&
                        candidate.returnType == STRING_DESCRIPTOR &&
                        candidate.implementation?.instructions?.any { instruction ->
                            instruction.getReference<StringReference>()?.string == "ProgressEvent(event="
                        } == true &&
                        candidate.implementation?.instructions?.any { instruction ->
                            instruction.getReference<StringReference>()?.string == ", post="
                        } == true
                },
            ) ?: continue
            val wrapperPostField = wrapperToStrings.fieldForToStringLabel(", post=")
            if (wrapperPostField.type != postDescriptor) continue
            for ((metadataCallIndex, metadataCall) in postMetadataCalls) {
                val metadataPostType = metadataCall.parameterTypes[1].toString()
                val postAccessors = wrapperClass.methods.filter { candidate ->
                    isCanonicalPostAccessor(
                        candidate,
                        wrapperPostField,
                        postResultField,
                        metadataPostType,
                    )
                }
                addAll(
                    resolveVideoTabPostPaths(
                        instructions = instructions,
                        branchTargets = branchTargets,
                        method = method,
                        containerField = containerField,
                        wrapperPostField = wrapperPostField,
                        canonicalAccessors = postAccessors,
                        postResultField = postResultField,
                        metadataCall = metadataCall,
                        containerReadIndex = containerReadIndex,
                        metadataCallIndex = metadataCallIndex,
                        watermarkedDispatchIndex = watermarkedDispatchIndex,
                    ),
                )
            }
        }
    }
    return requireExactlyOne(
        "NewX video-tab post source on watermarked download path in $method",
        candidatePaths,
    )
}

/**
 * Proves that the wrapper's canonical-post accessor consists only of the semantic field chain:
 * wrapper.post -> timelinePost.postResult -> optional cast -> return. A generated accessor may
 * disappear; the caller resolver also accepts that same chain inlined into the handler.
 */
private fun isCanonicalPostAccessor(
    method: Method,
    wrapperPostField: FieldReference,
    postResultField: FieldReference,
    canonicalPostType: String,
): Boolean {
    if (AccessFlags.STATIC.isSet(method.accessFlags) ||
        method.parameterTypes.isNotEmpty() || method.returnType != canonicalPostType
    ) return false
    val instructions = method.implementation?.instructions?.toList() ?: return false
    val allowedOpcodes = OBJECT_MOVE_OPCODES + setOf(Opcode.IGET_OBJECT, Opcode.CHECK_CAST, Opcode.RETURN_OBJECT)
    if (instructions.any { it.opcode !in allowedOpcodes }) return false
    val fieldReads = instructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.IGET_OBJECT) return@mapIndexedNotNull null
        val field = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
        index to field
    }
    if (fieldReads.map { it.second.toString() } !=
        listOf(wrapperPostField.toString(), postResultField.toString())
    ) return false
    val wrapperReadIndex = requireExactlyOne(
        "NewX canonical accessor wrapper post read",
        fieldReads.filter { (_, field) -> field.toString() == wrapperPostField.toString() }.map { (index) -> index },
    )
    val postReadIndex = requireExactlyOne(
        "NewX canonical accessor post-result read",
        fieldReads.filter { (_, field) -> field.toString() == postResultField.toString() }.map { (index) -> index },
    )
    if (wrapperReadIndex >= postReadIndex) return false
    val wrapperRead = instructions[wrapperReadIndex] as? TwoRegisterInstruction ?: return false
    if (!method.provesParameterValue(wrapperReadIndex, wrapperRead.registerB, method.p0Register)) return false
    val postRead = instructions[postReadIndex] as? TwoRegisterInstruction ?: return false
    if (!instructions.valueReachesRegister(wrapperReadIndex, wrapperRead.registerA, postReadIndex, postRead.registerB)) {
        return false
    }
    val returns = instructions.mapIndexedNotNull { index, instruction ->
        index.takeIf { instruction.opcode == Opcode.RETURN_OBJECT }
    }
    if (returns.size != 1) return false
    val returnIndex = returns.single()
    val returnRegister = (instructions[returnIndex] as? OneRegisterInstruction)?.registerA ?: return false
    val casts = instructions.mapIndexedNotNull { index, instruction ->
        index.takeIf { instruction.opcode == Opcode.CHECK_CAST }
    }
    if (casts.size > 1 || casts.any { index ->
            instructions[index].getReference<TypeReference>()?.type != canonicalPostType
        }
    ) return false
    if (casts.isNotEmpty()) {
        val castIndex = casts.single()
        if (castIndex <= postReadIndex || castIndex >= returnIndex) return false
        val castRegister = (instructions[castIndex] as? OneRegisterInstruction)?.registerA ?: return false
        if (!instructions.valueReachesRegister(postReadIndex, postRead.registerA, castIndex, castRegister) ||
            !instructions.valueReachesRegister(castIndex, castRegister, returnIndex, returnRegister)
        ) return false
    } else if (postResultField.type != canonicalPostType ||
        !instructions.valueReachesRegister(postReadIndex, postRead.registerA, returnIndex, returnRegister)
    ) {
        return false
    }
    return true
}

/**
 * Matches the nullable diamond that feeds the watermarked dispatch:
 *
 * ```
 * container = this.U                 (containerReadIndex)
 * <optional object moves, harmless reads>   (straight-line, container register intact)
 * if-eqz guard, :null                (guardIndex, guard derived from container)
 * <optional object moves>
 * <canonical post producer>          (accessor invoke + move-result, or the two iget-object reads;
 *                                     optional object moves around them, then an optional cast)
 * <optional object moves>
 * goto :merge                        (metadataCallIndex - 2, the instruction before the null arm)
 * :null const/4 post, 0              (metadataCallIndex - 1)
 * :merge metadata(f, post, c)        (metadataCallIndex)
 * move-result-object e               (metadataCallIndex + 1)
 * <payload writers>                  (proved by provesValueFromInstruction, all paths)
 * ... watermarked dispatch(url, mime, e, cb)
 * ```
 *
 * Each guard that tests a value derived from the source read yields one candidate tuple. The success
 * arm, the straight-line source-to-guard region, and the nullable island's incoming edges are all
 * validated, so no other path can reach the merge or the dispatch.
 */
private fun resolveVideoTabPostPaths(
    instructions: List<Instruction>,
    branchTargets: Map<Int, Int>,
    method: Method,
    containerField: FieldReference,
    wrapperPostField: FieldReference,
    canonicalAccessors: List<Method>,
    postResultField: FieldReference,
    metadataCall: MethodReference,
    containerReadIndex: Int,
    metadataCallIndex: Int,
    watermarkedDispatchIndex: Int,
): List<ResolvedVideoTabPostTuple> {
    val containerRead = instructions[containerReadIndex] as? TwoRegisterInstruction ?: return emptyList()
    val containerRegister = containerRead.registerA
    val metadataPostRegister = instructions[metadataCallIndex].registersUsed.getOrNull(1) ?: return emptyList()

    // The merge is the goto's target, not necessarily the metadata call itself: 12.41 loads the
    // downloader field between the merge and the call. Walk back from the call over harmless
    // straight-line instructions to the nearest merge whose preceding pair is `goto` + null arm.
    var mergeIndex = -1
    for (candidate in metadataCallIndex downTo containerReadIndex + 3) {
        if (instructions[candidate - 2].opcode in GOTO_OPCODES && branchTargets[candidate - 2] == candidate) {
            mergeIndex = candidate
            break
        }
        val before = instructions[candidate - 1]
        if (before.opcode !in STRAIGHT_LINE_OPCODES) break
    }
    if (mergeIndex < 0) return emptyList()
    val nullIndex = mergeIndex - 1
    val gotoIndex = nullIndex - 1
    if (gotoIndex <= containerReadIndex + 1 || watermarkedDispatchIndex <= metadataCallIndex + 1) return emptyList()

    // Null arm: either a literal 0 written to the merged register, or a move from a register that
    // is proven to hold a literal 0 on every path reaching the arm (R8 shares one null register).
    val nullInstruction = instructions[nullIndex] as? OneRegisterInstruction ?: return emptyList()
    val postRegister = nullInstruction.registerA
    val nullIsLiteralZero = nullInstruction.opcode in INTEGER_LITERAL_OPCODES &&
        (nullInstruction as? NarrowLiteralInstruction)?.narrowLiteral == 0
    val nullIsSharedZero = nullInstruction.opcode in OBJECT_MOVE_OPCODES &&
        (nullInstruction as? TwoRegisterInstruction)?.registerB?.let { source ->
            (0 until nullIndex).any { origin ->
                val write = instructions[origin] as? OneRegisterInstruction
                write != null && write.opcode in INTEGER_LITERAL_OPCODES && write.registerA == source &&
                    (write as? NarrowLiteralInstruction)?.narrowLiteral == 0 &&
                    method.provesValueFromInstruction(origin, source, nullIndex, source)
            }
        } == true
    if (!nullIsLiteralZero && !nullIsSharedZero) return emptyList()
    if (instructions[gotoIndex].opcode !in GOTO_OPCODES || branchTargets[gotoIndex] != mergeIndex) {
        return emptyList()
    }
    // Merge to metadata call: straight-line, merged register intact, and it is the call's post.
    val mergeToCallStraight = (mergeIndex until metadataCallIndex).all { index ->
        instructions[index].opcode in STRAIGHT_LINE_OPCODES &&
            postRegister !in instructions[index].writtenRegisters()
    }
    if (!mergeToCallStraight || metadataPostRegister != postRegister) return emptyList()

    val metadataResult = instructions[metadataCallIndex + 1] as? OneRegisterInstruction ?: return emptyList()
    if (metadataResult.opcode != Opcode.MOVE_RESULT_OBJECT) return emptyList()
    val payloadRegister = instructions[watermarkedDispatchIndex].registersUsed.getOrNull(3) ?: return emptyList()

    // Guards are discovered from the branch graph: any if-eqz to the null constant between the
    // source read and the goto. Each one must test a value derived from the source read.
    return buildList {
        for (guardIndex in (containerReadIndex + 1 until gotoIndex)) {
            val guard = instructions[guardIndex] as? OneRegisterInstruction ?: continue
            if (instructions[guardIndex].opcode != Opcode.IF_EQZ || branchTargets[guardIndex] != nullIndex) continue
            if (!instructions.valueReachesRegister(containerReadIndex, containerRegister, guardIndex, guard.registerA)) {
                continue
            }
            // Source read to guard is straight-line and keeps the container value intact.
            val straightLine = (containerReadIndex + 1 until guardIndex).all { index ->
                val instruction = instructions[index]
                instruction.opcode in STRAIGHT_LINE_OPCODES &&
                    containerRegister !in instruction.writtenRegisters()
            }
            if (!straightLine) continue
            if (!instructions.isCanonicalPostProducer(
                    containerReadIndex = containerReadIndex,
                    containerRegister = containerRegister,
                    guardIndex = guardIndex,
                    gotoIndex = gotoIndex,
                    metadataPostRegister = postRegister,
                    accessors = canonicalAccessors,
                    wrapperPostField = wrapperPostField,
                    postResultField = postResultField,
                    canonicalPostType = metadataCall.parameterTypes[1].toString(),
                )
            ) continue
            if (!branchTargets.isIsolatedNullableMerge(
                    containerReadIndex = containerReadIndex,
                    guardIndex = guardIndex,
                    gotoIndex = gotoIndex,
                    nullIndex = nullIndex,
                    mergeIndex = mergeIndex,
                    dispatchIndex = watermarkedDispatchIndex,
                )
            ) continue
            // Full CFG provenance runs only for a candidate that already passed the cheap checks.
            if (!method.provesParameterValue(containerReadIndex, containerRead.registerB, method.p0Register)) continue
            if (!method.provesValueFromInstruction(
                    metadataCallIndex + 1,
                    metadataResult.registerA,
                    watermarkedDispatchIndex,
                    payloadRegister,
                )
            ) continue
            add(ResolvedVideoTabPostTuple(containerField, wrapperPostField))
        }
    }
}

/**
 * Validates the success arm between the guard and the goto. Object moves may appear anywhere in it.
 * The essential operations are exactly one of:
 * - `invoke-virtual/interface[/range] {container}, canonicalAccessor` + `move-result-object`, or
 * - `iget-object wrapperPost` + `iget-object postResult` (the inlined chain),
 *
 * optionally followed by one `check-cast` to the canonical type. The final value must reach the
 * metadata post register at the goto. Any other call, read, or cast fails closed.
 */
private fun List<Instruction>.isCanonicalPostProducer(
    containerReadIndex: Int,
    containerRegister: Int,
    guardIndex: Int,
    gotoIndex: Int,
    metadataPostRegister: Int,
    accessors: List<Method>,
    wrapperPostField: FieldReference,
    postResultField: FieldReference,
    canonicalPostType: String,
): Boolean {
    val armIndices = (guardIndex + 1 until gotoIndex).toList()
    if (armIndices.isEmpty() || armIndices.any { index -> this[index].opcode !in PRODUCER_OPCODES }) return false
    val calls = armIndices.filter { index -> this[index].opcode in INVOKE_OPCODES }
    val results = armIndices.filter { index -> this[index].opcode == Opcode.MOVE_RESULT_OBJECT }
    val fieldReads = armIndices.filter { index -> this[index].opcode == Opcode.IGET_OBJECT }
    val casts = armIndices.filter { index -> this[index].opcode == Opcode.CHECK_CAST }
    if (casts.size > 1) return false
    val castIndex = casts.singleOrNull()

    val producedIndex: Int
    val producedRegister: Int
    if (calls.isNotEmpty() && fieldReads.isEmpty()) {
        // Invoke-virtual/interface of the canonical accessor, paired with its move-result.
        val callIndex = requireExactlyOne("NewX video-tab canonical post accessor call", calls)
        val call = this[callIndex]
        val resultIndex = callIndex + 1
        if (results != listOf(resultIndex) || resultIndex >= gotoIndex) return false
        val callReference = call.getReference<MethodReference>() ?: return false
        val receiver = call.registersUsed.singleOrNull() ?: return false
        if (accessors.none { accessor -> accessor.toString() == callReference.toString() } ||
            !valueReachesRegister(containerReadIndex, containerRegister, callIndex, receiver)
        ) return false
        producedIndex = resultIndex
        producedRegister = (this[resultIndex] as OneRegisterInstruction).registerA
    } else if (calls.isEmpty() && results.isEmpty() && fieldReads.size == 2) {
        // Inlined chain: wrapper.post, then timelinePost.postResult.
        val wrapperIndex = requireExactlyOne(
            "NewX video-tab wrapper post read",
            fieldReads.filter { index -> this[index].getReference<FieldReference>()?.toString() == wrapperPostField.toString() },
        )
        val postIndex = requireExactlyOne(
            "NewX video-tab post-result read",
            fieldReads.filter { index -> this[index].getReference<FieldReference>()?.toString() == postResultField.toString() },
        )
        if (wrapperIndex >= postIndex) return false
        val wrapperRead = this[wrapperIndex] as? TwoRegisterInstruction ?: return false
        val postRead = this[postIndex] as? TwoRegisterInstruction ?: return false
        if (wrapperRead.getReference<FieldReference>()?.toString() != wrapperPostField.toString() ||
            postRead.getReference<FieldReference>()?.toString() != postResultField.toString() ||
            (castIndex == null && postResultField.type != canonicalPostType) ||
            !valueReachesRegister(containerReadIndex, containerRegister, wrapperIndex, wrapperRead.registerB) ||
            !valueReachesRegister(wrapperIndex, wrapperRead.registerA, postIndex, postRead.registerB)
        ) return false
        producedIndex = postIndex
        producedRegister = postRead.registerA
    } else {
        return false
    }

    // The optional cast consumes the produced object and must come after every essential read.
    var finalIndex = producedIndex
    var finalRegister = producedRegister
    if (castIndex != null) {
        if (castIndex <= producedIndex) return false
        val cast = this[castIndex] as? OneRegisterInstruction ?: return false
        if (cast.getReference<TypeReference>()?.type != canonicalPostType ||
            !valueReachesRegister(producedIndex, producedRegister, castIndex, cast.registerA)
        ) return false
        finalIndex = castIndex
        finalRegister = cast.registerA
    }
    return valueReachesRegister(finalIndex, finalRegister, gotoIndex, metadataPostRegister)
}

private val INVOKE_OPCODES = setOf(
    Opcode.INVOKE_VIRTUAL,
    Opcode.INVOKE_VIRTUAL_RANGE,
    Opcode.INVOKE_INTERFACE,
    Opcode.INVOKE_INTERFACE_RANGE,
)

private val IF_OPCODES = setOf(
    Opcode.IF_EQ,
    Opcode.IF_NE,
    Opcode.IF_LT,
    Opcode.IF_GE,
    Opcode.IF_GT,
    Opcode.IF_LE,
    Opcode.IF_EQZ,
    Opcode.IF_NEZ,
    Opcode.IF_LTZ,
    Opcode.IF_GEZ,
    Opcode.IF_GTZ,
    Opcode.IF_LEZ,
)

private val GOTO_OPCODES = setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)

private val BRANCH_OPCODES = IF_OPCODES + GOTO_OPCODES

private val SWITCH_OPCODES = setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH)

/** Reads and object moves allowed between the source read and the nullable guard. */
private val STRAIGHT_LINE_OPCODES = OBJECT_MOVE_OPCODES + setOf(
    Opcode.IGET,
    Opcode.IGET_WIDE,
    Opcode.IGET_OBJECT,
    Opcode.IGET_BOOLEAN,
    Opcode.IGET_BYTE,
    Opcode.IGET_CHAR,
    Opcode.IGET_SHORT,
)

/** The success arm may only hold object moves around the essential producer operations. */
private val PRODUCER_OPCODES = OBJECT_MOVE_OPCODES + INVOKE_OPCODES + setOf(
    Opcode.MOVE_RESULT_OBJECT,
    Opcode.IGET_OBJECT,
    Opcode.CHECK_CAST,
)

/**
 * The nullable island may only be entered through its branch and only left through its merge.
 * An incoming edge into the success arm, the null arm, or the metadata-result-to-dispatch region
 * from any other source means the merge value is not the proven one.
 */
private fun Map<Int, Int>.isIsolatedNullableMerge(
    containerReadIndex: Int,
    guardIndex: Int,
    gotoIndex: Int,
    nullIndex: Int,
    mergeIndex: Int,
    dispatchIndex: Int,
): Boolean = all { (source, target) ->
    // Every branch that lands between the source read and the dispatch must be one of the two
    // proven edges: guard-to-null and goto-to-merge.
    target !in (containerReadIndex + 1)..dispatchIndex ||
        (source == guardIndex && target == nullIndex) ||
        (source == gotoIndex && target == mergeIndex)
}

/**
 * Maps each goto/if instruction index to its target index. Builder branches resolve through their
 * label; immutable branches resolve through their code offset.
 */
private fun List<Instruction>.branchTargetIndices(): Map<Int, Int> {
    val offsets = IntArray(size)
    val indexByOffset = HashMap<Int, Int>()
    var offset = 0
    forEachIndexed { index, instruction ->
        offsets[index] = offset
        indexByOffset[offset] = index
        offset += instruction.codeUnits
    }
    val targets = LinkedHashMap<Int, Int>()
    forEachIndexed { index, instruction ->
        if (instruction.opcode !in BRANCH_OPCODES) return@forEachIndexed
        val target = (instruction as? BuilderOffsetInstruction)?.target?.location?.index
            ?: (instruction as? OffsetInstruction)?.let { branch ->
                indexByOffset[offsets[index] + branch.codeOffset]
            }
            ?: throw PatchException("NewX video-tab handler has an unresolved branch: $instruction")
        if (target !in indices) {
            throw PatchException("NewX video-tab handler branch target $target is outside the method: $instruction")
        }
        targets[index] = target
    }
    return targets
}

/**
 * Every register written by this instruction, from the opcode's own write metadata. Wide results
 * also kill the high half, so a `this` alias in `v(n+1)` is never kept past a wide write to `v(n)`.
 */
private fun Instruction.writtenRegisters(): Set<Int> {
    if (!opcode.setsRegister()) return emptySet()
    val destination = when (this) {
        is OneRegisterInstruction -> this.registerA
        is TwoRegisterInstruction -> this.registerA
        is ThreeRegisterInstruction -> this.registerA
        else -> return emptySet()
    }
    return if (opcode.setsWideRegister()) setOf(destination, destination + 1) else setOf(destination)
}

private fun traceRegisterBackward(
    instructions: List<Instruction>,
    beforeIndex: Int,
    register: Int,
): Int {
    var tracked = register
    for (index in beforeIndex - 1 downTo 0) {
        val instruction = instructions[index]
        if (instruction.opcode in OBJECT_MOVE_OPCODES) {
            val move = instruction as? TwoRegisterInstruction ?: return tracked
            if (move.registerA == tracked) tracked = move.registerB
            continue
        }
        if (instruction.destinationRegisterOrNull() == tracked) return tracked
    }
    return tracked
}

private fun MutableMethod.receiverRegister(): Int {
    for (instruction in instructions) {
        if (instruction.opcode !in OBJECT_MOVE_OPCODES) continue
        val move = instruction as? TwoRegisterInstruction ?: continue
        if (move.registerB == p0Register) return move.registerA
    }
    return p0Register
}

private fun NativeDownloadHook.install() {
    method.insertHook(insertionIndex, relocateBranchTargets = true) {
        // Two registers is the complete budget: the working register becomes the handled flag
        // after the post is read, and the post register is dead once the route call returns.
        val postRegister = scratchRegister()
        val workRegister = scratchRegister()
        when (val source = post) {
            is DirectPostSource -> iget(postRegister, source.thisRegister, source.field)
            is HandlerPostSource -> {
                move(workRegister, source.thisRegister, OBJECT_DESCRIPTOR)
                iget(postRegister, workRegister, source.containerField)
                ifEqz(postRegister, Target.Original)
                readModelAccessor(source.accessor, postRegister)
                ifEqz(postRegister, Target.Original)
            }
        }
        invokeStatic(
            methodReference(NATIVE_DOWNLOAD_ROUTE),
            postRegister,
            moveToScratchIfHigh(urlRegister, STRING_DESCRIPTOR),
            moveToScratchIfHigh(mimeRegister, STRING_DESCRIPTOR),
        )
        moveResult(workRegister, "Z")
        ifEqz(workRegister, Target.Original)
        if (returnsObject) {
            sget(postRegister, KOTLIN_UNIT_FIELD)
            returnObject(postRegister)
        } else {
            returnVoid()
        }
    }
}

/**
 * A parameter register above v15 forces the whole invoke to `/range`, which needs a staged
 * contiguous span the method may not have. Copying the value into a free four-bit register keeps
 * the call encodable as a plain `invoke-static`.
 */
private fun app.crimera.bytecode.Block.moveToScratchIfHigh(
    register: Int,
    type: String = OBJECT_DESCRIPTOR,
): Int {
    if (register <= 15) return register
    val scratch = scratchRegister()
    move(scratch, register, type)
    return scratch
}
