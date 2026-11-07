/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.stories

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.download.storyViewerItemList
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

// Story menu output list allocation.
private val STORY_MENU_LIST_FILTERS = listOf(
    methodCall(returnType = "Ljava/util/ArrayList;", parameters = listOf(), opcode = Opcode.INVOKE_STATIC),
    opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
)

// The method is obfuscated but this is where it adds buttons.
object AddStoryButtonFingerprint : Fingerprint(
    returnType = "[Ljava/lang/CharSequence;",
    parameters = listOf("L"),
    strings = listOf("[INTERNAL] Pause Playback"),
    filters = STORY_MENU_LIST_FILTERS,
)

private object ExtractedStoryButtonFingerprint : Fingerprint(
    classFingerprint = SelfStoryAddStoryButtonFingerprint,
    returnType = "[Ljava/lang/CharSequence;",
    parameters = listOf("L"),
    strings = listOf("location_story_action_sheet"),
    filters = STORY_MENU_LIST_FILTERS,
)

object SelfStoryAddStoryButtonFingerprint : Fingerprint(
    returnType = "[Ljava/lang/CharSequence;",
    strings = listOf("ReelOptionsOverflowHelper"),
    filters = STORY_MENU_LIST_FILTERS,
)

// The method is obfuscated but this is where the onclick call executes.
internal object OnCLickStoryButtonFingerprint : Fingerprint(
    isStatic = true,
    returnType = "V",
    strings = listOf("explore_viewer", "friendships/mute_friend_reel/%s/", "[INTERNAL] Pause Playback"),
)

internal object SelfStoryOnCLickStoryButtonFingerprint : Fingerprint(
    isStatic = true,
    returnType = "V",
    strings = listOf("story_interactions/bulk_story_like/", "[INTERNAL] Pause Playback"),
)

val handleStoryButtonPatch =
    bytecodePatch(
        description = "This patch is used for handing button interaction on stories",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(decoderEntity)
        execute {

            val STORY_BUTTON_EXTENSION_CLASS = "${PATCHES_DESCRIPTOR}/story/StoryButton;"
            listOf(
                SelfStoryAddStoryButtonFingerprint.match(),
                AddStoryButtonFingerprint.matchOrNull() ?: ExtractedStoryButtonFingerprint.match(),
            ).forEach {
                val listIndex = it.instructionMatches.last().index
                val arrayListRegister = it.instructionMatches.last().instruction.registersUsed.single()

                it.method.addInstructions(
                    listIndex + 1,
                    """
                    invoke-static/range {v$arrayListRegister .. v$arrayListRegister}, $STORY_BUTTON_EXTENSION_CLASS->addButtons(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                    move-result-object v$arrayListRegister
                    """.trimIndent(),
                )
            }

            val onClickFingerprints = listOf(SelfStoryOnCLickStoryButtonFingerprint, OnCLickStoryButtonFingerprint)
            val reelType = storyViewerItemList().definingClass
            onClickFingerprints.forEach { fingerprint ->
                fingerprint.method.apply {
                    val classDef = fingerprint.classDef
                    val reelItemClassName = "Lcom/instagram/model/reels/ReelItem;"
                    val appActivity = "Landroid/app/Activity;"
                    val charSequence = "Ljava/lang/CharSequence;"
                    val dismissListener = "Landroid/content/DialogInterface\$OnDismissListener;"

                    val classFields = classDef.fields
                    val reelItemField = classFields.single { it.type == reelItemClassName }
                    val reelField = classDef.instanceFields.single { it.type == reelType }
                    val sessionField = classDef.instanceFields.single { it.type == USER_SESSION_CLASS }
                    val appActivityField = classFields.single { it.type == appActivity }
                    val dismissListenerField = classFields.singleOrNull { it.type == dismissListener }
                        ?: throw PatchException("Expected one story menu dismiss listener field")
                    val reelItemMediaField = instructions.mapNotNull { it.getReference<FieldReference>() }
                        .filter { it.definingClass == reelItemClassName && it.type == MEDIA_CLASS_NAME }
                        .distinctBy { it.toString() }
                        .singleOrNull() ?: throw PatchException("Expected one story menu media field")

                    val characterSequenceParameterIndex = parameters.indexOfLast { it.type == charSequence }
                    val selfClassParameterIndex = parameters.indexOfLast { it.type == classDef.type }
                    val dismissListenerParameterIndex = parameters.indices.singleOrNull {
                        parameters[it].type == dismissListener
                    } ?: throw PatchException("Expected one story menu dismiss listener parameter")
                    if (p0Register < 7 || characterSequenceParameterIndex < 0 || selfClassParameterIndex < 0) {
                        throw PatchException("Unexpected story menu click parameters or local registers")
                    }

                    // At method entry, v0..v5 hold the contiguous invoke-range arguments and v6 keeps the menu instance.
                    addInstructionsWithLabels(
                        0,
                        """
                        move-object/from16 v0, p$characterSequenceParameterIndex
                        move-object/from16 v6, p$selfClassParameterIndex
                        iget-object v1, v6, $appActivityField
                        iget-object v2, v6, $sessionField
                        iget-object v3, v6, $reelItemField
                        iget-object v3, v3, $reelItemMediaField
                        iget-object v4, v6, $reelField
                        move-object/from16 v5, p$dismissListenerParameterIndex
                        invoke-static/range {v0 .. v5}, $STORY_BUTTON_EXTENSION_CLASS->storyDialogAction($charSequence Landroid/content/Context;${USER_SESSION_CLASS}Ljava/lang/Object;Ljava/lang/Object;$dismissListener)Z
                        move-result v5
                        if-eqz v5, :other_buttons
                        const/4 v5, 0x0
                        iput-object v5, v6, $dismissListenerField
                        return-void
                        :other_buttons
                        invoke-static {v0,v1,v3}, $STORY_BUTTON_EXTENSION_CLASS->storyButtonAction($charSequence Landroid/content/Context;Ljava/lang/Object;)Z
                        move-result v0
                        if-eqz v0, :piko
                        return-void
                        """.trimIndent(),
                        ExternalLabel("piko", getInstruction(0)),
                    )
                }
            }
        }
    }
