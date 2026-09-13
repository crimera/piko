package app.crimera.patches.newx.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal val COMPOSE_SETTINGS_BASIC_ITEM_PARAMETERS =
    listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "L",
        "Lkotlin/jvm/functions/Function0;",
        "L",
        "J",
        "J",
        "Landroidx/compose/runtime/Composer;",
        "I",
        "I",
    )

/**
 * Resolves the repackaged Compose row owner from its preserved X settings caller.
 *
 * Multiple callers may share one renderer after Compose lowering; callers are deduplicated by
 * the settings patch before the renderer is modified.
 */
internal object ComposeSettingsBasicItemCallerFingerprint : Fingerprint(
    definingClass = "Lcom/x/settings/common/",
    filters =
        listOf(
            methodCall(
                parameters = COMPOSE_SETTINGS_BASIC_ITEM_PARAMETERS,
                returnType = "V",
            ),
        ),
)

/**
 * The Compose compiler may type the composer prologue result as the public Composer interface or
 * its runtime implementation. Match the stable runtime package/type shape, not generated method
 * names or optional parameter null checks.
 */
internal fun composeSettingsBasicItemFingerprint(reference: MethodReference) =
    Fingerprint(
        definingClass = reference.definingClass,
        name = reference.name,
        returnType = "V",
        parameters = COMPOSE_SETTINGS_BASIC_ITEM_PARAMETERS,
        filters =
            listOf(
                methodCall(
                    opcode = Opcode.INVOKE_VIRTUAL,
                    definingClass = "Landroidx/compose/runtime/",
                    parameters = listOf("I"),
                    returnType = "Landroidx/compose/runtime/",
                ),
            ),
    )
