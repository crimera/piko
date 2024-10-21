package app.util

import java.io.File

inline fun measureExecutionTime(block: () -> Unit): Long {
    val start = System.currentTimeMillis()
    block()
    val end = System.currentTimeMillis()
    return end - start
}

fun fixStrings(file: File) {
    val regex = Regex("""(<string\s+name="conference_default_title">)([^<]*)(<\/string>)""")
    val defaultValue = "&#120143; Conference"
    val content = file.readText()
    file.writeText(
        content.replace(regex) {
            "${it.groupValues[1]}$defaultValue${it.groupValues[3]}"
        }
    )
}