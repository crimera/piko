/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.improveImageViewing

import app.morphe.patcher.Fingerprint

internal object SetDPIMetricsFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("%sdpi; %sx%s"),
)

internal object ReturnExtendedImageUrlFingerprint : Fingerprint(
    returnType = "Lcom/instagram/model/mediasize/ExtendedImageUrl;",
    parameters = listOf("Ljava/lang/Integer;", "Ljava/util/List;", "I"),
)
