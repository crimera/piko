/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.internalStuffs.developerOptions

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal object FragmentNavigatorTransitionFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Integer;"),
    strings = listOf("FragmentNavigator.transitionInternal", "FragmentNavigator", "Fragment is null when attempting transition"),
)

internal object HomeIconOnClickListenerFingerprint : Fingerprint(
    name = "onLongClick",
    strings = listOf("click", "activity"),
)

internal object DeveloperOptionCallWithCallableFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.STATIC, AccessFlags.FINAL, AccessFlags.PUBLIC),
    strings = listOf("debug_options_error"),
)
