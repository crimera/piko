package app.crimera.patches.newx.misc.dynamiccolor

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"

/**
 * Finds the semantic Compose palette provider by package role and its three lazy cache reads.
 * Cache, palette, and factory descriptors are deliberately resolved from the match.
 */
internal object HorizonThemePaletteProviderFingerprint : Fingerprint(
    definingClass = "Lcom/x/compose/theme/",
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
 * Finds the full NewX color-scale provider. DIM and LIGHTS_OUT intentionally share one
 * cached dark palette, so the first lazy cache is read twice before the standard cache.
 */
internal object NewXDynamicColorPaletteProviderFingerprint : Fingerprint(
    definingClass = "Lcom/x/compose/core/",
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
