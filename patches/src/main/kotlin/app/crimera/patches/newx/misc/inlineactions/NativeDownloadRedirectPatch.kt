package app.crimera.patches.newx.misc.inlineactions

import app.crimera.bytecode.Target
import app.crimera.bytecode.fieldReference
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.models.newXTimelineModelResolutionPatch
import app.crimera.patches.newx.models.resolvedNewXTimelineModels
import app.crimera.patches.newx.premium.NewXVideoTabDownloadHandlerFingerprint
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.OBJECT_MOVE_OPCODES
import app.crimera.patches.newx.utils.destinationRegisterOrNull
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableField
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

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
    override val field: FieldReference,
) : NativePostSource

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
    return parameters.size == 3 &&
        parameters[0] == ownerDescriptor &&
        parameters[2] == CONTINUATION_DESCRIPTOR &&
        returnType == OBJECT_DESCRIPTOR
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
    val handlers = NewXVideoTabDownloadHandlerFingerprint.scopedMatchAll()
    if (handlers.size != 2) {
        throw PatchException(
            "Expected two NewX video-tab download handlers, found ${handlers.size}: " +
                handlers.joinToString { it.originalMethod.toString() },
        )
    }

    return handlers.flatMap { match ->
        val method = match.method
        val post = resolveVideoTabPost(method, postDescriptor)
        buildList {
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

context(context: BytecodePatchContext)
private fun resolveVideoTabPost(
    method: MutableMethod,
    postDescriptor: String,
): HandlerPostSource {
    val postReads =
        method.instructions.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
            val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
            field.takeIf { it.type == postDescriptor }
        }.distinctBy(FieldReference::toString)
    val postRead =
        requireExactlyOne(
            "NewX video-tab post read in $method",
            postReads,
        )
    val containerField =
        requireExactlyOne(
            "NewX video-tab post container field in ${method.definingClass}",
            context.mutableClassDefBy(method.definingClass).fields.filter { field ->
                !AccessFlags.STATIC.isSet(field.accessFlags) &&
                    field.type == postRead.definingClass
            },
        )
    return HandlerPostSource(
        thisRegister = method.receiverRegister(),
        containerField = containerField,
        field = postRead,
    )
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
                iget(workRegister, source.thisRegister, source.containerField)
                iget(postRegister, workRegister, source.field)
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
