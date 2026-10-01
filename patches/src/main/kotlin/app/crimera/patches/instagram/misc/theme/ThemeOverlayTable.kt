/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import com.reandroid.apk.xmlencoder.XMLTableBlockEncoder
import com.reandroid.arsc.base.Block
import java.io.File
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal data class OverlayValues(
    val day: Map<String, String>,
    val night: Map<String, String>,
    val dayApi34: Map<String, String>? = null,
    val nightApi34: Map<String, String>? = null,
    val dayDrawables: Map<String, String> = emptyMap(),
    val nightDrawables: Map<String, String> = emptyMap(),
) {
    init {
        require((dayApi34 == null) == (nightApi34 == null))
        require(dayApi34 == null || dayApi34.keys == day.keys)
        require(nightApi34 == null || nightApi34.keys == night.keys)
        require(dayDrawables.keys == nightDrawables.keys)
    }
}

internal fun buildThemeOverlayTable(
    sourceManifest: File,
    sourcePublic: File,
    packageName: String,
    outputFile: File,
    values: OverlayValues,
): File {
    require(sourceManifest.isFile)
    require(sourcePublic.isFile)
    require(packageName.isNotBlank())
    require(values.day.keys == values.night.keys)

    val workRoot = Files.createTempDirectory("piko-theme-overlay").toFile()
    try {
        sourceManifest
            .copyTo(workRoot.resolve("AndroidManifest.xml"), overwrite = true)

        val targetPackage = workRoot.resolve("resources/$packageName")
        val targetValues = targetPackage.resolve("res/values")
        targetValues.mkdirs()
        writePublicSubset(
            source = sourcePublic,
            output = targetValues.resolve("public.xml"),
            values = values,
        )

        writeValuesXml(
            targetPackage.resolve("res/values-v31/colors.xml"),
            values.day,
        )
        writeValuesXml(
            targetPackage.resolve("res/values-night-v31/colors.xml"),
            values.night,
        )
        writeTypedItemsXml(
            targetPackage.resolve("res/values-v31/drawables.xml"),
            "drawable",
            values.dayDrawables,
        )
        writeTypedItemsXml(
            targetPackage.resolve("res/values-night-v31/drawables.xml"),
            "drawable",
            values.nightDrawables,
        )
        values.dayApi34?.let { api34Values ->
            writeValuesXml(
                targetPackage.resolve("res/values-v34/colors.xml"),
                api34Values,
            )
        }
        values.nightApi34?.let { api34Values ->
            writeValuesXml(
                targetPackage.resolve("res/values-night-v34/colors.xml"),
                api34Values,
            )
        }

        val encoder = XMLTableBlockEncoder()
        encoder.scanMainDirectory(workRoot)
        outputFile.parentFile.mkdirs()
        val tableBlock: Block = encoder.tableBlock
        outputFile.outputStream().use { tableBlock.writeBytes(it) }
        check(outputFile.isFile && outputFile.length() > 0L)
        return outputFile
    } finally {
        workRoot.deleteRecursively()
    }
}

private fun writeTypedItemsXml(
    output: File,
    type: String,
    values: Map<String, String>,
) {
    if (values.isEmpty()) return

    output.parentFile.mkdirs()
    val document = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .newDocument()
    val resources = document.createElement("resources")
    document.appendChild(resources)

    values.forEach { (name, value) ->
        resources.appendChild(
            document.createElement("item").apply {
                setAttribute("type", type)
                setAttribute("name", name)
                textContent = value
            },
        )
    }
    TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        transform(DOMSource(document), StreamResult(output))
    }
}

private fun writeValuesXml(
    output: File,
    colors: Map<String, String>,
) {
    output.parentFile.mkdirs()
    val document = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .newDocument()
    val resources = document.createElement("resources")
    document.appendChild(resources)

    colors.forEach { (name, value) ->
        resources.appendChild(
            document.createElement("color").apply {
                setAttribute("name", name)
                textContent = value
            },
        )
    }
    TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        transform(DOMSource(document), StreamResult(output))
    }
}

private data class PublicName(
    val type: String,
    val name: String,
)

private val localReference =
    Regex("""[@?](?:(com\.instagram\.android):)?([A-Za-z_][\w]*)/([A-Za-z0-9_.]+)""")

private fun writePublicSubset(
    source: File,
    output: File,
    values: OverlayValues,
) {
    val factory = DocumentBuilderFactory.newInstance()
    val sourceDocument = factory.newDocumentBuilder().parse(source)
    val sourceRoot = sourceDocument.documentElement
    val required = mutableSetOf<PublicName>()

    values.day.keys.forEach { required += PublicName("color", it) }
    values.dayDrawables.keys.forEach { required += PublicName("drawable", it) }
    (
        values.day.values +
            values.night.values +
            values.dayApi34.orEmpty().values +
            values.nightApi34.orEmpty().values +
            values.dayDrawables.values +
            values.nightDrawables.values
    ).forEach { value ->
        collectLocalReferences(value, required)
    }

    val publicNodes = sourceDocument.getElementsByTagName("public")
    val publicByName = buildMap {
        for (index in 0 until publicNodes.length) {
            val element = publicNodes.item(index) as org.w3c.dom.Element
            put(
                PublicName(
                    type = element.getAttribute("type"),
                    name = element.getAttribute("name"),
                ),
                element,
            )
        }
    }
    val missing = required - publicByName.keys
    require(missing.isEmpty()) {
        "Missing public resource ids: ${missing.sortedBy { "${it.type}/${it.name}" }}"
    }

    val outputDocument = factory.newDocumentBuilder().newDocument()
    val outputRoot = outputDocument.createElement("resources").apply {
        setAttribute("package", sourceRoot.getAttribute("package"))
        setAttribute("id", sourceRoot.getAttribute("id"))
    }
    outputDocument.appendChild(outputRoot)
    required
        .map { requireNotNull(publicByName[it]) }
        .sortedBy { it.getAttribute("id").removePrefix("0x").toLong(16) }
        .forEach { outputRoot.appendChild(outputDocument.importNode(it, true)) }

    output.parentFile.mkdirs()
    TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        transform(DOMSource(outputDocument), StreamResult(output))
    }
}

private fun collectLocalReferences(
    text: String,
    output: MutableSet<PublicName>,
) {
    localReference.findAll(text).forEach { match ->
        output += PublicName(
            type = match.groupValues[2],
            name = match.groupValues[3],
        )
    }
}
