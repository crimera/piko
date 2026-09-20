package app.crimera.patches.newx.misc.customsharingdomain

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXTextInput
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val SHARE_URL_RESOLVER_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/ShareUrlResolver;"
private const val CHANGE_DOMAIN_METHOD =
    "$SHARE_URL_RESOLVER_DESCRIPTOR->changeDomain(Ljava/lang/String;)Ljava/lang/String;"
private const val CUSTOM_DOMAIN_VALIDATOR_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/CustomSharingDomainValidator;"
private const val SHARE_SHEET_DESCRIPTOR_PREFIX = "Lcom/x/dms/components/sharesheet/"
private const val SHARE_IMPL_DESCRIPTOR_PREFIX = "Lcom/x/share/impl/"
private const val DM_SHARESHEET_DESCRIPTOR_PREFIX = "Lcom/x/dm/sharesheet/"
private const val SHARE_API_I_DESCRIPTOR = "Lcom/x/share/api/i;"
private const val MOVED_SHARE_HELPER_DESCRIPTOR_PREFIX =
    "Lcom/google/android/gms/internal/mlkit_vision_common/"
private const val NAVIGATION_DESCRIPTOR_PREFIX = "Lcom/x/navigation/"
private const val SHARE_STATUS_URL_PREFIX = "https://x.com/i/status/"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val INTENT_DESCRIPTOR = "Landroid/content/Intent;"
private const val SEND_ACTION = "android.intent.action.SEND"
private const val EXTRA_TEXT = "android.intent.extra.TEXT"

private data class ShareIntentCallSite(
    val instructionIndex: Int,
    val urlRegister: Int,
)

/**
 * Share-sheet constructor that owns the final URL field. The status URL is a stable semantic
 * anchor; the void return shape identifies the share-sheet constructor.
 */
internal object ShareSheetUrlConstructorFingerprint : Fingerprint(
    definingClass = SHARE_SHEET_DESCRIPTOR_PREFIX,
    returnType = "V",
    filters = listOf(string(SHARE_STATUS_URL_PREFIX)),
)

/** Copy callback that writes the post URL to the clipboard (pre-12.28 post share flow). */
internal object ShareSheetCopyCallbackFingerprint : Fingerprint(
    definingClass = SHARE_IMPL_DESCRIPTOR_PREFIX,
    parameters = listOf(STRING_DESCRIPTOR),
    returnType = "V",
    filters = listOf(string("link"), string("copy_link")),
)

/**
 * DM share stub that replaced the copy path in 12.28. Proves contract move vs broken anchor:
 * old builds have both real impl (above) and stub; new builds have stub only.
 */
internal object ShareSheetCopyStubFingerprint : Fingerprint(
    definingClass = DM_SHARESHEET_DESCRIPTOR_PREFIX,
    parameters = listOf(STRING_DESCRIPTOR),
    returnType = "V",
)

/** Share Intent helper moved out of the share implementation package in 12.23 and later. */
internal object MovedShareIntentBuilderFingerprint : Fingerprint(
    definingClass = MOVED_SHARE_HELPER_DESCRIPTOR_PREFIX,
    parameters = listOf(STRING_DESCRIPTOR, STRING_DESCRIPTOR),
    returnType = INTENT_DESCRIPTOR,
    filters = listOf(string(SEND_ACTION), string(EXTRA_TEXT)),
)

/** Post share Intent builder owning status URL construction (12.28+ static helper). */
internal object ShareImplIntentBuilderFingerprint : Fingerprint(
    definingClass = SHARE_IMPL_DESCRIPTOR_PREFIX,
    parameters = listOf(SHARE_API_I_DESCRIPTOR),
    returnType = INTENT_DESCRIPTOR,
    filters = listOf(string(SHARE_STATUS_URL_PREFIX)),
)

/** URL getter used by post-detail navigation and quote/interactor links. */
internal object PostNavigationUrlFingerprint : Fingerprint(
    definingClass = NAVIGATION_DESCRIPTOR_PREFIX,
    parameters = emptyList(),
    returnType = STRING_DESCRIPTOR,
    filters = listOf(string(SHARE_STATUS_URL_PREFIX)),
    custom = { method, _ -> method.hasStatusUrlReturnFlow() },
)

/** Proves that the status URL marker contributes to the value returned by this getter. */
private fun Method.hasStatusUrlReturnFlow(): Boolean {
    val methodInstructions = implementation?.instructions?.toList() ?: return false
    val markerIndices = methodInstructions.mapIndexedNotNull { index, instruction ->
        val reference = instruction.getReference<StringReference>() ?: return@mapIndexedNotNull null
        index.takeIf {
            (instruction.opcode == Opcode.CONST_STRING ||
                instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
                reference.string == SHARE_STATUS_URL_PREFIX
        }
    }
    val returnIndices = methodInstructions.mapIndexedNotNull { index, instruction ->
        index.takeIf { instruction.opcode == Opcode.RETURN_OBJECT }
    }
    if (markerIndices.size != 1 || returnIndices.size != 1) return false

    val markerIndex = markerIndices.single()
    val markerRegister =
        (methodInstructions[markerIndex] as? OneRegisterInstruction)?.registerA
            ?: return false
    val returnIndex = returnIndices.single()

    val directResults = methodInstructions.mapIndexedNotNull { index, instruction ->
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        if (index <= markerIndex || reference.returnType != STRING_DESCRIPTOR) {
            return@mapIndexedNotNull null
        }
        if (markerRegister !in instruction.registersUsed ||
            methodInstructions.getOrNull(index + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT
        ) {
            return@mapIndexedNotNull null
        }
        val resultRegister =
            (methodInstructions[index + 1] as? OneRegisterInstruction)?.registerA
                ?: return@mapIndexedNotNull null
        index.takeIf {
            methodInstructions.valueReachesReturn(
                resultIndex = index + 1,
                resultRegister = resultRegister,
                returnIndex = returnIndex,
            )
        }
    }

    val builderResults = methodInstructions.mapIndexedNotNull { index, instruction ->
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        val arguments = instruction.registersUsed
        if (
            index <= markerIndex ||
                reference.definingClass != "Ljava/lang/StringBuilder;" ||
                reference.name != "<init>" ||
                reference.parameterTypes.map(CharSequence::toString) != listOf(STRING_DESCRIPTOR) ||
                arguments.size != 2 ||
                arguments[1] != markerRegister
        ) {
            return@mapIndexedNotNull null
        }
        val builderRegister = arguments.first()
        val toStringResults = methodInstructions.mapIndexedNotNull { toStringIndex, toStringInstruction ->
            if (toStringIndex <= index) return@mapIndexedNotNull null
            val toStringReference = toStringInstruction.getReference<MethodReference>()
                ?: return@mapIndexedNotNull null
            if (
                toStringReference.definingClass != "Ljava/lang/StringBuilder;" ||
                    toStringReference.name != "toString" ||
                    toStringReference.parameterTypes.isNotEmpty() ||
                    toStringReference.returnType != STRING_DESCRIPTOR ||
                    toStringInstruction.registersUsed != listOf(builderRegister) ||
                    methodInstructions.getOrNull(toStringIndex + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT
            ) {
                return@mapIndexedNotNull null
            }
            val resultRegister =
                (methodInstructions[toStringIndex + 1] as? OneRegisterInstruction)?.registerA
                    ?: return@mapIndexedNotNull null
            toStringIndex.takeIf {
                methodInstructions.valueReachesReturn(
                    resultIndex = toStringIndex + 1,
                    resultRegister = resultRegister,
                    returnIndex = returnIndex,
                )
            }
        }
        if (toStringResults.size != 1) return@mapIndexedNotNull null
        index
    }

    return directResults.size + builderResults.size == 1
}

private fun List<Instruction>.valueReachesReturn(
    resultIndex: Int,
    resultRegister: Int,
    returnIndex: Int,
): Boolean {
    if (resultIndex >= returnIndex) return false
    var register = resultRegister
    for (index in resultIndex + 1 until returnIndex) {
        val instruction = this[index]
        if (instruction.opcode == Opcode.MOVE_OBJECT || instruction.opcode == Opcode.MOVE) {
            val move = instruction as? TwoRegisterInstruction ?: return false
            if (move.registerA == register) register = move.registerB
            continue
        }
        val destination = (instruction as? OneRegisterInstruction)?.registerA
        if (destination == register) return false
    }
    val returnInstruction = this[returnIndex] as? OneRegisterInstruction ?: return false
    return returnInstruction.registerA == register
}

@Suppress("unused")
val newXCustomSharingDomainPatch =
    bytecodePatch(
        name = "NewX: Custom sharing domain",
        description = "Allows for using domains like fxtwitter when sharing tweets/posts.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        newXTextInput(
            id = "newx.content.custom_sharing_domain",
            category = Categories.CONTENT,
            strings = settingStrings("piko_newx_custom_sharing_domain"),
            order = 500,
            defaultValue = "",
            validatorClassDescriptor = CUSTOM_DOMAIN_VALIDATOR_DESCRIPTOR,
        )

        dependsOn(newXExtensionPatch)

        execute {
            hookShareSheetPostUrls()
            hookShareSheetCopyCallbacks()
            hookShareIntentBuilder()
            hookPostNavigationUrls()
        }
    }

context(_: app.morphe.patcher.patch.BytecodePatchContext)
private fun Fingerprint.requireSingleMatch(label: String): Match {
    return requireExactlyOne(
        label = label,
        candidates = scopedMatchAllOrNull().orEmpty(),
    )
}

context(_: app.morphe.patcher.patch.BytecodePatchContext)
private fun hookShareSheetPostUrls() {
    val method =
        ShareSheetUrlConstructorFingerprint
            .requireSingleMatch("NewX share-sheet URL constructor")
            .method
    hookShareSheetUrlConstructor(method)
}

context(_: app.morphe.patcher.patch.BytecodePatchContext)
private fun hookShareSheetCopyCallbacks() {
    val matches = ShareSheetCopyCallbackFingerprint.scopedMatchAllOrNull().orEmpty()
    if (matches.isEmpty()) {
        // 12.28+: post copy moved into the share-sheet URL field (hookShareSheetPostUrls covers
        // SEND + Compose copy via h.v/b0). The DM stub remains as a no-op. Validate the stub so
        // an unknown breakage still fails closed instead of silently skipping.
        requireExactlyOne(
            label = "NewX share-sheet copy stub variant",
            candidates = ShareSheetCopyStubFingerprint.scopedMatchAllOrNull().orEmpty(),
        )
        return
    }
    val selectedMatch =
        requireExactlyOne(
            label = "NewX share-sheet copy callback variant",
            candidates = matches,
        )
    selectedMatch.method.addInstructions(
        0,
        """
        invoke-static {p1}, $CHANGE_DOMAIN_METHOD
        move-result-object p1
        """.trimIndent(),
    )
}

context(_: app.morphe.patcher.patch.BytecodePatchContext)
private fun hookShareIntentBuilder() {
    val movedMatches = MovedShareIntentBuilderFingerprint.scopedMatchAllOrNull().orEmpty()
    val selectedMatch =
        requireExactlyOne(
            label = "NewX share Intent builder variant",
            candidates = movedMatches,
        )
    val helperMethod = selectedMatch.method
    val oldCopy =
        requireAtMostOne(
            label = "NewX share-sheet copy callback variant",
            candidates = ShareSheetCopyCallbackFingerprint.scopedMatchAllOrNull().orEmpty(),
        )
    if (oldCopy != null) {
        hookHelperCallSites(oldCopy.method.definingClass, helperMethod)
        return
    }
    val newOwner =
        ShareImplIntentBuilderFingerprint.requireSingleMatch("NewX share Intent owner")
    hookHelperCallSites(newOwner.method.definingClass, helperMethod)
}

context(context: app.morphe.patcher.patch.BytecodePatchContext)
private fun hookHelperCallSites(shareClassDescriptor: String, helperMethod: MutableMethod) {
    val shareClass = context.classDefByOrNull(shareClassDescriptor)
        ?: throw PatchException(
            "NewX share implementation class is missing: $shareClassDescriptor",
        )
    val methodsWithCalls = buildList {
        shareClass.methods.forEach { method ->
            val callSites = method.implementation?.instructions?.mapIndexedNotNull { index, instruction ->
                if (instruction.opcode != Opcode.INVOKE_STATIC &&
                    instruction.opcode != Opcode.INVOKE_STATIC_RANGE
                ) {
                    return@mapIndexedNotNull null
                }
                val reference = instruction.getReference<MethodReference>()
                    ?: return@mapIndexedNotNull null
                if (!reference.matches(helperMethod)) return@mapIndexedNotNull null
                val urlRegister = instruction.registersUsed.firstOrNull()
                    ?: throw PatchException("NewX share Intent call has no URL argument: $method")
                ShareIntentCallSite(index, urlRegister)
            }.orEmpty()
            if (callSites.isEmpty()) return@forEach
            val mutableMethod = method as? MutableMethod
                ?: throw PatchException("NewX share Intent call method is not mutable: $method")
            add(mutableMethod to callSites)
        }
    }
    if (methodsWithCalls.isEmpty()) {
        throw PatchException("NewX moved share Intent helper has no share implementation call sites")
    }

    methodsWithCalls.forEach { (method, callSites) ->
        callSites.asReversed().forEach { callSite ->
            method.addDomainRewrite(callSite.instructionIndex, callSite.urlRegister)
        }
    }
}

context(_: app.morphe.patcher.patch.BytecodePatchContext)
private fun hookPostNavigationUrls() {
    val matches = PostNavigationUrlFingerprint.scopedMatchAllOrNull().orEmpty()
    if (matches.size != 2) {
        throw PatchException(
            "Expected two NewX post navigation URL getters, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }

    matches.forEach { match ->
        val method = match.method
        val returnIndices =
            method.instructions.mapIndexedNotNull { index, instruction ->
                if (instruction.opcode == Opcode.RETURN_OBJECT) index else null
            }
        if (returnIndices.size != 1) {
            throw PatchException(
                "Expected one returned URL in NewX post navigation getter $method, found " +
                    returnIndices.size,
            )
        }

        val returnIndex = returnIndices.single()
        val returnInstruction = method.instructions[returnIndex] as? OneRegisterInstruction
            ?: throw PatchException("Expected a one-register URL return in $method")
        method.addDomainRewrite(returnIndex, returnInstruction.registerA)
    }
}

private fun hookShareSheetUrlConstructor(method: MutableMethod) {
    val statusResultIndices = method.findStatusUrlResultIndices()
    if (statusResultIndices.size != 1) {
        throw PatchException(
            "Expected one status URL result in NewX share-sheet constructor $method, found " +
                statusResultIndices.size,
        )
    }

    val statusResultIndex = statusResultIndices.single()
    val urlFieldStoreIndices =
        method.instructions.mapIndexedNotNull { index, instruction ->
            if (index <= statusResultIndex || instruction.opcode != Opcode.IPUT_OBJECT) {
                return@mapIndexedNotNull null
            }
            val field = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
            if (field.definingClass != method.definingClass || field.type != STRING_DESCRIPTOR) {
                return@mapIndexedNotNull null
            }
            index
        }
    if (urlFieldStoreIndices.size != 1) {
        throw PatchException(
            "Expected one final share URL field store in NewX share-sheet constructor $method, found " +
                urlFieldStoreIndices.size,
        )
    }

    val fieldStoreIndex = urlFieldStoreIndices.single()
    val valueRegister =
        (method.instructions[fieldStoreIndex] as? TwoRegisterInstruction)?.registerA
            ?: throw PatchException("Expected a two-register share URL field store in $method")
    method.addDomainRewrite(fieldStoreIndex, valueRegister)
}

private fun MutableMethod.findStatusUrlResultIndices(): List<Int> {
    val methodInstructions = instructions
    val offsets = IntArray(methodInstructions.size)
    val indexByOffset = mutableMapOf<Int, Int>()
    var codeOffset = 0
    methodInstructions.forEachIndexed { index, instruction ->
        offsets[index] = codeOffset
        indexByOffset[codeOffset] = index
        codeOffset += instruction.codeUnits
    }
    fun branchTargetIndex(index: Int): Int? {
        val branch = methodInstructions[index] as? OffsetInstruction ?: return null
        return indexByOffset[offsets[index] + branch.codeOffset]
    }
    return methodInstructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.CONST_STRING && instruction.opcode != Opcode.CONST_STRING_JUMBO) {
            return@mapIndexedNotNull null
        }
        val reference = instruction.getReference<StringReference>() ?: return@mapIndexedNotNull null
        if (reference.string != SHARE_STATUS_URL_PREFIX) return@mapIndexedNotNull null
        val markerRegister =
            (instruction as? OneRegisterInstruction)?.registerA
                ?: throw PatchException(
                    "Expected a one-register status URL prefix in $this at instruction $index",
                )

        // The switch epilogue falls through to the shared prefix+id builder in 12.26 but routes
        // the status branch through a goto to that same builder in 12.27. Follow one goto so both
        // layouts resolve to the builder consuming the prefix register.
        val successorIndex = index + 1
        val successor =
            methodInstructions.getOrNull(successorIndex)
                ?: throw PatchException(
                    "Expected status URL prefix to be followed by a static builder invoke in $this at " +
                        "instruction $index",
                )
        val builderIndex =
            when (successor.opcode) {
                Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32 ->
                    branchTargetIndex(successorIndex)
                        ?: throw PatchException(
                            "Expected status URL goto to resolve to a static builder invoke in $this at " +
                                "instruction $successorIndex",
                        )
                else -> successorIndex
            }
        val builder = methodInstructions.getOrNull(builderIndex)
        val builderOpcode = builder?.opcode
        if (builderOpcode != Opcode.INVOKE_STATIC && builderOpcode != Opcode.INVOKE_STATIC_RANGE) {
            throw PatchException(
                "Expected status URL prefix to be followed by a static builder invoke in $this at " +
                    "instruction $index",
            )
        }
        if (markerRegister !in builder.registersUsed) {
            throw PatchException(
                "Expected status URL builder to consume the prefix register in $this at " +
                    "instruction $builderIndex",
            )
        }
        val resultIndex = builderIndex + 1
        if (methodInstructions.getOrNull(resultIndex)?.opcode != Opcode.MOVE_RESULT_OBJECT) {
            throw PatchException(
                "Expected status URL builder to be followed by move-result-object in $this at " +
                    "instruction $builderIndex",
            )
        }
        resultIndex
    }
}

private fun MethodReference.matches(method: MutableMethod): Boolean =
    definingClass == method.definingClass &&
        name == method.name &&
        returnType == method.returnType &&
        parameterTypes.map(CharSequence::toString) == method.parameterTypes.map(CharSequence::toString)

private fun MutableMethod.addDomainRewrite(instructionIndex: Int, register: Int) {
    val invokeOpcode = if (register <= 15) "invoke-static" else "invoke-static/range"
    val registerRange = if (register <= 15) "{v$register}" else "{v$register .. v$register}"
    addInstructions(
        instructionIndex,
        """
        $invokeOpcode $registerRange, $CHANGE_DOMAIN_METHOD
        move-result-object v$register
        """.trimIndent(),
    )
}
