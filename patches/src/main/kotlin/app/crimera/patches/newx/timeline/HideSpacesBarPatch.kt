package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.settings.returnVoidIfEnabled
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.patch
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
        !packageRelativeName.contains('/') && method.hasKnownSpacesBarSignature()
    },
)

private val SPACES_BAR_TAIL =
    listOf(
        COMPOSER_DESCRIPTOR,
        "I",
        "I",
    )
private val SPACES_BAR_CALLBACK_DESCRIPTORS =
    setOf(FUNCTION_ONE_DESCRIPTOR, FUNCTION_TWO_DESCRIPTOR)

// The renderer takes item callbacks followed by one or more no-argument callbacks.
private const val MINIMUM_SPACES_BAR_CALLBACK_COUNT = 2

/**
 * Spaces bar renderer by Compose shape. No string, resource or stable `com/x/spaces` type anchors it:
 * on 12.41, 12.43 and 12.45.1 the renderer is `j` in an obfuscated `ui/home` class whose flow, model
 * and lambda class names change per release, and it has no const-string. The anchor is therefore the
 * shape: a leading object, one Modifier and one Boolean option, item callbacks followed by no-argument
 * callbacks, and the Compose `Composer, I, I` tail. Options are counted, not positioned, so reordered
 * Compose parameters still match, and exact cardinality in the caller fails closed on any ambiguity.
 */
internal fun Method.hasKnownSpacesBarSignature(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    if (!parameters.firstOrNull().orEmpty().isObjectDescriptor()) return false
    if (parameters.takeLast(SPACES_BAR_TAIL.size) != SPACES_BAR_TAIL) return false

    val middle = parameters.drop(1).dropLast(SPACES_BAR_TAIL.size)
    val options = middle.filter { it == MODIFIER_DESCRIPTOR || it == "Z" }
    if (options.count { it == MODIFIER_DESCRIPTOR } != 1 || options.count { it == "Z" } != 1) return false
    val callbacks = middle.filterNot { it == MODIFIER_DESCRIPTOR || it == "Z" }
    if (callbacks.any { it !in SPACES_BAR_CALLBACK_DESCRIPTORS && it != FUNCTION_ZERO_DESCRIPTOR }) return false
    val itemCallbacks = callbacks.dropLastWhile { it == FUNCTION_ZERO_DESCRIPTOR }
    return itemCallbacks.size < callbacks.size &&
        itemCallbacks.size >= MINIMUM_SPACES_BAR_CALLBACK_COUNT &&
        itemCallbacks.all { it in SPACES_BAR_CALLBACK_DESCRIPTORS }
}

private fun String.isObjectDescriptor(): Boolean = startsWith('L') && endsWith(';')

@Suppress("unused")
val newXHideSpacesBarPatch =
    patch(
        name = "NewX: Hide Spaces bar",
        description = "Hides the Spaces bar above NewX timelines.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)
        category("NewX patches")

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
