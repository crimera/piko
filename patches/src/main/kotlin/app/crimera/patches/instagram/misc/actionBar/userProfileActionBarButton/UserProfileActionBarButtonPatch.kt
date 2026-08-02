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
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableField
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val IG_LINEAR_LAYOUT_CLASS = "Lcom/instagram/common/ui/base/IgLinearLayout;"

internal fun findProfileActionBarBuilderInvocation(
    candidates: List<IndexedValue<MethodReference>>,
    expectedParameterTypes: List<String>,
    methodsInDefiningClass: (String) -> Iterable<Method>,
): IndexedValue<MethodReference> {
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

    if (matchingDefinitions.count() != 1) {
        throw PatchException("Expected exactly one matching method in the profile action bar builder class")
    }

    return candidate
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

            ProfileActionBarFingerprint.method.apply {
                if (AccessFlags.STATIC.isSet(accessFlags)) {
                    throw PatchException("Expected the profile action bar method to be an instance method")
                }

                fun parameterRegister(type: String): Int {
                    val matches = parameters.withIndex().filter { it.value.type == type }
                    return matches.singleOrNull()?.index?.plus(1)
                        ?: throw PatchException(
                            "Expected exactly one $type parameter in the profile action bar method, found ${matches.size}",
                        )
                }

                val actionBarRelatedObjectParameterRegister =
                    parameterRegister(ProfileActionBarRelatedFingerprint.classDef.type)
                val userSessionParameterRegister = parameterRegister(USER_SESSION_CLASS)

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
                val profileActionBarBuilderInvocation =
                    findProfileActionBarBuilderInvocation(
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
                            classDefBy { it.type == definingClass }.methods
                        },
                    )
                val profileActionBarIconInjectMethodIndex = profileActionBarBuilderInvocation.index
                val profileActionBarBuilderInstruction = getInstruction(profileActionBarIconInjectMethodIndex)
                val profileActionBarBuilderReference = profileActionBarBuilderInvocation.value
                val layoutParameterIndices =
                    profileActionBarBuilderReference.parameterTypes.withIndex().filter {
                        it.value == IG_LINEAR_LAYOUT_CLASS
                    }
                if (layoutParameterIndices.size != 2) {
                    throw PatchException(
                        "Expected two profile action bar layout parameters, found ${layoutParameterIndices.size}",
                    )
                }

                val builderRegisters = profileActionBarBuilderInstruction.registersUsed
                if (builderRegisters.size != profileActionBarBuilderReference.parameterTypes.size) {
                    throw PatchException("Profile action bar builder register count does not match its parameters")
                }
                val layoutRegister = builderRegisters[layoutParameterIndices.last().index]
                val nextReturnVoidIndex =
                    indexOfFirstInstructionOrThrow(profileActionBarIconInjectMethodIndex, Opcode.RETURN_VOID)
                val freeRegisterProvider =
                    getFreeRegisterProvider(
                        index = profileActionBarIconInjectMethodIndex + 1,
                        numberOfFreeRegistersNeeded = 3,
                        layoutRegister,
                    )
                val userObjectRegister = freeRegisterProvider.getFreeRegister()
                val userSessionRegister = freeRegisterProvider.getFreeRegister()
                val layoutCopyRegister = freeRegisterProvider.getFreeRegister()
                val code =
                    """
                    move-object/from16 v$userObjectRegister, p$actionBarRelatedObjectParameterRegister
                    if-eqz v$userObjectRegister, :piko
                    iget-object v$userObjectRegister, v$userObjectRegister, $profileHeaderFieldInActionBarRelatedClass

                    if-eqz v$userObjectRegister, :piko
                    iget-object v$userObjectRegister, v$userObjectRegister, $userDetailViewModelFieldInProfileHeaderRelatedClass

                    if-eqz v$userObjectRegister, :piko
                    iget-object v$userObjectRegister, v$userObjectRegister, $userDataFieldInUserDetailClass
                    move-object/from16 v$userSessionRegister, p$userSessionParameterRegister
                    move-object/from16 v$layoutCopyRegister, v$layoutRegister
                    invoke-static {v$layoutCopyRegister, v$userSessionRegister, v$userObjectRegister}, $ACTIONBAR_DESCRIPTOR->userProfileActionBarButton(Landroid/view/ViewGroup;${USER_SESSION_CLASS}Ljava/lang/Object;)V
                    """.trimIndent()

                addInstructionsWithLabels(
                    profileActionBarIconInjectMethodIndex + 1,
                    code,
                    ExternalLabel("piko", getInstruction(nextReturnVoidIndex)),
                )

                addFlags("profileActionBarFlags")
            }
        }
    }
