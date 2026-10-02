package app.crimera.tools.newx

import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.util.ReferenceUtil
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess

private const val DEFAULT_EXTENSIONS_ROOT = "build/resources/main/extensions"
private const val DEFAULT_SOURCE_ROOT = "src/main/kotlin"
private const val EXTENSION_MARKER = "Lapp/morphe/extension/"

/** Kotlin escape for a literal dollar inside an interpolated string. */
private val DOLLAR_ESCAPE = "\${'\$'}"

private val STRING_CONSTANT =
    Regex("""(?:const\s+)?val\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*String\s*)?=\s*"([^"\n]*)"""")

private val INTERPOLATION = Regex("""\$\{([A-Za-z_][A-Za-z0-9_.]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)""")

private val IMPORT_DECLARATION = Regex("""^import\s+([\w.]+?)(?:\.\*)?\s*$""", RegexOption.MULTILINE)

/**
 * `L<owner>;-><member>(<proto>)<return>` inside one line of Kotlin source. Both the owner and
 * the descriptor may interpolate constants, so matching is deliberately loose and resolution
 * happens afterwards.
 *
 * Field references (`L<owner>;-><name>:<type>`) are not matched: extension fields are read
 * through patch-time resolution, never through a literal descriptor in this code base.
 */
private val MEMBER_REFERENCE =
    Regex(
        """([A-Za-z0-9/_${'$'}.{}\[\]<>;-]*?)->([A-Za-z0-9_${'$'}<>]+)\(([A-Za-z0-9/;\[${'$'}_.\-]*)\)([A-Za-z0-9/;\[${'$'}_.\-]*)""",
    )

/** A Kotlin source file scanned for descriptor references. */
internal data class SourceFile(
    val path: String,
    val text: String,
)

/** A member reference found in patch source, with the owner and member fully resolved. */
internal data class DescriptorReference(
    val path: String,
    val line: Int,
    val owner: String,
    val member: String,
) {
    override fun toString(): String = "$path:$line: $owner->$member"
}

/** A reference the linter could not resolve, kept so the report states its real coverage. */
internal data class SkippedReference(
    val path: String,
    val line: Int,
    val reference: String,
) {
    override fun toString(): String = "$path:$line: $reference"
}

internal class ExtensionIndex(
    private val classes: Set<String>,
    private val methods: Map<String, Set<String>>,
    private val fields: Map<String, Set<String>>,
) {
    val classCount: Int get() = classes.size
    val methodCount: Int get() = methods.values.sumOf(Set<String>::size)

    fun declares(reference: DescriptorReference): Boolean {
        if (reference.owner !in classes) return false
        val declared =
            if (reference.member.contains(':')) {
                fields[reference.owner].orEmpty()
            } else {
                methods[reference.owner].orEmpty()
            }
        return reference.member in declared
    }

    /** Same-name or same-prefix members of the owner, to make a typo obvious in the report. */
    fun candidates(reference: DescriptorReference): List<String> {
        val declared =
            if (reference.member.contains(':')) {
                fields[reference.owner].orEmpty()
            } else {
                methods[reference.owner].orEmpty()
            }
        val name = reference.member.substringBefore('(').substringBefore(':')
        return declared
            .filter { member -> member.startsWith(name.take(4)) }
            .sorted()
            .take(3)
    }
}

internal fun parseExtensionIndex(files: List<File>): ExtensionIndex {
    val classes = linkedSetOf<String>()
    val methods = linkedMapOf<String, MutableSet<String>>()
    val fields = linkedMapOf<String, MutableSet<String>>()
    files.forEach { file ->
        val dexFile = DexFileFactory.loadDexFile(file, Opcodes.getDefault())
        dexFile.classes.forEach { classDef ->
            val owner = classDef.type
            classes.add(owner)
            classDef.methods.forEach { method ->
                methods.getOrPut(owner) { linkedSetOf() }
                    .add(ReferenceUtil.getMethodDescriptor(method).substringAfter("->"))
            }
            classDef.fields.forEach { field ->
                fields.getOrPut(owner) { linkedSetOf() }.add("${field.name}:${field.type}")
            }
        }
    }
    return ExtensionIndex(classes, methods, fields)
}

/**
 * Resolves descriptor constants the way Kotlin does, as far as descriptor scanning needs it.
 *
 * Every declaration is resolved in the scope of the file that *declares* it, so a chain such as
 * `INTEGRATIONS_PACKAGE` -> `PATCHES_DESCRIPTOR` keeps the value of its own package no matter
 * which file uses it. A use site sees its file-local declarations, the declarations of the files
 * it imports, then names declared exactly once in the tree, then names every file spells
 * identically (`STRING_DESCRIPTOR`). A name declared in several files with different values and
 * used without importing one of them stays unresolved: treating it as global previously resolved
 * `$PATCHES_DESCRIPTOR/...` against an unrelated package and produced false failures, so
 * ambiguity now means "skip", never "fail".
 */
internal class ConstantScopes(
    private val perFile: Map<String, Map<String, String>>,
    private val declaringFileCounts: Map<String, Int>,
) {
    fun forFile(path: String): ConstantScope = ConstantScope(perFile[path].orEmpty(), declaringFileCounts)
}

/** The constants visible in one file, plus whether a name is declared inconsistently elsewhere. */
internal class ConstantScope(
    private val values: Map<String, String>,
    private val declaringFileCounts: Map<String, Int>,
) {
    fun lookup(name: String): String? = values[name]

    fun isAmbiguous(name: String): Boolean = (declaringFileCounts[name] ?: 0) > 1

    companion object {
        val EMPTY = ConstantScope(emptyMap(), emptyMap())
    }
}

private data class Declaration(
    val path: String,
    val name: String,
)

internal fun collectConstantScopes(sources: List<SourceFile>): ConstantScopes {
    val declarations = sources.associate { file -> file.path to rawDeclarations(file.text) }
    val filesByQualifiedName = sources.associateBy { file -> file.path.removeSuffix(".kt").replace('/', '.') }
    val importedPaths = sources.associate { file -> file.path to importedFilePaths(file, filesByQualifiedName) }
    val packagePaths = sources.groupBy { file -> file.path.substringBeforeLast('/', "") }
        .mapValues { (_, files) -> files.map { file -> file.path } }

    val declaringFiles = mutableMapOf<String, MutableSet<String>>()
    declarations.forEach { (path, constants) ->
        constants.keys.forEach { name -> declaringFiles.getOrPut(name) { linkedSetOf() }.add(path) }
    }
    val uniqueDeclaration = mutableMapOf<String, String>()
    val sharedSafely = mutableMapOf<String, String>()
    declaringFiles.forEach { (name, files) ->
        val values = files.mapNotNull { path -> declarations[path]?.get(name) }.distinct()
        if (files.size == 1) uniqueDeclaration[name] = files.first()
        if (values.size == 1 && '$' !in values.single()) sharedSafely[name] = values.single()
    }

    val packageDeclarationCounts = mutableMapOf<Pair<String, String>, Int>()
    packagePaths.forEach { (directory, paths) ->
        val names = mutableMapOf<String, Int>()
        paths.forEach { path -> declarations[path].orEmpty().keys.forEach { name -> names.merge(name, 1, Int::plus) } }
        names.forEach { (name, count) -> packageDeclarationCounts[directory to name] = count }
    }

    val resolved = linkedMapOf<Declaration, String>()
    fun lookup(
        name: String,
        fromPath: String,
    ): String? =
        resolved[Declaration(fromPath, name)]
            ?: importedPaths.getValue(fromPath).firstNotNullOfOrNull { path ->
                resolved[Declaration(path, name)]
            }
            ?: packageSiblings(fromPath, name, packagePaths, packageDeclarationCounts)
                .firstNotNullOfOrNull { path -> resolved[Declaration(path, name)] }
            ?: uniqueDeclaration[name]?.let { path -> resolved[Declaration(path, name)] }
            ?: sharedSafely[name]

    repeat(MAXIMUM_CONSTANT_PASSES) {
        var progress = false
        declarations.forEach { (path, constants) ->
            constants.forEach { (name, raw) ->
                if (resolved.containsKey(Declaration(path, name))) return@forEach
                resolveInterpolations(raw) { identifier -> lookup(identifier, path) }?.let { value ->
                    resolved[Declaration(path, name)] = value
                    progress = true
                }
            }
        }
        if (!progress) return@repeat
    }

    val scopeByFile =
        sources.associate { file ->
            val scope = linkedMapOf<String, String>()
            declarations.getValue(file.path).keys.forEach { name ->
                resolved[Declaration(file.path, name)]?.let { value -> scope[name] = value }
            }
            importedPaths.getValue(file.path).forEach { path ->
                declarations[path].orEmpty().keys.forEach { name ->
                    resolved[Declaration(path, name)]?.let { value -> scope.putIfAbsent(name, value) }
                }
            }
            packageSiblings(file.path, null, packagePaths, packageDeclarationCounts).forEach { path ->
                declarations[path].orEmpty().keys.forEach { name ->
                    resolved[Declaration(path, name)]?.let { value -> scope.putIfAbsent(name, value) }
                }
            }
            uniqueDeclaration.forEach { (name, path) ->
                resolved[Declaration(path, name)]?.let { value -> scope.putIfAbsent(name, value) }
            }
            sharedSafely.forEach { (name, value) -> scope.putIfAbsent(name, value) }
            file.path to scope.toMap()
        }
    return ConstantScopes(scopeByFile, declaringFiles.mapValues { (_, files) -> files.size })
}

private const val MAXIMUM_CONSTANT_PASSES = 8

/**
 * Sibling files of the same package (directory) that declare [name], or all siblings when [name]
 * is null. Kotlin resolves same-package declarations without an import, but only names the
 * package declares once are used, so a duplicate cannot be picked arbitrarily.
 */
private fun packageSiblings(
    fromPath: String,
    name: String?,
    packagePaths: Map<String, List<String>>,
    packageDeclarationCounts: Map<Pair<String, String>, Int>,
): List<String> {
    val directory = fromPath.substringBeforeLast('/', "")
    return packagePaths[directory].orEmpty().filter { path ->
        path != fromPath && (name == null || packageDeclarationCounts[directory to name] == 1)
    }
}

/** Files an `import` statement can point at: the file itself, or the file holding an imported member. */
private fun importedFilePaths(
    file: SourceFile,
    filesByQualifiedName: Map<String, SourceFile>,
): Set<String> {
    val paths = linkedSetOf<String>()
    IMPORT_DECLARATION.findAll(file.text).forEach { match ->
        val segments = match.groupValues[1].split('.')
        for (length in segments.size downTo 1) {
            val source = filesByQualifiedName[segments.take(length).joinToString(".")] ?: continue
            paths.add(source.path)
            break
        }
    }
    return paths
}

private fun rawDeclarations(text: String): Map<String, String> {
    val declarations = linkedMapOf<String, String>()
    STRING_CONSTANT.findAll(text.replace(DOLLAR_ESCAPE, DOLLAR_PLACEHOLDER)).forEach { match ->
        declarations.putIfAbsent(match.groupValues[1], match.groupValues[2])
    }
    return declarations
}

/** Resolves `$CONSTANT`/`${CONSTANT}` interpolations, or null when any part stays dynamic. */
private fun resolveInterpolations(
    text: String,
    lookup: (String) -> String?,
): String? {
    var unresolved = false
    val resolvedText =
        INTERPOLATION.replace(text) { match ->
            val identifier = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val value = lookup(identifier.substringBefore('.'))
            if (value == null) {
                unresolved = true
                match.value
            } else {
                value
            }
        }
    if (unresolved) return null
    return resolvedText.replace(DOLLAR_PLACEHOLDER, "$")
}

/**
 * Stands in for `${'$'}` while a line is scanned. It only uses characters that are valid inside
 * a descriptor, so a literal dollar never splits a reference match.
 */
private const val DOLLAR_PLACEHOLDER = "z0dollar0z"

internal fun findDescriptorReferences(
    path: String,
    text: String,
    constants: ConstantScope,
): Pair<List<DescriptorReference>, List<SkippedReference>> {
    val references = mutableListOf<DescriptorReference>()
    val skipped = mutableListOf<SkippedReference>()
    text.lineSequence().forEachIndexed { index, line ->
        val lineNumber = index + 1
        // A literal dollar inside a descriptor is written as the `${'$'}` idiom; fold it into a
        // placeholder first so the reference pattern sees plain characters.
        val sanitized = line.replace(DOLLAR_ESCAPE, DOLLAR_PLACEHOLDER)
        if ("->" !in sanitized || EXTENSION_MARKER !in sanitized && !sanitized.contains('$')) {
            return@forEachIndexed
        }
        MEMBER_REFERENCE.findAll(sanitized).forEach { match ->
            val raw = match.value
            val resolved = resolveInterpolations(raw) { identifier -> constants.lookup(identifier) }
            // The marker can only be missing here when the owner itself is a constant, so resolve
            // the owner separately before deciding this reference is not an extension call.
            val ownerText =
                resolveInterpolations(raw.substringBefore("->")) { identifier -> constants.lookup(identifier) }
                    .orEmpty()
            val isExtensionCall =
                resolved?.contains(EXTENSION_MARKER) == true ||
                    ownerText.contains(EXTENSION_MARKER) ||
                    raw.contains(EXTENSION_MARKER)
            if (!isExtensionCall && !hasAmbiguousInterpolation(raw, constants)) return@forEach
            if (resolved == null) {
                skipped.add(SkippedReference(path, lineNumber, raw))
                return@forEach
            }
            val markerIndex = resolved.indexOf(EXTENSION_MARKER)
            if (markerIndex < 0) return@forEach
            val owner = resolved.substring(markerIndex).substringBefore("->")
            if (!owner.endsWith(";")) return@forEach
            references.add(
                DescriptorReference(
                    path = path,
                    line = lineNumber,
                    owner = owner,
                    member = resolved.substringAfter("->"),
                ),
            )
        }
    }
    return references to skipped
}

/**
 * True when the owner interpolates a constant that several files declare differently. Such a
 * reference cannot be resolved, but it must be reported instead of silently shrinking coverage.
 */
private fun hasAmbiguousInterpolation(
    raw: String,
    constants: ConstantScope,
): Boolean =
    INTERPOLATION.findAll(raw.substringBefore("->")).any { match ->
        val identifier = match.groupValues[1].ifEmpty { match.groupValues[2] }
        constants.lookup(identifier.substringBefore('.')) == null && constants.isAmbiguous(identifier.substringBefore('.'))
    }

private fun kotlinSources(root: File): List<SourceFile> =
    root.walkTopDown()
        .filter { it.isFile && it.name.endsWith(".kt") }
        .sorted()
        .map { file -> SourceFile(root.toPath().relativize(file.toPath()).toString(), file.readText()) }
        .toList()

private fun extensionDexes(root: File): List<File> =
    root.listFiles { file -> file.isFile && file.name.endsWith(".mpe") }
        .orEmpty()
        .sorted()

fun main(args: Array<String>) {
    val options =
        args.filter { it.startsWith("--") && '=' in it }
            .associate { it.substringBefore('=').removePrefix("--") to it.substringAfter('=') }
    val flags = args.filter { it.startsWith("--") && '=' !in it }.toSet()
    val extensionsRoot = Paths.get(options["extensions"] ?: DEFAULT_EXTENSIONS_ROOT).toFile()
    val sourceRoot = Paths.get(options["sources"] ?: DEFAULT_SOURCE_ROOT).toFile()

    val dexes = extensionDexes(extensionsRoot)
    require(dexes.isNotEmpty()) {
        "No .mpe extension dex found in ${extensionsRoot.absolutePath}. " +
            "Build them first: ./gradlew :patches:classes"
    }
    val sources = kotlinSources(sourceRoot)
    require(sources.isNotEmpty()) {
        "No Kotlin patch sources found in ${sourceRoot.absolutePath}"
    }

    val index = parseExtensionIndex(dexes)
    val scopes = collectConstantScopes(sources)
    val references = mutableListOf<DescriptorReference>()
    val skipped = mutableListOf<SkippedReference>()
    sources.forEach { file ->
        val (fileReferences, fileSkipped) =
            findDescriptorReferences(file.path, file.text, scopes.forFile(file.path))
        references.addAll(fileReferences)
        skipped.addAll(fileSkipped)
    }

    val failures = references.filterNot(index::declares)
    println(
        "Extension descriptor check: ${references.size} reference(s) validated against " +
            "${index.classCount} classes / ${index.methodCount} methods in ${dexes.size} .mpe file(s)",
    )
    if (skipped.isNotEmpty()) {
        println("${skipped.size} reference(s) skipped because a constant or member stays dynamic:")
        skipped.take(20).forEach { println("  $it") }
        if (skipped.size > 20) println("  ... ${skipped.size - 20} more")
    }
    if (failures.isEmpty()) return

    failures.forEach { reference ->
        val candidates = index.candidates(reference)
        val hint = if (candidates.isEmpty()) "" else " candidates: ${candidates.joinToString()}"
        System.err.println("$reference is not declared by the extension dex.$hint")
    }
    System.err.println("Extension descriptor check found ${failures.size} unresolved reference(s)")
    if ("--report-only" !in flags) exitProcess(1)
}
