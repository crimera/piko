/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.link.customsharingdomain

import app.crimera.patches.twitter.link.cleartrackingparams.AddSessionTokenFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val INTENT_DESCRIPTOR = "Landroid/content/Intent;"
private const val SHARE_STATUS_URL_PREFIX = "https://x.com/i/status/"
private const val SHARE_SHEET_DESCRIPTOR_PREFIX = "Lcom/x/dms/components/sharesheet/"
private const val SHARE_IMPL_DESCRIPTOR_PREFIX = "Lcom/x/share/impl/"
private const val NAVIGATION_DESCRIPTOR_PREFIX = "Lcom/x/navigation/"

private const val CHANGE_DOMAIN_METHOD =
    "$PATCHES_DESCRIPTOR/links/Urls;->changeDomain(Ljava/lang/String;)Ljava/lang/String;"

/** Final URL state used by copy, external-app, and system-share actions. */
internal object ShareSheetUrlConstructorFingerprint : Fingerprint(
    definingClass = SHARE_SHEET_DESCRIPTOR_PREFIX,
    returnType = "V",
    filters = listOf(string(SHARE_STATUS_URL_PREFIX)),
)

/** Common Intent builder used by the system and per-app share actions. */
internal object ShareIntentFingerprint : Fingerprint(
    definingClass = SHARE_IMPL_DESCRIPTOR_PREFIX,
    parameters = listOf(STRING_DESCRIPTOR, STRING_DESCRIPTOR),
    returnType = INTENT_DESCRIPTOR,
    filters = listOf(
        string("android.intent.action.SEND"),
        string("android.intent.extra.TEXT"),
    ),
)

/** Post-detail navigation URL builder. */
internal object PostNavigationUrlFingerprint : Fingerprint(
    definingClass = NAVIGATION_DESCRIPTOR_PREFIX,
    parameters = emptyList(),
    returnType = STRING_DESCRIPTOR,
    filters = listOf(
        string(SHARE_STATUS_URL_PREFIX),
        opcode(Opcode.INVOKE_STATIC, MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        opcode(Opcode.RETURN_OBJECT, MatchAfterImmediately()),
    ),
)

@Suppress("unused")
val customSharingDomainPatch =
    bytecodePatch(
        name = "Custom sharing domain",
        description = "Allows for using domains like fxtwitter when sharing tweets/posts.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)
        execute {
            hookLegacyShareLinkBuilder()
            hookShareSheetUrl()
            hookShareIntent()
            hookPostNavigationUrl()
            enableSettings("enableCustomSharingDomain")
        }
    }

context(_: BytecodePatchContext)
private fun Fingerprint.requireSingleMatch(label: String): Match {
    val matches = scopedMatchAllOrNull().orEmpty()
    if (matches.size != 1) {
        throw PatchException(
            "Expected one $label match, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }
    return matches.single()
}

context(_: BytecodePatchContext)
private fun hookLegacyShareLinkBuilder() {
    val matches = AddSessionTokenFingerprint.scopedMatchAllOrNull().orEmpty()
    if (matches.size > 1) {
        throw PatchException(
            "Expected at most one legacy share-link builder, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }

    matches.singleOrNull()?.method?.addInstructions(
        0,
        """
        invoke-static {p0}, $CHANGE_DOMAIN_METHOD
        move-result-object p0
        """.trimIndent(),
    )
}

context(context: BytecodePatchContext)
private fun hookShareSheetUrl() {
    val method = ShareSheetUrlConstructorFingerprint.requireSingleMatch("share-sheet URL constructor").method
    val statusResultIndex = method.findStatusUrlResultIndex()
    val fieldStoreIndices =
        method.instructions.mapIndexedNotNull { index, instruction ->
            if (index <= statusResultIndex || instruction.opcode != Opcode.IPUT_OBJECT) {
                return@mapIndexedNotNull null
            }
            val field = instruction.getReference<FieldReference>() ?: return@mapIndexedNotNull null
            if (field.definingClass != method.definingClass || field.type != STRING_DESCRIPTOR) {
                return@mapIndexedNotNull null
            }
            index
        }

    if (fieldStoreIndices.size != 1) {
        throw PatchException(
            "Expected one final share URL field store in $method, found ${fieldStoreIndices.size}",
        )
    }

    val fieldStoreIndex = fieldStoreIndices.single()
    val fieldStore = method.instructions[fieldStoreIndex] as? TwoRegisterInstruction
        ?: throw PatchException("Expected a two-register final share URL field store in $method")
    method.addDomainRewrite(fieldStoreIndex, fieldStore.registerA)
}

private fun MutableMethod.findStatusUrlResultIndex(): Int {
    val resultIndices =
        instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode != Opcode.CONST_STRING && instruction.opcode != Opcode.CONST_STRING_JUMBO) {
                return@mapIndexedNotNull null
            }
            val reference = instruction.getReference<StringReference>() ?: return@mapIndexedNotNull null
            if (reference.string != SHARE_STATUS_URL_PREFIX) return@mapIndexedNotNull null

            val builderIndex = index + 1
            val builderOpcode = instructions.getOrNull(builderIndex)?.opcode
            if (builderOpcode != Opcode.INVOKE_STATIC && builderOpcode != Opcode.INVOKE_STATIC_RANGE) {
                throw PatchException(
                    "Expected status URL prefix to be followed by a static builder invoke in $this at " +
                        "instruction $index",
                )
            }
            val resultIndex = index + 2
            if (instructions.getOrNull(resultIndex)?.opcode != Opcode.MOVE_RESULT_OBJECT) {
                throw PatchException(
                    "Expected status URL builder to be followed by move-result-object in $this at " +
                        "instruction $builderIndex",
                )
            }
            resultIndex
        }

    if (resultIndices.size != 1) {
        throw PatchException("Expected one status URL result in $this, found ${resultIndices.size}")
    }
    return resultIndices.single()
}

context(context: BytecodePatchContext)
private fun hookShareIntent() {
    ShareIntentFingerprint.requireSingleMatch("share Intent builder").method.addInstructions(
        0,
        """
        invoke-static {p0}, $CHANGE_DOMAIN_METHOD
        move-result-object p0
        """.trimIndent(),
    )
}

context(context: BytecodePatchContext)
private fun hookPostNavigationUrl() {
    val match = PostNavigationUrlFingerprint.requireSingleMatch("post navigation URL builder")
    val resultIndex = match.instructionMatches[2].index
    val result = match.method.instructions.getOrNull(resultIndex) as? OneRegisterInstruction
        ?: throw PatchException("Expected a move-result-object in ${match.method}")
    if (result.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException("Expected a move-result-object in ${match.method} at instruction $resultIndex")
    }
    match.method.addDomainRewrite(resultIndex + 1, result.registerA)
}

private fun MutableMethod.addDomainRewrite(instructionIndex: Int, register: Int) {
    if (register !in 0..0xffff) {
        throw PatchException("URL register is out of range: v$register")
    }

    val invokeOpcode = if (register <= 15) "invoke-static" else "invoke-static/range"
    val registerRange = if (register <= 15) "{v$register}" else "{v$register .. v$register}"
    addInstructions(
        instructionIndex,
        """
        $invokeOpcode $registerRange, $CHANGE_DOMAIN_METHOD
        move-result-object v$register
        """.trimIndent(),
    )
}
