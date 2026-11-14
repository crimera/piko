package app.crimera.patches.newx.timeline

import app.crimera.patches.common.isObjectDescriptor
import app.crimera.patches.common.parameterDescriptors
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
    returnType = "V",
    custom = { method, _ ->
        val parameters = method.parameterDescriptors()
        parameters.size == 5 &&
            parameters.take(3).all(String::isObjectDescriptor) &&
            parameters.takeLast(2).all { descriptor -> descriptor == "Z" }
    },
)
