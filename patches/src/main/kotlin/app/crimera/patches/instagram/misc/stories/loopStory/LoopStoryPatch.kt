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
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val REEL_VIEWER_FRAGMENT = "Linstagram/features/stories/fragment/ReelViewerFragment;"
private const val REEL_ITEM = "Lcom/instagram/model/reels/ReelItem;"

private fun storyProgressCompletedFingerprint(photoTimerField: String) = Fingerprint(
    returnType = "V",
    definingClass = REEL_VIEWER_FRAGMENT,
    strings = listOf("userSession"),
    parameters = listOf(REEL_ITEM),
    filters = listOf(
        // Photo story restart block.
        fieldAccess(name = photoTimerField, opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.CONST_4, MatchAfterImmediately()),
        opcode(Opcode.IF_NE, MatchAfterWithin(4)),
    ),
)

private object StoryVideoPlayerCastFingerprint : Fingerprint(
    definingClass = REEL_VIEWER_FRAGMENT,
    filters = listOf(
        fieldAccess(definingClass = "this", name = "mVideoPlayer", opcode = Opcode.IGET_OBJECT),
        opcode(Opcode.CHECK_CAST, MatchAfterWithin(1)),
    ),
)

private object StoryVideoPlayerInitFingerprint : Fingerprint(
    definingClass = REEL_VIEWER_FRAGMENT,
    name = "onViewCreated",
    filters = listOf(
        fieldAccess(definingClass = "this", name = "mVideoPlayer", opcode = Opcode.IPUT_OBJECT),
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
        fieldAccess(definingClass = "this", type = reelItemType, opcode = Opcode.IGET_OBJECT),
        methodCall(
            definingClass = "this",
            parameters = listOf(playerType, "I"),
            returnType = "V",
            opcode = Opcode.INVOKE_STATIC,
        ),
    ),
)

private fun storyRelativeDirectSeekFingerprint(playerType: String) = Fingerprint(
    definingClass = playerType,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("I"),
    returnType = "V",
    filters = listOf(
        // Relative seek adds the current position before calling the absolute seek helper.
        opcode(Opcode.ADD_INT_2ADDR),
        // Absolute seek helper.
        methodCall(
            definingClass = "this",
            parameters = listOf("I"),
            returnType = "V",
            opcode = Opcode.INVOKE_DIRECT,
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
            val playerCast = StoryVideoPlayerCastFingerprint.matchOrNull()
            val playerInit = if (playerCast == null) StoryVideoPlayerInitFingerprint.matchSingle() else null
            val playerField = playerCast?.instructionMatches?.first()?.fieldReference
                ?: playerInit!!.instructionMatches.single().fieldReference
            val playerTypes = playerCast?.let { listOf(it.instructionMatches.last().typeReference) }
                ?: playerInit!!.originalMethod.implementation!!.instructions
                    .mapNotNull { it.getReference<MethodReference>() }
                    .filter { it.name == "<init>" }
                    .map { it.definingClass }
                    .distinct()
                    .filter { classDefByOrNull(it)?.interfaces?.contains(playerField.type) == true }
            if (playerTypes.isEmpty()) throw PatchException("No story video player implementations")

            (storyProgressCompletedFingerprint("mPhotoTimerController").matchOrNull()
                ?: storyProgressCompletedFingerprint("photoTimerController").matchSingle()).let {
                val reelItemRegister = it.method.p0Register + 1
                val reelItemType = REEL_ITEM

                val resume = classDefBy(playerField.type).methods.singleOrNull {
                    it.parameterTypes == listOf("Ljava/lang/String;", "Z") && it.returnType == "V"
                } ?: throw PatchException("Expected one story video resume method")
                val videoRestart = playerTypes.mapIndexed { index, playerType ->
                    val legacySeek = storyRelativeSeekFingerprint(playerType, reelItemType).matchOrNull()
                    val currentItem = legacySeek?.instructionMatches?.first()?.fieldReference
                        ?: classDefBy(playerType).fields.singleOrNull {
                            it.type == reelItemType && AccessFlags.PUBLIC.isSet(it.accessFlags) &&
                                !AccessFlags.STATIC.isSet(it.accessFlags)
                        } ?: throw PatchException("Expected one public current video story field")
                    if (classDefBy(playerType).fields.none {
                            it.toString() == currentItem.toString() && AccessFlags.PUBLIC.isSet(it.accessFlags)
                        }
                    ) throw PatchException("Current video story field is not public")
                    var seekGuard = ""
                    val absoluteSeek = legacySeek?.instructionMatches?.last()?.methodReference ?: run {
                        val relativeSeek = storyRelativeDirectSeekFingerprint(playerType).matchSingle()
                        val seekHelper = relativeSeek.instructionMatches.last().methodReference
                        // Some players reject special story types before seeking.
                        val seekRestrictions = relativeSeek.originalMethod.implementation!!.instructions
                            .filter { instruction -> instruction.opcode == Opcode.INVOKE_VIRTUAL }
                            .mapNotNull { instruction -> instruction.getReference<MethodReference>() }
                            .filter { method ->
                                method.definingClass == reelItemType && method.parameterTypes.isEmpty() &&
                                    method.returnType == "Z"
                            }.distinct()
                        if (seekRestrictions.size > 1) throw PatchException("Ambiguous story seek restriction")
                        seekGuard = seekRestrictions.singleOrNull()?.let { restriction ->
                            """
                            invoke-virtual {v$reelItemRegister}, $restriction
                            move-result v1
                            if-nez v1, :piko
                            """.trimIndent()
                        }.orEmpty()
                        Fingerprint(
                            definingClass = playerType,
                            accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
                            parameters = listOf("I"),
                            returnType = "V",
                            filters = listOf(methodCall(reference = seekHelper)),
                        ).matchAll().singleOrNull { it.originalMethod.name != relativeSeek.originalMethod.name }
                            ?.originalMethod ?: throw PatchException("Expected one absolute story seek method")
                    }
                    val seekOpcode = if (legacySeek != null) "invoke-static" else "invoke-virtual"

                    """
                    instance-of v1, v0, $playerType
                    if-eqz v1, :piko_next_player_$index
                    check-cast v0, $playerType
                    iget-object v1, v0, $currentItem
                    invoke-virtual {v$reelItemRegister, v1}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :piko_photo
                    $seekGuard
                    const/4 v1, 0x0
                    $seekOpcode {v0, v1}, $absoluteSeek
                    const-string v1, "resume"
                    const/4 v2, 0x0
                    invoke-interface {v0, v1, v2}, $resume
                    goto :piko_photo
                    :piko_next_player_$index
                    """.trimIndent()
                }.joinToString("\n")

                val photoRestart = it.instructionMatches[0]
                val photoRegister = photoRestart.getInstruction<TwoRegisterInstruction>().registerA
                it.method.apply {
                    if (p0Register <= 3 || p0Register > 15 || reelItemRegister != p0Register + 1 ||
                        reelItemRegister > 15 || photoRegister != 2 ||
                        it.instructionMatches[1].getInstruction<OneRegisterInstruction>().registerA != 1 ||
                        it.instructionMatches[2].instruction.registersUsed != listOf(0, 3)
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
                        $videoRestart
                        goto :piko
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
