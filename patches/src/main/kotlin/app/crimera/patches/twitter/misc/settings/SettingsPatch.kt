/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.settings

import app.crimera.utils.InitMethod
import app.crimera.patches.twitter.misc.crashLogs.crashLogsPatch
import app.crimera.patches.twitter.misc.extension.sharedExtensionPatch
import app.crimera.patches.twitter.misc.extension.twitterInitHook
import app.crimera.patches.twitter.premium.redirectBMNavBar.redirectBMTab
import app.crimera.patches.twitter.utils.Constants.ACTIVITY_HOOK_CLASS
import app.crimera.patches.twitter.utils.Constants.ACTIVITY_SETTINGS_CLASS
import app.crimera.patches.twitter.utils.Constants.DEEPLINK_HOOK_CLASS
import app.crimera.patches.twitter.utils.Constants.SSTS_DESCRIPTOR
import app.crimera.patches.twitter.utils.Constants.UTILS_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.Annotation
import com.android.tools.smali.dexlib2.iface.MethodParameter
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val SETTINGS_SEARCH_UI_CONTROLLER_CLASS =
    "$ACTIVITY_SETTINGS_CLASS/SettingsSearchUIController;"

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

            AuthorizeAppActivityFingerprint.classDef.apply {
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
                                    invoke-static { v$activityRegister }, $SETTINGS_SEARCH_UI_CONTROLLER_CLASS->handleBackPressed(Landroid/app/Activity;)Z
                                    move-result v$freeRegister
                                    if-eqz v$freeRegister, :continue_back_press
                                    return-void
                                    :continue_back_press
                                    nop
                                """,
                            )
                        }
                    } catch (exception: Exception) {
                        throw PatchException("Failed to hook AuthorizeAppActivity.onBackPressed: ${exception.message}")
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
                            invoke-static { v1 }, $SETTINGS_SEARCH_UI_CONTROLLER_CLASS->handleBackPressed(Landroid/app/Activity;)Z
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

            twitterInitHook.fingerprint.method.addInstruction(
                0,
                "$SSTS_DESCRIPTOR->load()V",
            )

            // execute ends.
        }
    }
