package app.revanced.patches.shared.misc.settings.preference

import app.revanced.util.resource.BaseResource
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Base preference class for all preferences.
 *
 * @param key The key of the preference. If null, other parameters must be specified.
 * @param titleKey The key of the preference title.
 * @param icon The preference icon resource name.
 * @param layout Layout declaration.
 * @param summaryKey The key of the preference summary.
 * @param tag The tag or full class name of the preference.
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class BasePreference(
    val key: String? = null,
    val titleKey: String? = "${key}_title",
    val summaryKey: String? = "${key}_summary",
    icon: String? = null,
    layout: String? = null,
    val tag: String
) {

    var icon: String? = icon
        internal set

    var layout: String? = layout
        internal set

    /**
     * Serialize preference element to XML.
     * Overriding methods should invoke super and operate on its return value.
     *
     * @param resourceCallback A callback for additional resources.
     * @param ownerDocument Target document to create elements from.
     *
     * @return The serialized element.
     */
    open fun serialize(ownerDocument: Document, resourceCallback: (BaseResource) -> Unit): Element =
        ownerDocument.createElement(tag).apply {
            key?.let { setAttribute("android:key", it) }
            titleKey?.let { setAttribute("android:title", "@string/${titleKey}") }
            summaryKey?.let { addSummary(it) }
            icon?.let {
                setAttribute("android:icon", it)
                setAttribute("app:iconSpaceReserved", "true")
            }
            layout?.let { setAttribute("android:layout", layout) }
        }

    companion object {
        fun Element.addSummary(summaryKey: String, summaryType: SummaryType = SummaryType.DEFAULT) =
            setAttribute("android:${summaryType.type}", "@string/$summaryKey")
    }
}
