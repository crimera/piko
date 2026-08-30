package app.crimera.patches.newx.misc.customsharingdomain

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXTextInput
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
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
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val SHARE_URL_RESOLVER_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/ShareUrlResolver;"
private const val CHANGE_DOMAIN_METHOD =
    "$SHARE_URL_RESOLVER_DESCRIPTOR->changeDomain(Ljava/lang/String;)Ljava/lang/String;"
private const val CUSTOM_DOMAIN_VALIDATOR_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/CustomSharingDomainValidator;"
private const val SHARE_SHEET_DESCRIPTOR_PREFIX = "Lcom/x/dms/components/sharesheet/"
private const val SHARE_IMPL_DESCRIPTOR_PREFIX = "Lcom/x/share/impl/"
private const val LEGACY_SHARE_COPY_DESCRIPTOR_PREFIX = "Lcom/x/reactwithvideo/"
private const val NAVIGATION_DESCRIPTOR_PREFIX = "Lcom/x/navigation/"
private const val SHARE_STATUS_URL_PREFIX = "https://x.com/i/status/"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val INTENT_DESCRIPTOR = "Landroid/content/Intent;"
private const val SEND_ACTION = "android.intent.action.SEND"
private const val EXTRA_TEXT = "android.intent.extra.TEXT"

/**
 * Share-sheet constructor that owns the final URL field. The status URL is a stable semantic
 * anchor; the void return shape identifies the share-sheet constructor.
 */
internal object ShareSheetUrlConstructorFingerprint : Fingerprint(
    definingClass = SHARE_SHEET_DESCRIPTOR_PREFIX,
    returnType = "V",
    filters = listOf(string(SHARE_STATUS_URL_PREFIX)),
)

/** Copy callback that writes the post URL to the clipboard in newer NewX builds. */
internal object ShareSheetCopyCallbackFingerprint : Fingerprint(
    definingClass = SHARE_IMPL_DESCRIPTOR_PREFIX,
    parameters = listOf(STRING_DESCRIPTOR),
    returnType = "V",
    filters = listOf(string("link"), string("copy_link")),
)

/** Legacy share-sheet callback that receives both media URIs and post URLs. */
internal object LegacyShareSheetCopyFingerprint : Fingerprint(
    definingClass = LEGACY_SHARE_COPY_DESCRIPTOR_PREFIX,
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "Ljava/lang/Object;",
    filters = listOf(string("url")),
)

/** Shared Intent builder used by both the system chooser and direct-app share actions. */
internal object ShareIntentBuilderFingerprint : Fingerprint(
    definingClass = SHARE_IMPL_DESCRIPTOR_PREFIX,
    parameters = listOf(STRING_DESCRIPTOR, STRING_DESCRIPTOR),
    returnType = INTENT_DESCRIPTOR,
    filters = listOf(string(SEND_ACTION), string(EXTRA_TEXT)),
)

/** URL getter used by post-detail navigation and quote/interactor links. */
internal object PostNavigationUrlFingerprint : Fingerprint(
    definingClass = NAVIGATION_DESCRIPTOR_PREFIX,
    name = "l",
    parameters = emptyList(),
    returnType = STRING_DESCRIPTOR,
    filters = listOf(string(SHARE_STATUS_URL_PREFIX)),
)

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
    val matches = scopedMatchAllOrNull().orEmpty()
    if (matches.size != 1) {
        throw PatchException(
            "Expected one $label match, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }
    return matches.single()
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
    val currentMatches = ShareSheetCopyCallbackFingerprint.scopedMatchAllOrNull().orEmpty()
    val legacyMatches = LegacyShareSheetCopyFingerprint.scopedMatchAllOrNull().orEmpty()
    if (currentMatches.size + legacyMatches.size != 1) {
        throw PatchException(
            "Expected one NewX share-sheet copy callback variant, found " +
                (currentMatches.size + legacyMatches.size),
        )
    }

    if (currentMatches.size == 1) {
        currentMatches.single().method.addInstructions(
            0,
            """
            invoke-static {p1}, $CHANGE_DOMAIN_METHOD
            move-result-object p1
            """.trimIndent(),
        )
        return
    }

    val legacyMethod = legacyMatches.single().method
    val stringCastIndices =
        legacyMethod.instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode != Opcode.CHECK_CAST) return@mapIndexedNotNull null
            if (instruction.getReference<TypeReference>()?.type != STRING_DESCRIPTOR) {
                return@mapIndexedNotNull null
            }
            index
        }
    if (stringCastIndices.size != 1) {
        throw PatchException(
            "Expected one legacy NewX share URL cast in $legacyMethod, found " +
                stringCastIndices.size,
        )
    }

    val castIndex = stringCastIndices.single()
    val castInstruction = legacyMethod.instructions[castIndex] as? OneRegisterInstruction
        ?: throw PatchException("Expected a one-register legacy NewX share URL cast in $legacyMethod")
    legacyMethod.addDomainRewrite(castIndex + 1, castInstruction.registerA)
}

context(_: app.morphe.patcher.patch.BytecodePatchContext)
private fun hookShareIntentBuilder() {
    ShareIntentBuilderFingerprint
        .requireSingleMatch("NewX share Intent builder")
        .method
        .addInstructions(
            0,
            """
            invoke-static {p0}, $CHANGE_DOMAIN_METHOD
            move-result-object p0
            """.trimIndent(),
        )
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

private fun MutableMethod.findStatusUrlResultIndices(): List<Int> =
    instructions.mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.CONST_STRING && instruction.opcode != Opcode.CONST_STRING_JUMBO) {
            return@mapIndexedNotNull null
        }
        val reference = instruction.getReference<StringReference>() ?: return@mapIndexedNotNull null
        if (reference.string != SHARE_STATUS_URL_PREFIX) return@mapIndexedNotNull null

        val builderIndex = index + 1
        val builderOpcode = instructions.getOrNull(builderIndex)?.opcode
        if (builderOpcode != Opcode.INVOKE_STATIC && builderOpcode != Opcode.INVOKE_STATIC_RANGE) {
            throw PatchException(
                "Expected status URL prefix to be followed by a static builder invoke in $this at " +
                    "instruction $index",
            )
        }
        val resultIndex = index + 2
        if (instructions.getOrNull(resultIndex)?.opcode != Opcode.MOVE_RESULT_OBJECT) {
            throw PatchException(
                "Expected status URL builder to be followed by move-result-object in $this at " +
                    "instruction $builderIndex",
            )
        }
        resultIndex
    }

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
