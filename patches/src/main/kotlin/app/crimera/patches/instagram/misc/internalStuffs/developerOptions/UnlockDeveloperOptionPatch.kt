/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.internalStuffs.developerOptions

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val unlockDeveloperOptionPatch =
    bytecodePatch(
        name = "Unlock developer options",
        description = "Unlocks developer option by long pressing home icon",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {

            HomeIconOnClickListenerFingerprint.method.apply {
                val firstInvokeStaticInstruction = getInstruction(indexOfFirstInstruction(Opcode.INVOKE_STATIC))

                val instagramMainActivityRegister = getInstruction(0).registersUsed[0]
                val userSessionRegister = getInstruction(1).registersUsed[0]
                val freeRegister = findFreeRegister(2)
                val freeRegister2 = freeRegister + 1

                val developerOptionCallClassDef = DeveloperOptionCallWithCallableFingerprint.classDef
                val developerOptionsFragmentField = developerOptionCallClassDef.fields.first()
                val developerOptionsFragmentCall = developerOptionCallClassDef.methods.last()

                val fragmentClass = FragmentNavigatorTransitionFingerprint.classDef
                val fragmentClassMethods = fragmentClass.methods
                val fragmentConstructor = fragmentClassMethods.first { it.name == "<init>" }
                val fragmentSetMethod =
                    fragmentClassMethods.last {
                        it.parameters.size == 1 &&
                            it.parameters[0].type == "Landroidx/fragment/app/Fragment;"
                    }
                val fragmentCallMethod =
                    fragmentClassMethods.last {
                        it.returnType == "V" && it.parameters.isEmpty() &&
                            it.instructions[0].opcode == Opcode.SGET_OBJECT
                    }

                addInstructionsWithLabels(
                    2,
                    """
                    $PREF_CALL_DESCRIPTOR->enableDevOptions()Z
                    move-result v$freeRegister
                    
                    if-eqz v$freeRegister, :no_dev
                    $PREF_CALL_DESCRIPTOR->directlyOpenMetaConfig()Z
                    move-result v$freeRegister
                    
                    if-eqz v$freeRegister, :not_direct_config
                    # thanks to instafel
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
