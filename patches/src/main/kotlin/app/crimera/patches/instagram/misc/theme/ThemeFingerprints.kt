/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

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
            "dark",
            "light",
            "system",
        ),
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameterTypes.size == 3 &&
            method.parameterTypes[0].toString().isObjectDescriptor() &&
            method.parameterTypes[1] == FUNCTION1_DESCRIPTOR &&
            method.parameterTypes[2] == "I" &&
            method.implementation?.instructions?.any { instruction ->
                val value =
                    ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string
                value?.startsWith(
                    "com.instagram.settings.impl.accessibility.DarkModeSection " +
                        "(AccessibilityOptionsComposeFragment.kt:",
                ) == true
            } == true
    },
)
