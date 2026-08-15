package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.returnVoidIfEnabled
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteToggle
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string

private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val FUNCTION_ZERO_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val FUNCTION_ONE_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"

private object XLiteSpacesBarFingerprint : Fingerprint(
    definingClass = "Lcom/x/spaces/ui/home/",
    parameters =
        listOf(
            "L",
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
    returnType = "V",
    custom = { _, classDef ->
        val packageRelativeName = classDef.type.removePrefix("Lcom/x/spaces/ui/home/")
        classDef.type.startsWith("Lcom/x/spaces/ui/home/") && !packageRelativeName.contains('/')
    },
    filters =
        listOf(
            string("stateFlow"),
            string("onSpaceClicked"),
        ),
)

@Suppress("unused")
val xLiteHideSpacesBarPatch =
    bytecodePatch(
        name = "X-Lite: Hide Spaces bar",
        description = "Hides the Spaces bar above X-Lite timelines.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        val hideSpacesBar =
            xLiteToggle(
                id = "xlite.timeline.hide_spaces_bar",
                category = Categories.TIMELINE,
                strings = settingStrings("piko_xlite_hide_spaces_bar"),
                order = 250,
                defaultValue = false,
            )

        execute {
            val matches = XLiteSpacesBarFingerprint.scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite Spaces bar renderer, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }
            hideSpacesBar.returnVoidIfEnabled(matches.single().method, 0)
        }
    }
