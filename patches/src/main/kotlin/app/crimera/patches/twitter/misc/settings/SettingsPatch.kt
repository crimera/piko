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
            val arrayCreation =
                initMethod
                    .instructions
                    .first { it.opcode == Opcode.FILLED_NEW_ARRAY_RANGE }
                    .location.index + 1

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
            val constIndex = prefCLickedFingerprint.instructionMatches.first().index

            val igetObjLoc =
                prefCLickedMethod
                    .instructions
                    .first { it.opcode == Opcode.IGET_OBJECT }
                    .location.index
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
                            instructions.first(),
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
                    addInstructionsWithLabels(
                        invokeSuperInstructionIndex + 1,
                        functionCall,
                        ExternalLabel(
                            "deep_link",
                            instructions.first { it.opcode == Opcode.RETURN_VOID },
                        ),
                    )
                    deepLinkPatched = true
                }
            }
            if (!deepLinkPatched) {
                UrlInterpreterActivityPairIPFingerprint.method.apply {
                    val loc = instructions.last { it.opcode == Opcode.SGET_OBJECT }.location.index
                    if (loc > 0) {
                        addInstructionsWithLabels(
                            loc,
                            functionCall,
                            ExternalLabel(
                                "deep_link",
                                instructions.first { it.opcode == Opcode.RETURN_VOID },
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

            // execute ends.
        }
    }
