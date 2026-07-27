package app.crimera.patches.xlite.misc.navbar

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.SettingReadRegisterConstraint
import app.crimera.patches.xlite.settings.choice
import app.crimera.patches.xlite.settings.injectRead
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteMultiChoice
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.NAV_BAR_FILTER_DESCRIPTOR
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
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/** Filters X-Lite navigation state before the landing component consumes it. */
private object XLiteTabDataFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters =
        listOf(
            methodCall(
                smali = "Lcom/x/navigation/MainLandingArgs\$TabType;->getEntries()Lkotlin/enums/EnumEntries;",
            ),
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                definingClass = "Lcom/x/navigation/MainLandingArgs\$TabType;",
                name = "COMMUNITIES",
                type = "Lcom/x/navigation/MainLandingArgs\$TabType;",
            ),
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                definingClass = "Lcom/x/navigation/MainLandingArgs\$TabType;",
                name = "SPACES",
                type = "Lcom/x/navigation/MainLandingArgs\$TabType;",
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
private const val F_INIT_PARAM_COUNT = 16
private const val PROFILE_USER_DESCRIPTOR = "Lcom/x/models/ProfileUser;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val MAP_DESCRIPTOR = "Ljava/util/Map;"

private fun MutableMethod.findStateInitIndex(anchorIndex: Int): Int {
    val candidates =
        instructions
            .drop(anchorIndex + 1)
            .mapNotNull { instruction ->
                if (instruction.opcode != Opcode.INVOKE_DIRECT_RANGE) return@mapNotNull null

                val range = instruction as? Instruction3rc ?: return@mapNotNull null
                if (range.registerCount != F_INIT_PARAM_COUNT + 1) return@mapNotNull null

                val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
                val parameters = reference.parameterTypes.map { it.toString() }
                if (reference.name != "<init>" ||
                    reference.returnType != "V" ||
                    parameters.size != F_INIT_PARAM_COUNT ||
                    parameters.getOrNull(0) != PROFILE_USER_DESCRIPTOR ||
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
        "Expected one stable X-Lite tabData State constructor, found ${candidates.size}: " +
            candidates.joinToString(),
    )
}

private fun validateFingerprintMatch(match: Match) {
    if (match.instructionMatches.size != FINGERPRINT_ANCHOR_COUNT) {
        throw PatchException(
            "X-Lite tabData fingerprint returned ${match.instructionMatches.size} anchors; " +
                "expected $FINGERPRINT_ANCHOR_COUNT",
        )
    }
}

@Suppress("unused")
val customizeXLiteNavBarPatch =
    bytecodePatch(
        name = "X-Lite: Customize navigation bar items",
        description = "Lets you hide selected items from the X-Lite bottom navigation bar.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        val hiddenTabs =
            xLiteMultiChoice(
                id = "xlite.content.hidden_nav_bar_items",
                category = Categories.CONTENT,
                strings = settingStrings("piko_xlite_nav_bar"),
                order = 300,
                defaultValue = emptySet(),
                rebootApp = true,
                options =
                    listOf(
                        choice("HOME", "piko_xlite_nav_bar_home"),
                        choice("EXPLORE", "piko_xlite_nav_bar_explore"),
                        choice("GROK", "piko_xlite_nav_bar_grok"),
                        choice("NOTIFICATIONS", "piko_xlite_nav_bar_notifications"),
                        choice("DM", "piko_xlite_nav_bar_dm"),
                        choice("COMMUNITIES", "piko_xlite_nav_bar_communities"),
                        choice("SPACES", "piko_xlite_nav_bar_spaces"),
                    ),
            )

        execute {
            val matches = XLiteTabDataFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite tabData builder, found ${matches.size}: " +
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
                        throw PatchException("X-Lite tabData Map.put fingerprint anchor is invalid")
                    }

                    val fInitIndex = findStateInitIndex(mapPutIndex)
                    val fInitInstruction =
                        instructions.getOrNull(fInitIndex) as? Instruction3rc
                            ?: throw PatchException(
                                "X-Lite tabData State constructor is not a range instruction",
                            )
                    if (fInitInstruction.opcode != Opcode.INVOKE_DIRECT_RANGE) {
                        throw PatchException("X-Lite tabData State constructor is not invoke-direct/range")
                    }
                    if (TAB_DATA_ARG_INDEX !in 0 until fInitInstruction.registerCount) {
                        throw PatchException(
                            "X-Lite tabData argument index $TAB_DATA_ARG_INDEX is outside the " +
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
                                "No safe low register available for X-Lite tabData filtering: " +
                                    exception.message,
                            )
                        }
                    if (workRegister !in 0..15) {
                        throw PatchException("X-Lite tabData work register is not 4-bit: v$workRegister")
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
                                "Failed to inject the X-Lite hidden-tab setting read: " +
                                    exception.message,
                            )
                        }
                    if (read.register !in 0..15) {
                        throw PatchException("X-Lite hidden-tab setting register is not 4-bit: v${read.register}")
                    }
                    if (read.nextIndex < 0 || read.nextIndex >= instructions.size) {
                        throw PatchException("X-Lite hidden-tab setting read returned an invalid insertion index")
                    }
                    if (instructions[read.nextIndex].opcode != Opcode.INVOKE_DIRECT_RANGE) {
                        throw PatchException(
                            "X-Lite hidden-tab setting read is not immediately before the State constructor",
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
