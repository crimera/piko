/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.internalStuffs.developerOptions

import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.internalStuffs.QE_FRAGMENT_DESCRIPTOR
import app.crimera.patches.instagram.misc.internalStuffs.checkMappingsPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.findFreeRegister
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val unlockDeveloperOptionPatch =
    bytecodePatch(
        name = "Unlock developer options",
        description = "Unlocks developer option by long pressing home icon",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, hookFlagsPatch, checkMappingsPatch)
        execute {

            HomeIconOnClickListenerFingerprint.method.apply {
                val matches = HomeIconOnClickListenerFingerprint.instructionMatches
                val instagramMainActivityRegister = getInstruction<OneRegisterInstruction>(matches[0].index).registerA
                val userSessionRegister = getInstruction<OneRegisterInstruction>(matches[1].index).registerA
                val firstInvokeStaticInstruction = getInstruction(matches[2].index)
                val freeRegister = findFreeRegister(matches[2].index)
                val freeRegister2 = freeRegister + 1

                val developerOptionsFragmentField = DeveloperOptionCallWithCallableFingerprint.originalClassDef.fields.first()
                val developerOptionsFragmentCall = DeveloperOptionsOpenFingerprint.originalMethod

                val fragmentClass = FragmentNavigatorTransitionFingerprint.originalClassDef
                val fragmentConstructor = FragmentNavigatorConstructorFingerprint.originalMethod
                val fragmentSetMethod = FragmentNavigatorSetFragmentFingerprint.originalMethod
                // The last method that only starts the transition.
                val fragmentCallMethod = FragmentNavigatorCommitFingerprint.matchAll()
                    .last { it.instructionMatches.first().index == 0 }.originalMethod

                val userSessionCastType = fragmentConstructor.parameterTypes[1]

                addInstructionsWithLabels(
                    matches[2].index,
                    """
                    $PREF_CALL_DESCRIPTOR->enableDevOptions()Z
                    move-result v$freeRegister
                    
                    if-eqz v$freeRegister, :no_dev
                    $PREF_CALL_DESCRIPTOR->directlyOpenMetaConfig()Z
                    move-result v$freeRegister
                    
                    if-eqz v$freeRegister, :not_direct_config
                    # thanks to instafel
                    check-cast v$userSessionRegister, $userSessionCastType
                    new-instance v$freeRegister, $QE_FRAGMENT_DESCRIPTOR
                    invoke-direct {v$freeRegister}, $QE_FRAGMENT_DESCRIPTOR-><init>()V
                    
                    new-instance v$freeRegister2, ${fragmentClass.type}
                    invoke-direct {v$freeRegister2, v$instagramMainActivityRegister, v$userSessionRegister}, $fragmentConstructor
                    invoke-virtual {v$freeRegister2, v$freeRegister}, $fragmentSetMethod
                    
                    invoke-virtual {v$freeRegister2}, $fragmentCallMethod
                    const/4 v$freeRegister2, 0x1
                    return v$freeRegister2
                    
                    :not_direct_config
                    sget-object v$freeRegister, $developerOptionsFragmentField
                    invoke-virtual {v$freeRegister, v$instagramMainActivityRegister, v$instagramMainActivityRegister, v$userSessionRegister}, $developerOptionsFragmentCall
                    const/4 v$freeRegister, 0x1
                    return v$freeRegister
                    
                    """.trimIndent(),
                    ExternalLabel("no_dev", firstInvokeStaticInstruction),
                )
            }
            enableSettings("enableDeveloperOptions")
        }
    }
