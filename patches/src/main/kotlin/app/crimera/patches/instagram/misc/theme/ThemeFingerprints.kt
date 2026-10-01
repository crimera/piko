/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal object LegacyDarkModeFragmentConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("theme_settings"),
)

internal object CurrentSystemUiModeFingerprint : Fingerprint(
    returnType = "I",
    parameters = emptyList(),
    strings =
        listOf(
            "ig_device_theme",
            "KEY_CONFIG_CURRENT_SYSTEM_UI_MODE",
        ),
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.implementation != null
    },
)

internal object DarkModeSectionFingerprint : Fingerprint(
    returnType = "V",
    strings =
        listOf(
            "com.instagram.settings.impl.accessibility.DarkModeSection " +
                "(AccessibilityOptionsComposeFragment.kt:267)",
            "dark",
            "light",
            "system",
        ),
    custom = { method, _ ->
        method.parameterTypes.count {
            it == "Lkotlin/jvm/functions/Function1;"
        } == 1
    },
)
