package app.revanced.patches.shared.misc.settings.preference

import app.revanced.util.resource.BaseResource
import org.w3c.dom.Document

/**
 * A non-interactive preference.
 *
 * Typically used to present static text, but also used for custom extension code that responds to taps.
 *
 * @param key The preference key.
 * @param summaryKey The preference summary key.
 * @param icon The preference icon resource name.
 * @param layout Layout declaration.
 * @param tag The tag or full class name of the preference.
 * @param selectable If the preference is selectable and responds to tap events.
 */
@Suppress("MemberVisibilityCanBePrivate")
class NonInteractivePreference(
    key: String,
    titleKey: String = "${key}_title",
    summaryKey: String? = "${key}_summary",
    icon: String? = null,
    layout: String? = null,
    tag: String = "Preference",
    val selectable: Boolean = false,
) : BasePreference(key, titleKey, summaryKey, icon, layout, tag) {
    override fun serialize(ownerDocument: Document, resourceCallback: (BaseResource) -> Unit) =
        super.serialize(ownerDocument, resourceCallback).apply {
            setAttribute("android:selectable", selectable.toString())
        }
}
