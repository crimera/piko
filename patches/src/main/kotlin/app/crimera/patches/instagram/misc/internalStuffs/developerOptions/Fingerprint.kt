/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.internalStuffs.developerOptions

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal const val QE_FRAGMENT_DESCRIPTOR = "Lcom/instagram/debug/quickexperiment/QuickExperimentCategoriesFragment;"

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
