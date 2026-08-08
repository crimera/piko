package app.crimera.patches.xlite.misc.canonicalurls

import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.instanceOf
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val URL_ENTITY = "Lcom/x/models/text/UrlEntity;"
private const val CONTEXTUAL_POST = "Lcom/x/models/ContextualPost;"
private const val POST_IDENTIFIER = "Lcom/x/models/PostIdentifier;"
private const val URI = "Landroid/net/Uri;"

private const val POST_URL_READ_FILTER_INDEX = 4
private const val TEXT_ENTITY_URL_READ_FILTER_INDEX = 5

/**
 * The Compose timeline post event handler.
 *
 * The target is identified by the stable URL-entity → URI → post-ID chain used by the
 * actual external-link open. Event, coroutine, presenter, and navigation descriptors are
 * obfuscated and deliberately represented by `L` wildcards or omitted.
 */
private object TimelinePostLinkClickFingerprint : Fingerprint(
    returnType = "V",
    parameters =
        listOf(
            "L",
            CONTEXTUAL_POST,
            "L",
            "L",
            "L",
            "L",
            "L",
        ),
    filters =
        listOf(
            // Promoted URL-click scribing reads the short URL before the open path.
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = URL_ENTITY,
                name = "getUrl",
                parameters = emptyList(),
                returnType = "Ljava/lang/String;",
            ),
            // External-link handling starts from the expanded URL.
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = URL_ENTITY,
                name = "getExpandedUrl",
                parameters = emptyList(),
                returnType = "Ljava/lang/String;",
            ),
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                definingClass = URI,
                name = "parse",
                parameters = listOf("Ljava/lang/String;"),
                returnType = URI,
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = URI,
                name = "getAuthority",
                parameters = emptyList(),
                returnType = "Ljava/lang/String;",
            ),
            // This is the URL consumed by the external-link open, not the earlier scribe read.
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = URL_ENTITY,
                name = "getUrl",
                parameters = emptyList(),
                returnType = "Ljava/lang/String;",
            ),
            opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = CONTEXTUAL_POST,
                name = "getId",
                parameters = emptyList(),
                returnType = POST_IDENTIFIER,
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = POST_IDENTIFIER,
                name = "getValue",
                parameters = emptyList(),
                returnType = "J",
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = URL_ENTITY,
                name = "getExpandedUrl",
                parameters = emptyList(),
                returnType = "Ljava/lang/String;",
            ),
        ),
)

/**
 * The Compose navigation helper that opens a clicked text entity.
 *
 * The stable rich-text type sequence identifies the method without depending on the
 * obfuscated navigation interface, entity base class, or method name.
 */
private object TextEntityNavigationFingerprint : Fingerprint(
    parameters = listOf("L", "L"),
    returnType = "V",
    filters =
        listOf(
            instanceOf("Lcom/x/models/text/MentionEntity;"),
            instanceOf(URL_ENTITY),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = URL_ENTITY,
                name = "getExpandedUrl",
                parameters = emptyList(),
                returnType = "Ljava/lang/String;",
            ),
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                definingClass = URI,
                name = "parse",
                parameters = listOf("Ljava/lang/String;"),
                returnType = URI,
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = URI,
                name = "getAuthority",
                parameters = emptyList(),
                returnType = "Ljava/lang/String;",
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = URL_ENTITY,
                name = "getUrl",
                parameters = emptyList(),
                returnType = "Ljava/lang/String;",
            ),
            opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        ),
)

/**
 * The URL picker in the same navigation helper class as TextEntityNavigationFingerprint.
 * The class fingerprint removes the release-specific obfuscated owner descriptor.
 */
private object TimelineUrlPickerFingerprint : Fingerprint(
    classFingerprint = TextEntityNavigationFingerprint,
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    returnType = "Ljava/lang/String;",
    filters =
        listOf(
            methodCall(
                opcode = Opcode.INVOKE_STATIC,
                definingClass = URI,
                name = "parse",
                parameters = listOf("Ljava/lang/String;"),
                returnType = URI,
            ),
            methodCall(
                opcode = Opcode.INVOKE_VIRTUAL,
                definingClass = URI,
                name = "getAuthority",
                parameters = emptyList(),
                returnType = "Ljava/lang/String;",
            ),
        ),
)

/**
 * Opens the canonical (expanded) URL directly when a link is clicked anywhere in the
 * Compose UI, instead of letting Twitter resolve the shortened `t.co` link first.
 *
 * Only the Compose / URT navigation path is touched; the legacy view-based UI
 * (tweetview, `com.twitter.navigation.timeline.TimelineUrlLauncher`) is left intact.
 */
@Suppress("unused")
val xLiteCanonicalUrlsPatch =
    bytecodePatch(
        name = "X-Lite: Open canonical URLs",
        description =
            "Opens the expanded (canonical) URL directly when clicking links instead of the shortened t.co link.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        execute {
            openExpandedUrlInPostPresenter()

            // Resolve the navigation class once. The URL-picker fingerprint reuses this
            // cached class match before the text-entity method is mutated.
            val textEntityNavigationMatch = TextEntityNavigationFingerprint.requireSingleMatch()
            val urlPickerMatch = TimelineUrlPickerFingerprint.requireSingleMatch()
            preferExpandedUrlInUrlPicker(urlPickerMatch)
            replaceUrlEntityRead(textEntityNavigationMatch, TEXT_ENTITY_URL_READ_FILTER_INDEX)
        }
    }

private fun BytecodePatchContext.openExpandedUrlInPostPresenter() {
    val match = TimelinePostLinkClickFingerprint.requireSingleMatch()
    replaceUrlEntityRead(match, POST_URL_READ_FILTER_INDEX)
}

private fun replaceUrlEntityRead(match: Match, filterIndex: Int) {
    val method = match.method
    val urlReadIndex = match.instructionMatches[filterIndex].index
    val urlRead = method.instructions[urlReadIndex] as? FiveRegisterInstruction
        ?: throw PatchException("Expected an encoded UrlEntity getter at instruction $urlReadIndex")

    method.replaceInstruction(
        urlReadIndex,
        "invoke-virtual {v${urlRead.registerC}}, $URL_ENTITY->getExpandedUrl()Ljava/lang/String;",
    )
}

private fun BytecodePatchContext.preferExpandedUrlInUrlPicker(match: Match) {
    val method = match.method

    // `e(url, expanded)`: return the expanded URL as soon as it is present,
    // falling back to the original (short) URL otherwise.
    val firstInstruction = method.instructions.first()
    method.addInstructionsWithLabels(
        0,
        """
        if-eqz p1, :piko_canonical_url_fallback
        return-object p1
        """.trimIndent(),
        ExternalLabel("piko_canonical_url_fallback", firstInstruction),
    )
}

context(_: BytecodePatchContext)
private fun Fingerprint.requireSingleMatch(): Match {
    val matches = matchAll()
    return matches.singleOrNull()
        ?: throw PatchException(
            "Expected exactly one match, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
}
