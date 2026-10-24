/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.shareLinks

import app.crimera.patches.instagram.links.shareLinks.ShareLinkKind.AUDIO
import app.crimera.patches.instagram.links.shareLinks.ShareLinkKind.HIGHLIGHT
import app.crimera.patches.instagram.links.shareLinks.ShareLinkKind.LIVE
import app.crimera.patches.instagram.links.shareLinks.ShareLinkKind.POST
import app.crimera.patches.instagram.links.shareLinks.ShareLinkKind.PROFILE
import app.crimera.patches.instagram.links.shareLinks.ShareLinkKind.STORY
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal val shareLinksPatch =
    bytecodePatch {
        dependsOn(settingsPatch)

        execute {
            hookShareLinks("processSharedUrl", skip = setOf(PROFILE, AUDIO, HIGHLIGHT))
            hookShareLinks("sanitizeSharedUrl", skip = setOf(POST, STORY, LIVE))
        }
    }

internal enum class ShareLinkKind {
    POST,
    PROFILE,
    STORY,
    LIVE,
    AUDIO,
    HIGHLIGHT,
}

/**
 * Routes every share URL the app hands out through [extensionMethodName] on `Links`.
 *
 * @param extensionMethodName Name of a `String -> String` method on the `Links` extension class.
 * @param skip Kinds of share link to leave untouched.
 */
context(patchContext: BytecodePatchContext)
internal fun hookShareLinks(
    extensionMethodName: String,
    skip: Set<ShareLinkKind> = emptySet(),
) {
    fun hook(urlRegister: Int) =
        """
        invoke-static/range { v$urlRegister .. v$urlRegister }, $LINKS_DESCRIPTOR->$extensionMethodName(Ljava/lang/String;)Ljava/lang/String;
        move-result-object v$urlRegister
        """.trimIndent()

    listOfNotNull(
        PermalinkResponseJsonParserFingerprint.takeIf { POST !in skip },
        ProfileUrlResponseJsonParserFingerprint.takeIf { PROFILE !in skip },
        AudioUrlResponseJsonParserFingerprint.takeIf { AUDIO !in skip },
        StoryItemThirdPartySharingUrlResponseImplFingerprint.takeIf { STORY !in skip },
        LiveThirdPartySharingUrlResponseImplFingerprint.takeIf { LIVE !in skip },
    ).forEach { fingerprint ->
        val match = fingerprint.matchSingle()
        val urlUse = match.instructionMatches.single {
            it.instruction.opcode == Opcode.IPUT_OBJECT || it.instruction.opcode == Opcode.RETURN_OBJECT
        }
        match.method.addInstructions(urlUse.index, hook(urlUse.instruction.registersUsed.first()))
    }

    if (HIGHLIGHT !in skip) {
        val callbackType = HighlightShareUrlRequestFingerprint.instructionMatches.first()
            .instruction.getReference<TypeReference>()!!.type
        highlightShareUrlCallbackFingerprint(callbackType).let {
            val resultIndex = it.instructionMatches.last().index
            val urlRegister = it.instructionMatches.last().getInstruction<OneRegisterInstruction>().registerA
            it.method.addInstructions(resultIndex + 1, hook(urlRegister))
        }
    }
}
