package app.crimera.patches.newx.settings

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
    CUSTOM_SCREEN,
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
    val iconResourceName: String?,
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
        require(catalogs.isNotEmpty()) { "No NewX settings contributions were discovered" }

        val errors = mutableListOf<String>()
        val settings = linkedMapOf<String, AggregateSetting>()
        val groups = linkedMapOf<String, AggregateGroup>()

        fun validateResource(resourceName: String?, ownerId: String) {
            if (resourceName == null || resourceName in resourceNames) return
            errors += "Missing NewX string resource $resourceName referenced by $ownerId"
        }

        fun visitGroup(group: AggregateGroup) {
            val existing = groups.putIfAbsent(group.id, group)
            if (existing != null && existing.metadata() != group.metadata()) {
                errors += "Incompatible repeated NewX group metadata for ${group.id}"
            }
            if (group.id in settings) {
                errors += "NewX ID is used by both a group and setting: ${group.id}"
            }
            validateResource(group.titleResourceName, group.id)
            validateResource(group.summaryResourceName, group.id)

            group.children.forEach { node ->
                when (node) {
                    is AggregateGroupNode -> visitGroup(node.group)
                    is AggregateSettingNode -> {
                        val setting = node.setting
                        if (settings.putIfAbsent(setting.id, setting) != null) {
                            errors += "Duplicate NewX setting ID across contributions: ${setting.id}"
                        }
                        if (setting.id in groups) {
                            errors += "NewX ID is used by both a group and setting: ${setting.id}"
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
                errors += "Unknown NewX registry read ${read.id} at ${read.location}"
                return@forEach
            }
            if (definition.type != read.type) {
                errors +=
                    "NewX registry read type mismatch for ${read.id} at ${read.location}: " +
                        "${read.type} reader, ${definition.type} definition"
            }
        }

        require(errors.isEmpty()) { errors.joinToString(prefix = "\n", separator = "\n") }
    }

    private fun AggregateGroup.metadata() =
        listOf(titleResourceName, summaryResourceName, iconResourceName, order)
}

internal object NewXContributionDiscovery {
    private const val PACKAGE_PATH = "app/crimera/patches/newx"
    private const val INDEX_CLASS =
        "app.crimera.patches.newx.settings.SettingsContributionIndex"

    fun discover(): List<AggregateCatalog> {
        val outputRoot =
            Path.of(
                SettingsContributionIndex::class.java.protectionDomain.codeSource.location.toURI(),
            )
        val patchClasses = discoverPatchClasses(outputRoot)
        require(patchClasses.isNotEmpty()) {
            "No NewX *PatchKt classes found under $outputRoot"
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
            iconResourceName = value.property("IconResourceName"),
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
                "TextInputSettingDefinition",
                "SingleChoiceSettingDefinition",
                -> AggregateSettingType.STRING
                "MultiChoiceSettingDefinition" -> AggregateSettingType.STRING_SET
                "ActionSettingDefinition" -> AggregateSettingType.ACTION
                "CustomScreenSettingDefinition" -> AggregateSettingType.CUSTOM_SCREEN
                else -> error("Unknown NewX settings node ${value.javaClass.name}")
            }
        val choices =
            if (type == AggregateSettingType.STRING_SET || value.javaClass.simpleName == "SingleChoiceSettingDefinition") {
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

internal object NewXValidationInputs {
    private val stringNamePattern = Regex("""<string\s+name=[\"']([^\"']+)[\"']""")
    private val constantPattern =
        Regex("""(?:public|protected|private)?\s*static\s+final\s+String\s+(\w+)\s*=\s*\"(newx\.[^\"]+)\"\s*;""")
    private val registryReadPattern =
        Regex(
            """SettingsRegistry\s*\.\s*get(Boolean|String|StringSet)(?:OrDefault)?\s*\(\s*(\"newx\.[^\"]+\"|\w+)""",
        )

    fun repositoryRoot(): Path {
        var path = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (path.parent != null) {
            if (Files.isDirectory(path.resolve("patches/src/main")) &&
                Files.isDirectory(path.resolve("extensions/newx/src/main"))
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
                "patches/src/main/resources/addresources/values/newx/strings.xml",
            )
        return stringNamePattern.findAll(Files.readString(strings)).map { it.groupValues[1] }.toSet()
    }

    fun registryReads(repositoryRoot: Path): List<AggregateRegistryRead> {
        val sourceRoot = repositoryRoot.resolve("extensions/newx/src/main/java")
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
            else -> error("Unknown NewX registry reader get$name")
        }
}
