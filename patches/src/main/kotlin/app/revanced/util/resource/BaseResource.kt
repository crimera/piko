package app.revanced.util.resource

import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Base resource class for all resources.
 *
 * @param name The name of the resource.
 * @param tag The tag of the resource.
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class BaseResource(
    val name: String,
    val tag: String
) {
    /**
     * Serialize resource element to XML.
     * Overriding methods should invoke super and operate on its return value.
     * @param ownerDocument Target document to create elements from.
     * @param resourceCallback Called when a resource has been processed.
     */
    open fun serialize(ownerDocument: Document, resourceCallback: (BaseResource) -> Unit = { }): Element {
        return ownerDocument.createElement(tag).apply {
            setAttribute("name", name)
        }
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + tag.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BaseResource

        return name == other.name
    }
}