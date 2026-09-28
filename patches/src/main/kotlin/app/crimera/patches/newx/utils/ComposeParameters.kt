package app.crimera.patches.newx.utils

import com.android.tools.smali.dexlib2.iface.Method

/**
 * The Compose compiler lowers `@Composable` signatures differently between releases: synthetic
 * `Composer` parameters, change/default bitmasks, and auxiliary `Modifier`/lambda slots come and
 * go. These helpers express the stable part of a composable signature for a Fingerprint `custom`
 * block, so a release bump does not need a new exact parameter list.
 */
internal fun Method.parameterDescriptors(): List<String> =
    parameterTypes.map(CharSequence::toString)

internal fun String.isObjectDescriptor(): Boolean = startsWith("L") || startsWith("[")

internal fun List<String>.hasComposeShape(
    required: List<String> = emptyList(),
    first: String? = null,
    last: String? = null,
    objectFirst: Boolean = false,
): Boolean {
    if (first != null && firstOrNull() != first) return false
    if (last != null && lastOrNull() != last) return false
    if (objectFirst && firstOrNull()?.isObjectDescriptor() != true) return false
    return containsAll(required)
}
