package app.crimera.patches.newx.utils

import app.morphe.patcher.patch.PatchException

/**
 * Returns the only candidate, or fails with the resolver label and every candidate description.
 */
internal fun <T> requireExactlyOne(
    label: String,
    candidates: Collection<T>,
    describe: (T) -> String = { candidate -> candidate.toString() },
): T {
    if (candidates.size == 1) return candidates.single()

    throw PatchException(
        "Expected exactly one $label, found ${candidates.size}: " +
            candidates.joinToString(prefix = "[", postfix = "]", transform = describe),
    )
}

/**
 * Returns the only candidate when present, or fails if the resolver found more than one.
 */
internal fun <T> requireAtMostOne(
    label: String,
    candidates: Collection<T>,
    describe: (T) -> String = { candidate -> candidate.toString() },
): T? {
    if (candidates.size <= 1) return candidates.firstOrNull()

    throw PatchException(
        "Expected at most one $label, found ${candidates.size}: " +
            candidates.joinToString(prefix = "[", postfix = "]", transform = describe),
    )
}
