package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.settings.returnVoidIfEnabled
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.Method

private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val FUNCTION_ZERO_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val FUNCTION_ONE_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val FUNCTION_TWO_DESCRIPTOR = "Lkotlin/jvm/functions/Function2;"

private object NewXSpacesBarFingerprint : Fingerprint(
    definingClass = "Lcom/x/spaces/ui/home/",
    returnType = "V",
    custom = { method, classDef ->
        val packageRelativeName = classDef.type.removePrefix("Lcom/x/spaces/ui/home/")
        classDef.type.startsWith("Lcom/x/spaces/ui/home/") &&
            !packageRelativeName.contains('/') &&
            method.hasKnownSpacesBarSignature()
    },
)

private val SPACES_BAR_PREFIX =
    listOf(
        FUNCTION_ONE_DESCRIPTOR,
        MODIFIER_DESCRIPTOR,
        "Z",
    )
private val SPACES_BAR_TAIL =
    listOf(
        FUNCTION_ZERO_DESCRIPTOR,
        COMPOSER_DESCRIPTOR,
        "I",
        "I",
    )
private val SPACES_BAR_CALLBACK_DESCRIPTORS =
    setOf(FUNCTION_ONE_DESCRIPTOR, FUNCTION_TWO_DESCRIPTOR)

// Callback band between the prefix and the trailing (Function0, Composer, Int, Int) group.
// Releases gained slots (12.26 alpha) and widened one to Function2 (12.31 alpha.04), so treat
// the band as a family instead of enumerating tested shapes.
private val SPACES_BAR_CALLBACK_COUNT_RANGE = 2..4

private fun Method.hasKnownSpacesBarSignature(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    if (!parameters.firstOrNull().orEmpty().isObjectDescriptor()) return false

    val callbackStart = 1 + SPACES_BAR_PREFIX.size
    val callbackEnd = parameters.size - SPACES_BAR_TAIL.size
    if (callbackEnd - callbackStart !in SPACES_BAR_CALLBACK_COUNT_RANGE) return false
    if (parameters.subList(1, callbackStart) != SPACES_BAR_PREFIX) return false
    if (parameters.subList(callbackStart, callbackEnd).any { it !in SPACES_BAR_CALLBACK_DESCRIPTORS }) return false
    return parameters.subList(callbackEnd, parameters.size) == SPACES_BAR_TAIL
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
