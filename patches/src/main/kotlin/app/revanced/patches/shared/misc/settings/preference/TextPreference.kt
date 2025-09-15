package app.revanced.patches.shared.misc.settings.preference

import app.revanced.util.resource.BaseResource
import org.w3c.dom.Document

/**
 * A text preference.
 *
 * @param key The preference key. If null, other parameters must be specified.
 * @param titleKey The preference title key.
 * @param summaryKey The preference summary key.
 * @param icon The preference icon resource name.
 * @param layout Layout declaration.
 * @param tag The preference tag.
 * @param inputType The preference input type.
 */
@Suppress("MemberVisibilityCanBePrivate")
class TextPreference(
    key: String? = null,
    titleKey: String = "${key}_title",
    summaryKey: String? = "${key}_summary",
    icon: String? = null,
    layout: String? = null,
    tag: String = "app.revanced.extension.shared.settings.preference.ResettableEditTextPreference",
    val inputType: InputType = InputType.TEXT,
) : BasePreference(key, titleKey, summaryKey, icon, layout, tag) {
    override fun serialize(
        ownerDocument: Document,
        resourceCallback: (BaseResource) -> Unit,
    ) = super.serialize(ownerDocument, resourceCallback).apply {
        setAttribute("android:inputType", inputType.type)
    }
}
