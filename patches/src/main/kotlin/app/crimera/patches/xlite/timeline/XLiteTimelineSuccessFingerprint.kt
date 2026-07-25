package app.crimera.patches.xlite.timeline

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
