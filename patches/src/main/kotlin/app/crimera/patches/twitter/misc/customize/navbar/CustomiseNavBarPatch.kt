/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.navbar

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.crimera.patches.twitter.utils.flagSettings
import app.crimera.patches.twitter.utils.is_11_88_or_greater
import app.crimera.patches.twitter.utils.versionCheckPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.checkCast
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val TAB_CUSTOMIZATION_CLASS_PREFIX = "Lcom/twitter/subscriptions/tabcustomization/model/"

private object CustomiseNavBarParentFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(
        "tabCustomizationPreferences"
    )
)

private object CustomiseNavBarFingerprint : Fingerprint(
    classFingerprint = CustomiseNavBarParentFingerprint,
    returnType = "Ljava/util/List;",
    filters = listOf(
        string("subscriptions_feature_1008"),
        opcode(
            opcode = Opcode.MOVE_RESULT,
            location = MatchAfterWithin(5)
        ),
        checkCast("Ljava/lang/Iterable;"),
        opcode(Opcode.RETURN_OBJECT)
    )
)

private object CustomiseNavBarSecondaryFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    filters = listOf(
        string("subscriptions_feature_1008"),
        opcode(
            opcode = Opcode.MOVE_RESULT,
            location = MatchAfterWithin(5)
        ),
        string("currentSelectedElements"),
        opcode(Opcode.RETURN_OBJECT)
    )
)

private object CustomiseNavBarSecondaryInitFingerprint : Fingerprint(
    classFingerprint = CustomiseNavBarSecondaryFingerprint,
    name = "<init>",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/util/ArrayList;"
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Ljava/util/Map;"
        )
    )
)

private object CustomiseNavBarSecondaryMapFingerprint : Fingerprint(
    classFingerprint = CustomiseNavBarSecondaryFingerprint,
    parameters = listOf(TAB_CUSTOMIZATION_CLASS_PREFIX),
    returnType = "V",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = TAB_CUSTOMIZATION_CLASS_PREFIX,
            type = TAB_CUSTOMIZATION_CLASS_PREFIX
        ),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = TAB_CUSTOMIZATION_CLASS_PREFIX,
            type = TAB_CUSTOMIZATION_CLASS_PREFIX,
            location = MatchAfterWithin(3)
        ),
        opcode(Opcode.THROW)
    )
)

@Suppress("unused")
val customiseNavBarPatch =
    bytecodePatch(
        name = "Customize Navigation Bar items",
    ) {
        compatibleWith(COMPATIBILITY_X)

        dependsOn(
            settingsPatch,
            versionCheckPatch
        )

        execute {

            CustomiseNavBarFingerprint.let {
                it.method.apply {
                    val listIndex = it.instructionMatches.last().index
                    val listRegister =
                        getInstruction<OneRegisterInstruction>(listIndex).registerA

                    addInstructions(
                        listIndex,
                        """
                            invoke-static { v$listRegister }, $CUSTOMISE_DESCRIPTOR;->navBar(Ljava/util/List;)Ljava/util/List;
                            move-result-object v$listRegister
                        """
                    )

                    val booleanIndex = it.instructionMatches[1].index
                    val booleanRegister =
                        getInstruction<OneRegisterInstruction>(booleanIndex).registerA

                    addInstruction(
                        booleanIndex + 1,
                        "const/4 v$booleanRegister, 0x1"
                    )
                }
            }

            if (is_11_88_or_greater) {
                val (listField, mapField) =
                    with(CustomiseNavBarSecondaryInitFingerprint.instructionMatches) {
                        Pair(
                            first().instruction.getReference<FieldReference>()!!,
                            last().instruction.getReference<FieldReference>()!!,
                        )
                    }

                val (navBarFieldName1, navBarFieldName2) =
                    with(CustomiseNavBarSecondaryMapFingerprint.instructionMatches) {
                        Pair(
                            first().instruction.getReference<FieldReference>()!!.name,
                            this[1].instruction.getReference<FieldReference>()!!.name,
                        )
                    }

                CustomiseNavBarSecondaryFingerprint.let {
                    it.method.apply {
                        val listIndex = it.instructionMatches.last().index
                        val listRegister = getInstruction<OneRegisterInstruction>(listIndex).registerA
                        val mapRegister = getFreeRegisterProvider(listIndex, 1).getFreeRegister()
                        val freeRegisterProvider = getFreeRegisterProvider(listIndex, 2, listRegister, mapRegister)
                        val freeRegister1 = freeRegisterProvider.getFreeRegister()
                        val freeRegister2 = freeRegisterProvider.getFreeRegister()

                        addInstructions(
                            listIndex,
                            """
                                const-string v$freeRegister1, "$navBarFieldName1"
                                const-string v$freeRegister2, "$navBarFieldName2"
                                invoke-static { v$listRegister, v$freeRegister1, v$freeRegister2 }, $CUSTOMISE_DESCRIPTOR;->navBar(Ljava/util/ArrayList;Ljava/lang/String;Ljava/lang/String;)Ljava/util/ArrayList;
                                move-result-object v$listRegister
                                iput-object v$listRegister, p0, $listField
                                iget-object v$mapRegister, p0, $mapField
                                invoke-static { v$mapRegister }, $CUSTOMISE_DESCRIPTOR;->navBar(Ljava/util/Map;)Ljava/util/Map;
                                move-result-object v$mapRegister
                                iput-object v$mapRegister, p0, $mapField                                
                            """
                        )

                        val booleanIndex = it.instructionMatches[1].index
                        val booleanRegister =
                            getInstruction<OneRegisterInstruction>(booleanIndex).registerA

                        addInstruction(
                            booleanIndex + 1,
                            "const/4 v$booleanRegister, 0x1"
                        )
                    }
                }
            }

            enableSettings("navBarCustomisation")
            flagSettings("navbarFix")
        }
    }
