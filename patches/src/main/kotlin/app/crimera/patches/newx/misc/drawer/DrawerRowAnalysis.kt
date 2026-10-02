package app.crimera.patches.newx.misc.drawer

import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val DRAWER_SCOPE = "Lcom/x/main/drawer/"

private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val ICONS_DESCRIPTOR_PREFIX = "Lcom/x/icons/"

internal fun MethodReference.isStringResourceLookup(): Boolean =
    returnType.toString() == "Ljava/lang/String;" &&
        parameterTypes.map(CharSequence::toString) == listOf(COMPOSER_DESCRIPTOR, "I")

/**
 * Title-based drawer row renderer. Releases add a selected flag and a trailing content lambda to
 * the same renderer, so only the stable prefix and the trailing composable fields are asserted.
 */
internal fun MethodReference.isDrawerRowRenderer(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return returnType.toString() == "V" &&
        parameters.size in 8..10 &&
        parameters[0] == STRING_DESCRIPTOR &&
        parameters[1].startsWith(ICONS_DESCRIPTOR_PREFIX) &&
        parameters[2] == FUNCTION0_DESCRIPTOR &&
        parameters[3] == MODIFIER_DESCRIPTOR &&
        parameters.count { it == COMPOSER_DESCRIPTOR } == 1 &&
        parameters.count { it == "I" } == 2 &&
        parameters[parameters.size - 1] == "I" &&
        parameters[parameters.size - 2] == "I"
}
