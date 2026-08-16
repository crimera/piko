package app.crimera.patches.xlite.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
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
 * ALPHA PATH: normally contributes one caller.
 * BETA PATH: may contribute multiple callers that share the same renderer.
 * TODO: Re-evaluate the alpha caller shape when alpha is deprecated; retain beta renderer deduplication.
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

internal fun composeSettingsBasicItemFingerprint(reference: MethodReference) =
    Fingerprint(
        definingClass = reference.definingClass,
        name = reference.name,
        returnType = "V",
        parameters = COMPOSE_SETTINGS_BASIC_ITEM_PARAMETERS,
        filters =
            listOf(
                string("title"),
                methodCall(
                    parameters = listOf("I"),
                    returnType = "Landroidx/compose/runtime/Composer;",
                ),
            ),
    )
