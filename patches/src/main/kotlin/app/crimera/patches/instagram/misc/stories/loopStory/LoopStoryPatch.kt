/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.stories.loopStory

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.methodReference
import app.crimera.patches.instagram.utils.typeReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val REEL_VIEWER_FRAGMENT = "Linstagram/features/stories/fragment/ReelViewerFragment;"

internal object StoryProgressCompletedFingerprint : Fingerprint(
    returnType = "V",
    definingClass = REEL_VIEWER_FRAGMENT,
    strings = listOf("userSession"),
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        // Story item cast.
        opcode(Opcode.CHECK_CAST),
        // Photo story restart block.
        fieldAccess(name = "mPhotoTimerController", opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.CONST_4, MatchAfterImmediately()),
        opcode(Opcode.IF_NE, MatchAfterWithin(4)),
    ),
)

private object StoryVideoPlayerCastFingerprint : Fingerprint(
    definingClass = REEL_VIEWER_FRAGMENT,
    filters = listOf(
        fieldAccess(definingClass = "this", name = "mVideoPlayer", opcode = Opcode.IGET_OBJECT),
        // Concrete video player.
        opcode(Opcode.CHECK_CAST, MatchAfterWithin(1)),
    ),
)

/**
 * Relative seek method of the concrete story video player, which seeks with the absolute seek helper.
 */
private fun storyRelativeSeekFingerprint(playerType: String, reelItemType: String) = Fingerprint(
    definingClass = playerType,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("I"),
    returnType = "V",
    filters = listOf(
        // Current video story.
        fieldAccess(definingClass = "this", type = reelItemType, opcode = Opcode.IGET_OBJECT),
        // Absolute seek helper.
        methodCall(
            definingClass = "this",
            parameters = listOf(playerType, "I"),
            returnType = "V",
            opcode = Opcode.INVOKE_STATIC,
        ),
    ),
)

@Suppress("unused")
val loopStoryPatch =
    bytecodePatch(
        name = "Loop story",
        description = "Replay the current story when it ends",
    ) {
        dependsOn(settingsPatch)

        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            val playerCast = StoryVideoPlayerCastFingerprint.instructionMatches
            val playerField = playerCast.first().fieldReference
            val playerType = playerCast.last().typeReference

            StoryProgressCompletedFingerprint.let {
                val entryCast = it.instructionMatches.first()
                val reelItemRegister = entryCast.instruction.registersUsed[0]
                val reelItemType = entryCast.typeReference

                val seekMatch = storyRelativeSeekFingerprint(playerType, reelItemType).match()
                val currentItem = seekMatch.instructionMatches.first().fieldReference
                val absoluteSeek = seekMatch.instructionMatches.last().methodReference
                if (classDefBy(playerType).fields.none {
                        it.toString() == currentItem.toString() && AccessFlags.PUBLIC.isSet(it.accessFlags)
                    }
                ) throw PatchException("Current video story field is not public")
                val resume = classDefBy(playerField.type).methods.singleOrNull {
                    it.parameterTypes == listOf("Ljava/lang/String;", "Z") && it.returnType == "V"
                } ?: throw PatchException("Expected one story video resume method")

                val photoRestart = it.instructionMatches[1]
                val photoRegister = photoRestart.getInstruction<TwoRegisterInstruction>().registerA
                it.method.apply {
                    if (p0Register <= 3 || p0Register > 15 || reelItemRegister != p0Register + 1 ||
                        reelItemRegister > 15 || photoRegister != 2 ||
                        it.instructionMatches[2].getInstruction<OneRegisterInstruction>().registerA != 1 ||
                        it.instructionMatches[3].instruction.registersUsed != listOf(0, 3)
                    ) throw PatchException("Unexpected story photo restart registers")

                    addInstructionsWithLabels(
                        0,
                        """
                        ${PREF_CALL_DESCRIPTOR}->loopStory()Z
                        move-result v0
                        if-eqz v0, :piko
                        check-cast v$reelItemRegister, $reelItemType
                        iget-object v0, p0, $playerField
                        if-eqz v0, :piko_photo
                        instance-of v1, v0, $playerType
                        if-eqz v1, :piko
                        check-cast v0, $playerType
                        iget-object v1, v0, $currentItem
                        invoke-virtual {v$reelItemRegister, v1}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z
                        move-result v1
                        if-eqz v1, :piko_photo
                        const/4 v1, 0x0
                        invoke-static {v0, v1}, $absoluteSeek
                        const-string v1, "resume"
                        const/4 v2, 0x0
                        invoke-interface {v0, v1, v2}, $resume
                        :piko_photo
                        const/4 v3, 0x1
                        goto :piko_loop
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(0)),
                        ExternalLabel("piko_loop", getInstruction(photoRestart.index)),
                    )
                }
            }
            enableSettings("loopStory")
        }
    }
