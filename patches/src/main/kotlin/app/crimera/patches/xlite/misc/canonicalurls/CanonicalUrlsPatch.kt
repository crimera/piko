package app.crimera.patches.xlite.misc.canonicalurls

import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.instanceOf
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val URI = "Landroid/net/Uri;"
private const val POST_URL_FIELD_FILTER_INDEX = 3
private const val TEXT_ENTITY_URL_FIELD_FILTER_INDEX = 5

private object UrlEntityModelFingerprint : Fingerprint(
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    strings = listOf("UrlEntity(displayUrl="),
)

private object MentionEntityModelFingerprint : Fingerprint(
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    strings = listOf("MentionEntity(userId=", ", startIdx="),
)

private object ContextualPostModelFingerprint : Fingerprint(
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    strings = listOf("ContextualPost(canonicalPost=", ", quotedPost="),
)

private data class UrlEntityFields(
    val type: String,
    val expandedUrl: FieldReference,
    val url: FieldReference,
)

private data class CanonicalUrlMatches(
    val postLinkClick: Match,
    val textEntityNavigation: Match,
    val urlPicker: Match,
    val expandedUrlField: FieldReference,
)

/**
 * Opens expanded URL values in the Compose/URT navigation paths instead of shortened URLs.
 *
 * The alpha obfuscates model descriptors and removes the model getters. Model classes and
 * fields are therefore resolved from their stable data-class labels, then used only to build
 * release-specific fingerprints at patch time.
 */
@Suppress("unused")
val xLiteCanonicalUrlsPatch =
    bytecodePatch(
        name = "X-Lite: Open canonical URLs",
        description =
            "Opens the expanded (canonical) URL directly when clicking links instead of the shortened t.co link.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        execute {
            val matches = resolveCanonicalUrlMatches()
            replaceUrlEntityFieldRead(
                matches.postLinkClick,
                POST_URL_FIELD_FILTER_INDEX,
                matches.expandedUrlField,
            )
            replaceUrlEntityFieldRead(
                matches.textEntityNavigation,
                TEXT_ENTITY_URL_FIELD_FILTER_INDEX,
                matches.expandedUrlField,
            )
            preferExpandedUrlInUrlPicker(matches.urlPicker)
        }
    }

context(_: BytecodePatchContext)
private fun resolveCanonicalUrlMatches(): CanonicalUrlMatches {
    val urlEntityMatch = UrlEntityModelFingerprint.requireSingleMatch("URL entity model")
    val urlEntityFields = resolveUrlEntityFields(urlEntityMatch)
    val mentionType = MentionEntityModelFingerprint.requireSingleMatch("mention entity model")
        .originalClassDef.type
    val contextualPostType =
        ContextualPostModelFingerprint.requireSingleMatch("contextual post model")
            .originalClassDef.type

    val textEntityNavigationFingerprint =
        Fingerprint(
            definingClass = "Lcom/x/",
            parameters = listOf("L", "L"),
            returnType = "V",
            filters =
                listOf(
                    instanceOf(mentionType),
                    instanceOf(urlEntityFields.type),
                    fieldAccess(
                        opcode = Opcode.IGET_OBJECT,
                        reference = urlEntityFields.expandedUrl,
                    ),
                    uriParseCall(),
                    uriAuthorityCall(),
                    fieldAccess(
                        opcode = Opcode.IGET_OBJECT,
                        reference = urlEntityFields.url,
                    ),
                ),
        )
    val textEntityNavigationMatch =
        textEntityNavigationFingerprint.requireSingleMatch("text-entity navigation")

    val urlPickerMatch =
        Fingerprint(
            classFingerprint = textEntityNavigationFingerprint,
            parameters = listOf(STRING_DESCRIPTOR, STRING_DESCRIPTOR),
            returnType = STRING_DESCRIPTOR,
            filters = listOf(uriParseCall(), uriAuthorityCall()),
        ).requireSingleMatch("URL picker")

    val postLinkClickMatch =
        Fingerprint(
            definingClass = "Lcom/x/",
            returnType = "V",
            parameters = listOf("L", contextualPostType, "L", "L", "L", "L", "L"),
            filters =
                listOf(
                    fieldAccess(
                        opcode = Opcode.IGET_OBJECT,
                        reference = urlEntityFields.expandedUrl,
                    ),
                    uriParseCall(),
                    uriAuthorityCall(),
                    fieldAccess(
                        opcode = Opcode.IGET_OBJECT,
                        reference = urlEntityFields.url,
                    ),
                    methodCall(
                        opcode = Opcode.INVOKE_INTERFACE,
                        name = "getId",
                        parameters = emptyList(),
                        returnType = "L",
                    ),
                    fieldAccess(opcode = Opcode.IGET_WIDE, type = "J"),
                ),
        ).requireSingleMatch("post link click handler")

    return CanonicalUrlMatches(
        postLinkClick = postLinkClickMatch,
        textEntityNavigation = textEntityNavigationMatch,
        urlPicker = urlPickerMatch,
        expandedUrlField = urlEntityFields.expandedUrl,
    )
}

private fun resolveUrlEntityFields(match: Match): UrlEntityFields {
    val owner = match.originalClassDef.type
    val constructor =
        match.originalClassDef.methods.singleOrNull { method ->
            method.name == "<init>" &&
                listOf("displayUrl", "expandedUrl", "url").all { label ->
                    method.hasNamedParameter(label)
                }
        } ?: throw PatchException("Could not resolve the semantic URL entity constructor")

    val fields = listOf("displayUrl", "expandedUrl", "url").map { label ->
        constructor.fieldWrittenFromNamedParameter(label, owner)
    }
    if (fields.distinctBy(FieldReference::toString).size != fields.size) {
        throw PatchException("URL entity constructor reuses a String field: ${fields.joinToString()}")
    }

    return UrlEntityFields(
        type = owner,
        expandedUrl = fields[1],
        url = fields[2],
    )
}

private fun Method.hasNamedParameter(label: String): Boolean =
    findNamedParameterRegister(label) != null

private fun Method.fieldWrittenFromNamedParameter(
    label: String,
    owner: String,
): FieldReference {
    val parameterRegister =
        findNamedParameterRegister(label)
            ?: throw PatchException("URL entity constructor has no $label parameter")
    val fields =
        implementation?.instructions?.toList().orEmpty().mapNotNull { instruction ->
            if (instruction.opcode != Opcode.IPUT_OBJECT) return@mapNotNull null
            val registers = instruction as? TwoRegisterInstruction ?: return@mapNotNull null
            if (registers.registerA != parameterRegister) return@mapNotNull null
            instruction.getReference<FieldReference>()?.takeIf { field ->
                field.definingClass == owner && field.type == STRING_DESCRIPTOR
            }
        }.distinctBy(FieldReference::toString)
    return fields.singleOrNull()
        ?: throw PatchException("Expected one $label URL entity field write, found ${fields.joinToString()}")
}

private fun Method.findNamedParameterRegister(label: String): Int? {
    val instructions = implementation?.instructions?.toList().orEmpty()
    instructions.forEachIndexed { index, instruction ->
        val stringInstruction = instruction as? OneRegisterInstruction ?: return@forEachIndexed
        val reference = instruction.getReference<StringReference>() ?: return@forEachIndexed
        if (reference.string != label) return@forEachIndexed
        val invoke = instructions.getOrNull(index + 1) ?: return@forEachIndexed
        val methodReference = invoke.getReference<MethodReference>() ?: return@forEachIndexed
        if (
            methodReference.parameterTypes.map(CharSequence::toString) !=
                listOf("Ljava/lang/Object;", STRING_DESCRIPTOR) ||
            methodReference.returnType != "V"
        ) return@forEachIndexed
        val arguments = invoke.argumentRegistersOrNull() ?: return@forEachIndexed
        if (arguments.getOrNull(1) != stringInstruction.registerA) return@forEachIndexed
        return arguments.firstOrNull()
    }
    return null
}

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.argumentRegistersOrNull(): List<Int>? =
    when (this) {
        is FiveRegisterInstruction -> listOf(registerC, registerD)
        is RegisterRangeInstruction -> listOf(startRegister, startRegister + 1)
        else -> null
    }

private fun uriParseCall() =
    methodCall(
        opcode = Opcode.INVOKE_STATIC,
        definingClass = URI,
        name = "parse",
        parameters = listOf(STRING_DESCRIPTOR),
        returnType = URI,
    )

private fun uriAuthorityCall() =
    methodCall(
        opcode = Opcode.INVOKE_VIRTUAL,
        definingClass = URI,
        name = "getAuthority",
        parameters = emptyList(),
        returnType = STRING_DESCRIPTOR,
    )

private fun replaceUrlEntityFieldRead(
    match: Match,
    filterIndex: Int,
    replacement: FieldReference,
) {
    val method = match.method
    val fieldReadIndex = match.instructionMatches[filterIndex].index
    val fieldRead =
        method.instructions[fieldReadIndex] as? TwoRegisterInstruction
            ?: throw PatchException(
                "Expected an encoded URL-entity field read at instruction $fieldReadIndex",
            )
    if (fieldRead.opcode != Opcode.IGET_OBJECT) {
        throw PatchException(
            "Expected an iget-object URL-entity field read at instruction $fieldReadIndex",
        )
    }

    method.replaceInstruction(
        fieldReadIndex,
        "iget-object v${fieldRead.registerA}, v${fieldRead.registerB}, $replacement",
    )
}

private fun preferExpandedUrlInUrlPicker(match: Match) {
    val method = match.method

    // `f(url, expanded)`: use the expanded URL whenever it is available.
    val firstInstruction = method.instructions.first()
    method.addInstructionsWithLabels(
        0,
        """
        if-eqz p1, :piko_canonical_url_fallback
        return-object p1
        """.trimIndent(),
        ExternalLabel("piko_canonical_url_fallback", firstInstruction),
    )
}

context(_: BytecodePatchContext)
private fun Fingerprint.requireSingleMatch(label: String): Match {
    val matches = matchAll().distinctBy { it.originalMethod.toString() }
    return matches.singleOrNull()
        ?: throw PatchException(
            "Expected exactly one $label match, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
}
