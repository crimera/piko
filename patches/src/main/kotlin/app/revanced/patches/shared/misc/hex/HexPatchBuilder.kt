package app.revanced.patches.shared.misc.hex

import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.rawResourcePatch
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.max

fun hexPatch(ignoreMissingTargetFiles: Boolean = false, block: HexPatchBuilder.() -> Unit) =
    hexPatch(ignoreMissingTargetFiles, fun(): Set<Replacement> = HexPatchBuilder().apply(block))

@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
class HexPatchBuilder internal constructor(
    private val replacements: MutableSet<Replacement> = mutableSetOf(),
) : Set<Replacement> by replacements {
    infix fun String.asPatternTo(replacementPattern: String) = byteArrayOf(this) to byteArrayOf(replacementPattern)

    infix fun <T> Pair<T, T>.inFile(filePath: String) {
        if (first is String && second is String) {
            val first = first as String
            val second = second as String

            replacements += Replacement(
                first.toByteArray(), second.toByteArray(),
                filePath
            )
        } else if (first is ByteArray && second is ByteArray) {
            val first = first as ByteArray
            val second = second as ByteArray

            replacements += Replacement(first, second, filePath)
        } else {
            throw PatchException("Unsupported types for pattern and replacement: $first, $second")
        }
    }
}

// The replacements being passed using a function is intended.
// Previously the replacements were a property of the patch. Getter were being delegated to that property.
// This late evaluation was being leveraged in app.revanced.patches.all.misc.hex.HexPatch.
// Without the function, the replacements would be evaluated at the time of patch creation.
// This isn't possible because the delegated property is not accessible at that time.
@Deprecated("Use the hexPatch function with the builder parameter instead.")
fun hexPatch(ignoreMissingTargetFiles: Boolean = false, replacementsSupplier: () -> Set<Replacement>) =
    rawResourcePatch {
        execute {
            replacementsSupplier().groupBy { it.targetFilePath }.forEach { (targetFilePath, replacements) ->
                val targetFile = get(targetFilePath, true)
                if (ignoreMissingTargetFiles && !targetFile.exists()) return@forEach

                // TODO: Use a file channel to read and write the file instead of reading the whole file into memory,
                //  in order to reduce memory usage.
                val targetFileBytes = targetFile.readBytes()
                replacements.forEach { it.replacePattern(targetFileBytes) }
                targetFile.writeBytes(targetFileBytes)
            }
        }
    }

/**
 * Represents a pattern to search for and its replacement pattern in a file.
 *
 * @property bytes The bytes to search for.
 * @property replacementBytes The bytes to replace the [bytes] with.
 * @property targetFilePath The path to the file to make the changes in relative to the APK root.
 */
class Replacement(
    private val bytes: ByteArray,
    replacementBytes: ByteArray,
    internal val targetFilePath: String,
) {
    val replacementBytesPadded = replacementBytes + ByteArray(bytes.size - replacementBytes.size)

    @Deprecated("Use the constructor with ByteArray parameters instead.")
    constructor(
        pattern: String,
        replacementPattern: String,
        targetFilePath: String,
    ) : this(
        byteArrayOf(pattern),
        byteArrayOf(replacementPattern),
        targetFilePath
    )

    /**
     * Replaces the [bytes] with the [replacementBytes] in the [targetFileBytes].
     *
     * @param targetFileBytes The bytes of the file to make the changes in.
     */
    internal fun replacePattern(targetFileBytes: ByteArray) {
        val startIndex = indexOfPatternIn(targetFileBytes)

        if (startIndex == -1) {
            throw PatchException(
                "Pattern not found in target file: " +
                        bytes.joinToString(" ") { "%02x".format(it) }
            )
        }

        replacementBytesPadded.copyInto(targetFileBytes, startIndex)
    }

    // TODO: Allow searching in a file channel instead of a byte array to reduce memory usage.
    /**
     * Returns the index of the first occurrence of [bytes] in the haystack
     * using the Boyer-Moore algorithm.
     *
     * @param haystack The array to search in.
     *
     * @return The index of the first occurrence of the [bytes] in the haystack or -1
     * if the [bytes] is not found.
     */
    private fun indexOfPatternIn(haystack: ByteArray): Int {
        val needle = bytes

        val haystackLength = haystack.size - 1
        val needleLength = needle.size - 1
        val right = IntArray(256) { -1 }

        for (i in 0 until needleLength) right[needle[i].toInt().and(0xFF)] = i

        var skip: Int
        for (i in 0..haystackLength - needleLength) {
            skip = 0

            for (j in needleLength - 1 downTo 0) {
                if (needle[j] != haystack[i + j]) {
                    skip = max(1, j - right[haystack[i + j].toInt().and(0xFF)])

                    break
                }
            }

            if (skip == 0) return i
        }
        return -1
    }
}

/**
 * Convert a string representing a pattern of hexadecimal bytes to a byte array.
 *
 * @return The byte array representing the pattern.
 * @throws PatchException If the pattern is invalid.
 */
private fun byteArrayOf(pattern: String) = try {
    pattern.split(" ").map { it.toInt(16).toByte() }.toByteArray()
} catch (e: NumberFormatException) {
    throw PatchException(
        "Could not parse pattern: $pattern. A pattern is a sequence of case insensitive strings " +
                "representing hexadecimal bytes separated by spaces",
        e,
    )
}
