package app.crimera.tools.newx

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.system.exitProcess

/**
 * Small source-level guardrail for NewX patch-time discovery code.
 *
 * This is deliberately not a Kotlin compiler plug-in. The rules are about a few reviewable
 * resolver invariants, and keeping the scanner dependency-free makes it usable from a clean
 * checkout through Gradle. The scanner masks comments and string/character literals before
 * looking for calls, so examples embedded in diagnostics and documentation are not findings.
 */
internal object NewXResolverLinter {
    enum class Rule(
        val id: String,
        private val description: String,
    ) {
        RAW_FIRST("raw-first", "first() selects one candidate without proving uniqueness"),
        RAW_LAST("raw-last", "last() selects one candidate without proving uniqueness"),
        RAW_FIND("raw-find", "find() returns the first matching candidate"),
        RAW_SINGLE("raw-single", "single() requires exactly one candidate"),
        NULLABLE_FIRST("nullable-first", "firstOrNull() can silently skip a required candidate"),
        NULLABLE_LAST("nullable-last", "lastOrNull() can silently skip a required candidate"),
        NULLABLE_SINGLE("nullable-single", "singleOrNull() can hide zero or ambiguous candidates"),
        NULLABLE_INDEX("nullable-index", "a nullable indexed lookup can silently skip a candidate"),
        MAP_NOT_NULL("map-not-null", "mapNotNull can drop candidates before selection"),
        ;

        override fun toString(): String = "$id: $description"
    }

    data class Finding(
        val path: String,
        val line: Int,
        val column: Int,
        val rule: Rule,
        val message: String,
        val sourceLine: String,
    ) {
        override fun toString(): String =
            "$path:$line:$column: ${rule.id}: $message\n    ${sourceLine.trim()}"
    }

    private data class Selection(
        val operation: String,
        val start: Int,
        val callEnd: Int,
        val receiver: String,
        val callText: String,
    )

    private data class MapSite(
        val start: Int,
        val callEnd: Int,
        val receiver: String,
        val variable: String?,
    )

    private val selectionPattern =
        Regex(
            """\.\s*(singleOrNull|firstOrNull|lastOrNull|elementAtOrNull|getOrNull|single|first|last|find)\s*(?=\(|\{)""",
        )
    private val mapNotNullPattern = Regex("""\.\s*mapNotNull\s*(?=\{)""")
    private val identifierPattern = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
    private val functionPattern = Regex("""\bfun\b""")
    private val directivePattern =
        Regex("""^\s*//\s*newx-resolver-lint\s*:\s*(?:allow|ignore)\s+([^\r\n]+)""")

    private val exactCardinalityHelpers =
        setOf(
            "requireExactlyOne",
            "requireSingle",
            "requireSingleMatch",
            "assertExactlyOne",
            "requireOne",
        )
    private val atMostOneHelpers =
        setOf(
            "requireAtMostOne",
            "assertAtMostOne",
        )
    private val orderSensitiveRules =
        setOf(
            Rule.RAW_FIRST,
            Rule.RAW_LAST,
            Rule.RAW_FIND,
            Rule.RAW_SINGLE,
            Rule.NULLABLE_FIRST,
            Rule.NULLABLE_LAST,
            Rule.NULLABLE_INDEX,
            Rule.MAP_NOT_NULL,
        )
    private val nullableRules =
        setOf(
            Rule.NULLABLE_FIRST,
            Rule.NULLABLE_LAST,
            Rule.NULLABLE_SINGLE,
            Rule.NULLABLE_INDEX,
        )
    private val candidateSuffixes =
        listOf(
            "candidates",
            "matches",
            "methods",
            "fields",
            "constructors",
            "writes",
            "reads",
            "calls",
            "bridges",
            "slots",
            "scales",
            "allocations",
            "stores",
            "loads",
            "accessors",
            "getters",
            "setters",
            "references",
            "results",
            "consumers",
            "branches",
            "sites",
            "literals",
            "comparisons",
            "types",
            "indices",
        )
    private val positionalNames =
        setOf(
            "arguments",
            "argumentregisters",
            "parameters",
            "parametertypes",
            "registers",
            "registersused",
        )

    /** Lints one source string. Paths are kept as supplied for stable, useful diagnostics. */
    fun lintSource(path: String, source: String): List<Finding> {
        if (source.isEmpty()) return emptyList()

        val masked = maskKotlin(source)
        val localSequenceVariables = findLocalSequenceVariables(masked)
        val selections =
            selectionPattern.findAll(masked).map { match ->
                val start = match.range.first
                val callEnd = callEnd(masked, match.range.last + 1)
                Selection(
                    operation = match.groupValues[1],
                    start = start,
                    callEnd = callEnd,
                    receiver = receiverBefore(masked, start),
                    callText = masked.substring(start, callEnd),
                )
            }.toList()
        val mapSites =
            mapNotNullPattern.findAll(masked).map { match ->
                val start = match.range.first
                val callEnd = callEnd(masked, match.range.last + 1)
                MapSite(
                    start = start,
                    callEnd = callEnd,
                    receiver = receiverBefore(masked, start),
                    variable = assignedVariableBefore(masked, start),
                )
            }.toList()

        val findings = mutableListOf<Finding>()
        selections.forEach { selection ->
            val receiverKeys = candidateKeys(selection.receiver)
            val candidateLike =
                receiverKeys.isNotEmpty() ||
                    receiverLooksLikeCandidate(selection.receiver) ||
                    selection.receiver.lowercase().contains("matchall") ||
                    selection.receiver.lowercase().contains("mapnotnull")
            if (!candidateLike) return@forEach

            val rule = selectionRule(selection.operation) ?: return@forEach
            val localOrder =
                isLocalInstructionOrder(
                    selection = selection,
                    receiverKeys = receiverKeys,
                    localSequenceVariables = localSequenceVariables,
                )
            if (localOrder) return@forEach

            val exactProof = hasCardinalityProof(masked, selection.start, receiverKeys, exact = true)
            val atMostProof =
                exactProof || hasCardinalityProof(masked, selection.start, receiverKeys, exact = false)
            val unsafe =
                when (selection.operation) {
                    "first", "last", "find", "single" -> !exactProof
                    "singleOrNull" ->
                        !atMostProof && nullableFallthrough(masked, source, selection)
                    "firstOrNull", "lastOrNull", "elementAtOrNull", "getOrNull" ->
                        !atMostProof && nullableFallthrough(masked, source, selection)
                    else -> false
                }
            if (unsafe && !isSuppressed(source, lineOf(source, selection.start), rule)) {
                findings +=
                    finding(
                        path = path,
                        source = source,
                        index = selection.start,
                        rule = rule,
                        message = selectionMessage(selection, receiverKeys),
                    )
            }
        }

        mapSites.forEach { mapSite ->
            val mappedSelections =
                selections.filter { selection ->
                    selection.start >= mapSite.callEnd &&
                        functionStart(masked, selection.start) ==
                            functionStart(masked, mapSite.start) &&
                        selectionUsesMap(selection, mapSite)
                }
            mappedSelections.forEach { selection ->
                val receiverKeys = candidateKeys(selection.receiver)
                val candidateLike =
                    receiverKeys.isNotEmpty() ||
                        receiverLooksLikeCandidate(selection.receiver) ||
                        mapSite.variable != null ||
                        mapSite.receiver.lowercase().contains("instructions")
                if (!candidateLike) return@forEach
                val rule = selectionRule(selection.operation) ?: return@forEach
                if (selection.operation == "single" && !selectionUsesExactProof(masked, selection)) {
                    addMapFindingIfNeeded(
                        findings = findings,
                        path = path,
                        source = source,
                        mapSite = mapSite,
                        rule = rule,
                    )
                    return@forEach
                }
                val localOrder =
                    isLocalInstructionOrder(
                        selection = selection,
                        receiverKeys = receiverKeys,
                        localSequenceVariables = localSequenceVariables,
                    )
                if (localOrder && mapSite.variable == null) return@forEach
                val exactProof = selectionUsesExactProof(masked, selection)
                val atMostProof =
                    exactProof || selectionUsesAtMostProof(masked, selection)
                val unsafe =
                    when (selection.operation) {
                        "first", "last", "find" -> !exactProof
                        "singleOrNull", "firstOrNull", "lastOrNull", "elementAtOrNull", "getOrNull" ->
                            !atMostProof && nullableFallthrough(masked, source, selection)
                        else -> false
                    }
                if (
                    unsafe &&
                    !isSuppressed(source, lineOf(source, mapSite.start), Rule.MAP_NOT_NULL)
                ) {
                    addMapFindingIfNeeded(
                        findings = findings,
                        path = path,
                        source = source,
                        mapSite = mapSite,
                        rule = rule,
                    )
                }
            }
        }

        return findings
            .distinctBy { Triple(it.path, it.line to it.column, it.rule) }
            .sortedWith(compareBy<Finding> { it.line }.thenBy { it.column }.thenBy { it.rule.id })
    }

    /** Lints all Kotlin files below a source root, in deterministic path order. */
    fun lintDirectory(sourceRoot: Path): List<Finding> {
        require(sourceRoot.isDirectory()) { "NewX resolver source root is not a directory: $sourceRoot" }
        val files =
            Files.walk(sourceRoot).use { stream ->
                stream
                    .filter { path -> path.isRegularFile() && path.toString().endsWith(".kt") }
                    .iterator()
                    .asSequence()
                    .map { file ->
                        file to sourceRoot.relativize(file).toString().replace('\\', '/')
                    }.toList()
            }.sortedBy { (_, relativePath) -> relativePath }
        require(files.isNotEmpty()) { "No Kotlin NewX resolver sources found under $sourceRoot" }
        return files.flatMap { (file, relativePath) ->
            lintSource(relativePath, file.readText())
        }
    }

    private fun selectionRule(operation: String): Rule? =
        when (operation) {
            "first" -> Rule.RAW_FIRST
            "last" -> Rule.RAW_LAST
            "find" -> Rule.RAW_FIND
            "single" -> Rule.RAW_SINGLE
            "firstOrNull" -> Rule.NULLABLE_FIRST
            "lastOrNull" -> Rule.NULLABLE_LAST
            "singleOrNull" -> Rule.NULLABLE_SINGLE
            "elementAtOrNull", "getOrNull" -> Rule.NULLABLE_INDEX
            else -> null
        }

    private fun selectionMessage(selection: Selection, receiverKeys: List<String>): String {
        val receiver = receiverKeys.joinToString().ifEmpty { selection.receiver.compact() }
        val cardinality =
            if (selection.operation in setOf("single", "first", "last", "find")) {
                "exact"
            } else {
                "exact/at-most-one"
            }
        return "${selection.operation}() on candidate-like '$receiver'; require $cardinality " +
            "cardinality or use an explicit fail-closed resolver"
    }

    private fun addMapFindingIfNeeded(
        findings: MutableList<Finding>,
        path: String,
        source: String,
        mapSite: MapSite,
        rule: Rule,
    ) {
        val line = lineOf(source, mapSite.start)
        if (findings.any { it.path == path && it.line == line && it.rule == Rule.MAP_NOT_NULL }) return
        findings +=
            finding(
                path = path,
                source = source,
                index = mapSite.start,
                rule = Rule.MAP_NOT_NULL,
                message =
                    "mapNotNull result is later selected with ${rule.id}; dropped values must be " +
                        "represented by an explicit candidate/cardinality decision",
            )
    }

    private fun finding(
        path: String,
        source: String,
        index: Int,
        rule: Rule,
        message: String,
    ): Finding {
        val line = lineOf(source, index)
        val lineStart = source.lastIndexOf('\n', (index - 1).coerceAtLeast(0)).let { previous ->
            if (previous < 0) 0 else previous + 1
        }
        val lineEnd = source.indexOf('\n', index).let { next -> if (next < 0) source.length else next }
        return Finding(
            path = path,
            line = line,
            column = index - lineStart + 1,
            rule = rule,
            message = message,
            sourceLine = source.substring(lineStart, lineEnd),
        )
    }

    private fun lineOf(source: String, index: Int): Int =
        source.asSequence().take(index.coerceIn(0, source.length)).count { it == '\n' } + 1

    private fun candidateKeys(receiver: String): List<String> =
        identifierPattern.findAll(receiver)
            .map { it.value }
            .filter(::isCandidateIdentifier)
            .distinct()
            .toList()

    private fun receiverLooksLikeCandidate(receiver: String): Boolean {
        val lower = receiver.lowercase()
        return candidateSuffixes.any { suffix -> lower.contains(suffix) }
    }

    private fun isCandidateIdentifier(identifier: String): Boolean {
        val lower = identifier.lowercase()
        if (lower in positionalNames || lower == "instruction" || lower == "instructions") return false
        if (lower == "matchall" || lower == "scopedmatchall") return true
        return candidateSuffixes.any { suffix -> lower == suffix || lower.endsWith(suffix) }
    }

    private fun isOrderReceiver(receiver: String): Boolean {
        val lower = receiver.lowercase().compact()
        if (lower.contains("instructionmatches")) return true
        if (Regex("(?:^|[.])(?:instructions|methodinstructions|constructorinstructions|selectioninstructions)(?:[.]|$)")
                .containsMatchIn(lower)
        ) {
            return true
        }
        if (lower.endsWith("instructions") || lower.endsWith("instruction")) return true
        if (positionalNames.any { name -> lower == name || lower.endsWith(".$name") }) return true
        return false
    }

    private fun isLocalInstructionOrder(
        selection: Selection,
        receiverKeys: List<String>,
        localSequenceVariables: Set<String>,
    ): Boolean {
        val receiver = selection.receiver
        if (isOrderReceiver(receiver)) {
            // A transformed instruction sequence is a candidate list again; preserve the
            // mapNotNull check instead of treating it as a raw positional lookup.
            return !receiver.lowercase().contains("mapnotnull")
        }
        if (selection.operation == "singleOrNull") return false
        val receiverNames =
            identifierPattern.findAll(receiver).map { it.value }.map(String::lowercase).toSet()
        if (receiverNames.intersect(localSequenceVariables).isEmpty()) return false
        if (receiverKeys.isEmpty()) return false
        // A bounded lookup such as “the last instruction before this index” is intentional order
        // semantics. A bare first/last on an instruction-derived candidate list remains a finding.
        return INDEX_BOUND_PATTERN.containsMatchIn(selection.callText)
    }

    private fun findLocalSequenceVariables(masked: String): Set<String> {
        val names = mutableSetOf<String>()
        masked.lineSequence().forEach { line ->
            val declaration =
                LOCAL_SEQUENCE_DECLARATION_PATTERN.find(line) ?: return@forEach
            names += declaration.groupValues[1].lowercase()
        }
        return names
    }

    private fun hasCardinalityProof(
        masked: String,
        position: Int,
        receiverKeys: List<String>,
        exact: Boolean,
    ): Boolean {
        if (receiverKeys.isEmpty()) return false
        val prefix = masked.substring(functionStart(masked, position), position)
        return receiverKeys.any { key ->
            val escaped = Regex.escape(key)
            val helperNames = if (exact) exactCardinalityHelpers else exactCardinalityHelpers + atMostOneHelpers
            val helperPattern =
                Regex(
                    """\b(?:${helperNames.joinToString("|")})\s*\([^)]*\b$escaped\b""",
                    setOf(RegexOption.DOT_MATCHES_ALL),
                )
            if (helperPattern.containsMatchIn(prefix)) return@any true
            val guard =
                if (exact) {
                        Regex(
                            """(?:if\s*\([^)]*\b$escaped\b\s*\.\s*size\s*!=\s*1[^)]*\))\s*(?:\{[\s\S]{0,900}?\b(?:throw|error\s*\(|return\s+false)\b|(?:throw|error\s*\(|return\s+false))""",
                        setOf(RegexOption.DOT_MATCHES_ALL),
                    )
                } else {
                    Regex(
                        """(?:if\s*\([^)]*\b$escaped\b\s*\.\s*size\s*>\s*1[^)]*\))\s*(?:\{[\s\S]{0,900}?\b(?:throw|error\s*\(|return\s+(?:false|null))\b|(?:throw|error\s*\(|return\s+(?:false|null)))""",
                        setOf(RegexOption.DOT_MATCHES_ALL),
                    )
                }
            guard.containsMatchIn(prefix)
        }
    }

    private fun selectionUsesExactProof(masked: String, selection: Selection): Boolean =
        hasCardinalityProof(masked, selection.start, candidateKeys(selection.receiver), exact = true)

    private fun selectionUsesAtMostProof(masked: String, selection: Selection): Boolean =
        hasCardinalityProof(masked, selection.start, candidateKeys(selection.receiver), exact = false)

    private fun nullableFallthrough(
        masked: String,
        source: String,
        selection: Selection,
    ): Boolean {
        val after = masked.substring(selection.callEnd).trimStart()
        if (after.startsWith("!!")) return false
        if (after.startsWith("?.")) return true
        if (FAIL_CLOSED_ELVIS_PATTERN.containsMatchIn(after.take(600))) return false
        if (after.startsWith("?:")) {
            val rightHandSide = after.removePrefix("?:").trimStart()
            return !FAIL_CLOSED_FALLBACK_PATTERN.containsMatchIn(rightHandSide)
        }
        if (NULL_COMPARISON_PATTERN.containsMatchIn(after)) return false

        val statementStart =
            maxOf(
                masked.lastIndexOf(';', selection.start),
                masked.lastIndexOf('\n', selection.start),
            ) + 1
        val statementPrefix = masked.substring(statementStart, selection.start)
        if (Regex("""\b(?:requireNotNull|checkNotNull)\s*\($""").containsMatchIn(statementPrefix)) {
            return false
        }
        val assigned = ASSIGNMENT_PATTERN.find(statementPrefix)?.groupValues?.get(1)
        if (assigned != null) {
            val scopeEnd = sourceScopeEnd(masked, selection.callEnd)
            return !nullFailureGuard(masked, assigned, selection.callEnd, scopeEnd)
        }
        val returnPrefix = statementPrefix.trimStart()
        return returnPrefix.startsWith("return") || after.firstOrNull() !in setOf(';', ',', ')', '}')
    }

    private fun nullFailureGuard(
        masked: String,
        variable: String,
        from: Int,
        until: Int,
    ): Boolean {
        val tail = masked.substring(from, until)
        val escaped = Regex.escape(variable)
        if (Regex("""\brequireNotNull\s*\(\s*$escaped\s*\)""").containsMatchIn(tail)) return true
        return Regex(
            """if\s*\(\s*$escaped\s*==\s*null\s*\)\s*(?:\{[\s\S]{0,600}?\b(?:throw|error\s*\()\b|(?:throw|error\s*\())""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        ).containsMatchIn(tail)
    }

    private fun selectionUsesMap(selection: Selection, mapSite: MapSite): Boolean {
        if (selection.receiver.lowercase().contains("mapnotnull")) return true
        val variable = mapSite.variable?.lowercase() ?: return false
        return identifierPattern.findAll(selection.receiver)
            .any { it.value.lowercase() == variable }
    }

    private fun assignedVariableBefore(masked: String, position: Int): String? {
        val statementStart =
            maxOf(
                masked.lastIndexOf(';', position),
                masked.lastIndexOf('\n', position),
            ) + 1
        return ASSIGNMENT_PATTERN.find(masked.substring(statementStart, position))?.groupValues?.get(1)
    }

    private fun functionStart(masked: String, position: Int): Int {
        var start = 0
        functionPattern.findAll(masked).forEach { match ->
            if (match.range.first < position) start = match.range.first
        }
        return start
    }

    private fun sourceScopeEnd(masked: String, position: Int): Int {
        val depthBefore = braceDepth(masked, position)
        var depth = depthBefore
        for (index in position until masked.length) {
            when (masked[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth < depthBefore) return index
                }
            }
        }
        return masked.length
    }

    private fun braceDepth(masked: String, until: Int): Int =
        masked.substring(0, until.coerceIn(0, masked.length)).count { it == '{' } -
            masked.substring(0, until.coerceIn(0, masked.length)).count { it == '}' }

    private fun callEnd(masked: String, afterMethod: Int): Int {
        var index = afterMethod
        while (index < masked.length && masked[index].isWhitespace()) index++
        if (index >= masked.length) return index
        val closing =
            when (masked[index]) {
                '(' -> ')'
                '{' -> '}'
                else -> return index
            }
        return matchingDelimiter(masked, index, masked[index], closing)
    }

    private fun matchingDelimiter(masked: String, start: Int, opening: Char, closing: Char): Int {
        var depth = 0
        for (index in start until masked.length) {
            when (masked[index]) {
                opening -> depth++
                closing -> {
                    depth--
                    if (depth == 0) return index + 1
                }
            }
        }
        return masked.length
    }

    private fun receiverBefore(masked: String, dot: Int): String {
        var index = dot - 1
        while (index >= 0 && masked[index].isWhitespace()) index--
        var depth = 0
        while (index >= 0) {
            when (masked[index]) {
                ')', ']', '}' -> depth++
                '(', '[', '{' -> {
                    if (depth > 0) depth-- else break
                }
                '=', ';', ',', ':' -> if (depth == 0) break
                '\n' -> if (depth == 0) break
            }
            index--
        }
        return masked.substring(index + 1, dot).trim()
    }

    private fun isSuppressed(source: String, line: Int, rule: Rule): Boolean {
        val lines = source.lines()
        val relevantLines = buildList {
            lines.getOrNull(line - 1)?.let(::add)
            lines.getOrNull(line - 2)?.let(::add)
        }
        return relevantLines.any { sourceLine ->
            val match = directivePattern.find(sourceLine) ?: return@any false
            val tokens =
                match.groupValues[1]
                    .substringBefore("//")
                    .split(',', ' ', '\t')
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
            "all" in tokens ||
                rule.id in tokens ||
                ("instruction-order" in tokens && rule in orderSensitiveRules) ||
                ("optional" in tokens && rule in nullableRules)
        }
    }

    private fun String.compact(): String = replace(Regex("""\s+"""), "")

    /** Replace non-code characters with spaces while retaining newlines and source offsets. */
    private fun maskKotlin(source: String): String {
        val masked = StringBuilder(source.length)
        var index = 0
        var state = MaskState.CODE
        while (index < source.length) {
            val character = source[index]
            when (state) {
                MaskState.CODE -> {
                    when {
                        source.startsWith("//", index) -> {
                            masked.append("  ")
                            index += 2
                            state = MaskState.LINE_COMMENT
                        }
                        source.startsWith("/*", index) -> {
                            masked.append("  ")
                            index += 2
                            state = MaskState.BLOCK_COMMENT
                        }
                        source.startsWith("\"\"\"", index) -> {
                            masked.append("   ")
                            index += 3
                            state = MaskState.TRIPLE_STRING
                        }
                        character == '"' -> {
                            masked.append(' ')
                            index++
                            state = MaskState.STRING
                        }
                        character == '\'' -> {
                            masked.append(' ')
                            index++
                            state = MaskState.CHARACTER
                        }
                        else -> {
                            masked.append(character)
                            index++
                        }
                    }
                }
                MaskState.LINE_COMMENT -> {
                    masked.append(if (character == '\n') '\n' else ' ')
                    index++
                    if (character == '\n') state = MaskState.CODE
                }
                MaskState.BLOCK_COMMENT -> {
                    when {
                        source.startsWith("*/", index) -> {
                            masked.append("  ")
                            index += 2
                            state = MaskState.CODE
                        }
                        else -> {
                            masked.append(if (character == '\n') '\n' else ' ')
                            index++
                        }
                    }
                }
                MaskState.STRING, MaskState.CHARACTER -> {
                    if (character == '\\' && index + 1 < source.length) {
                        masked.append(' ')
                        masked.append(if (source[index + 1] == '\n') '\n' else ' ')
                        index += 2
                    } else {
                        masked.append(if (character == '\n') '\n' else ' ')
                        index++
                        val closing = if (state == MaskState.STRING) '"' else '\''
                        if (character == closing) state = MaskState.CODE
                    }
                }
                MaskState.TRIPLE_STRING -> {
                    if (source.startsWith("\"\"\"", index)) {
                        masked.append("   ")
                        index += 3
                        state = MaskState.CODE
                    } else {
                        masked.append(if (character == '\n') '\n' else ' ')
                        index++
                    }
                }
            }
        }
        return masked.toString()
    }

    private enum class MaskState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TRIPLE_STRING,
    }

    private val FAIL_CLOSED_FALLBACK_PATTERN =
        Regex("""^(?:throw\b|error\s*\(|requireNotNull\s*\(|checkNotNull\s*\(|fail\s*\()""")
    private val FAIL_CLOSED_ELVIS_PATTERN =
        Regex("""\?:\s*(?:throw\b|error\s*\(|requireNotNull\s*\(|checkNotNull\s*\(|fail\s*\()""")
    private val NULL_COMPARISON_PATTERN =
        Regex("""^(?:==|!=|===|!==)\s*null\b""")
    private val ASSIGNMENT_PATTERN =
        Regex("""\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*""")
    private val INDEX_BOUND_PATTERN =
        Regex("""\b[A-Za-z_][A-Za-z0-9_]*index\b\s*(?:<|>|in|until)""", RegexOption.IGNORE_CASE)
    private val LOCAL_SEQUENCE_DECLARATION_PATTERN =
        Regex(
            """\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=.*(?:instructions|instructionMatches|methodInstructions|selectionInstructions)""",
            RegexOption.IGNORE_CASE,
        )
}

private const val DEFAULT_NEWX_SOURCE_ROOT =
    "patches/src/main/kotlin/app/crimera/patches/newx"

/** Gradle's JavaExec entrypoint: `./gradlew :patches:lintNewxResolvers`. */
fun main(args: Array<String>) {
    val sourceRoot = Paths.get(args.firstOrNull { !it.startsWith("--") } ?: DEFAULT_NEWX_SOURCE_ROOT)
    val reportOnly = "--report-only" in args
    val findings = NewXResolverLinter.lintDirectory(sourceRoot)
    if (findings.isEmpty()) {
        println("NewX resolver lint passed: $sourceRoot")
        return
    }

    findings.forEach { finding -> System.err.println(finding) }
    System.err.println("NewX resolver lint found ${findings.size} issue(s) in $sourceRoot")
    if (!reportOnly) exitProcess(1)
}
