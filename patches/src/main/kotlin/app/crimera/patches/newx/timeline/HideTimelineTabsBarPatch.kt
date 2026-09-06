package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.returnVoidIfEnabled
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val HOME_TABBED_SCOPE = "Lcom/x/home/tabbed/"
private const val FUNCTION_ZERO_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val FUNCTION_ONE_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val COMMON_TABS_SCOPE = "Lcom/x/ui/common/tabs/"

private val TIMELINE_TABS_COMMON_PARAMETERS =
    listOf(
        FUNCTION_ONE_DESCRIPTOR,
        FUNCTION_ONE_DESCRIPTOR,
        "Z",
        "Z",
        FUNCTION_ZERO_DESCRIPTOR,
        MODIFIER_DESCRIPTOR,
        COMPOSER_DESCRIPTOR,
        "I",
    )

private object NewXTimelineTabsBarFingerprint : Fingerprint(
    definingClass = HOME_TABBED_SCOPE,
    returnType = "V",
    custom = { method, classDef ->
        val packageRelativeName = classDef.type.removePrefix(HOME_TABBED_SCOPE)
        classDef.type.startsWith(HOME_TABBED_SCOPE) &&
            !packageRelativeName.contains('/') &&
            method.isTimelineTabsRenderer()
    },
)

@Suppress("unused")
val newXHideTimelineTabsBarPatch =
    bytecodePatch(
        name = "NewX: Hide timeline tabs bar",
        description = "Removes the For You and Following tabs bar from NewX home timelines.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val hideTimelineTabsBar =
            newXToggle(
                id = "newx.timeline.hide_tabs_bar",
                category = Categories.TIMELINE,
                strings = settingStrings("piko_newx_hide_timeline_tabs"),
                order = 175,
                defaultValue = false,
                rebootApp = true,
            )

        execute {
            val matches = NewXTimelineTabsBarFingerprint.scopedMatchAll()
            if (matches.size != 2) {
                throw PatchException(
                    "Expected two NewX timeline tabs bar renderers, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }
            matches.forEach { match ->
                hideTimelineTabsBar.returnVoidIfEnabled(match.method, 0)
            }
        }
    }

private fun Method.isTimelineTabsRenderer(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    if (!hasTimelineTabsParameters(parameters)) return false

    val methodInstructions = implementation?.instructions ?: return false
    return methodInstructions.any { instruction ->
        instruction.getReference<MethodReference>()?.let { reference ->
            reference.definingClass.startsWith(COMMON_TABS_SCOPE) &&
                reference.returnType == "V"
        } == true
    }
}

private fun hasTimelineTabsParameters(parameters: List<String>): Boolean {
    if (parameters.size == TIMELINE_TABS_COMMON_PARAMETERS.size + 1) {
        return parameters[0].startsWith("L") &&
            parameters.drop(1) == TIMELINE_TABS_COMMON_PARAMETERS
    }
    if (parameters.size != TIMELINE_TABS_COMMON_PARAMETERS.size + 2) return false
    if (!parameters[0].startsWith("L")) return false
    if (!parameters[1].startsWith("L")) return false
    return parameters.drop(2) == TIMELINE_TABS_COMMON_PARAMETERS
}
