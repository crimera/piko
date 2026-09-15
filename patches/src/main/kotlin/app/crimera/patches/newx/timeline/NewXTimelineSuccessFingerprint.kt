package app.crimera.patches.newx.timeline

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

private object NewXTimelineSuccessClassFingerprint : Fingerprint(
    definingClass = "Lcom/x/urt/",
    filters =
        listOf(
            string("Success(timelineType="),
            string(", timelineItems="),
        ),
)

internal object NewXTimelineSuccessFingerprint : Fingerprint(
    classFingerprint = NewXTimelineSuccessClassFingerprint,
    name = "<init>",
    parameters = listOf("L", "L", "L", "Z", "Z"),
    returnType = "V",
)
