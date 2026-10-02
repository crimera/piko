package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.settings.returnVoidIfEnabled
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.literal
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val HOME_TABBED_SCOPE = "Lcom/x/home/tabbed/"
private const val FUNCTION_ZERO_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val FUNCTION_ONE_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val COMMON_TABS_SCOPE = "Lcom/x/ui/common/tabs/"
private const val INTEGER_DESCRIPTOR = "I"
private const val BOOLEAN_DESCRIPTOR = "Z"

// android.R.string "add_tab" ("Add tab"): the content description only the home timeline
// add-tab button carries. The resource id is stable across the declared NewX targets.
private const val ADD_TAB_STRING_RESOURCE_ID = 0x7f14006d

private val TIMELINE_TABS_CALLBACK_PREFIX =
    listOf(
        FUNCTION_ONE_DESCRIPTOR,
        FUNCTION_ONE_DESCRIPTOR,
    )
private val TIMELINE_TABS_TAIL =
    listOf(
        FUNCTION_ZERO_DESCRIPTOR,
        MODIFIER_DESCRIPTOR,
        COMPOSER_DESCRIPTOR,
        INTEGER_DESCRIPTOR,
    )

// Leading page parameters. The two renderer variants differ only by the presence of the pager
// state, so this count is the variant key instead of the total parameter size, which now
// overlaps between variants after the flag band widened.
private val TIMELINE_TABS_LEADING_OBJECT_COUNTS = 1..2

// Boolean flag slots between the callback prefix and the trailing group. Declared targets carry
// two (through 12.31 alpha.02) or three (12.31 alpha.04), so match the band, not one shape.
private val TIMELINE_TABS_FLAG_COUNT_RANGE = 2..3

private val TIMELINE_ADD_TAB_PARAMETERS =
    listOf(
        FUNCTION_ZERO_DESCRIPTOR,
        COMPOSER_DESCRIPTOR,
        INTEGER_DESCRIPTOR,
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

private object NewXTimelineAddTabButtonFingerprint : Fingerprint(
    definingClass = HOME_TABBED_SCOPE,
    returnType = "V",
    filters = listOf(literal(ADD_TAB_STRING_RESOURCE_ID)),
    custom = { method, classDef ->
        val packageRelativeName = classDef.type.removePrefix(HOME_TABBED_SCOPE)
        classDef.type.startsWith(HOME_TABBED_SCOPE) &&
            !packageRelativeName.contains('/') &&
            method.isTimelineAddTabButton()
    },
)

@Suppress("unused")
val newXHideTimelineTabsBarPatch =
    bytecodePatch(
        name = "NewX: Hide timeline tabs bar",
        description = "Removes the For You and Following tabs bar from NewX home timelines, with an option to hide the add-tab (+) button.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val (hideTimelineTabsBar, hideTimelineAddTab) =
            newXSettings {
                category(Categories.TIMELINE) {
                    group(Groups.TIMELINE_TABS) {
                        val hideTabsBar =
                            toggle(
                                id = "newx.timeline.hide_tabs_bar",
                                strings = settingStrings("piko_newx_hide_timeline_tabs"),
                                order = 200,
                                defaultValue = false,
                                rebootApp = true,
                            )
                        val hideAddTab =
                            toggle(
                                id = "newx.timeline.hide_add_tab_button",
                                strings = settingStrings("piko_newx_hide_timeline_add_tab"),
                                order = 300,
                                defaultValue = false,
                                rebootApp = true,
                            )
                        hideTabsBar to hideAddTab
                    }
                }
            }

        execute {
            val matches = NewXTimelineTabsBarFingerprint.scopedMatchAll()
            matches.requireTimelineTabsRenderers()
            matches.forEach { match ->
                hideTimelineTabsBar.returnVoidIfEnabled(match.method, 0)
            }

            val addTabButton =
                requireExactlyOne(
                    label = "NewX timeline add-tab button",
                    candidates = NewXTimelineAddTabButtonFingerprint.scopedMatchAll(),
                )
            hideTimelineAddTab.returnVoidIfEnabled(addTabButton.method, 0)
        }
}

private fun Method.isTimelineAddTabButton(): Boolean {
    if (!AccessFlags.STATIC.isSet(accessFlags)) return false
    return parameterTypes.map(CharSequence::toString) == TIMELINE_ADD_TAB_PARAMETERS
}

private fun Method.isTimelineTabsRenderer(): Boolean {
    if (!AccessFlags.STATIC.isSet(accessFlags)) return false

    val parameters = parameterTypes.map(CharSequence::toString)
    if (timelineTabsLeadingObjectCount(parameters) == null) return false

    val methodInstructions = implementation?.instructions ?: return false
    return methodInstructions.count(Instruction::isTimelineTabsComposeCall) == 1
}

/**
 * Returns the leading page parameter count when [parameters] matches a timeline tabs renderer
 * shape, or null. Each declared variant is tried explicitly: callback parameters are object
 * descriptors too, so a leading run of object descriptors cannot separate the variants.
 */
private fun timelineTabsLeadingObjectCount(parameters: List<String>): Int? {
    for (leadingObjects in TIMELINE_TABS_LEADING_OBJECT_COUNTS) {
        if (hasTimelineTabsParameters(parameters, leadingObjects)) return leadingObjects
    }
    return null
}

private fun hasTimelineTabsParameters(
    parameters: List<String>,
    leadingObjects: Int,
): Boolean {
    if (leadingObjects > parameters.size) return false
    if ((0 until leadingObjects).any { !parameters[it].isObjectDescriptor() }) return false

    val callbackStart = leadingObjects
    val flagStart = callbackStart + TIMELINE_TABS_CALLBACK_PREFIX.size
    val tailStart = parameters.size - TIMELINE_TABS_TAIL.size
    if (tailStart - flagStart !in TIMELINE_TABS_FLAG_COUNT_RANGE) return false
    if (parameters.subList(callbackStart, flagStart) != TIMELINE_TABS_CALLBACK_PREFIX) return false
    if (parameters.subList(flagStart, tailStart).any { it != BOOLEAN_DESCRIPTOR }) return false
    return parameters.subList(tailStart, parameters.size) == TIMELINE_TABS_TAIL
}

private fun Instruction.isTimelineTabsComposeCall(): Boolean {
    if (opcode != Opcode.INVOKE_STATIC && opcode != Opcode.INVOKE_STATIC_RANGE) return false

    val reference = getReference<MethodReference>() ?: return false
    if (!reference.definingClass.startsWith(COMMON_TABS_SCOPE)) return false
    if (reference.returnType != "V") return false

    val parameters = reference.parameterTypes.map(CharSequence::toString)
    val composerIndex = parameters.indexOfLast { it == COMPOSER_DESCRIPTOR }
    if (composerIndex < 0) return false
    if (parameters.count { it == COMPOSER_DESCRIPTOR } != 1) return false

    val flags = parameters.drop(composerIndex + 1)
    return flags.size >= 2 && flags.all { it == INTEGER_DESCRIPTOR }
}

private fun String.isObjectDescriptor(): Boolean = startsWith('L') && endsWith(';')

private fun List<Match>.requireTimelineTabsRenderers() {
    val expectedVariantCounts = TIMELINE_TABS_LEADING_OBJECT_COUNTS.associateWith { 1 }
    val actualVariantCounts =
        groupingBy { match ->
            timelineTabsLeadingObjectCount(match.method.parameterTypes.map(CharSequence::toString))
                ?: throw PatchException(
                    "Matched timeline tabs renderer lost its parameter shape: ${match.method}",
                )
        }.eachCount()
    val owners = map { it.originalMethod.definingClass }.distinct()
    val pagesDescriptors =
        mapNotNull { it.method.parameterTypes.firstOrNull()?.toString() }.distinct()

    if (
        size == expectedVariantCounts.values.sum() &&
        actualVariantCounts == expectedVariantCounts &&
        owners.size == 1 &&
        pagesDescriptors.size == 1
    ) {
        return
    }

    throw PatchException(
        "Expected one NewX timeline tabs renderer for each supported signature in one owner " +
            "with one shared pages type; found ${size}: " +
            "variants=$actualVariantCounts, owners=$owners, pages=$pagesDescriptors, " +
            "candidates=${joinToString { it.originalMethod.toString() }}",
    )
}
