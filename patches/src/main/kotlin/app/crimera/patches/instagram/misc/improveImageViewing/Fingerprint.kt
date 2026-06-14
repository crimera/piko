/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.improveImageViewing

import app.crimera.patches.instagram.utils.Constants.EXTENDED_IMAGE_URL_CLASS
import app.morphe.patcher.Fingerprint

internal object SetDPIMetricsFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("%sdpi; %sx%s"),
)

internal object ReturnExtendedImageUrlFingerprint : Fingerprint(
    returnType = EXTENDED_IMAGE_URL_CLASS,
    parameters = listOf("Ljava/lang/Integer;", "Ljava/util/List;", "I"),
)
