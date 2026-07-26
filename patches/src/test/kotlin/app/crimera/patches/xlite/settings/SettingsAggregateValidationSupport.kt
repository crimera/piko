package app.crimera.patches.xlite.settings

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name

enum class AggregateSettingType {
    BOOLEAN,
    STRING,
    STRING_SET,
    ACTION,
}

data class AggregateSetting(
    val id: String,
    val titleResourceName: String,
    val summaryResourceName: String?,
    val type: AggregateSettingType,
    val choiceResourceNames: List<String> = emptyList(),
)

data class AggregateGroup(
    val id: String,
    val titleResourceName: String,
    val summaryResourceName: String?,
    val order: Int,
    val children: List<AggregateNode>,
)

sealed interface AggregateNode

data class AggregateGroupNode(val group: AggregateGroup) : AggregateNode

data class AggregateSettingNode(val setting: AggregateSetting) : AggregateNode

data class AggregateCatalog(val categories: List<AggregateGroup>)

data class AggregateRegistryRead(
    val id: String,
    val type: AggregateSettingType,
    val location: String,
)

internal object SettingsAggregateValidator {
    fun validate(
        catalogs: List<AggregateCatalog>,
        resourceNames: Set<String>,
        registryReads: List<AggregateRegistryRead>,
    ) {
        require(catalogs.isNotEmpty()) { "No X-Lite settings contributions were discovered" }

        val errors = mutableListOf<String>()
        val settings = linkedMapOf<String, AggregateSetting>()
        val groups = linkedMapOf<String, AggregateGroup>()

        fun validateResource(resourceName: String?, ownerId: String) {
            if (resourceName == null || resourceName in resourceNames) return
            errors += "Missing X-Lite string resource $resourceName referenced by $ownerId"
        }

        fun visitGroup(group: AggregateGroup) {
            val existing = groups.putIfAbsent(group.id, group)
            if (existing != null && existing.metadata() != group.metadata()) {
                errors += "Incompatible repeated X-Lite group metadata for ${group.id}"
            }
            if (group.id in settings) {
                errors += "X-Lite ID is used by both a group and setting: ${group.id}"
            }
            validateResource(group.titleResourceName, group.id)
            validateResource(group.summaryResourceName, group.id)

            group.children.forEach { node ->
                when (node) {
                    is AggregateGroupNode -> visitGroup(node.group)
                    is AggregateSettingNode -> {
                        val setting = node.setting
                        if (settings.putIfAbsent(setting.id, setting) != null) {
                            errors += "Duplicate X-Lite setting ID across contributions: ${setting.id}"
                        }
                        if (setting.id in groups) {
                            errors += "X-Lite ID is used by both a group and setting: ${setting.id}"
                        }
                        validateResource(setting.titleResourceName, setting.id)
                        validateResource(setting.summaryResourceName, setting.id)
                        setting.choiceResourceNames.forEach { validateResource(it, setting.id) }
                    }
                }
            }
        }

        catalogs.flatMap(AggregateCatalog::categories).forEach(::visitGroup)

        registryReads.forEach { read ->
            val definition = settings[read.id]
            if (definition == null) {
                errors += "Unknown X-Lite registry read ${read.id} at ${read.location}"
                return@forEach
            }
            if (definition.type != read.type) {
                errors +=
                    "X-Lite registry read type mismatch for ${read.id} at ${read.location}: " +
                        "${read.type} reader, ${definition.type} definition"
            }
        }

        require(errors.isEmpty()) { errors.joinToString(prefix = "\n", separator = "\n") }
    }

    private fun AggregateGroup.metadata() =
        listOf(titleResourceName, summaryResourceName, order)
}

internal object XLiteContributionDiscovery {
    private const val PACKAGE_PATH = "app/crimera/patches/xlite"
    private const val INDEX_CLASS =
        "app.crimera.patches.xlite.settings.SettingsContributionIndex"

    fun discover(): List<AggregateCatalog> {
        val outputRoot =
            Path.of(
                SettingsContributionIndex::class.java.protectionDomain.codeSource.location.toURI(),
            )
        val patchClasses = discoverPatchClasses(outputRoot)
        require(patchClasses.isNotEmpty()) {
            "No X-Lite *PatchKt classes found under $outputRoot"
        }

        val urls =
            System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .map { Path.of(it).toUri().toURL() }
                .toTypedArray()

        URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
            patchClasses.forEach { className -> Class.forName(className, true, loader) }
            val indexClass = Class.forName(INDEX_CLASS, true, loader)
            val index = indexClass.getField("INSTANCE").get(null)
            val snapshotMethod =
                indexClass.methods.single { it.name == "snapshot" || it.name.startsWith("snapshot$") }
            val catalogs = snapshotMethod.invoke(index) as List<*>
            return catalogs.map(::catalogSnapshot)
        }
    }

    private fun discoverPatchClasses(outputRoot: Path): List<String> {
        val packageRoot = outputRoot.resolve(PACKAGE_PATH)
        if (!packageRoot.isDirectory()) return emptyList()

        return Files.walk(packageRoot).use { paths ->
            paths
                .filter { path ->
                    path.extension == "class" &&
                        '$' !in path.name &&
                        path.name.endsWith("PatchKt.class")
                }.map { path ->
                    outputRoot
                        .relativize(path)
                        .toString()
                        .removeSuffix(".class")
                        .replace(File.separatorChar, '.')
                }.sorted()
                .toList()
        }
    }

    private fun catalogSnapshot(catalog: Any?): AggregateCatalog {
        requireNotNull(catalog)
        val categories = catalog.property<List<*>>("Categories")
        return AggregateCatalog(categories.map(::groupSnapshot))
    }

    private fun groupSnapshot(value: Any?): AggregateGroup {
        requireNotNull(value)
        return AggregateGroup(
            id = value.property("Id"),
            titleResourceName = value.property("TitleResourceName"),
            summaryResourceName = value.property("SummaryResourceName"),
            order = value.property("Order"),
            children = value.property<List<*>>("Children").map(::nodeSnapshot),
        )
    }

    private fun nodeSnapshot(value: Any?): AggregateNode {
        requireNotNull(value)
        if (value.javaClass.simpleName == "SettingsGroupDefinition") {
            return AggregateGroupNode(groupSnapshot(value))
        }

        val type =
            when (value.javaClass.simpleName) {
                "ToggleSettingDefinition" -> AggregateSettingType.BOOLEAN
                "TextInputSettingDefinition" -> AggregateSettingType.STRING
                "MultiChoiceSettingDefinition" -> AggregateSettingType.STRING_SET
                "ActionSettingDefinition" -> AggregateSettingType.ACTION
                else -> error("Unknown X-Lite settings node ${value.javaClass.name}")
            }
        val choices =
            if (type == AggregateSettingType.STRING_SET) {
                value.property<List<*>>("Options").map { option ->
                    requireNotNull(option).property<String>("TitleResourceName")
                }
            } else {
                emptyList()
            }
        return AggregateSettingNode(
            AggregateSetting(
                id = value.property("Id"),
                titleResourceName = value.property("TitleResourceName"),
                summaryResourceName = value.property("SummaryResourceName"),
                type = type,
                choiceResourceNames = choices,
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.property(suffix: String): T =
        javaClass.getMethod("get$suffix").invoke(this) as T
}

internal object XLiteValidationInputs {
    private val stringNamePattern = Regex("""<string\s+name=[\"']([^\"']+)[\"']""")
    private val constantPattern =
        Regex("""(?:public|protected|private)?\s*static\s+final\s+String\s+(\w+)\s*=\s*\"(xlite\.[^\"]+)\"\s*;""")
    private val registryReadPattern =
        Regex("""SettingsRegistry\s*\.\s*get(Boolean|String|StringSet)\s*\(\s*(\"xlite\.[^\"]+\"|\w+)\s*\)""")

    fun repositoryRoot(): Path {
        var path = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (path.parent != null) {
            if (Files.isDirectory(path.resolve("patches/src/main")) &&
                Files.isDirectory(path.resolve("extensions/xlite/src/main"))
            ) {
                return path
            }
            path = path.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }

    fun resourceNames(repositoryRoot: Path): Set<String> {
        val strings =
            repositoryRoot.resolve(
                "patches/src/main/resources/addresources/values/xlite/strings.xml",
            )
        return stringNamePattern.findAll(Files.readString(strings)).map { it.groupValues[1] }.toSet()
    }

    fun registryReads(repositoryRoot: Path): List<AggregateRegistryRead> {
        val sourceRoot = repositoryRoot.resolve("extensions/xlite/src/main/java")
        val reads = mutableListOf<AggregateRegistryRead>()
        Files.walk(sourceRoot).use { paths ->
            paths.filter { it.extension == "java" }.forEach { path ->
                val source = Files.readString(path)
                val constants =
                    constantPattern.findAll(source).associate {
                        it.groupValues[1] to it.groupValues[2]
                    }
                registryReadPattern.findAll(source).forEach { match ->
                    val argument = match.groupValues[2]
                    val id =
                        if (argument.startsWith('"')) {
                            argument.trim('"')
                        } else {
                            constants[argument] ?: return@forEach
                        }
                    reads +=
                        AggregateRegistryRead(
                            id = id,
                            type = readerType(match.groupValues[1]),
                            location = repositoryRoot.relativize(path).toString(),
                        )
                }
            }
        }
        return reads
    }

    private fun readerType(name: String) =
        when (name) {
            "Boolean" -> AggregateSettingType.BOOLEAN
            "String" -> AggregateSettingType.STRING
            "StringSet" -> AggregateSettingType.STRING_SET
            else -> error("Unknown X-Lite registry reader get$name")
        }
}
