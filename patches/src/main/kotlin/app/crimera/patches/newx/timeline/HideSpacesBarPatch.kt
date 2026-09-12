package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.returnVoidIfEnabled
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.Method

private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val FUNCTION_ZERO_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val FUNCTION_ONE_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"

private object NewXSpacesBarFingerprint : Fingerprint(
    definingClass = "Lcom/x/spaces/ui/home/",
    returnType = "V",
    custom = { method, classDef ->
        val packageRelativeName = classDef.type.removePrefix("Lcom/x/spaces/ui/home/")
        classDef.type.startsWith("Lcom/x/spaces/ui/home/") &&
            !packageRelativeName.contains('/') &&
            method.hasKnownSpacesBarSignature()
    },
    filters =
        listOf(
            string("stateFlow"),
            string("onSpaceClicked"),
    ),
)

private val SPACES_BAR_PARAMETER_SUFFIXES =
    listOf(
        listOf(
            FUNCTION_ONE_DESCRIPTOR,
            MODIFIER_DESCRIPTOR,
            "Z",
            FUNCTION_ONE_DESCRIPTOR,
            FUNCTION_ONE_DESCRIPTOR,
            FUNCTION_ZERO_DESCRIPTOR,
            COMPOSER_DESCRIPTOR,
            "I",
            "I",
        ),
        listOf(
            FUNCTION_ONE_DESCRIPTOR,
            MODIFIER_DESCRIPTOR,
            "Z",
            FUNCTION_ONE_DESCRIPTOR,
            FUNCTION_ONE_DESCRIPTOR,
            FUNCTION_ONE_DESCRIPTOR,
            FUNCTION_ZERO_DESCRIPTOR,
            COMPOSER_DESCRIPTOR,
            "I",
            "I",
        ),
    )

private fun Method.hasKnownSpacesBarSignature(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    val stateFlowParameter = parameters.firstOrNull() ?: return false
    if (!stateFlowParameter.isObjectDescriptor()) return false
    return parameters.drop(1) in SPACES_BAR_PARAMETER_SUFFIXES
}

private fun String.isObjectDescriptor(): Boolean = startsWith('L') && endsWith(';')

@Suppress("unused")
val newXHideSpacesBarPatch =
    bytecodePatch(
        name = "NewX: Hide Spaces bar",
        description = "Hides the Spaces bar above NewX timelines.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val hideSpacesBar =
            newXToggle(
                id = "newx.timeline.hide_spaces_bar",
                category = Categories.TIMELINE,
                strings = settingStrings("piko_newx_hide_spaces_bar"),
                order = 250,
                defaultValue = false,
            )

        execute {
            val match =
                requireExactlyOne(
                    label = "NewX Spaces bar renderer",
                    candidates = NewXSpacesBarFingerprint.scopedMatchAllOrNull().orEmpty(),
                )
            hideSpacesBar.returnVoidIfEnabled(match.method, 0)
        }
    }
