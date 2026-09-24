/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.customise.font

import app.morphe.patcher.Fingerprint

/**
 * The typeface repository method every classic text component goes through to resolve
 * a font, matched on the systrace section name it opens.
 */
internal object TypefaceRepositoryLoadFingerprint : Fingerprint(
    returnType = "Landroid/graphics/Typeface;",
    strings = listOf("TypefaceRepository:load_typeface"),
)

/**
 * The IGDS font helper that hands typefaces to `IgTextView`s, Bloks mounted text,
 * spans and paints. Its typefaces never pass through the typeface repository.
 */
internal object IgdsFontHelperFingerprint : Fingerprint(
    strings = listOf("IgdsPrismFontHelper_setTextAppearance_UnsupportedOperationException"),
)

/**
 * Compose resolves fonts on its own, so nothing drawn by Compose - the settings screens,
 * the direct inbox - reaches the typeface repository. This is the platform adapter that
 * produces the typeface for every font family, resource backed or not, and wraps it in a
 * resolution result rather than returning it.
 */
internal object ComposePlatformTypefacesFingerprint : Fingerprint(
    strings = listOf("null cannot be cast to non-null type androidx.compose.ui.text.platform.AndroidTypeface"),
)

/**
 * The story text styles of the older story editor, which resolve their typeface from an enum
 * of style names rather than from a font descriptor.
 */
internal object LegacyStoryFontFingerprint : Fingerprint(
    strings = listOf("SIGNATURE", "TYPEWRITER", "LITERATURE"),
)

/**
 * `ResourcesCompat.getFont`, which loads the font resources the app declares of its own.
 */
internal object ResourcesCompatFontFingerprint : Fingerprint(
    returnType = "Landroid/graphics/Typeface;",
    strings = listOf("Font resource ID #0x", " could not be retrieved."),
)

/** React Native's registration of the "Optimistic VF App Lite" variable font. */
internal object ReactNativeFontRegistrationFingerprint : Fingerprint(
    strings = listOf("Optimistic VF App Lite "),
    custom = { method, _ -> method.parameters.isEmpty() && method.returnType.startsWith("L") },
)
