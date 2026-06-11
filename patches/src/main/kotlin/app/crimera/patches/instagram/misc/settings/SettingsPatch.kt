/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.crimera.patches.instagram.entity.developerOptions.developerOptionsEntity
import app.crimera.patches.instagram.entity.instagramButton.instagramButtonEntity
import app.crimera.patches.instagram.entity.profileinfo.profileInfoEntity
import app.crimera.patches.instagram.misc.actionBar.mainFeedActionBarButton.mainFeedActionBarButtonPatch
import app.crimera.patches.instagram.misc.extension.hooks.instagramInitHook
import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.userProfile.userProfileButtonPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.CONSTANTS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.LOAD_FLAGS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.SSTS_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.findFreeRegister
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

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
            addSettingsActivityPatch,
            mainFeedActionBarButtonPatch,
            userProfileButtonPatch,
            hookFlagsPatch,
            profileInfoEntity,
            instagramButtonEntity,
            developerOptionsEntity,
        )
        execute {

            IgFragmentActivityOnCreate.method.apply {

                val returnVoidIndex = indexOfFirstInstruction(Opcode.RETURN_VOID)

                addInstruction(
                    returnVoidIndex,
                    """
                    invoke-static {p0}, Lapp/morphe/extension/shared/Utils;->setActivity(Landroid/app/Activity;)V
                    """.trimIndent(),
                )
            }

            instagramInitHook.fingerprint.method.apply {

                addInstruction(
                    0,
                    SSTS_DESCRIPTOR.format("load"),
                )

                val firstInvokeSuperIndex = indexOfFirstInstruction(Opcode.INVOKE_SUPER)
                val contextRegister = getInstruction(firstInvokeSuperIndex).registersUsed[0]
                val freeRegister = findFreeRegister(firstInvokeSuperIndex, listOf(firstInvokeSuperIndex))

                addInstructions(
                    firstInvokeSuperIndex + 1,
                    """
                    new-instance v$freeRegister, Lapp/morphe/extension/crimera/CustomCrashHandler;
                    invoke-direct {v$freeRegister, v$contextRegister}, Lapp/morphe/extension/crimera/CustomCrashHandler;-><init>(Landroid/content/Context;)V
                    invoke-static {v$freeRegister}, Ljava/lang/Thread;->setDefaultUncaughtExceptionHandler(Ljava/lang/Thread${'$'}UncaughtExceptionHandler;)V
                    """.trimIndent(),
                )

                addInstruction(
                    firstInvokeSuperIndex + 2,
                    LOAD_FLAGS_DESCRIPTOR.format("load"),
                )
                // Loads strings for common extension.
                addInstruction(
                    firstInvokeSuperIndex + 3,
                    "invoke-static {}, $CONSTANTS_DESCRIPTOR/Strings;->load()V",
                )
            }

            // For welcome message.
            MainFeedFragmentOnCreateFingerprint.apply {
                val strIndex = stringMatches[0].index

                method.apply {
                    val contextIndex = indexOfFirstInstruction(strIndex, Opcode.MOVE_RESULT_OBJECT)
                    val contextInstruction = getInstruction(contextIndex)
                    val contextRegister = contextInstruction.registersUsed[0]

                    addInstruction(
                        contextIndex + 1,
                        """
                        invoke-static{v$contextRegister}, $PATCHES_DESCRIPTOR/WelcomeMessage;->openWelcomeMessage(Landroid/content/Context;)V
                        """.trimIndent(),
                    )
                }
            }
        }
    }
