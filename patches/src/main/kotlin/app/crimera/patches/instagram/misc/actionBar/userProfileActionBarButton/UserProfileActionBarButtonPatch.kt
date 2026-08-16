/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.userProfileActionBarButton

import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.USER_DETAIL_VIEW_MODEL_CLASS
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.shared.declaredParameterRegister
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableField
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val ACTIVITY_CLASS = "Landroid/app/Activity;"
private const val IG_LINEAR_LAYOUT_CLASS = "Lcom/instagram/common/ui/base/IgLinearLayout;"

internal fun findProfileActionBarBuilderMethod(
    candidates: List<IndexedValue<MethodReference>>,
    expectedParameterTypes: List<String>,
    methodsInDefiningClass: (String) -> Iterable<MutableMethod>,
): MutableMethod {
    val matchingCandidates =
        candidates.filter { candidate ->
            candidate.value.parameterTypes == expectedParameterTypes &&
                candidate.value.returnType == "V"
        }
    val candidate =
        matchingCandidates.singleOrNull()
            ?: throw PatchException(
                "Expected exactly one profile action bar builder invocation, found ${matchingCandidates.size}",
            )
    val reference = candidate.value
    val matchingDefinitions =
        methodsInDefiningClass(reference.definingClass).filter { method ->
            method.definingClass == reference.definingClass &&
                method.name == reference.name &&
                method.parameterTypes == expectedParameterTypes &&
                method.returnType == "V" &&
                AccessFlags.STATIC.isSet(method.accessFlags)
        }

    val definition =
        matchingDefinitions.singleOrNull()
            ?: throw PatchException(
                "Expected exactly one matching method in the profile action bar builder class, " +
                    "found ${matchingDefinitions.count()}",
            )

    return definition
}

private fun MethodReference.isRemoveAllViews(): Boolean =
    definingClass == "Landroid/view/ViewGroup;" &&
        name == "removeAllViews" &&
        parameterTypes.isEmpty() &&
        returnType == "V"

private fun MethodReference.isListIterator(): Boolean =
    definingClass == "Ljava/util/List;" &&
        name == "iterator" &&
        parameterTypes.isEmpty() &&
        returnType == "Ljava/util/Iterator;"

private fun registerComesFromParameter(
    method: MutableMethod,
    instructionIndex: Int,
    register: Int,
    parameterRegister: Int,
): Boolean {
    var currentRegister = register
    if (currentRegister == parameterRegister) return true

    for (index in instructionIndex - 1 downTo 0) {
        val instruction = method.instructions[index]
        val registerInstruction = instruction as? OneRegisterInstruction ?: continue
        if (!instruction.opcode.setsRegister() || registerInstruction.registerA != currentRegister) continue
        if (
            instruction.opcode !in
            setOf(
                Opcode.MOVE_OBJECT,
                Opcode.MOVE_OBJECT_FROM16,
                Opcode.MOVE_OBJECT_16,
            )
        ) {
            return false
        }
        val moveInstruction = instruction as? TwoRegisterInstruction ?: return false
        currentRegister = moveInstruction.registerB
        if (currentRegister == parameterRegister) return true
    }

    return false
}

private fun findProfileActionBarInjectionIndex(
    method: MutableMethod,
    layoutParameterRegisters: List<Int>,
    listParameterRegister: Int,
): Int {
    if (layoutParameterRegisters.size != 2) {
        throw PatchException(
            "Expected two profile action bar layout parameter registers, " +
                "found ${layoutParameterRegisters.size}",
        )
    }
    val removeAllViewsCalls =
        method.instructions.mapIndexedNotNull { index, instruction ->
            val reference =
                (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@mapIndexedNotNull null
            if (!reference.isRemoveAllViews()) return@mapIndexedNotNull null
            val registers = instruction.registersUsed
            if (registers.size != 1) {
                throw PatchException("removeAllViews invocation has an unexpected register count")
            }
            IndexedValue(index, registers.single())
        }
    if (
        removeAllViewsCalls.size != 2 ||
        removeAllViewsCalls[1].index != removeAllViewsCalls[0].index + 1
    ) {
        throw PatchException(
            "Expected exactly two consecutive removeAllViews invocations, " +
                "found ${removeAllViewsCalls.size}",
        )
    }
    removeAllViewsCalls.zip(layoutParameterRegisters).forEach { (call, parameter) ->
        if (!registerComesFromParameter(method, call.index, call.value, parameter)) {
            throw PatchException("removeAllViews invocation does not use its layout parameter")
        }
    }

    val iteratorIndex = removeAllViewsCalls.last().index + 1
    val iteratorInstruction = method.instructions.getOrNull(iteratorIndex)
        ?: throw PatchException("Profile action bar builder has no instruction after removeAllViews")
    val iteratorReference =
        (iteratorInstruction as? ReferenceInstruction)?.reference as? MethodReference
            ?: throw PatchException("Instruction after removeAllViews is not a method invocation")
    val iteratorRegisters = iteratorInstruction.registersUsed
    if (
        !iteratorReference.isListIterator() ||
        iteratorRegisters.size != 1 ||
        !registerComesFromParameter(
            method,
            iteratorIndex,
            iteratorRegisters.single(),
            listParameterRegister,
        )
    ) {
        throw PatchException("Expected List.iterator from the list parameter after removeAllViews")
    }

    return iteratorIndex
}

internal object ProfileActionBarRelatedFingerprint : Fingerprint(
    strings = listOf("notifications_entry_point_impression", "impression_cast_to_tv"),
    returnType = "V",
)

internal object ProfileHeaderRelatedFingerprint : Fingerprint(
    strings = listOf("profile_user_id", "click_point", "user_profile_header"),
    returnType = "V",
    custom = { methodDef, _ ->
        methodDef.parameters.size == 3
    },
)

internal object ProfileActionBarFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/profile/actionbar/ProfileActionBar;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

val userProfileActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on user profile action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(decoderEntity)

        execute {
            val profileHeaderFieldInActionBarRelatedClass: MutableField

            ProfileActionBarRelatedFingerprint.apply {
                val matchingFields = classDef.fields.filter { it.type == ProfileHeaderRelatedFingerprint.classDef.type }
                profileHeaderFieldInActionBarRelatedClass =
                    matchingFields.singleOrNull()
                        ?: throw PatchException(
                            "Expected exactly one profile header field in the action bar related class, found ${matchingFields.size}",
                        )
            }

            val userDetailViewModelFields =
                ProfileHeaderRelatedFingerprint.classDef.fields.filter {
                    it.type == USER_DETAIL_VIEW_MODEL_CLASS
                }
            val userDetailViewModelFieldInProfileHeaderRelatedClass: MutableField =
                userDetailViewModelFields.singleOrNull()
                    ?: throw PatchException(
                        "Expected exactly one user detail view model field in the profile header class, found ${userDetailViewModelFields.size}",
                    )

            val userDetailsClassFields = classDefBy { it.type == USER_DETAIL_VIEW_MODEL_CLASS }.fields

            val userDataFields = userDetailsClassFields.filter { it.type == USER_MODEL_CLASS_NAME }
            val userDataFieldInUserDetailClass =
                userDataFields.singleOrNull()
                    ?: throw PatchException(
                        "Expected exactly one user model field in the user detail view model, found ${userDataFields.size}",
                    )

            val profileActionBarBuilderMethod =
                ProfileActionBarFingerprint.method.run {
                    val expectedBuilderParameterTypes =
                        listOf(
                            "Landroid/app/Activity;",
                            "Landroid/content/Context;",
                            USER_SESSION_CLASS,
                            IG_LINEAR_LAYOUT_CLASS,
                            IG_LINEAR_LAYOUT_CLASS,
                            ProfileActionBarRelatedFingerprint.classDef.type,
                            "Ljava/util/List;",
                            "Lkotlin/jvm/functions/Function1;",
                        )
                    findProfileActionBarBuilderMethod(
                        candidates =
                            instructions.mapIndexedNotNull { index, instruction ->
                                if (instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
                                    null
                                } else {
                                    instruction.getReference<MethodReference>()?.let { IndexedValue(index, it) }
                                }
                            },
                        expectedParameterTypes = expectedBuilderParameterTypes,
                        methodsInDefiningClass = { definingClass ->
                            mutableClassDefBy(definingClass).methods
                        },
                    )
                }

            profileActionBarBuilderMethod.apply {
                fun uniqueParameterIndex(type: String): Int {
                    val matches = parameterTypes.withIndex().filter { it.value == type }
                    return matches.singleOrNull()?.index
                        ?: throw PatchException(
                            "Expected exactly one $type parameter in the profile action bar builder, " +
                                "found ${matches.size}",
                        )
                }

                val layoutParameterIndices =
                    parameterTypes.withIndex().filter {
                        it.value == IG_LINEAR_LAYOUT_CLASS
                    }.map { it.index }
                if (layoutParameterIndices.size != 2) {
                    throw PatchException(
                        "Expected two profile action bar layout parameters, found ${layoutParameterIndices.size}",
                    )
                }
                val activityRegister = declaredParameterRegister(this, uniqueParameterIndex(ACTIVITY_CLASS))
                val userSessionRegister = declaredParameterRegister(this, uniqueParameterIndex(USER_SESSION_CLASS))
                val layoutRegisters = layoutParameterIndices.map { declaredParameterRegister(this, it) }
                val actionBarRelatedRegister =
                    declaredParameterRegister(
                        this,
                        uniqueParameterIndex(ProfileActionBarRelatedFingerprint.classDef.type),
                    )
                val listRegister = declaredParameterRegister(this, uniqueParameterIndex("Ljava/util/List;"))
                val layoutRegister = layoutRegisters.last()
                val injectionIndex =
                    findProfileActionBarInjectionIndex(
                        method = this,
                        layoutParameterRegisters = layoutRegisters,
                        listParameterRegister = listRegister,
                    )
                val resumeInstruction = getInstruction(injectionIndex)
                val freeRegisterProvider =
                    getFreeRegisterProvider(
                        index = injectionIndex,
                        numberOfFreeRegistersNeeded = 4,
                        activityRegister,
                        layoutRegister,
                        userSessionRegister,
                        actionBarRelatedRegister,
                    )
                val userObjectRegister = freeRegisterProvider.getFreeRegister()
                val userSessionCopyRegister = freeRegisterProvider.getFreeRegister()
                val layoutCopyRegister = freeRegisterProvider.getFreeRegister()
                val activityCopyRegister = freeRegisterProvider.getFreeRegister()
                val code =
                    """
                    move-object/from16 v$userObjectRegister, v$actionBarRelatedRegister
                    if-eqz v$userObjectRegister, :piko
                    iget-object v$userObjectRegister, v$userObjectRegister, $profileHeaderFieldInActionBarRelatedClass

                    if-eqz v$userObjectRegister, :piko
                    iget-object v$userObjectRegister, v$userObjectRegister, $userDetailViewModelFieldInProfileHeaderRelatedClass

                    if-eqz v$userObjectRegister, :piko
                    iget-object v$userObjectRegister, v$userObjectRegister, $userDataFieldInUserDetailClass
                    move-object/from16 v$userSessionCopyRegister, v$userSessionRegister
                    move-object/from16 v$layoutCopyRegister, v$layoutRegister
                    move-object/from16 v$activityCopyRegister, v$activityRegister
                    invoke-static {v$activityCopyRegister, v$layoutCopyRegister, v$userSessionCopyRegister, v$userObjectRegister}, $ACTIONBAR_DESCRIPTOR->userProfileActionBarButton(${ACTIVITY_CLASS}Landroid/view/ViewGroup;${USER_SESSION_CLASS}Ljava/lang/Object;)V
                    """.trimIndent()

                addInstructionsWithLabels(
                    injectionIndex,
                    code,
                    ExternalLabel("piko", resumeInstruction),
                )
            }

            addFlags("profileActionBarFlags")
        }
    }
