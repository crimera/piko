package app.crimera.patches.newx.misc.canonicalurls

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.models.resolvedNewXPostModels
import app.crimera.patches.newx.models.newXPostModelResolutionPatch
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.instanceOf
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val URI = "Landroid/net/Uri;"
private const val CANONICAL_URL_RESOLVER =
    "Lapp/morphe/extension/newx/misc/CanonicalUrlResolver;"
private const val CANONICAL_URL_RESOLVE_METHOD =
    "$CANONICAL_URL_RESOLVER->resolve(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;"
private const val POST_URL_FIELD_FILTER_INDEX = 3
private const val TEXT_ENTITY_URL_FIELD_FILTER_INDEX = 5

private object UrlEntityModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/text/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("UrlEntity(displayUrl=")),
)

private object MentionEntityModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/text/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("MentionEntity(userId="), string(", startIdx=")),
)

private object CardUrlActionModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/cards/api/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("Url(url=")),
)

private object ProfileUserModelFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("ProfileUser(id=")),
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
 * ALPHA PATH: resolves model fields from stable data-class labels and patches the legacy
 * navigation callbacks. TODO: Remove this path when alpha compatibility is deprecated.
 * BETA PATH: patches the shared text/TimelineUrl helpers, the URT post callback, and legacy
 * card navigation; each still has a short-URL fallback.
 */
@Suppress("unused")
val newXCanonicalUrlsPatch =
    bytecodePatch(
        name = "NewX: Open canonical URLs",
        description =
            "Opens the expanded (canonical) URL directly when clicking links instead of the shortened t.co link.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXPostModelResolutionPatch, newXExtensionPatch)

        execute {
            val postModels = resolvedNewXPostModels()
            val contextualPostClass = classDefByOrNull(postModels.contextualPostDescriptor)
            val isBetaPath = contextualPostClass?.methods?.any { method ->
                method.name == "getCanonicalPost" &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == postModels.canonicalPostDescriptor
            } == true
            if (isBetaPath) {
                val urlEntityType = resolveUrlEntityType()
                patchBetaTextNavigation(urlEntityType)
                patchBetaPostLinkNavigation(urlEntityType, postModels.contextualPostDescriptor)
                val cardUrlActionType = resolveCardUrlActionType()
                patchBetaCardNavigation(cardUrlActionType, postModels.contextualPostDescriptor)
                patchProfileWebsiteNavigation(urlEntityType)
                return@execute
            }
            // ALPHA PATH: patch the legacy canonical-URL callbacks below.
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

            val cardUrlActionType = resolveCardUrlActionType()
            patchCardNavigation(cardUrlActionType, postModels.contextualPostDescriptor)
            patchProfileWebsiteNavigation(matches.expandedUrlField.definingClass)
        }
    }

context(_: BytecodePatchContext)
private fun resolveCanonicalUrlMatches(): CanonicalUrlMatches {
    val postModels = resolvedNewXPostModels()
    val urlEntityMatch = UrlEntityModelFingerprint.requireSingleMatch("URL entity model")
    val urlEntityFields = resolveUrlEntityFields(urlEntityMatch)
    val mentionType = MentionEntityModelFingerprint.requireSingleMatch("mention entity model")
        .originalClassDef.type
    val textEntityNavigationFingerprint =
        Fingerprint(
            definingClass = "Lcom/x/navigation/",
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
            definingClass = textEntityNavigationMatch.originalClassDef.type,
            parameters = listOf(STRING_DESCRIPTOR, STRING_DESCRIPTOR),
            returnType = STRING_DESCRIPTOR,
            filters = listOf(uriParseCall(), uriAuthorityCall()),
        ).requireSingleMatch("URL picker")

    val postLinkClickMatch =
        Fingerprint(
            definingClass = "Lcom/x/urt/items/post/",
            returnType = "V",
            parameters = listOf(
                "L",
                postModels.contextualPostDescriptor,
                "L",
                "L",
                "L",
                "L",
                "L",
            ),
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

private fun Instruction.argumentRegistersOrNull(): List<Int>? =
    registersUsed.takeIf { registers -> registers.size == 2 }

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

context(context: BytecodePatchContext)
private fun resolveUrlEntityType(): String =
    UrlEntityModelFingerprint.requireSingleMatch("URL entity model")
        .originalClassDef.type

context(context: BytecodePatchContext)
private fun resolveCardUrlActionType(): String =
    CardUrlActionModelFingerprint.requireSingleMatch("card URL action model")
        .originalClassDef.type

context(context: BytecodePatchContext)
private fun patchProfileWebsiteNavigation(urlEntityType: String) {
    val profileUserType =
        ProfileUserModelFingerprint.requireSingleMatch("profile user model")
            .originalClassDef.type
    val match =
        Fingerprint(
            definingClass = "Lcom/x/profile/header/",
            filters =
                listOf(
                    methodCall(
                        opcode = Opcode.INVOKE_VIRTUAL,
                        definingClass = profileUserType,
                        name = "getWebsite",
                        parameters = emptyList(),
                        returnType = urlEntityType,
                    ),
                    methodCall(
                        opcode = Opcode.INVOKE_VIRTUAL,
                        definingClass = urlEntityType,
                        name = "getDisplayUrl",
                        parameters = emptyList(),
                        returnType = STRING_DESCRIPTOR,
                    ),
                    methodCall(
                        opcode = Opcode.INVOKE_VIRTUAL,
                        definingClass = urlEntityType,
                        name = "getUrl",
                        parameters = emptyList(),
                        returnType = STRING_DESCRIPTOR,
                    ),
                ),
        ).requireSingleMatch("profile website link builder")

    replaceUrlEntityGetter(
        match,
        filterIndex = 2,
        urlEntityType = urlEntityType,
        label = "profile website URL getter",
    )
}

context(context: BytecodePatchContext)
private fun patchBetaTextNavigation(urlEntityType: String) {
    val mentionType = MentionEntityModelFingerprint.requireSingleMatch("mention entity model")
        .originalClassDef.type
    val textEntityNavigationMatch =
        Fingerprint(
            definingClass = "Lcom/x/navigation/",
            parameters = listOf("L", "L"),
            returnType = "V",
            filters =
                listOf(
                    instanceOf(mentionType),
                    instanceOf(urlEntityType),
                    urlEntityGetter("getExpandedUrl", urlEntityType),
                    uriParseCall(),
                    uriAuthorityCall(),
                    urlEntityGetter("getUrl", urlEntityType),
                ),
        ).requireSingleMatch("beta text-entity navigation")
    val urlPickerMatch =
        Fingerprint(
            definingClass = textEntityNavigationMatch.originalClassDef.type,
            parameters = listOf(STRING_DESCRIPTOR, STRING_DESCRIPTOR),
            returnType = STRING_DESCRIPTOR,
            filters = listOf(uriParseCall(), uriAuthorityCall()),
        ).requireSingleMatch("beta URL picker")

    replaceUrlEntityGetter(
        textEntityNavigationMatch,
        filterIndex = 5,
        urlEntityType = urlEntityType,
        label = "beta text-entity URL getter",
    )
    preferExpandedUrlInUrlPicker(urlPickerMatch)
}

context(context: BytecodePatchContext)
private fun patchBetaPostLinkNavigation(
    urlEntityType: String,
    contextualPostType: String,
) {
    val match =
        Fingerprint(
            definingClass = "Lcom/x/urt/items/post/",
            parameters = listOf("L", contextualPostType, "L", "L", "L", "L", "L"),
            returnType = "V",
            filters =
                listOf(
                    instanceOf(urlEntityType),
                    urlEntityGetter("getExpandedUrl", urlEntityType),
                    uriParseCall(),
                    uriAuthorityCall(),
                    urlEntityGetter("getUrl", urlEntityType),
                    methodCall(
                        opcode = Opcode.INVOKE_STATIC,
                        definingClass = "Lcom/x/navigation/",
                        parameters = listOf(STRING_DESCRIPTOR, "L", "Z"),
                        returnType = STRING_DESCRIPTOR,
                    ),
                    methodCall(
                        opcode = Opcode.INVOKE_VIRTUAL,
                        name = "getId",
                        parameters = emptyList(),
                        returnType = "L",
                    ),
                    methodCall(
                        opcode = Opcode.INVOKE_VIRTUAL,
                        name = "getValue",
                        parameters = emptyList(),
                        returnType = "J",
                    ),
                ),
        ).requireSingleMatch("beta post link click handler")

    replaceUrlEntityGetter(
        match,
        filterIndex = 4,
        urlEntityType = urlEntityType,
        label = "beta post link URL getter",
    )
}

private fun urlEntityGetter(name: String, urlEntityType: String) =
    methodCall(
        opcode = Opcode.INVOKE_VIRTUAL,
        definingClass = urlEntityType,
        name = name,
        parameters = emptyList(),
        returnType = STRING_DESCRIPTOR,
    )

private fun replaceUrlEntityGetter(
    match: Match,
    filterIndex: Int,
    urlEntityType: String,
    label: String,
) {
    val getterIndex = match.instructionMatches[filterIndex].index
    val getter = match.method.instructions[getterIndex]
    val getterReference = getter.getReference<MethodReference>()
        ?: throw PatchException("$label reference is missing")
    if (getterReference.name != "getUrl") {
        throw PatchException("Unexpected $label: $getterReference")
    }

    match.method.replaceInstruction(
        getterIndex,
        singleRegisterInvoke(
            getter.singleRegister(label),
            "$urlEntityType->getExpandedUrl()$STRING_DESCRIPTOR",
        ),
    )
}

private fun Instruction.singleRegister(label: String): Int =
    registersUsed.singleOrNull()
        ?: throw PatchException("$label does not have exactly one receiver register")

private fun singleRegisterInvoke(receiverRegister: Int, methodReference: String): String {
    if (receiverRegister !in 0..0xffff) {
        throw PatchException("Invoke receiver register is out of range: v$receiverRegister")
    }
    val opcode = if (receiverRegister <= 15) "invoke-virtual" else "invoke-virtual/range"
    val registers =
        if (receiverRegister <= 15) "{v$receiverRegister}"
        else "{v$receiverRegister .. v$receiverRegister}"
    return "$opcode $registers, $methodReference"
}

context(context: BytecodePatchContext)
private fun patchBetaCardNavigation(cardUrlActionType: String, contextualPostType: String) {
    val filters =
        listOf(
            instanceOf(cardUrlActionType),
            fieldAccess(
                opcode = Opcode.IGET_OBJECT,
                definingClass = cardUrlActionType,
                type = STRING_DESCRIPTOR,
            ),
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                definingClass = "Lcom/x/navigation/",
                parameters = listOf(STRING_DESCRIPTOR, "L", "Z"),
                returnType = STRING_DESCRIPTOR,
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = contextualPostType,
                name = "getId",
                parameters = emptyList(),
                returnType = "L",
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                name = "getValue",
                parameters = emptyList(),
                returnType = "J",
            ),
            methodCall(
                definingClass = "Lcom/x/urt/items/post/",
                parameters = listOf(STRING_DESCRIPTOR, "J", "L", STRING_DESCRIPTOR),
                returnType = "L",
            ),
        )
    val match =
        Fingerprint(
            definingClass = "Lcom/x/urt/items/post/",
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "Ljava/lang/Object;",
            filters = filters,
        ).requireSingleMatch("beta card navigation callback")

    val urlFieldReadIndex = match.instructionMatches[1].index
    val urlFieldRead = match.method.objectFieldRead(urlFieldReadIndex, "Beta card callback URL")
    val urlField = urlFieldRead.getReference<FieldReference>()
        ?: throw PatchException("Beta card callback card URL field reference is missing")
    if (urlField.type != STRING_DESCRIPTOR || urlField.definingClass != cardUrlActionType) {
        throw PatchException("Unexpected beta card URL field: $urlField")
    }

    val postGetIdIndex = match.instructionMatches[3].index
    val postGetIdInstruction = match.method.instructions[postGetIdIndex]
    val postRegister = postGetIdInstruction.singleRegister("Beta card callback contextual-post")

    patchCardUrl(
        match,
        urlFieldReadIndex + 1,
        postRegister,
        urlFieldRead.registerA,
    )
}

private fun Method.objectFieldRead(index: Int, label: String): TwoRegisterInstruction {
    val instruction = instructions.toList().getOrNull(index) as? TwoRegisterInstruction
    if (instruction?.opcode == Opcode.IGET_OBJECT) return instruction
    throw PatchException("$label is not an iget-object")
}

private fun patchCardUrl(
    match: Match,
    insertionIndex: Int,
    postRegister: Int,
    urlRegister: Int,
) {
    if (postRegister !in 0..15 || urlRegister !in 0..15) {
        throw PatchException(
            "Card canonical URL resolver requires four-bit registers: " +
                "post=v$postRegister, url=v$urlRegister",
        )
    }

    match.method.addInstructions(
        insertionIndex,
        """
        invoke-static {v$postRegister, v$urlRegister}, $CANONICAL_URL_RESOLVE_METHOD
        move-result-object v$urlRegister
        """.trimIndent(),
    )
}

context(context: BytecodePatchContext)
private fun patchCardNavigation(cardUrlActionType: String, contextualPostType: String) {
    val match =
        Fingerprint(
            // The alpha repackages this callback into the preserved Compose namespace.
            definingClass = "Landroidx/compose/animation/core/",
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "Ljava/lang/Object;",
            filters =
                listOf(
                    methodCall(
                        opcode = Opcode.INVOKE_VIRTUAL,
                        definingClass = cardUrlActionType,
                        parameters = emptyList(),
                        returnType = STRING_DESCRIPTOR,
                    ),
                    methodCall(
                        opcode = Opcode.INVOKE_STATIC,
                        definingClass = "Lcom/x/navigation/",
                        parameters = listOf(STRING_DESCRIPTOR, "L"),
                        returnType = STRING_DESCRIPTOR,
                    ),
                    methodCall(
                        opcode = Opcode.INVOKE_INTERFACE,
                        name = "getId",
                        parameters = emptyList(),
                        returnType = "L",
                    ),
                    fieldAccess(opcode = Opcode.IGET_WIDE, type = "J"),
                    methodCall(
                        definingClass = "Lcom/x/urt/items/post/",
                        parameters = listOf(STRING_DESCRIPTOR, "J", "L", STRING_DESCRIPTOR),
                        returnType = "L",
                    ),
                ),
        ).requireSingleMatch("card navigation callback")

    val urlGetterIndex = match.instructionMatches.first().index
    val urlResultIndex = urlGetterIndex + 1
    val urlResult = match.method.instructions.getOrNull(urlResultIndex)
        as? OneRegisterInstruction
        ?: throw PatchException("Card URL getter has no move-result-object")
    if (match.method.instructions[urlResultIndex].opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException("Card URL getter is not followed by move-result-object")
    }

    val postRegister = resolveContextualPostRegister(match.method, urlGetterIndex, contextualPostType)
    patchCardUrl(match, urlResultIndex + 1, postRegister, urlResult.registerA)
}

private fun resolveContextualPostRegister(
    method: Method,
    beforeIndex: Int,
    contextualPostType: String,
): Int {
    val registers = method.instructions
        .take(beforeIndex)
        .mapNotNull { instruction ->
            if (instruction.opcode != Opcode.CHECK_CAST) return@mapNotNull null
            if (instruction.getReference<TypeReference>()?.type != contextualPostType) {
                return@mapNotNull null
            }
            (instruction as? OneRegisterInstruction)?.registerA
        }
        .distinct()
    return registers.lastOrNull()
        ?: throw PatchException("Could not resolve the contextual post register in card navigation callback")
}

context(_: BytecodePatchContext)
private fun Fingerprint.requireSingleMatch(label: String): Match {
    val matches = scopedMatchAll().distinctBy { it.originalMethod.toString() }
    return matches.singleOrNull()
        ?: throw PatchException(
            "Expected exactly one $label match, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
}
