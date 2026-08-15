package app.crimera.patches.xlite.timeline

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

private object XLiteTimelineSuccessClassFingerprint : Fingerprint(
    definingClass = "Lcom/x/urt/",
    filters =
        listOf(
            string("Success(timelineType="),
            string(", timelineItems="),
        ),
)

internal object XLiteTimelineSuccessFingerprint : Fingerprint(
    classFingerprint = XLiteTimelineSuccessClassFingerprint,
    name = "<init>",
    parameters = listOf("L", "L", "L", "Z", "Z"),
    returnType = "V",
)
