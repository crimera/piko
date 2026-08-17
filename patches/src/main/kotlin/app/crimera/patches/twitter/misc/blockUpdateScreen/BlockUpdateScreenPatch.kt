/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.blockUpdateScreen

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private object FullCoverDialogInflateFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/ui/dialog/fullcover/",
    name = "<init>",
    returnType = "V",
    filters = listOf(
        resourceLiteral(
            type = ResourceType.LAYOUT,
            name = "vdl_full_cover"
        ),
        resourceLiteral(
            type = ResourceType.ID,
            name = "dismiss_button"
        ),
        fieldAccess(
            opcode = Opcode.IPUT_OBJECT,
            definingClass = "this",
            type = "Landroid/view/View;"
        )
    )
)

private fun getShowDialogFingerprint(dismissButtonField: FieldReference) = object : Fingerprint(
    name = "get",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            reference = dismissButtonField
        ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            name = "setOnClickListener",
            location = MatchAfterWithin(5)
        )
    )
) {}

@Suppress("unused")
val blockUpdateScreenPatch =
    bytecodePatch(
        name = "Block update screen",
        description = "Blocks the 'This app is out of date' update screen from being shown on launch",
    ) {
        compatibleWith(COMPATIBILITY_X)

        dependsOn(
            settingsPatch,
            resourceMappingPatch
        )

        execute {
            val dismissButtonField = FullCoverDialogInflateFingerprint
                .instructionMatches
                .last()
                .instruction
                .getReference<FieldReference>()!!

            getShowDialogFingerprint(dismissButtonField).let {
                it.method.apply {
                    val match = it.instructionMatches.last()
                    val index = match.index
                    val register = match.instruction.registersUsed[0]

                    addInstruction(
                        index + 1,
                        "invoke-static { v$register }, $PREF_DESCRIPTOR;->blockUpdateScreen(Landroid/view/View;)V"
                    )
                }
            }

            enableSettings("blockUpdateScreen")
        }
    }
