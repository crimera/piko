/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.settings

import app.crimera.patches.twitter.misc.crashLogs.crashLogsPatch
import app.crimera.patches.twitter.misc.extension.sharedExtensionPatch
import app.crimera.patches.twitter.misc.extension.twitterInitHook
import app.crimera.patches.twitter.premium.redirectBMNavBar.redirectBMTab
import app.crimera.patches.twitter.utils.Constants.ACTIVITY_HOOK_CLASS
import app.crimera.patches.twitter.utils.Constants.DEEPLINK_HOOK_CLASS
import app.crimera.patches.twitter.utils.Constants.SSTS_DESCRIPTOR
import app.crimera.patches.twitter.utils.Constants.UTILS_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

val settingsPatch =
    bytecodePatch(
        description = "Adds settings",
    ) {
        dependsOn(
            checkCompatibilityPatch,
            sharedExtensionPatch,
            settingsResourcePatch,
            redirectBMTab,
            addResourcesPatch,
            crashLogsPatch,
        )

        execute {
            addAppResources("shared")
            addAppResources("twitter")

            SettingsFragmentFingerprint.let {
                it.method.apply {
                    val index = it.instructionMatches.last().index
                    val register = getInstruction<OneRegisterInstruction>(index).registerA

                    addInstructions(
                        index + 1,
                        """
                            invoke-static { v$register }, $UTILS_DESCRIPTOR;->addPref([Ljava/lang/String;)[Ljava/lang/String;
                            move-result-object v$register
                        """,
                    )
                }
            }

            SettingsPreferenceFingerprint.let {
                it.method.apply {
                    val index = it.instructionMatches.first().index
                    val register = getInstruction<TwoRegisterInstruction>(index).registerA
                    val freeRegister = findFreeRegister(index + 1, register)

                    addInstructionsWithLabels(
                        index + 1,
                        """
                            invoke-static { v$register }, $ACTIVITY_HOOK_CLASS->startSettingsActivity(Ljava/lang/String;)Z
                            move-result v$freeRegister
                            if-eqz v$freeRegister, :ignore
                            const/4 v$freeRegister, 0x1
                            return v$freeRegister
                            :ignore
                            nop
                        """,
                    )
                }
            }

            listOf(
                Triple(
                    AuthorizeAppActivityFingerprint,
                    AuthorizeAppActivityVirtualFingerprint,
                    "$ACTIVITY_HOOK_CLASS->create(Landroid/app/Activity;)Z",
                ),
                Triple(
                    UrlInterpreterActivityFingerprint,
                    UrlInterpreterActivityVirtualFingerprint,
                    "$DEEPLINK_HOOK_CLASS->deeplink(Landroid/app/Activity;)Z",
                ),
            ).forEach { (originalFingerprint, virtualFingerprint, extensionMethodCall) ->
                val insertIndex: Int
                val insertMethod: MutableMethod

                val originalMethod = originalFingerprint.method
                val overrideIndex =
                    originalMethod.indexOfFirstInstruction {
                        opcode == Opcode.INVOKE_SUPER &&
                            getReference<MethodReference>()?.name == "onCreate"
                    } + 1

                if (overrideIndex > 0) {
                    insertIndex = overrideIndex
                    insertMethod = originalMethod
                } else {
                    insertMethod = virtualFingerprint.method
                    insertIndex = insertMethod.indexOfFirstInstructionOrThrow {
                        val reference = getReference<MethodReference>()
                        opcode == Opcode.INVOKE_STATIC &&
                            reference?.definingClass == originalMethod.definingClass &&
                            reference.name.startsWith("onCreate")
                    } + 1
                }

                insertMethod.apply {
                    val activityRegister =
                        getInstruction<FiveRegisterInstruction>(insertIndex - 1).registerC
                    val freeRegister = findFreeRegister(insertIndex, activityRegister)

                    addInstructionsWithLabels(
                        insertIndex,
                        """
                            invoke-static { v$activityRegister }, $extensionMethodCall
                            move-result v$freeRegister
                            if-eqz v$freeRegister, :ignore
                            return-void
                            :ignore
                            nop
                        """,
                    )
                }
            }

            twitterInitHook.fingerprint.method.addInstruction(
                0,
                "$SSTS_DESCRIPTOR->load()V",
            )

            // execute ends.
        }
    }
