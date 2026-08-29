package app.crimera.patches.newx.misc.customsharingdomain

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXTextInput
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val SHARE_URL_RESOLVER_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/ShareUrlResolver;"
private const val CHANGE_DOMAIN_METHOD =
    "$SHARE_URL_RESOLVER_DESCRIPTOR->changeDomain(Ljava/lang/String;)Ljava/lang/String;"
private const val CUSTOM_DOMAIN_VALIDATOR_DESCRIPTOR =
    "Lapp/morphe/extension/newx/misc/CustomSharingDomainValidator;"
private const val SHARE_SHEET_DESCRIPTOR_PREFIX = "Lcom/x/dms/components/sharesheet/"
private const val SHARE_STATUS_URL_PREFIX = "https://x.com/i/status/"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"

/**
 * Share-sheet constructor that owns the final URL field. The status URL is a stable semantic
 * anchor; the void return shape identifies the share-sheet constructor.
 */
internal object ShareSheetUrlConstructorFingerprint : Fingerprint(
    definingClass = SHARE_SHEET_DESCRIPTOR_PREFIX,
    returnType = "V",
    filters = listOf(string(SHARE_STATUS_URL_PREFIX)),
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
            hookShareSheetPostUrls()
        }
    }

context(_: app.morphe.patcher.patch.BytecodePatchContext)
private fun hookShareSheetPostUrls() {
    val matches = ShareSheetUrlConstructorFingerprint.scopedMatchAll()
    if (matches.size != 1) {
        throw PatchException(
            "Expected one NewX share-sheet URL constructor match, found ${matches.size}: " +
                matches.joinToString { it.originalMethod.toString() },
        )
    }

    matches.forEach { match -> hookShareSheetUrlConstructor(match.method) }
}

private fun hookShareSheetUrlConstructor(method: MutableMethod) {
    val statusResultIndices = method.findStatusUrlResultIndices()
    if (statusResultIndices.size != 1) {
        throw PatchException(
            "Expected one status URL result in NewX share-sheet constructor $method, found " +
                statusResultIndices.size,
        )
    }

    val statusResultIndex = statusResultIndices.single()
    val urlFieldStoreIndices =
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
    if (urlFieldStoreIndices.size != 1) {
        throw PatchException(
            "Expected one final share URL field store in NewX share-sheet constructor $method, found " +
                urlFieldStoreIndices.size,
        )
    }

    val fieldStoreIndex = urlFieldStoreIndices.single()
    val valueRegister =
        (method.instructions[fieldStoreIndex] as? TwoRegisterInstruction)?.registerA
            ?: throw PatchException("Expected a two-register share URL field store in $method")
    method.addDomainRewrite(fieldStoreIndex, valueRegister)
}

private fun MutableMethod.findStatusUrlResultIndices(): List<Int> =
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

private fun MutableMethod.addDomainRewrite(instructionIndex: Int, register: Int) {
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
