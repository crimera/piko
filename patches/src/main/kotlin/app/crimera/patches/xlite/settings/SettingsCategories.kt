package app.crimera.patches.xlite.settings

internal data class SettingsCategory(
    val id: String,
    val titleResourceName: String,
    val summaryResourceName: String?,
    val iconResourceName: String?,
    val order: Int,
)

internal object Categories {
    val TIMELINE =
        SettingsCategory(
            id = "xlite.timeline",
            titleResourceName = "piko_xlite_category_timeline_title",
            summaryResourceName = "piko_xlite_category_timeline_summary",
            iconResourceName = "ic_vector_home_stroke",
            order = 100,
        )

    val CONTENT =
        SettingsCategory(
            id = "xlite.content",
            titleResourceName = "piko_xlite_category_content_title",
            summaryResourceName = "piko_xlite_category_content_summary",
            iconResourceName = "ic_vector_bulleted_list",
            order = 200,
        )
}
