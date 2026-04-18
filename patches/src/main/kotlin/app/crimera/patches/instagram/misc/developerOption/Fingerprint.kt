/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.developerOption

import app.morphe.patcher.Fingerprint

internal object AREffectsDebugViewRelatedFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Landroid/view/View;"),
    strings = listOf("VIEW_AR_EFFECT_ID", "AR Effect ID:"),
)

internal object ChromeTraceRelatedFingerprint : Fingerprint(
    strings = listOf("Chrome trace is only available for employees"),
)

internal object PromoteActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/business/promote/activity/PromoteActivity;",
    strings = listOf("selected 2 media for A/B testing"),
)
