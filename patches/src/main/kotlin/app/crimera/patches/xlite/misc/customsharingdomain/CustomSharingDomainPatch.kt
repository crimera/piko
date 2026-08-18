package app.crimera.patches.xlite.misc.customsharingdomain

import app.crimera.patches.xlite.misc.extension.xLiteExtensionPatch
import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteTextInput
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val SHARE_URL_RESOLVER_DESCRIPTOR =
    "Lapp/morphe/extension/xlite/misc/ShareUrlResolver;"
private const val CHANGE_DOMAIN_METHOD =
    "$SHARE_URL_RESOLVER_DESCRIPTOR->changeDomain(Ljava/lang/String;)Ljava/lang/String;"
private const val CUSTOM_DOMAIN_VALIDATOR_DESCRIPTOR =
    "Lapp/morphe/extension/xlite/misc/CustomSharingDomainValidator;"

/**
 * Session-token share-link builder (m57188a in 12.18.0). The share targets funnel their link
 * builds through it: it parses the URL, strips/re-appends the `s`/`t` share parameters, and
 * returns the final share URL. This is the X-Lite counterpart of the legacy Twitter
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
 * Compose share-sheet URL builder. The Decompose share sheet (`com.x.dms.components.sharesheet`)
 * does not use the session-token method: it reads the post URL from `PostResult.getUrl()`, whose
 * default implementation lives on the obfuscated interface `com.x.models.f0` and builds
 * "https://x.com/<screenName>/status/<id>" (or the unavailable variant). Hooking this method
 * covers every model-derived share URL (share sheet, DM attachments, copy link) before the
 * session-token enrichment in AddSessionTokenFingerprint runs.
 */
internal object PostResultUrlFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/",
    name = "getUrl",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    strings =
        listOf(
            "https://x.com/",
            "/status/",
        ),
)

@Suppress("unused")
val xLiteCustomSharingDomainPatch =
    bytecodePatch(
        name = "X-Lite: Custom sharing domain",
        description = "Allows for using domains like fxtwitter when sharing tweets/posts.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        xLiteTextInput(
            id = "xlite.content.custom_sharing_domain",
            category = Categories.CONTENT,
            strings = settingStrings("piko_xlite_custom_sharing_domain"),
            order = 500,
            defaultValue = "",
            validatorClassDescriptor = CUSTOM_DOMAIN_VALIDATOR_DESCRIPTOR,
        )

        dependsOn(xLiteExtensionPatch)

        execute {
            hookShareLinkBuilder()
            hookComposeShareSheetUrlBuilder()
        }
    }

context(_: app.morphe.patcher.patch.BytecodePatchContext)
private fun hookShareLinkBuilder() {
    val matches = AddSessionTokenFingerprint.scopedMatchAll()
    if (matches.size != 1) {
        throw PatchException(
            "Expected one X-Lite share-link builder match, found ${matches.size}: " +
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
private fun hookComposeShareSheetUrlBuilder() {
    val matches = PostResultUrlFingerprint.scopedMatchAll()
    if (matches.size != 1) {
        throw PatchException(
            "Expected one X-Lite PostResult URL builder match, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }

    val method = matches.single().method
    val returnIndices =
        method.instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode != Opcode.RETURN_OBJECT) return@mapIndexedNotNull null
            val register = (instruction as? OneRegisterInstruction)?.registerA
            if (register != 0) return@mapIndexedNotNull null
            index
        }
    if (returnIndices.size != 2) {
        throw PatchException(
            "Expected two return-object v0 sites in the X-Lite PostResult URL builder, " +
                "found ${returnIndices.size}",
        )
    }

    // Insert before each return site, highest index first so earlier indices stay valid.
    returnIndices.asReversed().forEach { index ->
        method.addInstructions(
            index,
            """
            invoke-static {v0}, $CHANGE_DOMAIN_METHOD
            move-result-object v0
            """.trimIndent(),
        )
    }
}
