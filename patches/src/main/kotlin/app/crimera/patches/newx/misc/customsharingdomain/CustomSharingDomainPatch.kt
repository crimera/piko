package app.crimera.patches.newx.misc.customsharingdomain

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXTextInput
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.bytecode.Block
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.ShapeFingerprint
import app.crimera.patches.common.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.numberOfParameterRegisters
import app.morphe.patcher.util.registersUsed
import app.morphe.util.cloneMutable
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val SHARE_URL_RESOLVER_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/ShareUrlResolver;"
private const val REWRITE_SHARED_URL_METHOD =
    "$SHARE_URL_RESOLVER_DESCRIPTOR->rewriteSharedUrl(Ljava/lang/String;)Ljava/lang/String;"
private const val REWRITE_PROFILE_URL_METHOD =
    "$SHARE_URL_RESOLVER_DESCRIPTOR->rewriteProfileUrl(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;"
private const val CUSTOM_DOMAIN_VALIDATOR_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/CustomSharingDomainValidator;"
private const val SHARE_SHEET_DESCRIPTOR_PREFIX = "Lcom/x/dms/components/sharesheet/"
private const val SHARE_IMPL_DESCRIPTOR_PREFIX = "Lcom/x/share/impl/"
private const val DM_SHARESHEET_DESCRIPTOR_PREFIX = "Lcom/x/dm/sharesheet/"
private const val SHARE_API_I_DESCRIPTOR = "Lcom/x/share/api/i;"
private const val NAVIGATION_DESCRIPTOR_PREFIX = "Lcom/x/navigation/"
private const val PROFILE_MODEL_DESCRIPTOR_PREFIX = "Lcom/x/models/profile/"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val INTENT_CLASS = "Landroid/content/Intent;"
private const val SHARE_STATUS_URL_PREFIX = "https://x.com/i/status/"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val INTENT_DESCRIPTOR = "Landroid/content/Intent;"
private const val SEND_ACTION = "android.intent.action.SEND"
private const val EXTRA_TEXT = "android.intent.extra.TEXT"
private const val SHARE_ATTRIBUTION_QUERY_PARAMETER = "s="

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

/** DM share stub that replaced the post copy callback in 12.28. */
internal object ShareSheetCopyStubFingerprint : Fingerprint(
    definingClass = DM_SHARESHEET_DESCRIPTOR_PREFIX,
    parameters = listOf(STRING_DESCRIPTOR),
    returnType = "V",
)

/**
 * Share Intent helper that builds the ACTION_SEND Intent for the system share sheet.
 *
 * R8 repartitions obfuscated classes into arbitrary fake packages and renumbers class and method
 * names on every release: 12.27-prod `mlkit_vision_common/d7;->b`, 12.28 `.../v6`, `.../k8`,
 * `.../z8`, 12.29-alpha `mlkit_vision_face/b6`. No owner descriptor survives, so the resolver
 * anchors on the construction contract instead: `(String, String) -> Intent` that seeds an
 * ACTION_SEND Intent and keys EXTRA_TEXT on it.
 */
internal object MovedShareIntentBuilderFingerprint : ShapeFingerprint(
    parameters = listOf(STRING_DESCRIPTOR, STRING_DESCRIPTOR),
    returnType = INTENT_DESCRIPTOR,
    filters = listOf(string(SEND_ACTION), string(EXTRA_TEXT)),
    custom = { method, _ -> method.buildsActionSendIntentWithText() },
)

/** Appends the source code to an X URL. Hooking its result also covers direct share paths that
 * construct an Intent without passing through the common share-Intent builder. */
private object ShareAttributionUrlBuilderFingerprint : Fingerprint(
    returnType = STRING_DESCRIPTOR,
    filters = listOf(string(SHARE_ATTRIBUTION_QUERY_PARAMETER)),
    custom = { method, _ -> method.isShareAttributionUrlBuilder() },
)

/** Proves ACTION_SEND seeds the Intent and EXTRA_TEXT keys the share text. */
private fun Method.buildsActionSendIntentWithText(): Boolean {
    val methodInstructions = implementation?.instructions?.toList() ?: return false

    fun registersHoldingString(value: String): Set<Int> =
        methodInstructions.mapNotNull { instruction ->
            val reference = instruction.getReference<StringReference>() ?: return@mapNotNull null
            if (reference.string != value) return@mapNotNull null
            (instruction as? OneRegisterInstruction)?.registerA
        }.toSet()

    val actionRegisters = registersHoldingString(SEND_ACTION)
    val textRegisters = registersHoldingString(EXTRA_TEXT)
    if (actionRegisters.isEmpty() || textRegisters.isEmpty()) return false

    fun List<Instruction>.hasIntentInvoke(
        name: String,
        parameterTypes: List<String>,
        keyArgumentRegister: (List<Int>) -> Int?,
    ): Boolean =
        any { instruction ->
            val reference = instruction.getReference<MethodReference>() ?: return@any false
            reference.definingClass == INTENT_CLASS &&
                reference.name == name &&
                reference.parameterTypes.map(CharSequence::toString) == parameterTypes &&
                keyArgumentRegister(instruction.registersUsed) != null
        }

    val seedsAction =
        methodInstructions.hasIntentInvoke("<init>", listOf(STRING_DESCRIPTOR)) { arguments ->
            arguments.getOrNull(1)?.takeIf { it in actionRegisters }
        }
    val keysExtraText =
        methodInstructions.hasIntentInvoke(
            "putExtra",
            listOf(STRING_DESCRIPTOR, STRING_DESCRIPTOR),
        ) { arguments -> arguments.getOrNull(1)?.takeIf { it in textRegisters } }

    return seedsAction && keysExtraText
}

internal fun Method.isShareAttributionUrlBuilder(): Boolean {
    if (!AccessFlags.STATIC.isSet(accessFlags) || returnType != STRING_DESCRIPTOR) return false

    val parameters = parameterTypes.map(CharSequence::toString)
    if (parameters.size != 2 || parameters[0] != STRING_DESCRIPTOR) return false

    val implementation = implementation ?: return false
    val methodInstructions = implementation.instructions.toList()
    fun hasStringLiteral(value: String): Boolean = methodInstructions.any { instruction ->
        (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
            instruction.getReference<StringReference>()?.string == value
    }
    val shareParameterCount = methodInstructions.count { instruction ->
        (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
            instruction.getReference<StringReference>()?.string == SHARE_ATTRIBUTION_QUERY_PARAMETER
    }
    if (!hasStringLiteral("?") || !hasStringLiteral("&") || shareParameterCount != 1) {
        return false
    }

    val returnIndices = methodInstructions.mapIndexedNotNull { index, instruction ->
        index.takeIf { instruction.opcode == Opcode.RETURN_OBJECT }
    }
    if (returnIndices.size != 1) return false
    val returnIndex = returnIndices.single()
    val returnRegister =
        (methodInstructions[returnIndex] as? OneRegisterInstruction)?.registerA ?: return false

    val sourceUrlRegister = implementation.registerCount - numberOfParameterRegisters
    val attributionRegister = sourceUrlRegister + 1
    val codeRegisters = methodInstructions.mapIndexedNotNull { index, instruction ->
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        if (reference.definingClass != parameters[1] ||
            reference.name != "getCode" ||
            reference.parameterTypes.isNotEmpty() ||
            reference.returnType != STRING_DESCRIPTOR ||
            instruction.registersUsed != listOf(attributionRegister)
        ) {
            return@mapIndexedNotNull null
        }
        val result = methodInstructions.getOrNull(index + 1)
        if (result?.opcode != Opcode.MOVE_RESULT_OBJECT) return@mapIndexedNotNull null
        (result as? OneRegisterInstruction)?.registerA
    }.distinct()
    if (codeRegisters.size != 1) return false

    val queryParameterRegisters = mutableListOf<Int>()
    methodInstructions.forEach { instruction ->
        if (instruction.getReference<StringReference>()?.string != SHARE_ATTRIBUTION_QUERY_PARAMETER) {
            return@forEach
        }
        instruction.stringConstantRegister()?.let(queryParameterRegisters::add)
    }
    if (queryParameterRegisters.size != 1) return false
    val queryParameterRegister = queryParameterRegisters.single()

    val concatResults = methodInstructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.INVOKE_STATIC &&
            instruction.opcode != Opcode.INVOKE_STATIC_RANGE
        ) {
            return@mapIndexedNotNull null
        }
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        if (reference.returnType != STRING_DESCRIPTOR ||
            reference.parameterTypes.size < 3 ||
            reference.parameterTypes.any { it.toString() != STRING_DESCRIPTOR }
        ) {
            return@mapIndexedNotNull null
        }
        val arguments = instruction.registersUsed
        if (sourceUrlRegister !in arguments ||
            codeRegisters.single() !in arguments ||
            queryParameterRegister !in arguments
        ) {
            return@mapIndexedNotNull null
        }

        val result = methodInstructions.getOrNull(index + 1)
        if (result?.opcode != Opcode.MOVE_RESULT_OBJECT) return@mapIndexedNotNull null
        val resultRegister = (result as? OneRegisterInstruction)?.registerA ?: return@mapIndexedNotNull null
        resultRegister.takeIf { it == returnRegister }
    }
    return concatResults.size == 1
}

private fun Instruction.stringConstantRegister(): Int? =
    when (opcode) {
        Opcode.CONST_STRING -> (this as? Instruction21c)?.registerA
        Opcode.CONST_STRING_JUMBO -> (this as? Instruction31c)?.registerA
        else -> null
    }

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

/** Profile URL route whose model carries the account ID alongside its mutable handle. */
internal object ProfileNavigationUrlFingerprint : Fingerprint(
    definingClass = NAVIGATION_DESCRIPTOR_PREFIX,
    parameters = emptyList(),
    returnType = STRING_DESCRIPTOR,
    filters = listOf(string("https://x.com/")),
    custom = { method, _ -> method.hasProfileNavigationUrlFlow() },
)

internal object ProfileUserStringFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    parameters = emptyList(),
    returnType = STRING_DESCRIPTOR,
    filters = listOf(string("ProfileUser(id=")),
)

internal object ProfileShareUrlFingerprint : Fingerprint(
    definingClass = "Lcom/x/share/api/",
    parameters = emptyList(),
    returnType = STRING_DESCRIPTOR,
    filters = listOf(string("https://x.com/")),
)

private data class ProfileModelResult(
    val resultIndex: Int,
    val register: Int,
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

private fun Method.hasProfileNavigationUrlFlow(): Boolean = profileModelResults().size == 1

/** Finds the normalized profile object consumed by the route's profile-variant branches. */
private fun Method.profileModelResults(): List<ProfileModelResult> {
    val methodInstructions = implementation?.instructions?.toList() ?: return emptyList()
    return methodInstructions.mapIndexedNotNull { index, instruction ->
        val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
        if (!reference.returnType.startsWith(PROFILE_MODEL_DESCRIPTOR_PREFIX)) {
            return@mapIndexedNotNull null
        }

        val resultIndex = index + 1
        val resultInstruction = methodInstructions.getOrNull(resultIndex)
        if (resultInstruction?.opcode != Opcode.MOVE_RESULT_OBJECT) return@mapIndexedNotNull null
        val resultRegister = (resultInstruction as? OneRegisterInstruction)?.registerA
            ?: return@mapIndexedNotNull null
        val consumedAsProfile = methodInstructions.drop(resultIndex + 1).any { candidate ->
            val type = candidate.getReference<TypeReference>()?.type
                ?: return@any false
            if (!type.startsWith(PROFILE_MODEL_DESCRIPTOR_PREFIX)) return@any false

            when (candidate.opcode) {
                Opcode.INSTANCE_OF ->
                    (candidate as? TwoRegisterInstruction)?.registerB == resultRegister
                Opcode.CHECK_CAST ->
                    (candidate as? OneRegisterInstruction)?.registerA == resultRegister
                else -> false
            }
        }
        if (consumedAsProfile) ProfileModelResult(resultIndex, resultRegister) else null
    }
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
internal val newXSharedUrlRewritePatch =
    bytecodePatch {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch)

        execute {
            hookShareSheetPostUrls()
            hookShareSheetCopyCallbacks()
            hookShareIntentBuilder()
            hookShareAttributionUrlBuilder()
            hookPostNavigationUrls()
            hookProfileNavigationUrls()
            hookProfileShareUrl()
        }
    }

@Suppress("unused")
val newXCustomSharingDomainPatch =
    bytecodePatch(
        name = "NewX: Custom sharing domain",
        description = "Allows for using domains like fxtwitter when sharing tweets/posts.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)

        newXTextInput(
            id = "newx.content.custom_sharing_domain",
            category = Categories.LINKS,
            strings = settingStrings("piko_newx_custom_sharing_domain"),
            order = 500,
            defaultValue = "",
            validatorClassDescriptor = CUSTOM_DOMAIN_VALIDATOR_DESCRIPTOR,
        )

        dependsOn(newXSharedUrlRewritePatch)
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
    // 12.28+: post copy moved into the share-sheet URL field (hookShareSheetPostUrls covers
    // SEND + Compose copy via h.v/b0). The DM stub remains as a no-op. Validate the stub so
    // an unknown breakage still fails closed instead of silently skipping.
    requireExactlyOne(
        label = "NewX share-sheet copy stub variant",
        candidates = ShareSheetCopyStubFingerprint.scopedMatchAllOrNull().orEmpty(),
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
    val newOwner =
        ShareImplIntentBuilderFingerprint.requireSingleMatch("NewX share Intent owner")
    hookHelperCallSites(newOwner.method.definingClass, helperMethod)
}

context(_: app.morphe.patcher.patch.BytecodePatchContext)
private fun hookShareAttributionUrlBuilder() {
    val method =
        ShareAttributionUrlBuilderFingerprint
            .requireSingleMatch("NewX share attribution URL builder")
            .method
    rewriteShareAttributionUrlBuilder(method)
}

internal fun rewriteShareAttributionUrlBuilder(method: MutableMethod) {
    val returnIndices = method.instructions.mapIndexedNotNull { index, instruction ->
        index.takeIf { instruction.opcode == Opcode.RETURN_OBJECT }
    }
    if (returnIndices.size != 1) {
        throw PatchException(
            "Expected one returned attribution URL in NewX helper $method, found ${returnIndices.size}",
        )
    }
    val returnIndex = returnIndices.single()
    val returnRegister =
        (method.instructions[returnIndex] as? OneRegisterInstruction)?.registerA
            ?: throw PatchException("Expected one returned attribution URL register in $method")
    method.insertHook(index = returnIndex, relocateBranchTargets = true) {
        rewriteSharedUrl(returnRegister)
    }
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
            method.addSharedUrlRewrite(callSite.instructionIndex, callSite.urlRegister)
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
        method.addSharedUrlRewrite(returnIndex, returnInstruction.registerA)
    }
}

context(_: app.morphe.patcher.patch.BytecodePatchContext)
private fun hookProfileShareUrl() {
    val profileType = ProfileUserStringFingerprint
        .requireSingleMatch("NewX profile user model")
        .originalMethod.definingClass
    val candidates = ProfileShareUrlFingerprint.scopedMatchAllOrNull().orEmpty().filter { match ->
        match.originalMethod.profileShareModelReads(profileType).size == 1
    }
    val match = requireExactlyOne("NewX profile share URL getter", candidates)
    val original = match.method
    val target = original.reserveProfileSnapshotRegister()
    val method = target.method
    val modelRead = requireExactlyOne(
        "NewX profile share model read in $method",
        method.profileShareModelReads(profileType),
    )
    val returns = method.instructions.mapIndexedNotNull { index, instruction ->
        index.takeIf { instruction.opcode == Opcode.RETURN_OBJECT }
    }
    val returnIndex = requireExactlyOne("NewX profile share URL return in $method", returns)
    if (modelRead.first + 1 in method.instructions.branchTargetIndices() ||
        returnIndex in method.instructions.branchTargetIndices()
    ) {
        throw PatchException("NewX profile share URL getter has an unexpected branch target: $method")
    }
    val returnRegister = (method.instructions[returnIndex] as OneRegisterInstruction).registerA
    method.insertHook(index = returnIndex, relocateBranchTargets = false) {
        invokeStatic(methodReference(REWRITE_PROFILE_URL_METHOD), returnRegister, target.snapshotRegister)
        moveResult(returnRegister, STRING_DESCRIPTOR)
    }
    method.insertHook(index = modelRead.first + 1, relocateBranchTargets = false) {
        move(target.snapshotRegister, modelRead.second, OBJECT_DESCRIPTOR)
    }
    match.classDef.methods.remove(original)
    match.classDef.methods.add(method)
}

private fun Method.profileShareModelReads(profileType: String): List<Pair<Int, Int>> {
    val code = implementation?.instructions?.toList() ?: return emptyList()
    return code.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.IGET_OBJECT) return@mapIndexedNotNull null
        val field = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
        val read = instruction as? TwoRegisterInstruction ?: return@mapIndexedNotNull null
        if (field.definingClass != definingClass || field.type != profileType) return@mapIndexedNotNull null
        val next = code.getOrNull(index + 1) as? TwoRegisterInstruction ?: return@mapIndexedNotNull null
        val nextField = code[index + 1].getReference<FieldReference>() ?: return@mapIndexedNotNull null
        if (code[index + 1].opcode != Opcode.IGET_OBJECT || next.registerB != read.registerA ||
            nextField.definingClass != profileType || nextField.type != STRING_DESCRIPTOR
        ) return@mapIndexedNotNull null
        index to read.registerA
    }
}

context(_: app.morphe.patcher.patch.BytecodePatchContext)
private fun hookProfileNavigationUrls() {
    val match = ProfileNavigationUrlFingerprint.requireSingleMatch(
        "NewX profile navigation URL getter",
    )
    val originalMethod = match.method
    val target = originalMethod.reserveProfileSnapshotRegister()
    match.classDef.methods.remove(originalMethod)
    match.classDef.methods.add(target.method)
    hookProfileNavigationUrl(target.method, target.snapshotRegister)
}

internal data class ProfileNavigationUrlHookTarget(
    val method: MutableMethod,
    val snapshotRegister: Int,
)

internal fun MutableMethod.reserveProfileSnapshotRegister(): ProfileNavigationUrlHookTarget {
    val implementation =
        implementation ?: throw PatchException("NewX profile URL getter has no implementation: $this")
    val parameterRegisters = numberOfParameterRegisters
    if (parameterRegisters == 0) {
        throw PatchException("NewX profile URL getter has no instance receiver: $this")
    }

    val snapshotRegister = implementation.registerCount
    val expanded = cloneMutable(additionalRegisters = parameterRegisters + 1)
    val expandedImplementation =
        expanded.implementation
            ?: throw PatchException("Expanded NewX profile URL getter has no implementation: $this")
    val firstParameterRegister = expandedImplementation.registerCount - expanded.numberOfParameterRegisters
    if (snapshotRegister !in 0 until firstParameterRegister) {
        throw PatchException(
            "NewX profile snapshot register v$snapshotRegister is not a local in expanded getter $expanded",
        )
    }

    return ProfileNavigationUrlHookTarget(expanded, snapshotRegister)
}

internal fun hookProfileNavigationUrl(method: MutableMethod, savedProfileRegister: Int) {
    val implementation =
        method.implementation ?: throw PatchException("NewX profile URL getter has no implementation: $method")
    val firstParameterRegister = implementation.registerCount - method.numberOfParameterRegisters
    if (savedProfileRegister !in 0 until firstParameterRegister) {
        throw PatchException(
            "NewX profile snapshot register v$savedProfileRegister is not a local in $method",
        )
    }

    val profileResults = method.profileModelResults()
    if (profileResults.size != 1) {
        throw PatchException(
            "Expected one normalized profile model in NewX profile URL getter $method, found " +
                profileResults.size,
        )
    }

    val profileResult = profileResults.single()
    val saveIndex = profileResult.resultIndex + 1
    val preexistingTargets = method.instructions.branchTargetIndices()
    if (saveIndex in preexistingTargets) {
        throw PatchException(
            "NewX profile model snapshot has an incoming branch at $method instruction $saveIndex",
        )
    }

    method.insertHook(
        index = saveIndex,
        relocateBranchTargets = false,
    ) {
        move(savedProfileRegister, profileResult.register, OBJECT_DESCRIPTOR)
    }

    val returnIndices = method.instructions.mapIndexedNotNull { index, instruction ->
        index.takeIf { instruction.opcode == Opcode.RETURN_OBJECT }
    }
    if (returnIndices.isEmpty()) {
        throw PatchException("NewX profile URL getter has no object return: $method")
    }
    val branchTargets = method.instructions.branchTargetIndices()
    val targetedReturns = returnIndices.filter { it in branchTargets }
    if (targetedReturns.isNotEmpty()) {
        throw PatchException(
            "NewX profile URL getter has branch-targeted returns that cannot be rewritten in $method: " +
                targetedReturns,
        )
    }

    returnIndices.asReversed().forEach { returnIndex ->
        val returnRegister =
            (method.instructions[returnIndex] as? OneRegisterInstruction)?.registerA
                ?: throw PatchException("Expected a one-register profile URL return in $method")
        method.insertHook(
            index = returnIndex,
            excludedRegisters = listOf(returnRegister, savedProfileRegister),
            relocateBranchTargets = false,
        ) {
            invokeStatic(
                methodReference(REWRITE_PROFILE_URL_METHOD),
                returnRegister,
                savedProfileRegister,
            )
            moveResult(returnRegister, STRING_DESCRIPTOR)
        }
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
    // The field store is the merge point for every URL branch (post URL, status builder,
    // trending/lists, null). A plain insert before the store leaves the branch labels on the
    // original IPUT, so those paths would skip the rewrite. Insert at the control-flow label so
    // the branch targets run the rewrite before the store.
    method.addSharedUrlRewriteAtControlFlowLabel(fieldStoreIndex, valueRegister)
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

private fun List<Instruction>.branchTargetIndices(): Set<Int> {
    val offsets = IntArray(size)
    val indexByOffset = mutableMapOf<Int, Int>()
    var codeOffset = 0
    forEachIndexed { index, instruction ->
        offsets[index] = codeOffset
        indexByOffset[codeOffset] = index
        codeOffset += instruction.codeUnits
    }

    return mapIndexedNotNull { index, instruction ->
        val branch = instruction as? OffsetInstruction ?: return@mapIndexedNotNull null
        indexByOffset[offsets[index] + branch.codeOffset]
    }.toSet()
}

/**
 * Rewrites the URL held in [register] through the custom-domain resolver, in place.
 *
 * The API lowers the call to `invoke-static/range` by itself when [register] is above `v15`,
 * which is what the old hand written opcode choice did.
 */
private fun Block.rewriteSharedUrl(register: Int) {
    invokeStatic(methodReference(REWRITE_SHARED_URL_METHOD), register)
    moveResult(register, STRING_DESCRIPTOR)
}

/**
 * Rewrites the URL in front of [instructionIndex] keeping every incoming branch on the original
 * instruction, which is what the plain `addInstructions` this replaces did.
 */
private fun MutableMethod.addSharedUrlRewrite(instructionIndex: Int, register: Int) {
    insertHook(index = instructionIndex, relocateBranchTargets = false) { rewriteSharedUrl(register) }
}

/**
 * Rewrites the URL at the control-flow label: the labels of [instructionIndex] move onto the
 * rewrite, so every path that reached the original instruction runs it first (the behaviour of
 * `addInstructionsAtControlFlowLabel`).
 */
/**
 * The field store is the merge point of every URL branch, and those branches have to run the
 * rewrite, so their labels must move onto the injected block. The typed hook's relocation left a
 * label unplaced on 12.28.0-prod.01 (see docs/newx-typed-api-port.md), so this one site keeps the
 * library helper that is proven in production until that is fixed.
 */
private fun MutableMethod.addSharedUrlRewriteAtControlFlowLabel(instructionIndex: Int, register: Int) {
    addInstructionsAtControlFlowLabel(
        instructionIndex,
        rewriteSharedUrlInstructions(register),
    )
}

private fun rewriteSharedUrlInstructions(register: Int): String =
    """
        invoke-static {v$register}, $REWRITE_SHARED_URL_METHOD
        move-result-object v$register
    """.trimIndent()
