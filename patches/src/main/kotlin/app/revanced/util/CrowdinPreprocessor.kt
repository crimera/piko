package app.revanced.util

import java.io.File

/**
 * Comments out the non-standard <app> and <patch> tags.
 *
 * Previously this was done on Crowdin after pushing.
 * But Crowdin preprocessing has randomly failed but still used the unmodified
 * strings.xml file, which effectively deletes all patch strings from Crowdin.
 */
internal fun main(args: Array<String>) {
    if (args.size != 2) {
        throw RuntimeException("Exactly two arguments are required: <input_file> <output_file>")
    }

    val inputFilePath = args[0]
    val inputFile = File(inputFilePath)
    if (!inputFile.exists()) {
        throw RuntimeException(
            "Input file not found: $inputFilePath  currentDirectory: " + File(".").canonicalPath
        )
    }

    // Comment out the non-standard tags. Otherwise Crowdin interprets the file
    // not as Android but instead a generic xml file where strings are
    // identified by xml position and not key.
    val content = inputFile.readText()
    val tagRegex = """((<app\s+.*>)|(</app>)|(<patch\s+.*>)|(</patch>))""".toRegex()
    val modifiedContent = content.replace(tagRegex, """<!-- $1 -->""")

    // Write modified content to the output file (creates file if it doesn't exist).
    val outputFilePath = args[1]
    val outputFile = File(outputFilePath)
    outputFile.parentFile?.mkdirs()
    outputFile.writeText(modifiedContent)

    println("Preprocessed strings.xml to: $outputFilePath")
}

