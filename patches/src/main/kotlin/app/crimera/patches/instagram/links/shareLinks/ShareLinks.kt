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
import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

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
    ).forEach { fingerprint ->
        fingerprint.method.apply {
            val iPutObjectIndex =
                indexOfFirstInstructionOrThrow(fingerprint.stringMatches[0].index, Opcode.IPUT_OBJECT)

            addInstructions(iPutObjectIndex, hook(instructions[iPutObjectIndex].registersUsed[0]))
        }
    }

    listOfNotNull(
        StoryItemThirdPartySharingUrlResponseImplFingerprint.takeIf { STORY !in skip },
        LiveThirdPartySharingUrlResponseImplFingerprint.takeIf { LIVE !in skip },
    ).forEach { fingerprint ->
        fingerprint.method.apply {
            val returnInstruction = instructions.last { it.opcode == Opcode.RETURN_OBJECT }

            addInstructions(returnInstruction.location.index, hook(returnInstruction.registersUsed[0]))
        }
    }

    if (AUDIO !in skip) hookAudioShareLink(::hook)
    if (HIGHLIGHT !in skip) hookHighlightShareLink(::hook)
}

context(patchContext: BytecodePatchContext)
private fun hookAudioShareLink(hook: (Int) -> String) {
    val audioUrlParserMatch = AudioUrlResponseJsonParserFingerprint.matchAll(1..1).single()
    val audioUrlStringIndex = audioUrlParserMatch.stringMatches.single().index

    audioUrlParserMatch.method.apply {
        val audioUrlAssignmentIndex =
            instructions
                .withIndex()
                .filter { (index, instruction) ->
                    index > audioUrlStringIndex &&
                        instruction.opcode == Opcode.IPUT_OBJECT &&
                        ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.type ==
                        "Ljava/lang/String;"
                }.map { it.index }
                .singleOrNull()
                ?: throw PatchException("Expected one audio share URL assignment")

        addInstructions(
            audioUrlAssignmentIndex,
            hook(instructions[audioUrlAssignmentIndex].registersUsed[0]),
        )
    }
}

context(patchContext: BytecodePatchContext)
private fun hookHighlightShareLink(hook: (Int) -> String) {
    val highlightShareUrlRequestMatch = HighlightShareUrlRequestFingerprint.matchAll(1..1).single()
    val highlightCallbackType =
        highlightShareUrlRequestMatch.method.instructions
            .mapNotNull { instruction ->
                if (instruction.opcode != Opcode.NEW_INSTANCE) {
                    return@mapNotNull null
                }
                ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type
            }.singleOrNull()
            ?: throw PatchException("Expected one highlight share URL callback class")

    val highlightCallbackMethod =
        patchContext
            .mutableClassDefBy(highlightCallbackType)
            .methods
            .singleOrNull { method ->
                method.returnType == "V" &&
                    method.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;")
            } ?: throw PatchException("Expected one highlight share URL callback method")

    val highlightUrlResultIndex =
        highlightCallbackMethod.instructions
            .withIndex()
            .filter { (index, instruction) ->
                if (instruction.opcode != Opcode.INVOKE_INTERFACE) {
                    return@filter false
                }
                val methodReference =
                    (instruction as? ReferenceInstruction)?.reference as? MethodReference
                methodReference?.returnType == "Ljava/lang/String;" &&
                    highlightCallbackMethod.instructions.getOrNull(index + 1)?.opcode ==
                        Opcode.MOVE_RESULT_OBJECT
            }.map { it.index + 1 }
            .singleOrNull()
            ?: throw PatchException("Expected one highlight share URL result")

    val highlightUrlRegister =
        highlightCallbackMethod.instructions[highlightUrlResultIndex]
            .registersUsed
            .singleOrNull()
            ?: throw PatchException("Expected one highlight share URL result register")

    highlightCallbackMethod.addInstructions(
        highlightUrlResultIndex + 1,
        hook(highlightUrlRegister),
    )
}
