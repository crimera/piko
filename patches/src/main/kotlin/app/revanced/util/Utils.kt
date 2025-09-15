package app.revanced.util

internal object Utils {
    internal fun String.trimIndentMultiline() =
        this.split("\n")
            .joinToString("\n") { it.trimIndent() } // Remove the leading whitespace from each line.
            .trimIndent() // Remove the leading newline.
}

internal fun Boolean.toHexString(): String = if (this) "0x1" else "0x0"