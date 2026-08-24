package app.crimera.patches.newx.misc.customsharingdomain

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXTextInput
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val SHARE_URL_RESOLVER_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/ShareUrlResolver;"
private const val CHANGE_DOMAIN_METHOD =
    "$SHARE_URL_RESOLVER_DESCRIPTOR->changeDomain(Ljava/lang/String;)Ljava/lang/String;"
private const val CUSTOM_DOMAIN_VALIDATOR_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/CustomSharingDomainValidator;"

/**
 * Session-token share-link builder (m57188a in 12.18.0). The share targets funnel their link
 * builds through it: it parses the URL, strips/re-appends the `s`/`t` share parameters, and
 * returns the final share URL. This is the NewX counterpart of the legacy Twitter
 * AddSessionTokenFingerprint; the parameter-name strings are const-strings from the Kotlin
 * null-checks, not debug metadata.
 */
internal object AddSessionTokenFingerprint : Fingerprint(
    parameters =
        listOf(
            "Ljava/lang/String;",
            "L",
            "Ljava/lang/String;",
        ),
    returnType = "Ljava/lang/String;",
    strings =
        listOf(
            "<this>",
            "shareParam",
            "sessionToken",
        ),
)

/**
 * URL consumers owned by the Compose share sheet. Do not hook the shared model getter: quote-post
 * composition also calls it and sends the rewritten host as `quotedPostUrl` to the post API.
 * The share sheet has its own URL consumers, so rewrite only these call sites.
 */
internal object ShareSheetPostUrlFingerprint : Fingerprint(
    definingClass = "Lcom/x/dms/components/sharesheet/",
    filters =
        listOf(
            methodCall(
                definingClass = "Lcom/x/models/",
                name = "getUrl",
                parameters = emptyList(),
                returnType = "Ljava/lang/String;",
            ),
            string("https://x.com/i/status/"),
        ),
)

@Suppress("unused")
val newXCustomSharingDomainPatch =
    bytecodePatch(
        name = "NewX: Custom sharing domain",
        description = "Allows for using domains like fxtwitter when sharing tweets/posts.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        newXTextInput(
            id = "newx.content.custom_sharing_domain",
            category = Categories.CONTENT,
            strings = settingStrings("piko_newx_custom_sharing_domain"),
            order = 500,
            defaultValue = "",
            validatorClassDescriptor = CUSTOM_DOMAIN_VALIDATOR_DESCRIPTOR,
        )

        dependsOn(newXExtensionPatch)

        execute {
            hookShareLinkBuilder()
            hookShareSheetPostUrls()
        }
    }

context(_: app.morphe.patcher.patch.BytecodePatchContext)
private fun hookShareLinkBuilder() {
    val matches = AddSessionTokenFingerprint.scopedMatchAll()
    if (matches.size != 1) {
        throw PatchException(
            "Expected one NewX share-link builder match, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }

    matches.single().method.addInstructions(
        0,
        """
        invoke-static {p0}, $CHANGE_DOMAIN_METHOD
        move-result-object p0
        """.trimIndent(),
    )
}

context(_: app.morphe.patcher.patch.BytecodePatchContext)
private fun hookShareSheetPostUrls() {
    val matches = ShareSheetPostUrlFingerprint.scopedMatchAll()
    if (matches.size != 2) {
        throw PatchException(
            "Expected two NewX share-sheet URL consumer matches, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }

    matches.forEach { match ->
        val method = match.method
        val urlCallIndices =
            method.instructions.mapIndexedNotNull { index, instruction ->
                val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
                if (
                    instruction.opcode != Opcode.INVOKE_INTERFACE &&
                        instruction.opcode != Opcode.INVOKE_INTERFACE_RANGE &&
                        instruction.opcode != Opcode.INVOKE_VIRTUAL &&
                        instruction.opcode != Opcode.INVOKE_VIRTUAL_RANGE
                ) {
                    return@mapIndexedNotNull null
                }
                if (
                    !reference.definingClass.startsWith("Lcom/x/models/") ||
                        reference.name != "getUrl" ||
                        reference.parameterTypes.isNotEmpty() ||
                        reference.returnType != "Ljava/lang/String;"
                ) {
                    return@mapIndexedNotNull null
                }
                index
            }
        if (urlCallIndices.isEmpty()) {
            throw PatchException("Expected a NewX share-sheet post URL call in $method")
        }

        urlCallIndices.asReversed().forEach { callIndex ->
            val resultIndex = callIndex + 1
            val resultInstruction = method.instructions.getOrNull(resultIndex)
            if (resultInstruction?.opcode != Opcode.MOVE_RESULT_OBJECT) {
                throw PatchException(
                    "Expected getUrl() to be followed by move-result-object in $method at " +
                        "instruction $callIndex",
                )
            }
            val register = (resultInstruction as OneRegisterInstruction).registerA
            val invokeOpcode = if (register <= 15) "invoke-static" else "invoke-static/range"
            val registerRange = if (register <= 15) "{v$register}" else "{v$register .. v$register}"
            method.addInstructions(
                resultIndex + 1,
                """
                $invokeOpcode $registerRange, $CHANGE_DOMAIN_METHOD
                move-result-object v$register
                """.trimIndent(),
            )
        }
    }
}
