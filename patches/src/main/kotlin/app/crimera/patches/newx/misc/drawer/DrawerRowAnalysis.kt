package app.crimera.patches.newx.misc.drawer

import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val DRAWER_SCOPE = "Lcom/x/main/drawer/"

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val ICONS_DESCRIPTOR_PREFIX = "Lcom/x/icons/"

internal fun MethodReference.isStringResourceLookup(): Boolean =
    returnType == "Ljava/lang/String;" &&
        parameterTypes.map(CharSequence::toString) == listOf(COMPOSER_DESCRIPTOR, "I")

/**
 * Title-based row contract within an already anchored drawer call graph. R8 can reorder every
 * parameter, including Composer and change masks. Auxiliary reference/Int/Boolean parameters are
 * borrowed from the native call; title, icon and click must each remain unambiguous.
 */
internal fun MethodReference.isDrawerRowRenderer(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return returnType == "V" &&
        parameters.count { it == STRING_DESCRIPTOR } == 1 &&
        parameters.count { it.startsWith(ICONS_DESCRIPTOR_PREFIX) } == 1 &&
        parameters.count { it == FUNCTION0_DESCRIPTOR } == 1 &&
        parameters.count { it == MODIFIER_DESCRIPTOR } == 1 &&
        parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
        "I" in parameters &&
        parameters.all { it.startsWith("L") || it.startsWith("[") || it == "I" || it == "Z" }
}
