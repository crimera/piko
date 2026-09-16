package app.crimera.patches.newx.misc.navbar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.crimera.patches.newx.utils.destinationRegisterOrNull
import app.crimera.patches.newx.utils.requireExactlyOne
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/** Resolved NewX navigation tab data shared by all navigation bar patches. */
internal data class NewXNavBarTabData(
    val componentClass: String,
    val navigationType: String,
    val tabDataValueType: String,
)

/** Filters NewX navigation state before the landing component consumes it. */
internal object NewXTabDataFingerprint : Fingerprint(
    definingClass = "Lcom/x/main/",
    name = "<init>",
    returnType = "V",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                name = "getEntries",
                parameters = emptyList(),
                returnType = "Lkotlin/enums/EnumEntries;",
            ),
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "COMMUNITIES",
            ),
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "SPACES",
            ),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                definingClass = "Ljava/util/Map;",
                name = "put",
                parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
                returnType = "Ljava/lang/Object;",
            ),
        ),
)

internal const val FINGERPRINT_ANCHOR_COUNT = 4

internal fun validateNewXNavBarTabData(match: Match): NewXNavBarTabData {
    if (match.instructionMatches.size != FINGERPRINT_ANCHOR_COUNT) {
        throw PatchException(
            "NewX tabData fingerprint returned ${match.instructionMatches.size} anchors; " +
                "expected $FINGERPRINT_ANCHOR_COUNT",
        )
    }

    val entriesReference =
        match.instructionMatches[0].instruction.getReference<MethodReference>()
            ?: throw PatchException("NewX tabData getEntries anchor has no method reference")
    val communityReference =
        match.instructionMatches[1].instruction.getReference<FieldReference>()
            ?: throw PatchException("NewX tabData COMMUNITIES anchor has no field reference")
    val spacesReference =
        match.instructionMatches[2].instruction.getReference<FieldReference>()
            ?: throw PatchException("NewX tabData SPACES anchor has no field reference")
    val tabTypeDescriptor = entriesReference.definingClass.toString()
    if (entriesReference.name != "getEntries" ||
        entriesReference.parameterTypes.isNotEmpty() ||
        entriesReference.returnType.toString() != "Lkotlin/enums/EnumEntries;" ||
        communityReference.definingClass.toString() != tabTypeDescriptor ||
        communityReference.type.toString() != tabTypeDescriptor ||
        spacesReference.definingClass.toString() != tabTypeDescriptor ||
        spacesReference.type.toString() != tabTypeDescriptor ||
        communityReference.name != "COMMUNITIES" ||
        spacesReference.name != "SPACES"
    ) {
        throw PatchException("NewX tabData navigation enum anchors are inconsistent")
    }

    val putIndex = match.instructionMatches.last().index
    val putInstruction = match.method.instructions.getOrNull(putIndex)
    val put = putInstruction as? Instruction35c
        ?: throw PatchException("NewX tabData Map.put anchor is not an invoke-interface instruction")
    if (put.opcode != Opcode.INVOKE_INTERFACE) {
        throw PatchException("NewX tabData Map.put anchor is not an invoke-interface instruction")
    }
    val tabDataValueType = match.method.resolveTabDataValueType(putIndex, put.registerE)
        ?: throw PatchException("NewX tabData Map.put value type could not be resolved")

    return NewXNavBarTabData(
        componentClass = match.method.definingClass.toString(),
        navigationType = tabTypeDescriptor,
        tabDataValueType = tabDataValueType,
    )
}

/** Follows the put value register back to the constructor of the map value model. */
private fun MutableMethod.resolveTabDataValueType(
    putIndex: Int,
    valueRegister: Int,
): String? {
    for (index in putIndex - 1 downTo 0) {
        val instruction: Instruction = instructions[index]
        if (instruction.opcode == Opcode.NEW_INSTANCE &&
            (instruction as? OneRegisterInstruction)?.registerA == valueRegister
        ) {
            return instruction.getReference<TypeReference>()?.toString()
        }
        if (instruction.destinationRegisterOrNull() == valueRegister) return null
    }
    return null
}

internal data class TabDataFilterTarget(
    val method: MutableMethod,
    val insertionIndex: Int,
    val tabDataRegister: Int,
)

private const val TAB_DATA_ARG_INDEX = 9
private const val STATE_TAB_DATA_PARAMETER_INDEX = 8
private val STATE_CONSTRUCTOR_PARAMETER_COUNTS = setOf(16, 17, 18)
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val MAP_DESCRIPTOR = "Ljava/util/Map;"

/** Resolves the state constructor argument that receives the tab map. */
internal fun resolveNewXNavBarFilterTarget(match: Match): TabDataFilterTarget {
    val mapPutIndex = match.instructionMatches.last().index
    val mapPutInstruction = match.method.instructions.getOrNull(mapPutIndex)
    val mapPutReference = mapPutInstruction?.getReference<MethodReference>()
    if (mapPutInstruction?.opcode != Opcode.INVOKE_INTERFACE ||
        mapPutReference?.definingClass != MAP_DESCRIPTOR ||
        mapPutReference.name != "put" ||
        mapPutReference.parameterTypes.map { it.toString() } !=
            listOf("Ljava/lang/Object;", "Ljava/lang/Object;") ||
        mapPutReference.returnType != "Ljava/lang/Object;"
    ) {
        throw PatchException("NewX tabData Map.put fingerprint anchor is invalid")
    }

    val stateInitIndex = match.method.findStateInitIndex(mapPutIndex)
    val stateInitInstruction =
        match.method.instructions.getOrNull(stateInitIndex) as? Instruction3rc
            ?: throw PatchException("NewX tabData State constructor is not a range instruction")
    if (stateInitInstruction.opcode != Opcode.INVOKE_DIRECT_RANGE) {
        throw PatchException("NewX tabData State constructor is not invoke-direct/range")
    }
    if (TAB_DATA_ARG_INDEX !in 0 until stateInitInstruction.registerCount) {
        throw PatchException(
            "NewX tabData argument index $TAB_DATA_ARG_INDEX is outside the " +
                "${stateInitInstruction.registerCount}-register State constructor range",
        )
    }
    return TabDataFilterTarget(
        method = match.method,
        insertionIndex = stateInitIndex,
        tabDataRegister = stateInitInstruction.startRegister + TAB_DATA_ARG_INDEX,
    )
}

private fun MutableMethod.findStateInitIndex(anchorIndex: Int): Int {
    val candidates =
        instructions
            .drop(anchorIndex + 1)
            .mapNotNull { instruction ->
                if (instruction.opcode != Opcode.INVOKE_DIRECT_RANGE) return@mapNotNull null

                val range = instruction as? Instruction3rc ?: return@mapNotNull null
                val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
                val parameters = reference.parameterTypes.map { it.toString() }
                if (reference.name != "<init>" ||
                    reference.returnType != "V" ||
                    parameters.size !in STATE_CONSTRUCTOR_PARAMETER_COUNTS ||
                    range.registerCount != parameters.size + 1 ||
                    parameters.getOrNull(0)?.startsWith("L") != true ||
                    parameters.getOrNull(6) != LIST_DESCRIPTOR ||
                    parameters.getOrNull(7) != MAP_DESCRIPTOR ||
                    parameters.getOrNull(STATE_TAB_DATA_PARAMETER_INDEX) != MAP_DESCRIPTOR
                ) {
                    return@mapNotNull null
                }

                instruction.location.index
            }

    return requireExactlyOne("stable NewX tabData State constructor", candidates)
}
