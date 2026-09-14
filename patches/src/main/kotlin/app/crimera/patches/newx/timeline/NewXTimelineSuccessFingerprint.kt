package app.crimera.patches.newx.timeline

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import app.morphe.util.numberOfParameterRegisters

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

internal fun ensureTimelineSuccessRegisters(
    match: Match,
    requiredScratchRegisters: Int = 4,
): MutableMethod {
    val currentMethod = match.method
    val registerCount = currentMethod.implementation?.registerCount ?: 0
    val paramCount = currentMethod.numberOfParameterRegisters
    val currentLocals = registerCount - paramCount
    if (currentLocals >= requiredScratchRegisters) {
        return currentMethod
    }
    val additional = requiredScratchRegisters - currentLocals
    val expandedMethod = currentMethod.cloneMutable(additionalRegisters = additional)
    match.classDef.methods.remove(currentMethod)
    match.classDef.methods.add(expandedMethod)
    return expandedMethod
}
