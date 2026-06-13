/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 $7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.internalStuffs.developerOptions

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string

private object QuickExperimentFingerprint : Fingerprint(
    filters =
        listOf(
            string("is_employee"),
        ),
)

val unlockDeveloperOptionPatch =
    bytecodePatch(
        name = "Unlock developer options",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            val fragmentClassMethods = QuickExperimentFingerprint.classDef.methods
            val fragmentConstructor = fragmentClassMethods.firstOrNull { it.name == "<init>" }
                ?: throw PatchException("Failed to find constructor in QuickExperimentFingerprint")

            fragmentConstructor.replaceInstructions(
                listOf(
                    "return-void",
                ),
            )

            fragmentClassMethods.filter {
                it.returnType == "Z" && it.parameters.isEmpty()
            }.forEach {
                it.replaceInstructions(
                    listOf(
                        "const/4 v0, 0x1",
                        "return v0",
                    ),
                )
            }
        }
    }
