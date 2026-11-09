/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.fieldReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction

internal const val HIDE_LIKES = "$PREF_CALL_DESCRIPTOR->hideLikes()Z"

@Suppress("unused")
val hideLikesPatch = bytecodePatch(
    name = "Hide like buttons",
    description = "Hides like buttons on posts, reels and stories",
) {
    dependsOn(settingsPatch)
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        hideFeedLikes()
        hideReelLikes()
        hideStoryLikes()
        enableSettings("hideLikes")
    }
}

context(_: BytecodePatchContext)
private fun hideReelLikes() {
    // Each reel layout omits null children from the action bar.
    for (component in listOf("createLikeButtonComponent", "createLikeCountComponent")) {
        val factory = Fingerprint(
            returnType = "L",
            filters = listOf(string("android_purge_26_q2_ClipsUfiComponent_$component")),
        ).method
        factory.returnHiddenLikeState()
    }

    // Reject the liked-by row during selection so Instagram can pick the next
    // social context or display the timestamp on its own.
    val likedBy = Fingerprint(
        name = "<clinit>",
        filters = listOf(
            string("EFFECT_USED_BY_FRIENDS"),
            string("liked_by"),
            string("LIKED_BY", location = MatchAfterImmediately()),
            methodCall(name = "<init>", parameters = listOf("Ljava/lang/String;", "I", "Ljava/lang/String;")),
            fieldAccess(opcode = Opcode.SPUT_OBJECT, location = MatchAfterImmediately()),
        ),
    ).instructionMatches.last().fieldReference
    val selector = Fingerprint(
        returnType = "L",
        filters = listOf(
            string("android_purge_26_q3_MediaSocialContextViewUtil_getClipsViewerSocialContextModelFromMedia"),
            fieldAccess(reference = likedBy, opcode = Opcode.SGET_OBJECT),
            opcode(Opcode.IF_NE, location = MatchAfterImmediately()),
            // When the server supplies no candidates, the same selector can synthesize a liked-by row.
            opcode(Opcode.IF_EQZ),
            fieldAccess(reference = likedBy, opcode = Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
            opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
        ),
    ).match()
    val method = selector.method
    for (gate in listOf(selector.instructionMatches[3], selector.instructionMatches[2])) {
        val reject = method.getInstruction<BuilderOffsetInstruction>(gate.index).target.location.index
        // The tested candidate (or fallback eligibility result) is consumed by
        // this branch. Both successors overwrite it before its next read.
        val scratch = gate.instruction.registersUsed.first()
        method.addInstructionsWithLabels(
            gate.index + 1,
            """
            $HIDE_LIKES
            move-result v$scratch
            if-nez v$scratch, :reject
            """.trimIndent(),
            ExternalLabel("reject", method.getInstruction(reject)),
        )
    }
}

internal fun MutableMethod.returnHiddenLikeState() {
    val returnOpcode = when {
        returnType == "Z" -> "return"
        returnType.startsWith("L") -> "return-object"
        else -> throw PatchException("Unexpected like state type: $returnType")
    }
    val scratch = findFreeRegister(0)
    addInstructionsWithLabels(
        0,
        """
        $HIDE_LIKES
        move-result v$scratch
        if-eqz v$scratch, :original
        const/4 v$scratch, 0x0
        $returnOpcode v$scratch
        """.trimIndent(),
        ExternalLabel("original", getInstruction(0)),
    )
}

context(_: BytecodePatchContext)
private fun hideStoryLikes() {
    val component = Fingerprint(
        returnType = "Ljava/lang/String;",
        parameters = emptyList(),
        filters = listOf(string("StoryLikeButtonViewComponent")),
    ).originalMethod.definingClass
    val regular = Fingerprint(
        definingClass = component,
        parameters = listOf("Ljava/lang/Object;"),
        returnType = "V",
        filters = listOf(
            fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN),
            opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
            fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN, location = MatchAfterImmediately()),
            opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
            methodCall(
                opcode = Opcode.INVOKE_DIRECT,
                parameters = listOf("Lcom/instagram/ui/widget/bouncyufibutton/IgBouncyUfiButtonImageView;"),
                returnType = "V",
                location = MatchAfterImmediately(),
            ),
        ),
    ).match()
    val sponsoredBinder = Fingerprint(
        name = "<init>",
        filters = listOf(string("SponsoredStoriesLikeButtonBinder#bindView")),
    ).originalMethod.definingClass
    val sponsored = Fingerprint(
        definingClass = sponsoredBinder,
        parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
        returnType = "V",
        filters = listOf(
            opcode(Opcode.INSTANCE_OF),
            fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
            opcode(Opcode.IF_NEZ, location = MatchAfterImmediately()),
            opcode(Opcode.CONST_16, location = MatchAfterImmediately()),
            methodCall(name = "setVisibility", parameters = listOf("I"), returnType = "V", location = MatchAfterImmediately()),
            opcode(Opcode.RETURN_VOID, location = MatchAfterImmediately()),
        ),
    ).match()

    for ((binding, branch) in listOf(regular to regular.instructionMatches[1], sponsored to sponsored.instructionMatches[2])) {
        val visible = branch.instruction.registersUsed.single()
        if (binding.instructionMatches.first().instruction.registersUsed.first() != visible) {
            throw PatchException("Unexpected story like visibility branch")
        }
        binding.method.addInstructions(
            branch.index,
            """
            invoke-static {v$visible}, $PREF_DESCRIPTOR->showLikeButton(Z)Z
            move-result v$visible
            """.trimIndent(),
        )
    }
}
