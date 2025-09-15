package app.revanced.patches.shared.misc.settings.preference

import app.revanced.util.resource.ArrayResource
import app.revanced.util.resource.BaseResource
import org.w3c.dom.Document

/**
 * List preference.
 *
 * @param key The preference key. If null, other parameters must be specified.
 * @param titleKey The preference title key.
 * @param summaryKey The preference summary key.
 * @param icon The preference icon resource name.
 * @param layout Layout declaration.
 * @param tag The preference class type.
 * @param entriesKey The entries array key.
 * @param entryValuesKey The entry values array key.
 */
@Suppress("MemberVisibilityCanBePrivate")
class ListPreference(
    key: String? = null,
    titleKey: String = "${key}_title",
    /** Summary key is ignored and will be removed soon */
    // @Deprecated
    summaryKey: String? = null,
    icon: String? = null,
    layout: String? = null,
    tag: String = "app.revanced.extension.shared.settings.preference.CustomDialogListPreference",
    val entriesKey: String? = "${key}_entries",
    val entryValuesKey: String? = "${key}_entry_values",
) : BasePreference(key, titleKey, summaryKey, icon, layout, tag) {
    var entries: ArrayResource? = null
        private set
    var entryValues: ArrayResource? = null
        private set

    /**
     * List preference.
     *
     * @param key The preference key. If null, other parameters must be specified.
     * @param titleKey The preference title key.
     * @param summaryKey The preference summary key.
     * @param tag The preference tag.
     * @param entries The entries array.
     * @param entryValues The entry values array.
     */
    constructor(
        key: String? = null,
        titleKey: String = "${key}_title",
        summaryKey: String? = "${key}_summary",
        tag: String = "ListPreference",
        entries: ArrayResource,
        entryValues: ArrayResource,
    ) : this(key, titleKey, summaryKey, tag, entries.name, entryValues.name) {
        this.entries = entries
        this.entryValues = entryValues
    }

    override fun serialize(
        ownerDocument: Document,
        resourceCallback: (BaseResource) -> Unit,
    ) = super.serialize(ownerDocument, resourceCallback).apply {
        val entriesArrayName = entries?.also { resourceCallback.invoke(it) }?.name ?: entriesKey
        val entryValuesArrayName = entryValues?.also { resourceCallback.invoke(it) }?.name ?: entryValuesKey

        entriesArrayName?.let {
            setAttribute(
                "android:entries",
                "@array/$it",
            )
        }

        entryValuesArrayName?.let {
            setAttribute(
                "android:entryValues",
                "@array/$it",
            )
        }
    }
}
