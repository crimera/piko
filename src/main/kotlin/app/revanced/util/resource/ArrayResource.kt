package app.revanced.util.resource

import app.revanced.util.childElementsSequence
import org.w3c.dom.Document
import org.w3c.dom.Node

/**
 *  An array resource.
 *
 *  @param name The name of the array resource.
 *  @param items The items of the array resource.
 */
@Suppress("MemberVisibilityCanBePrivate")
class ArrayResource(
    name: String,
    val items: List<String>,
) : BaseResource(name, "string-array") {
    override fun serialize(ownerDocument: Document, resourceCallback: (BaseResource) -> Unit) =
        super.serialize(ownerDocument, resourceCallback).apply {
            setAttribute("name", name)

            items.forEach { item ->
                appendChild(ownerDocument.createElement("item").also { itemNode ->
                    itemNode.textContent = item
                })
            }
        }

    companion object {
        fun fromNode(node: Node): ArrayResource {
            val key = node.attributes.getNamedItem("name").textContent
            val items = node.childElementsSequence().map { it.textContent }.toList()

            return ArrayResource(key, items)
        }
    }
}