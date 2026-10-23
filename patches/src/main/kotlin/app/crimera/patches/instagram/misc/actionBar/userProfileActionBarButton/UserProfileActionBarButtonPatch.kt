/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.userProfileActionBarButton

import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.USER_DETAIL_VIEW_MODEL_CLASS
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.common.declaredParameterRegister
import app.crimera.patches.instagram.utils.fieldReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.addInstructionsAtControlFlowLabel
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val ACTIVITY_CLASS = "Landroid/app/Activity;"
private const val IG_LINEAR_LAYOUT_CLASS = "Lcom/instagram/common/ui/base/IgLinearLayout;"

internal object ProfileHeaderRelatedFingerprint : Fingerprint(
    strings = listOf("profile_user_id", "click_point", "user_profile_header"),
    returnType = "V",
    parameters = listOf("L", "L", "L"),
)

/**
 * Builds the profile action bar buttons, after removing the previous ones from both layouts.
 */
internal object ProfileActionBarBuilderFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        ACTIVITY_CLASS,
        "Landroid/content/Context;",
        USER_SESSION_CLASS,
        IG_LINEAR_LAYOUT_CLASS,
        IG_LINEAR_LAYOUT_CLASS,
        "L",
        "Ljava/util/List;",
        "Lkotlin/jvm/functions/Function1;",
    ),
    filters = listOf(
        methodCall(smali = "Landroid/view/ViewGroup;->removeAllViews()V"),
        methodCall(smali = "Landroid/view/ViewGroup;->removeAllViews()V", location = MatchAfterImmediately()),
        // Buttons of the action bar.
        methodCall(smali = "Ljava/util/List;->iterator()Ljava/util/Iterator;", location = MatchAfterImmediately()),
    ),
)

/**
 * Creates an action bar icon from its model.
 */
internal object ProfileActionIconFactoryFingerprint : Fingerprint(
    classFingerprint = ProfileActionBarBuilderFingerprint,
    returnType = "Landroid/widget/ImageView;",
    parameters = listOf("Landroid/content/Context;", USER_SESSION_CLASS, "L", "L"),
    filters = listOf(
        // Drawable of the model.
        fieldAccess(type = "I", opcode = Opcode.IGET),
        opcode(Opcode.RETURN_OBJECT),
    ),
)

// Static profile action bar builder parameters.
private const val BUILDER_ACTIVITY_PARAMETER = 0
private const val BUILDER_USER_SESSION_PARAMETER = 2
private const val BUILDER_LAYOUT_PARAMETER = 4
private const val BUILDER_ACTION_BAR_RELATED_PARAMETER = 5

val userProfileActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on user profile action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(decoderEntity)

        execute {
            val actionBarRelatedClass = classDefBy(
                ProfileActionBarBuilderFingerprint.originalMethod.parameterTypes[BUILDER_ACTION_BAR_RELATED_PARAMETER].toString(),
            )
            val profileHeaderClass = ProfileHeaderRelatedFingerprint.matchAll()
                .map { it.originalClassDef }
                .distinctBy { it.type }
                .singleOrNull { candidate -> actionBarRelatedClass.fields.any { it.type == candidate.type } }
                ?: throw PatchException("Expected one profile header referenced by the action bar")
            val profileHeaderFieldInActionBarRelatedClass =
                actionBarRelatedClass.fields.single { it.type == profileHeaderClass.type }
            val userDetailViewModelFieldInProfileHeaderRelatedClass =
                profileHeaderClass.fields.single { it.type == USER_DETAIL_VIEW_MODEL_CLASS }
            val userDataFieldInUserDetailClass =
                classDefBy(USER_DETAIL_VIEW_MODEL_CLASS).fields.single { it.type == USER_MODEL_CLASS_NAME }

            ProfileActionIconFactoryFingerprint.let {
                val drawableRead = it.instructionMatches.first().getInstruction<TwoRegisterInstruction>()
                val modelRegister = drawableRead.registerB
                val drawableField = it.instructionMatches.first().fieldReference
                val returnIndex = it.instructionMatches.last().index
                val viewRegister = it.instructionMatches.last().getInstruction<OneRegisterInstruction>().registerA
                val registers = it.method.getFreeRegisterProvider(returnIndex, 2, modelRegister, viewRegister)
                val viewCopy = registers.getFreeRegister()
                val drawableCopy = registers.getFreeRegister()
                it.method.addInstructionsAtControlFlowLabel(
                    returnIndex,
                    """
                    move-object/from16 v$viewCopy, v$modelRegister
                    iget v$drawableCopy, v$viewCopy, $drawableField
                    move-object/from16 v$viewCopy, v$viewRegister
                    invoke-static {v$viewCopy, v$drawableCopy}, $ACTIONBAR_DESCRIPTOR->hideProfileCreateButton(Landroid/view/View;I)V
                    """.trimIndent(),
                )
            }

            ProfileActionBarBuilderFingerprint.method.apply {
                val activityRegister = p0Register + BUILDER_ACTIVITY_PARAMETER
                val userSessionRegister = p0Register + BUILDER_USER_SESSION_PARAMETER
                val layoutRegister = p0Register + BUILDER_LAYOUT_PARAMETER
                val actionBarRelatedRegister = p0Register + BUILDER_ACTION_BAR_RELATED_PARAMETER
                val injectionIndex = ProfileActionBarBuilderFingerprint.instructionMatches.last().index
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
