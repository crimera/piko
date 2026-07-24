/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val SWIPE_NAVIGATION_CONTAINER_CLASS =
    "Lcom/instagram/ui/swipenavigation/container/SwipeNavigationContainer;"

private const val POSITION_CONFIG_CLASS =
    "Lcom/instagram/ui/swipenavigation/container/PositionConfig;"

private object SetInternalPositionFingerprint : Fingerprint(
    name = "setInternalPosition",
    definingClass = SWIPE_NAVIGATION_CONTAINER_CLASS,
    parameters = listOf(POSITION_CONFIG_CLASS),
    returnType = "V",
    filters =
        OpcodesFilter.opcodesToFilters(
            Opcode.IGET,
            Opcode.INVOKE_DIRECT,
            Opcode.MOVE_RESULT,
            Opcode.IGET_BOOLEAN,
            Opcode.INVOKE_DIRECT,
        ),
)

private object SpringVelocityFingerprint : Fingerprint(
    definingClass = SWIPE_NAVIGATION_CONTAINER_CLASS,
    parameters = listOf("Landroid/view/MotionEvent;", "F", "J"),
    returnType = "V",
    filters =
        OpcodesFilter.opcodesToFilters(
            Opcode.IGET_BOOLEAN,
            Opcode.IF_EQZ,
            Opcode.NEG_FLOAT,
            Opcode.INVOKE_DIRECT,
            Opcode.MOVE_RESULT_OBJECT,
            Opcode.FLOAT_TO_DOUBLE,
            Opcode.INVOKE_VIRTUAL,
        ),
)

@Suppress("unused")
val disableSwipeToCreatePatch =
    bytecodePatch(
        name = "Disable swipe to create",
        description = "Prevents opening the creation screen by swiping right on the home tab.",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val getSpringMethod =
                SpringVelocityFingerprint.instructionMatches[3]
                    .instruction
                    .getReference<MethodReference>()
                    ?.takeIf {
                        it.definingClass == SWIPE_NAVIGATION_CONTAINER_CLASS &&
                            it.name == "getSpring" &&
                            it.parameterTypes.isEmpty()
                    }
                    ?: throw PatchException("Unable to find the swipe navigation spring getter")

            val setSpringVelocityMethod =
                SpringVelocityFingerprint.instructionMatches[6]
                    .instruction
                    .getReference<MethodReference>()
                    ?.takeIf {
                        it.definingClass == getSpringMethod.returnType &&
                            it.parameterTypes.size == 1 &&
                            it.parameterTypes[0] == "D" &&
                            it.returnType == "V"
                    }
                    ?: throw PatchException("Unable to find the swipe navigation spring velocity setter")

            SetInternalPositionFingerprint.method.apply {
                val reasonField =
                    getInstruction(0)
                        .takeIf { it.opcode == Opcode.IGET_OBJECT }
                        ?.getReference<FieldReference>()
                        ?.takeIf {
                            it.definingClass == POSITION_CONFIG_CLASS &&
                                it.type == "Ljava/lang/String;"
                        }
                        ?: throw PatchException("Unable to find the swipe navigation reason field")

                val positionInstructions =
                    instructions.filter { instruction ->
                        instruction.opcode == Opcode.IGET &&
                            instruction.getReference<FieldReference>()?.let {
                                it.definingClass == POSITION_CONFIG_CLASS && it.type == "F"
                            } == true
                    }

                if (positionInstructions.size != 1) {
                    throw PatchException("Expected exactly one swipe navigation position field")
                }

                val positionInstruction = positionInstructions.single()
                val targetIndex = positionInstruction.location.index + 1
                val positionRegister =
                    (positionInstruction as? TwoRegisterInstruction)?.registerA
                        ?: throw PatchException("Unable to find the swipe navigation position field")
                val continueInstruction = getInstruction(targetIndex)

                addInstructionsWithLabels(
                    targetIndex,
                    """
                    $PREF_CALL_DESCRIPTOR->disableSwipeToCreate()Z
                    move-result v1
                    if-eqz v1, :piko_continue
                    iget-object v1, p1, $reasonField
                    const-string/jumbo v2, "swipe"
                    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :piko_continue
                    const/4 v2, 0x0
                    cmpl-float v1, v$positionRegister, v2
                    if-gez v1, :piko_continue
                    invoke-direct {p0}, $SWIPE_NAVIGATION_CONTAINER_CLASS->getClampedPosition()F
                    move-result v1
                    cmpl-float v1, v2, v1
                    if-gtz v1, :piko_continue
                    const/4 v$positionRegister, 0x0
                    invoke-direct {p0}, $getSpringMethod
                    move-result-object v1
                    const-wide/16 v2, 0x0
                    invoke-virtual {v1, v2, v3}, $setSpringVelocityMethod
                    """.trimIndent(),
                    ExternalLabel("piko_continue", continueInstruction),
                )
            }

            enableSettings("disableSwipeToCreate")
        }
    }
