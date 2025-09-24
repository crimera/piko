package app.revanced.util

import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.ResourcePatchContext
import app.revanced.patcher.util.Document
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val classLoader = object {}.javaClass.classLoader

/**
 * Returns a sequence for all child nodes.
 */
fun NodeList.asSequence() = (0 until this.length).asSequence().map { this.item(it) }

/**
 * Returns a sequence for all child nodes.
 */
@Suppress("UNCHECKED_CAST")
fun Node.childElementsSequence() = this.childNodes.asSequence().filter { it.nodeType == Node.ELEMENT_NODE } as Sequence<Element>

/**
 * Performs the given [action] on each child element.
 */
inline fun Node.forEachChildElement(action: (Element) -> Unit) =
    childElementsSequence().forEach {
        action(it)
    }

/**
 * Recursively traverse the DOM tree starting from the given root node.
 *
 * @param action function that is called for every node in the tree.
 */
fun Node.doRecursively(action: (Node) -> Unit) {
    action(this)
    val childNodes = this.childNodes
    for (i in 0 until childNodes.length) {
        childNodes.item(i).doRecursively(action)
    }
}

fun Node.insertFirst(node: Node) {
    if (hasChildNodes()) {
        insertBefore(node, firstChild)
    } else {
        appendChild(node)
    }
}

/**
 * Copy resources from the current class loader to the resource directory.
 *
 * @param sourceResourceDirectory The source resource directory name.
 * @param resources The resources to copy.
 */
fun ResourcePatchContext.copyResources(
    sourceResourceDirectory: String,
    vararg resources: ResourceGroup,
) {
    val targetResourceDirectory = this["res", false]

    for (resourceGroup in resources) {
        resourceGroup.resources.forEach { resource ->
            val resourceFile = "${resourceGroup.resourceDirectoryName}/$resource"
            Files.copy(
                inputStreamFromBundledResource(sourceResourceDirectory, resourceFile)!!,
                targetResourceDirectory.resolve(resourceFile).toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}

internal fun inputStreamFromBundledResource(
    sourceResourceDirectory: String,
    resourceFile: String,
): InputStream? = classLoader.getResourceAsStream("$sourceResourceDirectory/$resourceFile")

/**
 * Resource names mapped to their corresponding resource data.
 * @param resourceDirectoryName The name of the directory of the resource.
 * @param resources A list of resource names.
 */
class ResourceGroup(
    val resourceDirectoryName: String,
    vararg val resources: String,
)

/**
 * Iterate through the children of a node by its tag.
 * @param resource The xml resource.
 * @param targetTag The target xml node.
 * @param callback The callback to call when iterating over the nodes.
 */
fun ResourcePatchContext.iterateXmlNodeChildren(
    resource: String,
    targetTag: String,
    callback: (node: Node) -> Unit,
) = document(classLoader.getResourceAsStream(resource)!!).use { document ->
    val stringsNode = document.getElementsByTagName(targetTag).item(0).childNodes
    for (i in 1 until stringsNode.length - 1) callback(stringsNode.item(i))
}

/**
 * Copies the specified node of the source [Document] to the target [Document].
 * @param source the source [Document].
 * @param target the target [Document]-
 * @return AutoCloseable that closes the [Document]s.
 */
fun String.copyXmlNode(
    source: Document,
    target: Document,
): AutoCloseable {
    val hostNodes = source.getElementsByTagName(this).item(0).childNodes
    val destinationNode = target.getElementsByTagName(this).item(0)

    for (index in 0 until hostNodes.length) {
        val node = hostNodes.item(index).cloneNode(true)
        target.adoptNode(node)
        destinationNode.appendChild(node)
    }

    return AutoCloseable {
        source.close()
        target.close()
    }
}

internal fun Document.getNode(tagName: String) = getElementsByTagName(tagName).item(0)

internal fun NodeList.findElementByAttributeValue(
    attributeName: String,
    value: String,
): Element? {
    for (i in 0 until length) {
        val node = item(i)
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element

            if (element.getAttribute(attributeName) == value) {
                return element
            }

            // Recursively search.
            val found = element.childNodes.findElementByAttributeValue(attributeName, value)
            if (found != null) {
                return found
            }
        }
    }

    return null
}

internal fun NodeList.findElementByAttributeValueOrThrow(
    attributeName: String,
    value: String,
) = findElementByAttributeValue(attributeName, value) ?: throw PatchException("Could not find: $attributeName $value")

internal fun Element.copyAttributesFrom(oldContainer: Element) {
    // Copy attributes from the old element to the new element
    val attributes = oldContainer.attributes
    for (i in 0 until attributes.length) {
        val attr = attributes.item(i) as Attr
        setAttribute(attr.name, attr.value)
    }
}
