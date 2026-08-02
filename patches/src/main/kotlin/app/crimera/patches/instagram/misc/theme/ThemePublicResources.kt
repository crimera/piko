/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.resource.PublicXmlManager
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

private const val STRING_RESOURCE_TYPE = "string"

private data class PublicResourceEntry(
    val type: String,
    val name: String,
    val id: Int,
)

internal fun reservePublicStringIds(
    publicXml: File,
    names: List<String>,
): Map<String, Int> {
    if (!publicXml.isFile) {
        throw PatchException("Missing res/values/public.xml before adding resources")
    }
    if (names.isEmpty() || names.any(String::isBlank) || names.distinct().size != names.size) {
        throw PatchException("Material You public string names must be non-empty and unique")
    }

    val existingEntries = readPublicResourceEntries(publicXml)
    validatePublicResourceEntries(existingEntries)
    val existingByKey = existingEntries.groupBy { it.type to it.name }
    val stringTypePrefixes =
        existingEntries
            .filter { it.type == STRING_RESOURCE_TYPE }
            .map { it.id ushr 16 }
            .toSet()
    if (stringTypePrefixes.size != 1) {
        throw PatchException(
            "Expected one existing string resource type id, found $stringTypePrefixes",
        )
    }

    PublicXmlManager(publicXml).use { manager ->
        names.forEach { name ->
            val key = STRING_RESOURCE_TYPE to name
            val existing = existingByKey[key].orEmpty()
            val managerHasId = manager.idExists(STRING_RESOURCE_TYPE, name)
            if (managerHasId) {
                if (existing.size != 1) {
                    throw PatchException(
                        "Expected one existing public resource for string/$name, " +
                            "found ${existing.size}",
                    )
                }
            } else {
                if (existing.isNotEmpty()) {
                    throw PatchException(
                        "PublicXmlManager did not recognize existing string/$name",
                    )
                }
                manager.createPublicId(STRING_RESOURCE_TYPE, name)
                if (!manager.idExists(STRING_RESOURCE_TYPE, name)) {
                    throw PatchException(
                        "Failed to reserve public resource id for string/$name",
                    )
                }
            }
        }
    }

    val updatedEntries = readPublicResourceEntries(publicXml)
    val reservedIds =
        names.associateWith { name ->
            val matches =
                updatedEntries.filter {
                    it.type == STRING_RESOURCE_TYPE && it.name == name
                }
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one reserved public resource for string/$name, " +
                        "found ${matches.size}",
                )
            }
            matches.single().id
        }
    validateReservedPublicStringIds(
        entries = updatedEntries,
        expected = reservedIds,
        stringTypePrefix = stringTypePrefixes.single(),
    )
    return reservedIds
}

private fun readPublicResourceEntries(publicXml: File): List<PublicResourceEntry> =
    try {
        val nodes =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(publicXml)
                .getElementsByTagName("public")
        buildList {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val idText = element.getAttribute("id")
                val id =
                    idText
                        .takeIf { it.startsWith("0x") }
                        ?.removePrefix("0x")
                        ?.toLongOrNull(16)
                        ?.takeIf { it in 1..0xffffffffL }
                        ?.toInt()
                        ?: throw PatchException(
                            "Invalid public resource id: $idText",
                        )
                add(
                    PublicResourceEntry(
                        type = element.getAttribute("type"),
                        name = element.getAttribute("name"),
                        id = id,
                    ),
                )
            }
        }
    } catch (exception: PatchException) {
        throw exception
    } catch (exception: Exception) {
        throw PatchException(
            "Failed to read public resources: ${exception.message}",
        )
    }

private fun validatePublicResourceEntries(entries: List<PublicResourceEntry>) {
    val duplicateKeys =
        entries
            .groupingBy { it.type to it.name }
            .eachCount()
            .filterValues { it != 1 }
            .keys
    if (duplicateKeys.isNotEmpty()) {
        throw PatchException(
            "Duplicate public resource names: ${duplicateKeys.joinToString()}",
        )
    }

    val duplicateIds =
        entries
            .groupingBy(PublicResourceEntry::id)
            .eachCount()
            .filterValues { it != 1 }
            .keys
    if (duplicateIds.isNotEmpty()) {
        throw PatchException(
            "Duplicate public resource ids: " +
                duplicateIds.joinToString { "0x${it.toUInt().toString(16)}" },
        )
    }
}

private fun validateReservedPublicStringIds(
    entries: List<PublicResourceEntry>,
    expected: Map<String, Int>,
    stringTypePrefix: Int,
) {
    validatePublicResourceEntries(entries)
    if (expected.values.any { it == 0 } || expected.values.toSet().size != expected.size) {
        throw PatchException("Reserved Material You public string ids must be non-zero and unique")
    }

    expected.forEach { (name, id) ->
        if (id ushr 16 != stringTypePrefix) {
            throw PatchException(
                "Reserved public resource id for string/$name changed resource type",
            )
        }
        val matches =
            entries.filter {
                it.type == STRING_RESOURCE_TYPE && it.name == name && it.id == id
            }
        if (matches.size != 1) {
            throw PatchException(
                "Expected one reserved public resource for string/$name with id " +
                    "0x${id.toUInt().toString(16)}, found ${matches.size}",
            )
        }
    }
}
