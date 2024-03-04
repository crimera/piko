package app.revanced.util.resource

import app.revanced.patcher.patch.PatchException
import org.w3c.dom.Document
import org.w3c.dom.Node

/**
 * A string value.
 * Represents a string in the strings.xml file.
 *
 * @param name The name of the string.
 * @param value The value of the string.
 * @param formatted If the string is formatted. Defaults to `true`.
 */
class StringResource(
    name: String,
    val value: String,
    val formatted: Boolean = true,
) : BaseResource(name, "string") {
    override fun serialize(ownerDocument: Document, resourceCallback: (BaseResource) -> Unit) =
        super.serialize(ownerDocument, resourceCallback).apply {
            // if the string is un-formatted, explicitly add the formatted attribute
            if (!formatted) setAttribute("formatted", "false")

            if (value.contains(Regex("(?<!\\\\)['\"]")))
                throw PatchException("String $name cannot contain unescaped quotes in value \"$value\".")

            textContent = value
        }

    companion object {
        fun fromNode(node: Node): StringResource {
            val name = node.attributes.getNamedItem("name").textContent
            val value = node.textContent
            // TODO: Should this be true by default? It is too in the constructor of StringResource.
            val formatted = node.attributes.getNamedItem("formatted")?.textContent?.toBoolean() ?: true

            return StringResource(name, value, formatted)
        }
    }
}
