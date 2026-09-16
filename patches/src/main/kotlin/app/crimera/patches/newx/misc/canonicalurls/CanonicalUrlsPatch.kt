package app.crimera.patches.newx.misc.canonicalurls

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.models.resolvedNewXPostModels
import app.crimera.patches.newx.models.newXPostModelResolutionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.ToggleSettingDefinition
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.SETTINGS_REGISTRY_DESCRIPTOR
import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
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
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
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
private const val RICH_TEXT_DISPLAY_URL_FIELD_FILTER_INDEX = 1
private const val PROFILE_LINK_DISPLAY_URL_FIELD_FILTER_INDEX = 0
private const val PROFILE_LINK_OPEN_URL_FIELD_FILTER_INDEX = 1

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

private data class UrlEntityFields(
    val type: String,
    val displayUrl: FieldReference,
    val expandedUrl: FieldReference,
    val url: FieldReference,
)

private data class CanonicalUrlMatches(
    val postLinkClick: Match,
    val textEntityNavigation: Match,
    val urlPicker: UrlPickerShape,
    val expandedUrlField: FieldReference,
)

private sealed interface UrlPickerShape {
    data class Extracted(val match: Match) : UrlPickerShape

    data class Inlined(
        val match: Match,
        val sites: List<InlinedUrlPickerSite>,
    ) : UrlPickerShape
}

private data class InlinedUrlPickerSite(
    val gateIndex: Int,
    val selectedRegister: Int,
    val expandedRegister: Int,
    val continuation: Instruction,
)

private data class InlinedUrlPickerNormalization(
    val index: Int,
    val sourceRegister: Int,
)

/** Opens expanded URL values in the Compose/URT navigation paths. */
@Suppress("unused")
val newXCanonicalUrlsPatch =
    bytecodePatch(
        name = "NewX: Open canonical URLs",
        description =
            "Opens the expanded (canonical) URL directly when clicking links instead of the shortened t.co link.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXPostModelResolutionPatch, newXExtensionPatch)

        val useCanonicalUrls =
            newXToggle(
                id = "newx.content.use_canonical_urls",
                category = Categories.CONTENT,
                strings = settingStrings("piko_newx_canonical_urls"),
                order = 400,
                defaultValue = true,
            )

        execute {
            val postModels = resolvedNewXPostModels()
            val urlEntityFields = resolveUrlEntityFields(
                UrlEntityModelFingerprint.requireSingleMatch("URL entity model"),
            )
            val matches = resolveCanonicalUrlMatches(urlEntityFields)
            replaceUrlEntityFieldRead(
                matches.postLinkClick,
                POST_URL_FIELD_FILTER_INDEX,
                matches.expandedUrlField,
                useCanonicalUrls,
            )
            replaceUrlEntityFieldRead(
                matches.textEntityNavigation,
                TEXT_ENTITY_URL_FIELD_FILTER_INDEX,
                matches.expandedUrlField,
                useCanonicalUrls,
            )
            preferExpandedUrlInUrlPicker(matches.urlPicker, useCanonicalUrls)
            patchProfileLinkValues(urlEntityFields, useCanonicalUrls)
            patchRichTextUrlDisplay(urlEntityFields, useCanonicalUrls)

            val cardUrlActionType = resolveCardUrlActionType()
            patchCardNavigation(
                cardUrlActionType,
                postModels.contextualPostDescriptor,
                useCanonicalUrls,
            )
        }
    }

context(_: BytecodePatchContext)
private fun resolveCanonicalUrlMatches(urlEntityFields: UrlEntityFields): CanonicalUrlMatches {
    val postModels = resolvedNewXPostModels()
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

    val urlPickerShape =
        resolveUrlPickerShape(textEntityNavigationMatch.originalClassDef.type)

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
        urlPicker = urlPickerShape,
        expandedUrlField = urlEntityFields.expandedUrl,
    )
}

private fun resolveUrlEntityFields(match: Match): UrlEntityFields {
    val owner = match.originalClassDef.type
    val instructions = match.originalMethod.implementation?.instructions?.toList()
        ?: throw PatchException("URL entity toString method has no implementation")
    listOf(
        "UrlEntity(displayUrl=",
        ", expandedUrl=",
        ", url=",
    ).forEach { label ->
        requireExactlyOne(
            "URL entity toString label '$label'",
            instructions.filter { instruction ->
                instruction.getReference<StringReference>()?.string == label
            },
        )
    }

    // Kotlin's data-class formatting contract consumes these properties in declaration order.
    // Unlike constructor null-check parameter names, these reads survive both compiler lowerings.
    val orderedReads = instructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.IGET_OBJECT) return@mapNotNull null
        instruction.getReference<FieldReference>()?.takeIf { field ->
            field.definingClass == owner && field.type == STRING_DESCRIPTOR
        }
    }
    if (orderedReads.size != 3 || orderedReads.distinctBy(FieldReference::toString).size != 3) {
        throw PatchException(
            "Expected three distinct ordered URL entity String reads in toString, found " +
                orderedReads.joinToString(),
        )
    }

    val declaredStringFields = match.originalClassDef.fields.filter { field ->
        field.type == STRING_DESCRIPTOR && !AccessFlags.STATIC.isSet(field.accessFlags)
    }
    if (declaredStringFields.size != 3) {
        throw PatchException(
            "Expected exactly three URL entity instance String fields, found " +
                declaredStringFields.joinToString(),
        )
    }
    val fields = listOf("displayUrl", "expandedUrl", "url").mapIndexed { index, label ->
        val read = orderedReads[index]
        requireExactlyOne(
            "URL entity $label field declaration",
            declaredStringFields.filter { field -> field.toString() == read.toString() },
        )
    }
    val (displayUrl, expandedUrl, url) = fields

    return UrlEntityFields(
        type = owner,
        displayUrl = displayUrl,
        expandedUrl = expandedUrl,
        url = url,
    )
}

context(_: BytecodePatchContext)
private fun resolveUrlPickerShape(navigationOwner: String): UrlPickerShape {
    val extracted =
        Fingerprint(
            definingClass = navigationOwner,
            parameters = listOf(STRING_DESCRIPTOR, STRING_DESCRIPTOR),
            returnType = STRING_DESCRIPTOR,
            filters = listOf(uriParseCall(), uriAuthorityCall()),
            custom = { method, _ -> method.hasExtractedUrlPickerFlow(navigationOwner) },
        ).scopedMatchAllOrNull().orEmpty().distinctBy { it.originalMethod.toString() }
            .map(UrlPickerShape::Extracted)

    val inlined =
        Fingerprint(
            definingClass = navigationOwner,
            returnType = "V",
            filters = listOf(uriParseCall(), uriAuthorityCall()),
        ).scopedMatchAllOrNull().orEmpty().distinctBy { it.originalMethod.toString() }
            .mapNotNull { match ->
                match.method.resolveInlinedUrlPickerSites(navigationOwner)
                    .takeIf { sites -> sites.size == 2 }
                    ?.let { sites -> UrlPickerShape.Inlined(match, sites) }
            }

    return requireExactlyOne("URL picker capability shape", extracted + inlined)
}

private fun Method.hasExtractedUrlPickerFlow(owner: String): Boolean {
    if (!AccessFlags.STATIC.isSet(accessFlags)) return false
    val methodInstructions = implementation?.instructions?.toList() ?: return false
    val baseRegister = p0Register
    val expandedRegister = baseRegister + 1
    val parseIndices = methodInstructions.indices.filter { index ->
        methodInstructions[index].isUriParseCallWith(expandedRegister)
    }
    val parseIndex =
        requireAtMostOne(
            "extracted URL picker URI parse",
            parseIndices,
        ) ?: return false

    val normalizationCalls = (0 until parseIndex).filter { index ->
        val reference = methodInstructions[index].getReference<MethodReference>()
            ?: return@filter false
        val arguments = methodInstructions[index].registersUsed
        val result = methodInstructions.getOrNull(index + 1) as? OneRegisterInstruction
        methodInstructions[index].opcode == Opcode.INVOKE_STATIC &&
            reference.definingClass == owner &&
            reference.parameterTypes.map(CharSequence::toString) == listOf(STRING_DESCRIPTOR) &&
            reference.returnType == STRING_DESCRIPTOR &&
            arguments == listOf(expandedRegister) &&
            methodInstructions[index + 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
            result?.registerA == expandedRegister
    }
    val normalizationIndex =
        requireAtMostOne(
            "extracted URL picker normalization",
            normalizationCalls,
        ) ?: return false

    val expandedNullGates = (0 until normalizationIndex).filter { index ->
        val branch = methodInstructions[index] as? OneRegisterInstruction
        methodInstructions[index].opcode == Opcode.IF_EQZ && branch?.registerA == expandedRegister
    }
    requireAtMostOne(
        "extracted URL picker expanded-URL null gate",
        expandedNullGates,
    ) ?: return false
    val returnRegisters = methodInstructions.mapNotNull { instruction ->
        (instruction as? OneRegisterInstruction)
            ?.takeIf { instruction.opcode == Opcode.RETURN_OBJECT }
            ?.registerA
    }
    if (returnRegisters.sorted() != listOf(baseRegister, expandedRegister).sorted()) return false

    val parseResult = methodInstructions.getOrNull(parseIndex + 1) as? OneRegisterInstruction
        ?: return false
    val authorityCall = methodInstructions.getOrNull(parseIndex + 2) ?: return false
    val authorityReference = authorityCall.getReference<MethodReference>() ?: return false
    return methodInstructions[parseIndex + 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
        authorityCall.opcode == Opcode.INVOKE_VIRTUAL &&
        authorityReference.definingClass == URI &&
        authorityReference.name == "getAuthority" &&
        authorityReference.parameterTypes.isEmpty() &&
        authorityReference.returnType == STRING_DESCRIPTOR &&
        authorityCall.registersUsed == listOf(parseResult.registerA)
}

private fun Method.resolveInlinedUrlPickerSites(owner: String): List<InlinedUrlPickerSite> {
    val methodInstructions = implementation?.instructions?.toList() ?: return emptyList()
    return methodInstructions.indices.mapNotNull { parseIndex ->
        resolveInlinedUrlPickerSite(methodInstructions, parseIndex, owner)
    }.distinctBy(InlinedUrlPickerSite::gateIndex)
}

private fun resolveInlinedUrlPickerSite(
    instructions: List<Instruction>,
    parseIndex: Int,
    navigationOwner: String,
): InlinedUrlPickerSite? {
    val parseArguments = instructions[parseIndex].registersUsed
    if (parseArguments.size != 1 || !instructions[parseIndex].isUriParseCallWith(parseArguments[0])) {
        return null
    }
    val normalizedRegister = parseArguments.single()
    val parseResult = instructions.getOrNull(parseIndex + 1) as? OneRegisterInstruction ?: return null
    if (instructions[parseIndex + 1].opcode != Opcode.MOVE_RESULT_OBJECT) return null
    val authorityCall = instructions.getOrNull(parseIndex + 2) ?: return null
    val authorityReference = authorityCall.getReference<MethodReference>() ?: return null
    if (
        authorityCall.opcode != Opcode.INVOKE_VIRTUAL ||
        authorityReference.definingClass != URI ||
        authorityReference.name != "getAuthority" ||
        authorityReference.parameterTypes.isNotEmpty() ||
        authorityReference.returnType != STRING_DESCRIPTOR ||
        authorityCall.registersUsed != listOf(parseResult.registerA)
    ) return null
    val authorityCallIndex = parseIndex + 2

    val normalizationCandidates =
        (instructions.basicBlockStart(parseIndex) until parseIndex).mapNotNull { index ->
            val instruction = instructions[index]
            val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
            val result = instructions.getOrNull(index + 1) as? OneRegisterInstruction
            if (
                instruction.opcode != Opcode.INVOKE_STATIC ||
                reference.definingClass != navigationOwner ||
                reference.parameterTypes.map(CharSequence::toString) != listOf(STRING_DESCRIPTOR) ||
                reference.returnType != STRING_DESCRIPTOR ||
                instruction.registersUsed.size != 1 ||
                instructions.getOrNull(index + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT ||
                result?.registerA != normalizedRegister
            ) {
                return@mapNotNull null
            }
            InlinedUrlPickerNormalization(index, instruction.registersUsed.single())
        }
    val normalization = requireAtMostOne(
        "inlined URL picker normalization",
        normalizationCandidates,
    ) ?: return null
    val normalizationIndex = normalization.index
    val expandedRegister = normalization.sourceRegister

    val normalizationBlockStart = instructions.basicBlockStart(normalizationIndex)
    val nullGateCandidates = instructions.indices.filter { index ->
        val branch = instructions[index] as? OneRegisterInstruction
        instructions[index].opcode == Opcode.IF_EQZ &&
            branch?.registerA == expandedRegister &&
            (index + 1 == normalizationBlockStart || instructions.branchTargetIndex(index) == normalizationBlockStart)
    }
    val gateIndex = requireAtMostOne(
        "inlined URL picker expanded-URL null gate",
        nullGateCandidates,
    ) ?: return null
    val continuationIndex = instructions.branchTargetIndex(gateIndex) ?: return null
    if (continuationIndex <= parseIndex || continuationIndex >= instructions.size) return null

    val stringReads = (instructions.basicBlockStart(gateIndex) until gateIndex).mapNotNull { index ->
        val instruction = instructions[index]
        val read = instruction as? TwoRegisterInstruction ?: return@mapNotNull null
        val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
        if (instruction.opcode != Opcode.IGET_OBJECT || field.type != STRING_DESCRIPTOR) {
            return@mapNotNull null
        }
        Triple(index, read, field)
    }
    val expandedReadCandidates = stringReads.filter { (_, read, _) ->
        read.registerA == expandedRegister
    }
    val expandedRead = requireAtMostOne(
        "inlined URL picker expanded-URL field read",
        expandedReadCandidates,
    ) ?: return null
    val baseReadCandidates = stringReads.filter { (_, read, field) ->
        read.registerA != expandedRegister &&
            read.registerB == expandedRead.second.registerB &&
            field.definingClass == expandedRead.third.definingClass
    }
    val baseRead = requireAtMostOne(
        "inlined URL picker base-URL field read",
        baseReadCandidates,
    ) ?: return null
    if (baseRead.third.toString() == expandedRead.third.toString()) return null
    val selectedRegister = baseRead.second.registerA

    val castCandidates = (instructions.basicBlockStart(baseRead.first) until baseRead.first).filter { index ->
        val cast = instructions[index] as? OneRegisterInstruction
        instructions[index].opcode == Opcode.CHECK_CAST &&
            cast?.registerA == baseRead.second.registerB &&
            instructions[index].getReference<TypeReference>()?.type == baseRead.third.definingClass
    }
    requireAtMostOne(
        "inlined URL picker URL-entity cast",
        castCandidates,
    ) ?: return null
    val provesBaseNonNull = (expandedRead.first + 1 until gateIndex).any { index ->
        val instruction = instructions[index]
        val reference = instruction.getReference<MethodReference>() ?: return@any false
        val arguments = instruction.registersUsed
        (instruction.opcode == Opcode.INVOKE_VIRTUAL &&
            reference.definingClass == "Ljava/lang/Object;" &&
            reference.name == "getClass" &&
            arguments == listOf(selectedRegister)) ||
            (instruction.opcode == Opcode.INVOKE_STATIC &&
                reference.parameterTypes.firstOrNull()?.toString() == "Ljava/lang/Object;" &&
                arguments.firstOrNull() == selectedRegister)
    }
    if (!provesBaseNonNull) return null

    val policyBranches = (authorityCallIndex + 1 until continuationIndex).filter { index ->
        val branch = instructions[index] as? OneRegisterInstruction
        instructions[index].opcode == Opcode.IF_EQZ &&
            branch != null &&
            instructions.branchTargetIndex(index) == continuationIndex
    }
    val policyBranchIndex = requireAtMostOne(
        "inlined URL picker authority-policy branch",
        policyBranches,
    ) ?: return null
    val selectionCandidates =
        (policyBranchIndex + 1 until continuationIndex).mapNotNull { index ->
            val selection = instructions[index] as? TwoRegisterInstruction ?: return@mapNotNull null
            if (
                instructions[index].opcode !in
                    setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16) ||
                selection.registerA != selectedRegister ||
                selection.registerB != normalizedRegister
            ) {
                return@mapNotNull null
            }
            index
        }
    requireAtMostOne(
        "inlined URL picker canonical-URL selection",
        selectionCandidates,
    ) ?: return null

    val consumer = instructions[continuationIndex]
    val consumerReference = consumer.getReference<MethodReference>() ?: return null
    if (
        consumer.opcode != Opcode.INVOKE_STATIC ||
        consumerReference.definingClass != navigationOwner ||
        consumerReference.parameterTypes.firstOrNull()?.toString() != STRING_DESCRIPTOR ||
        consumerReference.returnType != STRING_DESCRIPTOR ||
        consumer.registersUsed.firstOrNull() != selectedRegister
    ) return null

    return InlinedUrlPickerSite(
        gateIndex = gateIndex,
        selectedRegister = selectedRegister,
        expandedRegister = expandedRegister,
        continuation = consumer,
    )
}

private fun Instruction.isUriParseCallWith(register: Int): Boolean {
    val reference = getReference<MethodReference>() ?: return false
    return opcode == Opcode.INVOKE_STATIC &&
        reference.definingClass == URI &&
        reference.name == "parse" &&
        reference.parameterTypes.map(CharSequence::toString) == listOf(STRING_DESCRIPTOR) &&
        reference.returnType == URI &&
        registersUsed == listOf(register)
}

private fun List<Instruction>.branchTargetIndex(index: Int): Int? {
    val (offsets, indexByOffset) = instructionOffsets()
    val branch = getOrNull(index) as? OffsetInstruction ?: return null
    return indexByOffset[offsets[index] + branch.codeOffset]
}

private fun List<Instruction>.basicBlockStart(index: Int): Int {
    val (offsets, indexByOffset) = instructionOffsets()
    fun branchTarget(instructionIndex: Int): Int? {
        val branch = this[instructionIndex] as? OffsetInstruction ?: return null
        return indexByOffset[offsets[instructionIndex] + branch.codeOffset]
    }

    val leaders = mutableSetOf(0)
    forEachIndexed { instructionIndex, instruction ->
        branchTarget(instructionIndex)?.let(leaders::add)
        if (instruction.opcode in BASIC_BLOCK_BOUNDARY_OPCODES) {
            (instructionIndex + 1).takeIf { it < size }?.let(leaders::add)
        }
    }

    var start = index
    while (start > 0 && start !in leaders) start--
    return start
}

private fun List<Instruction>.instructionOffsets(): Pair<IntArray, Map<Int, Int>> {
    val offsets = IntArray(size)
    val indexByOffset = mutableMapOf<Int, Int>()
    var codeOffset = 0
    forEachIndexed { instructionIndex, instruction ->
        offsets[instructionIndex] = codeOffset
        indexByOffset[codeOffset] = instructionIndex
        codeOffset += instruction.codeUnits
    }
    return offsets to indexByOffset
}

private val BASIC_BLOCK_BOUNDARY_OPCODES =
    setOf(
        Opcode.GOTO,
        Opcode.GOTO_16,
        Opcode.GOTO_32,
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
        Opcode.PACKED_SWITCH,
        Opcode.SPARSE_SWITCH,
        Opcode.RETURN_VOID,
        Opcode.RETURN,
        Opcode.RETURN_WIDE,
        Opcode.RETURN_OBJECT,
        Opcode.THROW,
    )

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
    setting: ToggleSettingDefinition,
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

    val originalField = fieldRead.getReference<FieldReference>()
        ?: throw PatchException(
            "URL-entity field read at instruction $fieldReadIndex has no field reference",
        )
    if (originalField == replacement) {
        throw PatchException(
            "URL-entity field read at instruction $fieldReadIndex already uses the replacement field",
        )
    }
    val continuation = method.instructions.getOrNull(fieldReadIndex + 1)
        ?: throw PatchException(
            "URL-entity field read at instruction $fieldReadIndex has no continuation",
        )
    // const-string, invoke-static/range, move-result, and if-eqz all support byte-addressable
    // registers, which avoids rejecting valid high-register Compose methods under register
    // pressure. The field access itself keeps its original four-bit operands.
    val settingRegister =
        method.getFreeRegisterProvider(
            fieldReadIndex,
            1,
            fieldRead.registerA,
            fieldRead.registerB,
        ).getFreeRegister()
    val originalLabel = "piko_canonical_url_original_$fieldReadIndex"
    val continuationLabel = "piko_canonical_url_continue_$fieldReadIndex"
    // Replace the entry instruction itself so existing branch labels land on the setting check.
    method.replaceInstruction(
        fieldReadIndex,
        "const-string v$settingRegister, \"${setting.id}\"",
    )
    method.addInstructionsWithLabels(
        fieldReadIndex + 1,
        """
            invoke-static/range {v$settingRegister .. v$settingRegister}, $SETTINGS_REGISTRY_DESCRIPTOR->getBooleanOrDefault(Ljava/lang/String;)Z
            move-result v$settingRegister
            if-eqz v$settingRegister, :$originalLabel
            iget-object v${fieldRead.registerA}, v${fieldRead.registerB}, $replacement
            goto :$continuationLabel
            :$originalLabel
            iget-object v${fieldRead.registerA}, v${fieldRead.registerB}, $originalField
        """.trimIndent(),
        ExternalLabel(continuationLabel, continuation),
    )
}

private fun preferExpandedUrlInUrlPicker(
    shape: UrlPickerShape,
    setting: ToggleSettingDefinition,
) {
    when (shape) {
        is UrlPickerShape.Extracted -> patchExtractedUrlPicker(shape.match, setting)
        is UrlPickerShape.Inlined -> {
            // Each insertion changes instruction indexes, so retain the later-to-earlier order.
            shape.sites.sortedByDescending(InlinedUrlPickerSite::gateIndex).forEach { site ->
                patchInlinedUrlPickerSite(shape.match.method, site, setting)
            }
        }
    }
}

private fun patchExtractedUrlPicker(
    match: Match,
    setting: ToggleSettingDefinition,
) {
    val method = match.method
    val baseRegister = method.p0Register
    val expandedRegister = baseRegister + 1

    // Use the raw expanded URL whenever it is available, preserving the established behavior.
    val firstInstruction = method.instructions.first()
    val settingRead =
        setting.injectRead(
            method = method,
            index = 0,
            excludedRegisters = listOf(baseRegister, expandedRegister),
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    method.addInstructionsWithLabels(
        settingRead.nextIndex,
        """
        if-eqz v${settingRead.register}, :piko_canonical_url_picker_original
        if-eqz v$expandedRegister, :piko_canonical_url_picker_original
        return-object v$expandedRegister
        """.trimIndent(),
        ExternalLabel("piko_canonical_url_picker_original", firstInstruction),
    )
}

private fun patchInlinedUrlPickerSite(
    method: MutableMethod,
    site: InlinedUrlPickerSite,
    setting: ToggleSettingDefinition,
) {
    if (site.selectedRegister !in 0..15 || site.expandedRegister !in 0..15) {
        throw PatchException(
            "Inlined canonical URL picker requires four-bit registers: " +
                "selected=v${site.selectedRegister}, expanded=v${site.expandedRegister}",
        )
    }
    val originalGate = method.instructions.getOrNull(site.gateIndex)
        ?: throw PatchException("Inlined URL picker has no expanded-URL gate")
    val settingRead =
        setting.injectRead(
            method = method,
            index = site.gateIndex,
            excludedRegisters = listOf(site.selectedRegister, site.expandedRegister),
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    val originalLabel = "piko_canonical_inline_original_${site.gateIndex}"
    val continuationLabel = "piko_canonical_inline_continue_${site.gateIndex}"
    method.addInstructionsWithLabels(
        settingRead.nextIndex,
        """
        if-eqz v${settingRead.register}, :$originalLabel
        if-eqz v${site.expandedRegister}, :$originalLabel
        move-object v${site.selectedRegister}, v${site.expandedRegister}
        goto :$continuationLabel
        """.trimIndent(),
        ExternalLabel(originalLabel, originalGate),
        ExternalLabel(continuationLabel, site.continuation),
    )
}

context(_: BytecodePatchContext)
private fun patchProfileLinkValues(
    urlEntityFields: UrlEntityFields,
    setting: ToggleSettingDefinition,
) {
    val match =
        Fingerprint(
            // The profile-link builder moved from `telemetry` to `lifecycle` in
            // newer builds. Keep the semantic field-pair anchor and only scope
            // it to the stable image-loader package.
            definingClass = "Lcom/x/media/imageloader/",
            filters = listOf(
                fieldAccess(
                    opcode = Opcode.IGET_OBJECT,
                    reference = urlEntityFields.displayUrl,
                ),
                fieldAccess(
                    opcode = Opcode.IGET_OBJECT,
                    reference = urlEntityFields.url,
                ),
            ),
        ).requireSingleMatch("profile link values")

    // The injected branch widens each matched read; patch the later read first so the earlier
    // match index remains valid.
    replaceUrlEntityFieldRead(
        match,
        PROFILE_LINK_OPEN_URL_FIELD_FILTER_INDEX,
        urlEntityFields.expandedUrl,
        setting,
    )
    replaceUrlEntityFieldRead(
        match,
        PROFILE_LINK_DISPLAY_URL_FIELD_FILTER_INDEX,
        urlEntityFields.expandedUrl,
        setting,
    )
}

context(_: BytecodePatchContext)
private fun patchRichTextUrlDisplay(
    urlEntityFields: UrlEntityFields,
    setting: ToggleSettingDefinition,
) {
    val match =
        Fingerprint(
            definingClass = "Lcom/x/ui/common/text/",
            filters = listOf(
                instanceOf(urlEntityFields.type),
                fieldAccess(
                    opcode = Opcode.IGET_OBJECT,
                    reference = urlEntityFields.displayUrl,
                ),
            ),
            custom = { method, _ -> method.hasUrlEntityDisplayFlow(urlEntityFields) },
        ).requireSingleMatch("rich-text URL display")

    replaceUrlEntityFieldRead(
        match,
        RICH_TEXT_DISPLAY_URL_FIELD_FILTER_INDEX,
        urlEntityFields.expandedUrl,
        setting,
    )
}

/**
 * Proves that the display-url read belongs to the URL-entity branch, rather than merely sharing
 * a method with an unrelated URL field. Compose's text return type and the renderer's name are
 * release details, so the URL entity cast/read data flow is the semantic anchor.
 */
private fun Method.hasUrlEntityDisplayFlow(fields: UrlEntityFields): Boolean {
    val methodInstructions = implementation?.instructions?.toList() ?: return false
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

    val displayReads = methodInstructions.mapIndexedNotNull { index, instruction ->
        val registers = instruction as? TwoRegisterInstruction ?: return@mapIndexedNotNull null
        val field = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
        index.takeIf {
            instruction.opcode == Opcode.IGET_OBJECT &&
                field == fields.displayUrl &&
                field.type == STRING_DESCRIPTOR
        }?.let { it to registers }
    }
    if (displayReads.size != 1) return false

    val (displayReadIndex, displayRead) = displayReads.single()
    val receiverRegister = displayRead.registerB
    val castIndices = (0 until displayReadIndex).filter { index ->
        val instruction = methodInstructions[index]
        instruction.opcode == Opcode.CHECK_CAST &&
            (instruction as? OneRegisterInstruction)?.registerA == receiverRegister &&
            instruction.getReference<TypeReference>()?.type == fields.type
    }
    if (castIndices.size != 1) return false
    val castIndex = castIndices.single()
    val entityInputRegister =
        methodInstructions.getOrNull(castIndex - 1)
            ?.takeIf { it.opcode == Opcode.MOVE_OBJECT || it.opcode == Opcode.MOVE }
            ?.let { it as? TwoRegisterInstruction }
            ?.takeIf { it.registerA == receiverRegister }
            ?.registerB
            ?: receiverRegister

    val instanceChecks = (0 until castIndex).mapNotNull { index ->
        val instruction = methodInstructions[index]
        if (instruction.opcode != Opcode.INSTANCE_OF) return@mapNotNull null
        val instanceOf = instruction as? TwoRegisterInstruction ?: return@mapNotNull null
        if (instanceOf.registerB != entityInputRegister ||
            instruction.getReference<TypeReference>()?.type != fields.type
        ) {
            return@mapNotNull null
        }

        val branchIndex = index + 1
        val branch = methodInstructions.getOrNull(branchIndex)
            ?: return@mapNotNull null
        if (branch.opcode != Opcode.IF_EQZ && branch.opcode != Opcode.IF_NEZ) {
            return@mapNotNull null
        }
        val branchRegister = (branch as? OneRegisterInstruction)?.registerA
            ?: return@mapNotNull null
        if (branchRegister != instanceOf.registerA) return@mapNotNull null
        val targetIndex = branchTargetIndex(branchIndex) ?: return@mapNotNull null
        val castIsOnSelectedBranch =
            when (branch.opcode) {
                // False skips the URL-entity path; the fall-through path reaches the cast.
                Opcode.IF_EQZ -> targetIndex > castIndex
                // True selects the URL-entity path; the branch target must reach the cast.
                Opcode.IF_NEZ -> targetIndex <= castIndex
                else -> false
            }
        index.takeIf { castIsOnSelectedBranch }
    }
    return instanceChecks.size == 1
}

context(context: BytecodePatchContext)
private fun resolveCardUrlActionType(): String =
    CardUrlActionModelFingerprint.requireSingleMatch("card URL action model")
        .originalClassDef.type

private fun patchCardUrl(
    match: Match,
    insertionIndex: Int,
    postRegister: Int,
    urlRegister: Int,
    setting: ToggleSettingDefinition,
) {
    if (postRegister !in 0..15 || urlRegister !in 0..15) {
        throw PatchException(
            "Card canonical URL resolver requires four-bit registers: " +
                "post=v$postRegister, url=v$urlRegister",
        )
    }

    val method = match.method
    val continuation = method.instructions.getOrNull(insertionIndex)
        ?: throw PatchException("Card URL resolver has no continuation instruction")
    val settingRead =
        setting.injectRead(
            method = method,
            index = insertionIndex,
            excludedRegisters = listOf(postRegister, urlRegister),
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    method.addInstructionsWithLabels(
        settingRead.nextIndex,
        """
        if-eqz v${settingRead.register}, :piko_canonical_card_url_continue
        invoke-static {v$postRegister, v$urlRegister}, $CANONICAL_URL_RESOLVE_METHOD
        move-result-object v$urlRegister
        """.trimIndent(),
        ExternalLabel("piko_canonical_card_url_continue", continuation),
    )
}

context(context: BytecodePatchContext)
private fun patchCardNavigation(
    cardUrlActionType: String,
    contextualPostType: String,
    setting: ToggleSettingDefinition,
) {
    val match =
        Fingerprint(
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

    val getIdIndex = match.instructionMatches[2].index
    val postRegister = resolveContextualPostRegister(
        match.method,
        urlResultIndex + 1,
        getIdIndex,
        contextualPostType,
    )
    patchCardUrl(
        match,
        urlResultIndex + 1,
        postRegister,
        urlResult.registerA,
        setting,
    )
}

private fun resolveContextualPostRegister(
    method: Method,
    afterIndex: Int,
    getIdIndex: Int,
    contextualPostType: String,
): Int {
    val instructions = method.instructions.toList()
    val getId = instructions.getOrNull(getIdIndex)
        ?: throw PatchException("Card navigation getId call is missing")
    val getIdReceiver = requireExactlyOne(
        "card navigation getId receiver register",
        getId.registersUsed,
    )
    val candidates = (afterIndex until getIdIndex).mapNotNull { index ->
        val instruction = instructions[index]
        val read = instruction as? TwoRegisterInstruction ?: return@mapNotNull null
        val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
        read.registerB.takeIf {
            instruction.opcode == Opcode.IGET_OBJECT &&
                read.registerA == getIdReceiver &&
                field.definingClass == contextualPostType &&
                field.type.startsWith("L")
        }
    }.distinct()
    return requireExactlyOne("card navigation contextual-post source", candidates)
}

context(_: BytecodePatchContext)
private fun Fingerprint.requireSingleMatch(label: String): Match {
    val matches = scopedMatchAll().distinctBy { it.originalMethod.toString() }
    return requireExactlyOne(label, matches) { match -> match.originalMethod.toString() }
}
