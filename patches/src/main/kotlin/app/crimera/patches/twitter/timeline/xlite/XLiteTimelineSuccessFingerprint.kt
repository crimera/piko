/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.xlite

import app.morphe.patcher.Fingerprint

private object XLiteTimelineSuccessClassFingerprint : Fingerprint(
    strings =
        listOf(
            "Success(timelineType=",
            ", timelineItems=",
        ),
)

internal object XLiteTimelineSuccessFingerprint : Fingerprint(
    classFingerprint = XLiteTimelineSuccessClassFingerprint,
    name = "<init>",
    parameters = listOf("L", "L", "L", "Z", "Z"),
    returnType = "V",
)
