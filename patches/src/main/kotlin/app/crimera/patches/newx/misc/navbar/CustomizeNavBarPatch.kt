package app.crimera.patches.newx.misc.navbar

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXMultiChoice
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.NAV_BAR_FILTER_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Filters NewX navigation state before the landing component consumes it. */
private object NewXTabDataFingerprint : Fingerprint(
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

private const val FINGERPRINT_ANCHOR_COUNT = 4
private const val TAB_DATA_ARG_INDEX = 9
private const val STATE_TAB_DATA_PARAMETER_INDEX = 8
private val STATE_CONSTRUCTOR_PARAMETER_COUNTS = setOf(16, 18)
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val MAP_DESCRIPTOR = "Ljava/util/Map;"

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

    if (candidates.size == 1) return candidates.single()
    throw PatchException(
        "Expected one stable NewX tabData State constructor, found ${candidates.size}: " +
            candidates.joinToString(),
    )
}

private fun validateFingerprintMatch(match: Match) {
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
}

@Suppress("unused")
val customizeNewXNavBarPatch =
    bytecodePatch(
        name = "NewX: Customize navigation bar items",
        description = "Lets you hide selected items from the NewX bottom navigation bar.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val hiddenTabs =
            newXMultiChoice(
                id = "newx.content.hidden_nav_bar_items",
                category = Categories.NAVIGATION,
                strings = settingStrings("piko_newx_nav_bar"),
                order = 100,
                defaultValue = emptySet(),
                rebootApp = true,
                options =
                    listOf(
                        choice("HOME", "piko_newx_nav_bar_home"),
                        choice("EXPLORE", "piko_newx_nav_bar_explore"),
                        choice("GROK", "piko_newx_nav_bar_grok"),
                        choice("NOTIFICATIONS", "piko_newx_nav_bar_notifications"),
                        choice("DM", "piko_newx_nav_bar_dm"),
                        choice("COMMUNITIES", "piko_newx_nav_bar_communities"),
                        choice("SPACES", "piko_newx_nav_bar_spaces"),
                    ),
            )

        execute {
            val matches = NewXTabDataFingerprint.scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX tabData builder, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            matches.single().let { match ->
                validateFingerprintMatch(match)
                match.method.apply {
                    val mapPutIndex = match.instructionMatches.last().index
                    val mapPutInstruction = instructions.getOrNull(mapPutIndex)
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

                    val fInitIndex = findStateInitIndex(mapPutIndex)
                    val fInitInstruction =
                        instructions.getOrNull(fInitIndex) as? Instruction3rc
                            ?: throw PatchException(
                                "NewX tabData State constructor is not a range instruction",
                            )
                    if (fInitInstruction.opcode != Opcode.INVOKE_DIRECT_RANGE) {
                        throw PatchException("NewX tabData State constructor is not invoke-direct/range")
                    }
                    if (TAB_DATA_ARG_INDEX !in 0 until fInitInstruction.registerCount) {
                        throw PatchException(
                            "NewX tabData argument index $TAB_DATA_ARG_INDEX is outside the " +
                                "${fInitInstruction.registerCount}-register State constructor range",
                        )
                    }
                    val tabDataRegister = fInitInstruction.startRegister + TAB_DATA_ARG_INDEX

                    val workRegister =
                        try {
                            getFreeRegisterProvider(fInitIndex, 1, tabDataRegister)
                                .getFreeRegister4Bit()
                        } catch (exception: RuntimeException) {
                            throw PatchException(
                                "No safe low register available for NewX tabData filtering: " +
                                    exception.message,
                            )
                        }
                    if (workRegister !in 0..15) {
                        throw PatchException("NewX tabData work register is not 4-bit: v$workRegister")
                    }
                    val read =
                        try {
                            hiddenTabs.injectRead(
                                method = this,
                                index = fInitIndex,
                                excludedRegisters = listOf(tabDataRegister, workRegister),
                                registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                            )
                        } catch (exception: RuntimeException) {
                            throw PatchException(
                                "Failed to inject the NewX hidden-tab setting read: " +
                                    exception.message,
                            )
                        }
                    if (read.register !in 0..15) {
                        throw PatchException("NewX hidden-tab setting register is not 4-bit: v${read.register}")
                    }
                    if (read.nextIndex < 0 || read.nextIndex >= instructions.size) {
                        throw PatchException("NewX hidden-tab setting read returned an invalid insertion index")
                    }
                    if (instructions[read.nextIndex].opcode != Opcode.INVOKE_DIRECT_RANGE) {
                        throw PatchException(
                            "NewX hidden-tab setting read is not immediately before the State constructor",
                        )
                    }

                    addInstructions(
                        read.nextIndex,
                        """
                            move-object/from16 v$workRegister, v$tabDataRegister
                            invoke-static {v$workRegister, v${read.register}}, $NAV_BAR_FILTER_DESCRIPTOR->filter(Ljava/util/Map;Ljava/util/Set;)Ljava/util/Map;
                            move-result-object v$workRegister
                            move-object/16 v$tabDataRegister, v$workRegister
                        """.trimIndent(),
                    )
                }
            }
        }
    }
