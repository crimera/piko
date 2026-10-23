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
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.methodReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchFirst
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val SWIPE_NAVIGATION_CONTAINER_CLASS =
    "Lcom/instagram/ui/swipenavigation/container/SwipeNavigationContainer;"

private const val POSITION_CONFIG_CLASS =
    "Lcom/instagram/ui/swipenavigation/container/PositionConfig;"

private object SetInternalPositionFingerprint : Fingerprint(
    name = "setInternalPosition",
    definingClass = SWIPE_NAVIGATION_CONTAINER_CLASS,
    parameters = listOf(POSITION_CONFIG_CLASS),
    returnType = "V",
    filters = listOf(
        // Navigation reason.
        fieldAccess(
            definingClass = POSITION_CONFIG_CLASS,
            type = "Ljava/lang/String;",
            opcode = Opcode.IGET_OBJECT,
            location = MatchFirst(),
        ),
        // Target position.
        fieldAccess(definingClass = POSITION_CONFIG_CLASS, type = "F", opcode = Opcode.IGET),
        methodCall(definingClass = "this", parameters = listOf("F"), returnType = "F", location = MatchAfterImmediately()),
    ),
)

private object SpringVelocityFingerprint : Fingerprint(
    definingClass = SWIPE_NAVIGATION_CONTAINER_CLASS,
    parameters = listOf("Landroid/view/MotionEvent;", "F", "J"),
    returnType = "V",
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_BOOLEAN),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        opcode(Opcode.NEG_FLOAT, location = MatchAfterImmediately()),
        methodCall(definingClass = "this", name = "getSpring", parameters = listOf(), location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.FLOAT_TO_DOUBLE, location = MatchAfterImmediately()),
        // Spring velocity setter.
        methodCall(parameters = listOf("D"), returnType = "V", location = MatchAfterImmediately()),
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
            val getSpringMethod = SpringVelocityFingerprint.instructionMatches[3].methodReference
            val setSpringVelocityMethod = SpringVelocityFingerprint.instructionMatches[6].methodReference

            SetInternalPositionFingerprint.method.apply {
                val reasonField = SetInternalPositionFingerprint.instructionMatches[0].fieldReference
                val positionIndex = SetInternalPositionFingerprint.instructionMatches[1].index
                val targetIndex = positionIndex + 1
                val positionRegister = SetInternalPositionFingerprint.instructionMatches[1].getInstruction<TwoRegisterInstruction>().registerA
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
