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
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField

private const val REEL_VIEWER = "Linstagram/features/stories/fragment/ReelViewerFragment;"
private const val REEL_ITEM = "Lcom/instagram/model/reels/ReelItem;"
private const val STORY_BUTTON_EXTENSION_CLASS = "${PATCHES_DESCRIPTOR}/story/StoryButton;"

private object OtherStoryMenuFingerprint : Fingerprint(
    classFingerprint = SelfStoryAddStoryButtonFingerprint,
    returnType = "[Ljava/lang/CharSequence;",
    parameters = listOf("L"),
    strings = listOf("location_story_action_sheet"),
    filters = listOf(
        // The first ordinary menu entry follows the branches for recommendations and other special cards.
        opcode(Opcode.IF_EQ),
        fieldAccess(
            definingClass = "this", type = "Landroid/content/res/Resources;",
            opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately(),
        ),
        opcode(Opcode.CONST, location = MatchAfterImmediately()),
        methodCall(
            parameters = listOf("Landroid/content/res/Resources;", "Ljava/util/AbstractCollection;", "I"),
            returnType = "V", opcode = Opcode.INVOKE_STATIC, location = MatchAfterImmediately(),
        ),
    ),
)

object SelfStoryAddStoryButtonFingerprint : Fingerprint(
    returnType = "[Ljava/lang/CharSequence;",
    strings = listOf("ReelOptionsOverflowHelper"),
    filters = listOf(
        methodCall(returnType = "Ljava/util/ArrayList;", parameters = listOf(), opcode = Opcode.INVOKE_STATIC),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
    ),
)

// The method is obfuscated but this is where the onclick call executes.
internal object OnCLickStoryButtonFingerprint : Fingerprint(
    classFingerprint = SelfStoryAddStoryButtonFingerprint,
    isStatic = true,
    returnType = "V",
    filters = listOf(
        string("branded_content_click"),
        string("ReelOptionsDialog"),
        string("[INTERNAL] Pause Playback"),
    ),
)

internal object SelfStoryOnCLickStoryButtonFingerprint : Fingerprint(
    isStatic = true,
    returnType = "V",
    strings = listOf("story_interactions/bulk_story_like/", "[INTERNAL] Pause Playback"),
    filters = listOf(
        methodCall(name = "getModuleName", parameters = emptyList(), returnType = "Ljava/lang/String;",
            opcode = Opcode.INVOKE_INTERFACE),
    ),
)

private fun patchStoryDialogPlayback() {
    val pause = Fingerprint(
        definingClass = REEL_VIEWER,
        isStatic = false,
        parameters = listOf("Ljava/lang/String;"),
        returnType = "V",
        filters = listOf(string("tapped"), string("context_switch")),
    ).originalMethod
    val resume = Fingerprint(
        definingClass = REEL_VIEWER,
        isStatic = false,
        parameters = listOf("Ljava/lang/String;", "Z"),
        returnType = "V",
        filters = listOf(string("fragment_paused")),
    ).match()

    val dialogOpen = ImmutableField(REEL_VIEWER, "pikoStoryDialogOpen", "Z",
        AccessFlags.PUBLIC.value or AccessFlags.SYNTHETIC.value, null, emptySet(), emptySet())
    resume.classDef.fields.add(dialogOpen.toMutable())
    for ((name, target) in listOf("pauseViewer" to pause, "resumeViewer" to resume.originalMethod)) {
        Fingerprint(
            definingClass = STORY_BUTTON_EXTENSION_CLASS,
            name = name,
            parameters = listOf(REEL_VIEWER) + target.parameterTypes.map { it.toString() },
            returnType = "V",
        ).method.addInstruction(0, "invoke-virtual/range {p0 .. p${target.parameterTypes.size}}, $target")
    }

    // Some menu sheets retain their own dismiss listener and resume after the next dialog is shown.
    resume.method.apply {
        if (p0Register !in 1..15) throw PatchException("Unexpected story resume registers")
        addInstructionsWithLabels(0, """
            iget-boolean v0, p0, $dialogOpen
            if-eqz v0, :resume
            return-void
        """.trimIndent(), ExternalLabel("resume", getInstruction(0)))
    }
}

val handleStoryButtonPatch =
    patch(
        description = "This patch is used for handing button interaction on stories",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(decoderEntity)
        execute {

            patchStoryDialogPlayback()
            val otherStoryMenu = OtherStoryMenuFingerprint.match()
            SelfStoryAddStoryButtonFingerprint.match().let {
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

            val firstEntry = otherStoryMenu.instructionMatches.last()
            val listRegister = firstEntry.instruction.registersUsed[1]
            val mediaRead = otherStoryMenu.method.instructions.take(firstEntry.index).single {
                val field = it.getReference<FieldReference>()
                it.opcode == Opcode.IGET_OBJECT && field?.definingClass == REEL_ITEM && field.type == MEDIA_CLASS_NAME
            }
            val mediaRegister = mediaRead.registersUsed.first()
            otherStoryMenu.method.addInstructionsWithLabels(
                firstEntry.index,
                """
                if-eqz v$mediaRegister, :native_entry
                invoke-static/range {v$listRegister .. v$listRegister}, $STORY_BUTTON_EXTENSION_CLASS->addButtons(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                """.trimIndent(),
                ExternalLabel("native_entry", otherStoryMenu.method.getInstruction(firstEntry.index)),
            )

            val onClickFingerprints = listOf(SelfStoryOnCLickStoryButtonFingerprint, OnCLickStoryButtonFingerprint)
            val reelType = storyViewerItemList().definingClass
            val moduleName = SelfStoryOnCLickStoryButtonFingerprint.instructionMatches.single().methodReference
            // Both menus use the current story. Only the self-story handler still reads its media directly.
            val reelItemMediaField = SelfStoryOnCLickStoryButtonFingerprint.method.instructions
                .mapNotNull { it.getReference<FieldReference>() }
                .filter { it.definingClass == REEL_ITEM && it.type == MEDIA_CLASS_NAME }
                .distinctBy { it.toString() }
                .singleOrNull() ?: throw PatchException("Expected one story menu media field")
            onClickFingerprints.forEach { fingerprint ->
                fingerprint.method.apply {
                    val classDef = fingerprint.classDef
                    val appActivity = "Landroid/app/Activity;"
                    val charSequence = "Ljava/lang/CharSequence;"
                    val dismissListener = "Landroid/content/DialogInterface\$OnDismissListener;"

                    val classFields = classDef.fields
                    val reelItemField = classFields.single { it.type == REEL_ITEM }
                    val reelField = classDef.instanceFields.single { it.type == reelType }
                    val sessionField = classDef.instanceFields.single { it.type == USER_SESSION_CLASS }
                    val appActivityField = classFields.single { it.type == appActivity }
                    val fragmentField = classFields.single { it.type == "Landroidx/fragment/app/Fragment;" }
                    val moduleField = classDef.instanceFields.single { it.type == moduleName.definingClass }
                    val dismissListenerField = classFields.singleOrNull { it.type == dismissListener }
                        ?: throw PatchException("Expected one story menu dismiss listener field")

                    val characterSequenceParameterIndex = parameters.indexOfLast { it.type == charSequence }
                    val selfClassParameterIndex = parameters.indexOfLast { it.type == classDef.type }
                    val dismissListenerParameterIndex = parameters.indices.singleOrNull {
                        parameters[it].type == dismissListener
                    } ?: throw PatchException("Expected one story menu dismiss listener parameter")
                    if (p0Register < 9 || characterSequenceParameterIndex < 0 || selfClassParameterIndex < 0) {
                        throw PatchException("Unexpected story menu click parameters or local registers")
                    }

                    // Pack the eight arguments into v0..v7 for invoke-range and keep the menu instance in v8.
                    addInstructionsWithLabels(
                        0,
                        """
                        move-object/from16 v0, p$characterSequenceParameterIndex
                        move-object/from16 v8, p$selfClassParameterIndex
                        iget-object v1, v8, $appActivityField
                        iget-object v2, v8, $sessionField
                        iget-object v3, v8, $reelItemField
                        iget-object v3, v3, $reelItemMediaField
                        iget-object v4, v8, $reelField
                        move-object/from16 v5, p$dismissListenerParameterIndex
                        iget-object v6, v8, $fragmentField
                        iget-object v7, v8, $moduleField
                        invoke-interface {v7}, $moduleName
                        move-result-object v7
                        invoke-static/range {v0 .. v7}, $STORY_BUTTON_EXTENSION_CLASS->storyDialogAction($charSequence $appActivity ${USER_SESSION_CLASS}Ljava/lang/Object;Ljava/lang/Object;${dismissListener}Ljava/lang/Object;Ljava/lang/String;)Z
                        move-result v5
                        if-eqz v5, :other_buttons
                        const/4 v5, 0x0
                        iput-object v5, v8, $dismissListenerField
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
