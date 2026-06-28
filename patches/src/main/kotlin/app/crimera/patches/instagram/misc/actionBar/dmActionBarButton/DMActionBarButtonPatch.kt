/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.dmActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val GHOST_MODE_QUICK_TOGGLE_DESCRIPTOR = "$ACTIONBAR_DESCRIPTOR/GhostModeQuickToggle;"
private const val DIRECT_INBOX_MODEL_LEADING_CLASS_NAME = "directInboxLeadingClassName"
private const val DIRECT_INBOX_MODEL_TITLE_CLASS_NAME = "directInboxTitleClassName"
private const val DIRECT_INBOX_MODEL_ACTION_CLASS_NAME = "directInboxActionClassName"
private const val DIRECT_INBOX_MODEL_PLACEMENT_CLASS_NAME = "directInboxPlacementClassName"
private const val DIRECT_INBOX_MODEL_LEADING_FIELD_NAME = "directInboxModelLeadingFieldName"
private const val DIRECT_INBOX_MODEL_TITLE_FIELD_NAME = "directInboxModelTitleFieldName"
private const val DIRECT_INBOX_MODEL_ACTIONS_FIELD_NAME = "directInboxModelActionsFieldName"
private const val DIRECT_INBOX_ACTION_PLACEMENT_FIELD_NAME = "directInboxActionPlacementFieldName"
private const val ENUM_DESCRIPTOR = "Ljava/lang/Enum;"
private const val INTEGER_DESCRIPTOR = "Ljava/lang/Integer;"
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"

object DMActionBarBuilderFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(resourceLiteral(ResourceType.LAYOUT, "layout_direct_thread_header")),
)

private object DirectInboxActionBarModelFingerprint : Fingerprint(
    custom = { methodDef, classDef ->
        methodDef.parameters.size == 2 &&
            methodDef.parameters[1].type == classDef.type &&
            methodDef.returnType.startsWith("L") &&
            methodDef.implementation?.instructions?.any { instruction ->
                val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                reference?.definingClass == methodDef.returnType &&
                    reference.name == "<init>" &&
                    reference.parameterTypes.size == 3 &&
                    reference.parameterTypes[2] == LIST_DESCRIPTOR
            } == true
    },
)

private object AddDirectInboxActionModelExtensionFingerprint : Fingerprint(
    definingClass = GHOST_MODE_QUICK_TOGGLE_DESCRIPTOR,
    name = "addDirectInboxActionModel",
)

private object FindDirectInboxInsertIndexExtensionFingerprint : Fingerprint(
    definingClass = GHOST_MODE_QUICK_TOGGLE_DESCRIPTOR,
    name = "findDirectInboxInsertIndex",
)

val dmActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on DM action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(resourceMappingPatch)

        execute {

            DMActionBarBuilderFingerprint.let {
                it.method
                    .apply {
                        val viewGroupInstruction = getInstruction(indexOfFirstInstruction(Opcode.CHECK_CAST))
                        val viewGroupRegister = viewGroupInstruction.registersUsed[0]

                        val layoutIndex = it.instructionMatches.first().index

                        val fistMoveResultObjectAfterLayoutIndex = indexOfFirstInstruction(layoutIndex, Opcode.MOVE_RESULT_OBJECT)

                        addInstruction(
                            fistMoveResultObjectAfterLayoutIndex + 1,
                            """
                            invoke-static {v$viewGroupRegister}, $ACTIONBAR_DESCRIPTOR/DMActionBar;->addActionBarButton(Landroid/view/ViewGroup;)V
                            """.trimIndent(),
                        )
                    }
            }

            DirectInboxActionBarModelFingerprint.method.apply {
                val actionBarModelType = returnType
                val actionBarModelClass = mutableClassDefBy(actionBarModelType)
                val actionBarModelConstructor = instructions
                    .mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
                    .first {
                        it.definingClass == actionBarModelType &&
                            it.name == "<init>" &&
                            it.parameterTypes.size == 3 &&
                            it.parameterTypes[2] == LIST_DESCRIPTOR
                    }
                val leadingType = actionBarModelConstructor.parameterTypes[0].toString()
                val titleType = actionBarModelConstructor.parameterTypes[1].toString()
                val actionType = instructions
                    .mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
                    .filter {
                        it.definingClass == definingClass &&
                            it.name != "<init>" &&
                            it.returnType.toString().startsWith("L")
                    }
                    .map { it.returnType.toString() }
                    .distinct()
                    .first { isDirectInboxActionClass(it) }
                val placementType = mutableClassDefBy(actionType).fields
                    .map { it.type }
                    .single { isEnumClass(it) }
                val modelLeadingFieldName = actionBarModelClass.fields.single { it.type == leadingType }.name
                val modelTitleFieldName = actionBarModelClass.fields.single { it.type == titleType }.name
                val modelActionsFieldName = actionBarModelClass.fields.single { it.type == LIST_DESCRIPTOR }.name
                val actionPlacementFieldName = mutableClassDefBy(actionType).fields.single { it.type == placementType }.name

                AddDirectInboxActionModelExtensionFingerprint.replaceString(
                    DIRECT_INBOX_MODEL_LEADING_CLASS_NAME,
                    classNameToExtension(leadingType),
                )
                AddDirectInboxActionModelExtensionFingerprint.replaceString(
                    DIRECT_INBOX_MODEL_TITLE_CLASS_NAME,
                    classNameToExtension(titleType),
                )
                AddDirectInboxActionModelExtensionFingerprint.replaceString(
                    DIRECT_INBOX_MODEL_ACTION_CLASS_NAME,
                    classNameToExtension(actionType),
                )
                AddDirectInboxActionModelExtensionFingerprint.replaceString(
                    DIRECT_INBOX_MODEL_PLACEMENT_CLASS_NAME,
                    classNameToExtension(placementType),
                )
                AddDirectInboxActionModelExtensionFingerprint.replaceString(
                    DIRECT_INBOX_MODEL_LEADING_FIELD_NAME,
                    modelLeadingFieldName,
                )
                AddDirectInboxActionModelExtensionFingerprint.replaceString(
                    DIRECT_INBOX_MODEL_TITLE_FIELD_NAME,
                    modelTitleFieldName,
                )
                AddDirectInboxActionModelExtensionFingerprint.replaceString(
                    DIRECT_INBOX_MODEL_ACTIONS_FIELD_NAME,
                    modelActionsFieldName,
                )
                FindDirectInboxInsertIndexExtensionFingerprint.replaceString(
                    DIRECT_INBOX_ACTION_PLACEMENT_FIELD_NAME,
                    actionPlacementFieldName,
                )

                val returnObjectInstructions = implementation!!.instructions
                    .withIndex()
                    .filter { it.value.opcode == Opcode.RETURN_OBJECT }

                check(returnObjectInstructions.isNotEmpty()) {
                    "Could not find return-object instructions in direct inbox action bar model method"
                }

                returnObjectInstructions
                    .asReversed()
                    .forEach { (index, instruction) ->
                        val returnRegister = (instruction as OneRegisterInstruction).registerA
                        addInstructions(
                            index,
                            """
                            invoke-static {v$returnRegister}, $ACTIONBAR_DESCRIPTOR/GhostModeQuickToggle;->addDirectInboxActionModel(Ljava/lang/Object;)Ljava/lang/Object;
                            move-result-object v$returnRegister
                            check-cast v$returnRegister, $actionBarModelType
                            """.trimIndent(),
                        )
                    }
                enableSettings("directInboxGhostModeQuickToggle")
            }
        }
    }

context(patchContext: BytecodePatchContext)
private fun isDirectInboxActionClass(type: String): Boolean = runCatching {
    patchContext.mutableClassDefBy(type).methods.any { method ->
        method.name == "<init>" &&
            method.parameters.size == 6 &&
            isEnumClass(method.parameters[0].type) &&
            method.parameters[1].type == INTEGER_DESCRIPTOR &&
            method.parameters[2].type == FUNCTION0_DESCRIPTOR &&
            method.parameters[3].type == FUNCTION1_DESCRIPTOR &&
            method.parameters[4].type == FUNCTION1_DESCRIPTOR &&
            method.parameters[5].type == "I"
    }
}.getOrDefault(false)

context(patchContext: BytecodePatchContext)
private fun isEnumClass(type: String): Boolean = runCatching {
    patchContext.mutableClassDefBy(type).superclass == ENUM_DESCRIPTOR
}.getOrDefault(false)

context(patchContext: BytecodePatchContext)
private fun Fingerprint.replaceString(
    placeholder: String,
    value: String,
) {
    var replaced = false
    method.instructions
        .withIndex()
        .filter { it.value.opcode == Opcode.CONST_STRING }
        .forEach { (index, instruction) ->
            val constStringInstruction = instruction as BuilderInstruction21c
            if (constStringInstruction.reference.toString() == placeholder) {
                method.replaceInstruction(index, "const-string v${constStringInstruction.registerA}, \"$value\"")
                replaced = true
            }
        }

    check(replaced) {
        "Could not replace direct inbox action bar placeholder: $placeholder"
    }
}
