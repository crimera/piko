package app.crimera.patches.xlite.misc.dynamiccolor

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val INLINE_ACTION_ENTRY_DESCRIPTOR = "Lcom/x/models/InlineActionEntry;"
private const val POST_ACTION_TYPE_DESCRIPTOR = "Lcom/x/models/PostActionType;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"

/**
 * Finds the semantic Compose palette provider by package role and its three lazy cache reads.
 * Cache, palette, and factory descriptors are deliberately resolved from the match.
 */
internal object HorizonThemePaletteProviderFingerprint : Fingerprint(
    parameters = listOf("L", COMPOSER_DESCRIPTOR),
    custom = { method, _ -> method.returnType.startsWith("Lcom/x/compose/theme/") },
    filters = listOf(
        methodCall(smali = "Ljava/lang/Enum;->ordinal()I"),
        opcode(Opcode.MOVE_RESULT),
        opcode(Opcode.AGET),
        opcode(Opcode.SGET_OBJECT),
        opcode(Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT),
        opcode(Opcode.CHECK_CAST),
        opcode(Opcode.SGET_OBJECT),
        opcode(Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT),
        opcode(Opcode.CHECK_CAST),
        opcode(Opcode.SGET_OBJECT),
        opcode(Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT),
        opcode(Opcode.CHECK_CAST),
    ),
)

/**
 * Finds the full X-Lite color-scale provider. DIM and LIGHTS_OUT intentionally share one
 * cached dark palette, so the first lazy cache is read twice before the standard cache.
 */
internal object XLiteDynamicColorPaletteProviderFingerprint : Fingerprint(
    parameters = listOf("L", COMPOSER_DESCRIPTOR),
    custom = { method, _ -> method.returnType.startsWith("Lcom/x/compose/core/") },
    filters = listOf(
        methodCall(smali = "Ljava/lang/Enum;->ordinal()I"),
        opcode(Opcode.MOVE_RESULT),
        opcode(Opcode.AGET),
        opcode(Opcode.SGET_OBJECT),
        opcode(Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT),
        opcode(Opcode.CHECK_CAST),
        opcode(Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT),
        opcode(Opcode.CHECK_CAST),
        opcode(Opcode.SGET_OBJECT),
        opcode(Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT),
        opcode(Opcode.CHECK_CAST),
    ),
)

/** Renders each model-backed icon in X-Lite's inline post action bar. */
internal object XLiteInlineActionEntryRendererFingerprint : Fingerprint(
    parameters = listOf(
        INLINE_ACTION_ENTRY_DESCRIPTOR,
        "L",
        "J",
        "F",
        "L",
        "J",
        "L",
        "Z",
        MODIFIER_DESCRIPTOR,
        COMPOSER_DESCRIPTOR,
        "I",
    ),
    returnType = "V",
    filters = listOf(
        methodCall(smali = "$INLINE_ACTION_ENTRY_DESCRIPTOR->getActionType()$POST_ACTION_TYPE_DESCRIPTOR"),
        methodCall(smali = "$INLINE_ACTION_ENTRY_DESCRIPTOR->isEnabled()Z"),
    ),
)
