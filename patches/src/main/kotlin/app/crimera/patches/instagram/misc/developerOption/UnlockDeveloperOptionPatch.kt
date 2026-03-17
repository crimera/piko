/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.developerOption

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val unlockDeveloperOptionPatch =
    bytecodePatch(
        name = "Unlock developer options",
        use = true,
    ) {
        compatibleWith("com.instagram.android")
        dependsOn(settingsPatch)
        execute {

            PromoteActivityOnCreate.method.apply {

                val strIndex = PromoteActivityOnCreate.stringMatches[0].index

                val invokeStatic = instructions.last { it.opcode == Opcode.INVOKE_STATIC && it.location.index < strIndex }

                val methodRef = invokeStatic.getReference<MethodReference>()
                val devOptionsMethod =
                    mutableClassDefBy(methodRef!!.definingClass)
                        .methods
                        .first { it.name == methodRef.name }
                devOptionsMethod.addInstructions(
                    0,
                    """
                    ${Constants.PREF_CALL_DESCRIPTOR}->enableDevOptions()Z
                    move-result v0
                    return v0
                    """.trimIndent(),
                )

                enableSettings("enableDeveloperOptions")
            }
        }
    }
