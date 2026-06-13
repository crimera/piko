/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.settings

import app.crimera.patches.twitter.misc.extension.sharedExtensionPatch
import app.crimera.patches.twitter.misc.extension.twitterInitHook
import app.crimera.patches.twitter.premium.redirectBMNavBar.redirectBMTab
import app.crimera.patches.twitter.utils.Constants.ACTIVITY_HOOK_CLASS
import app.crimera.patches.twitter.utils.Constants.ADD_PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.Constants.DEEPLINK_HOOK_CLASS
import app.crimera.patches.twitter.utils.Constants.SSTS_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.addAppResources
import app.morphe.patches.all.misc.resources.addResourcesPatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val START_ACTIVITY_DESCRIPTOR =
    "invoke-static {}, $ACTIVITY_HOOK_CLASS->startSettingsActivity()V"

val settingsPatch =
    bytecodePatch(
        description = "Adds settings",
    ) {
        dependsOn(
            sharedExtensionPatch,
            settingsResourcePatch,
            redirectBMTab,
            addResourcesPatch,
        )

        execute {
            addAppResources("shared")
            addAppResources("twitter")

            val initMethod = SettingsFingerprint.method
            val arrayCreationInstruction =
                initMethod
                    .instructions
                    .firstOrNull { it.opcode == Opcode.FILLED_NEW_ARRAY_RANGE }
                    ?: throw PatchException("Failed to find FILLED_NEW_ARRAY_RANGE in ${SettingsFingerprint.definingClass}")

            val arrayCreation = arrayCreationInstruction.location.index + 1

            initMethod.getInstruction<BuilderInstruction11x>(arrayCreation).registerA.also { reg ->
                initMethod.addInstructions(
                    arrayCreation + 1,
                    """
                const-string v1, "pref_mod"
                invoke-static {v$reg, v1}, $ADD_PREF_DESCRIPTOR
                move-result-object v$reg
            """,
                )
            }

            val prefCLickedFingerprint =
                Fingerprint(
                    returnType = "Z",
                    parameters = listOf("Landroidx/preference/Preference;"),
                    filters =
                        listOf(
                            opcode(Opcode.CONST_4),
                        ),
                ).match(SettingsFingerprint.classDef)

            val prefCLickedMethod = prefCLickedFingerprint.method
            val constIndex = prefCLickedFingerprint.instructionMatches.firstOrNull()?.index
                ?: throw PatchException("Failed to find CONST_4 in prefCLickedMethod of ${SettingsFingerprint.definingClass}")

            val igetObjInstruction =
                prefCLickedMethod
                    .instructions
                    .firstOrNull { it.opcode == Opcode.IGET_OBJECT }
                    ?: throw PatchException("Failed to find IGET_OBJECT in prefCLickedMethod of ${SettingsFingerprint.definingClass}")

            val igetObjLoc = igetObjInstruction.location.index
            val objFieldName = (prefCLickedMethod.getInstruction<ReferenceInstruction>(igetObjLoc).reference as FieldReference).name
            prefCLickedMethod.removeInstruction(igetObjLoc)

            prefCLickedMethod.addInstructionsWithLabels(
                0,
                """
            iget-object p1, p1, Landroidx/preference/Preference;->$objFieldName:Ljava/lang/String;
            const-string v1, "pref_mod" 
            invoke-virtual {p1, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
            move-result v2

            if-nez v2, :start
            goto :cont
            
            :start
            $START_ACTIVITY_DESCRIPTOR
            const/4 v3, 0x1
            return v3 
        """,
                ExternalLabel("cont", prefCLickedMethod.getInstruction(constIndex)),
            )

            AuthorizeAppActivity.apply {
                val superClass = classDef.superclass
                method.apply {
                    addInstructionsWithLabels(
                        0,
                        """
                        invoke-super {p0,p1}, $superClass->onCreate(Landroid/os/Bundle;)V
                        invoke-static {p0}, $ACTIVITY_HOOK_CLASS->create(Landroid/app/Activity;)Z
                        move-result v0
                        if-eqz v0, :piko
                        return-void
                        """.trimIndent(),
                        ExternalLabel(
                            "piko",
                            instructions.firstOrNull() ?: throw PatchException("Method has no instructions in AuthorizeAppActivity")
                        ),
                    )
                }
            }

            val functionCall =
                """
                invoke-static {p0}, $DEEPLINK_HOOK_CLASS->deeplink(Landroid/app/Activity;)Z
                move-result v0
                if-nez v0, :deep_link
                """.trimIndent()

            var deepLinkPatched = false
            UrlInterpreterActivityFingerprint.method.apply {
                val invokeSuperInstructionIndex = indexOfFirstInstruction(Opcode.INVOKE_SUPER)

                if (invokeSuperInstructionIndex > 0) {
                    val returnVoidInstruction = instructions.firstOrNull { it.opcode == Opcode.RETURN_VOID }
                        ?: throw PatchException("Failed to find RETURN_VOID in UrlInterpreterActivity")

                    addInstructionsWithLabels(
                        invokeSuperInstructionIndex + 1,
                        functionCall,
                        ExternalLabel(
                            "deep_link",
                            returnVoidInstruction,
                        ),
                    )
                    deepLinkPatched = true
                }
            }
            if (!deepLinkPatched) {
                UrlInterpreterActivityPairIPFingerprint.method.apply {
                    val sgetInstruction = instructions.lastOrNull { it.opcode == Opcode.SGET_OBJECT }
                        ?: throw PatchException("Failed to find SGET_OBJECT in UrlInterpreterActivityPairIP")

                    val loc = sgetInstruction.location.index
                    if (loc > 0) {
                        val returnVoidInstruction = instructions.firstOrNull { it.opcode == Opcode.RETURN_VOID }
                            ?: throw PatchException("Failed to find RETURN_VOID in UrlInterpreterActivityPairIP")

                        addInstructionsWithLabels(
                            loc,
                            functionCall,
                            ExternalLabel(
                                "deep_link",
                                returnVoidInstruction,
                            ),
                        )
                        deepLinkPatched = true
                    }
                }
            }

            twitterInitHook.fingerprint.method.addInstruction(
                0,
                "$SSTS_DESCRIPTOR->load()V",
            )
        }
    }
