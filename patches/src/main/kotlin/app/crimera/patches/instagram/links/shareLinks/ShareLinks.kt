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
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal val shareLinksPatch =
    patch {
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
        LiveThirdPartySharingUrlResponseImplFingerprint.takeIf { LIVE !in skip },
        PandoLiveShareUrlGetterFingerprint.takeIf { LIVE !in skip },
    ).forEach { fingerprint ->
        val urlUse = fingerprint.instructionMatches.single {
            it.instruction.opcode == Opcode.IPUT_OBJECT || it.instruction.opcode == Opcode.RETURN_OBJECT
        }
        fingerprint.method.addInstructions(urlUse.index, hook(urlUse.instruction.registersUsed.first()))
    }

    if (LIVE !in skip) {
        LiveShareUrlCallbackFingerprint.matchAll(3..3).forEach { match ->
            val result = match.instructionMatches.last()
            match.method.addInstructions(result.index + 1, hook(result.instruction.registersUsed.single()))
        }
        val event = LiveShareUrlEventFingerprint.match()
        val matches = event.instructionMatches
        val checkedUrl = matches[1]
        val eventUrl = matches[5]
        if (checkedUrl.methodReference != eventUrl.methodReference ||
            checkedUrl.instruction.registersUsed != eventUrl.instruction.registersUsed
        ) {
            throw PatchException("Expected the Live options dialog to deliver the same URL it checked")
        }
        val result = matches.last()
        event.method.addInstructions(result.index + 1, hook(result.instruction.registersUsed.single()))
    }

    if (STORY !in skip) {
        val getter = StoryItemThirdPartySharingUrlResponseImplFingerprint.matchOrNull()
        if (getter != null) {
            val result = getter.instructionMatches.single()
            getter.method.addInstructions(result.index, hook(result.instruction.registersUsed.single()))
        } else {
            // On 456 the six sharing paths read the URL directly from the parsed response.
            StoryItemShareUrlReadFingerprint.matchAll(6..6).forEach { match ->
                val result = match.instructionMatches.last()
                match.method.addInstructions(result.index + 1, hook(result.instruction.registersUsed.single()))
            }
        }
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
