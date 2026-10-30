/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.crimera.patches.instagram.entity.developerOptions.developerOptionsEntity
import app.crimera.patches.instagram.entity.dialogbox.instagramDialogBoxEntity
import app.crimera.patches.instagram.entity.instagramButton.instagramButtonEntity
import app.crimera.patches.instagram.entity.profileinfo.profileInfoEntity
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.links.validateLinks.validateLinksPatch
import app.crimera.patches.instagram.misc.actionBar.mainFeedActionBarButton.hideHomeActionButtonsPatch
import app.crimera.patches.instagram.misc.actionBar.mainFeedActionBarButton.mainFeedActionBarButtonPatch
import app.crimera.patches.instagram.misc.actionBar.userProfileActionBarButton.userProfileActionBarButtonPatch
import app.crimera.patches.instagram.misc.buildExpiredPopup.removeBuildExpiredPopupPatch
import app.crimera.patches.instagram.misc.extension.hooks.instagramInitHook
import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.notification.fixDirectNotificationActionCrashPatch
import app.crimera.patches.instagram.misc.notification.fixNotificationRegistrationCrashPatch
import app.crimera.patches.instagram.misc.userProfile.preserveUnknownFriendshipStatusPatch
import app.crimera.patches.instagram.misc.userProfile.userProfileButtonPatch
import app.crimera.patches.instagram.utils.Constants.ACTIVITY_SETTINGS_CLASS
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.CONSTANTS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.LOAD_FLAGS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.SSTS_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.shared.parameterRegisterStart
import app.crimera.utils.InitMethod
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.Annotation
import com.android.tools.smali.dexlib2.iface.MethodParameter
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val settingsPatch =
    bytecodePatch(
        name = "Add settings",
        description = "Adds settings to control preferences are patching",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            sharedExtensionPatch,
            settingsResourcePatch,
            nativeSettingsSwitchStylePatch,
            settingsTaskRemovalPatch,
            mainFeedActionBarButtonPatch,
            hideHomeActionButtonsPatch,
            userProfileActionBarButtonPatch,
            userDataEntity,
            userProfileButtonPatch,
            hookFlagsPatch,
            fixNotificationRegistrationCrashPatch,
            fixDirectNotificationActionCrashPatch,
            validateLinksPatch,
            removeBuildExpiredPopupPatch,
            preserveUnknownFriendshipStatusPatch,
            profileInfoEntity,
            instagramDialogBoxEntity,
            instagramButtonEntity,
            developerOptionsEntity,
            addResourcesPatch,
        )
        execute {
            addAppResources("shared")
            addAppResources("instagram")
            enableSettings("addSettings")

            // Returning from a modal must restore the activity used for native themes.
            for (fingerprint in listOf(IgFragmentActivityOnCreate, IgFragmentActivityOnResume)) {
                fingerprint.method.addInstruction(
                    fingerprint.instructionMatches.first().index,
                    """
                    invoke-static {p0}, Lapp/morphe/extension/shared/Utils;->setActivity(Landroid/app/Activity;)V
                    """.trimIndent(),
                )
            }

            ModalActivityOnCreate.method.apply {
                val overrideIndex = indexOfFirstInstructionOrThrow {
                    opcode == Opcode.INVOKE_SUPER &&
                        getReference<MethodReference>()?.name == "onCreate"
                } + 1

                val thisRegister = parameterRegisterStart(this)
                val freeRegister = findFreeRegister(overrideIndex, thisRegister)

                addInstructionsWithLabels(
                    overrideIndex,
                    """
                        invoke-static/range { v$thisRegister .. v${thisRegister + 1} }, $ACTIVITY_SETTINGS_CLASS/ActivityHook;->create(Landroid/app/Activity;Landroid/os/Bundle;)Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :not_piko
                        return-void
                        :not_piko
                        nop
                    """,
                )
            }

            ModalActivityOnPostCreate.method.apply {
                val overrideIndex = indexOfFirstInstructionOrThrow {
                    opcode == Opcode.INVOKE_SUPER &&
                        getReference<MethodReference>()?.name == "onPostCreate"
                } + 1

                val thisRegister = parameterRegisterStart(this)
                val freeRegister = findFreeRegister(overrideIndex, thisRegister)

                addInstructionsWithLabels(
                    overrideIndex,
                    """
                        invoke-static/range { v$thisRegister .. v$thisRegister }, $ACTIVITY_SETTINGS_CLASS/ActivityHook;->isPiko(Landroid/app/Activity;)Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :not_piko_post_create
                        return-void
                        :not_piko_post_create
                        nop
                    """,
                )
            }

            ModalActivityInitStartingFragment.method.apply {
                val thisRegister = parameterRegisterStart(this)
                val freeRegister = findFreeRegister(0, thisRegister)

                addInstructionsWithLabels(
                    0,
                    """
                        invoke-static/range { v$thisRegister .. v$thisRegister }, $ACTIVITY_SETTINGS_CLASS/ActivityHook;->isPiko(Landroid/app/Activity;)Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :not_piko_init
                        return-void
                        :not_piko_init
                        nop
                    """,
                )
            }

            ModalActivityOnCreate.classDef.apply {
                val activityClass = type
                val existingOnBackPressed = methods.firstOrNull {
                    it.name == "onBackPressed" && it.parameterTypes.isEmpty() && it.returnType == "V"
                }
                if (existingOnBackPressed != null) {
                    try {
                        existingOnBackPressed.apply {
                            val activityRegister = implementation!!.registerCount - 1
                            val freeRegister = findFreeRegister(0, activityRegister)

                            addInstructionsWithLabels(
                                0,
                                """
                                    invoke-static/range { v$activityRegister .. v$activityRegister }, $ACTIVITY_SETTINGS_CLASS/ActivityHook;->onBackPressed(Landroid/app/Activity;)Z
                                    move-result v$freeRegister
                                    if-eqz v$freeRegister, :continue_back_press
                                    return-void
                                    :continue_back_press
                                    nop
                                """,
                            )
                        }
                    } catch (exception: Exception) {
                        throw PatchException("Failed to hook ModalActivity.onBackPressed: ${exception.message}")
                    }
                } else {
                    val implementationBuilder = MethodImplementationBuilder(2)
                    implementationBuilder.addInstruction("return-void".toInstruction())

                    val method =
                        MutableMethod(
                            InitMethod(
                                validator = {},
                                compare = { other ->
                                    when {
                                        activityClass != other.definingClass -> activityClass.compareTo(other.definingClass)
                                        "onBackPressed" != other.name -> "onBackPressed".compareTo(other.name)
                                        else -> 0
                                    }
                                },
                                definingClass = activityClass,
                                name = "onBackPressed",
                                parameterTypes = mutableListOf<CharSequence>(),
                                returnType = "V",
                                annotations = mutableSetOf<Annotation>(),
                                accessFlags = AccessFlags.PUBLIC.value,
                                hiddenApiRestrictions = mutableSetOf<HiddenApiRestriction>(),
                                parameters = mutableListOf<MethodParameter>(),
                                implementation = implementationBuilder.methodImplementation,
                            ),
                        )

                    method.addInstructionsWithLabels(
                        0,
                        """
                            invoke-static { v1 }, $ACTIVITY_SETTINGS_CLASS/ActivityHook;->onBackPressed(Landroid/app/Activity;)Z
                            move-result v0
                            if-eqz v0, :continue_back_press
                            return-void
                            :continue_back_press
                            invoke-super { v1 }, $superclass->onBackPressed()V
                        """,
                    )
                    methods.add(method)
                }
            }

            instagramInitHook.fingerprint.method.apply {

                val firstInvokeSuperIndex = indexOfFirstInstructionOrThrow(Opcode.INVOKE_SUPER)
                val contextRegister = getInstruction(firstInvokeSuperIndex).registersUsed[0]
                val freeRegister = findFreeRegister(firstInvokeSuperIndex, listOf(firstInvokeSuperIndex))

                addInstructions(
                    firstInvokeSuperIndex + 1,
                    """
                    new-instance v$freeRegister, Lapp/morphe/extension/crimera/CustomCrashHandler;
                    invoke-direct {v$freeRegister, v$contextRegister}, Lapp/morphe/extension/crimera/CustomCrashHandler;-><init>(Landroid/content/Context;)V
                    invoke-static {v$freeRegister}, Ljava/lang/Thread;->setDefaultUncaughtExceptionHandler(Ljava/lang/Thread${'$'}UncaughtExceptionHandler;)V
                    
                    ${SSTS_DESCRIPTOR.format("load")}
                    ${LOAD_FLAGS_DESCRIPTOR.format("load")}
                    ${LOAD_FLAGS_DESCRIPTOR.format("load")}
                    invoke-static {}, $CONSTANTS_DESCRIPTOR/Constants;->load()V
                    """.trimIndent(),
                )
            }

            // For welcome message.
            MainFeedFragmentOnCreateFingerprint.let {
                val contextResultIndex = it.instructionMatches.last().index
                val contextRegister = it.instructionMatches.last().getInstruction<OneRegisterInstruction>().registerA

                it.method.addInstruction(
                    contextResultIndex + 1,
                    "invoke-static/range {v$contextRegister .. v$contextRegister}, " +
                        "$PATCHES_DESCRIPTOR/WelcomeMessage;->openWelcomeMessage(Landroid/content/Context;)V",
                )
            }
        }
    }
