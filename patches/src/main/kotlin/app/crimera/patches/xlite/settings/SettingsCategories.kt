package app.crimera.patches.xlite.settings

internal data class SettingsCategory(
    val id: String,
    val titleResourceName: String,
    val summaryResourceName: String?,
    val iconResourceName: String?,
    val order: Int,
)

internal data class SettingsGroupMetadata(
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

    val POST_ACTIONS_MEDIA =
        SettingsCategory(
            id = "xlite.post_actions_media",
            titleResourceName = "piko_xlite_category_post_actions_media_title",
            summaryResourceName = "piko_xlite_category_post_actions_media_summary",
            iconResourceName = "ic_vector_post_with_media",
            order = 300,
        )

    val NAVIGATION =
        SettingsCategory(
            id = "xlite.navigation",
            titleResourceName = "piko_xlite_category_navigation_title",
            summaryResourceName = "piko_xlite_category_navigation_summary",
            iconResourceName = "ic_vector_menu",
            order = 400,
        )

    val ADVANCED =
        SettingsCategory(
            id = "xlite.advanced",
            titleResourceName = "piko_xlite_category_advanced_title",
            summaryResourceName = "piko_xlite_category_advanced_summary",
            iconResourceName = "ic_vector_toolbox_stroke",
            order = 500,
        )
}

internal object Groups {
    val FEATURE_SWITCHES =
        SettingsGroupMetadata(
            id = "xlite.advanced.feature_switches",
            titleResourceName = "piko_xlite_feature_switches_title",
            summaryResourceName = "piko_xlite_feature_switches_summary",
            iconResourceName = "ic_vector_flask_stroke",
            order = 100,
        )

    val DYNAMIC_COLORS =
        SettingsGroupMetadata(
            id = "xlite.content.dynamic_colors",
            titleResourceName = "piko_xlite_group_dynamic_colors_title",
            summaryResourceName = "piko_xlite_group_dynamic_colors_summary",
            iconResourceName = "ic_vector_paintbrush_box",
            order = 100,
        )

    val INLINE_ACTIONS =
        SettingsGroupMetadata(
            id = "xlite.post_actions_media.inline_actions",
            titleResourceName = "piko_xlite_group_inline_actions_title",
            summaryResourceName = "piko_xlite_group_inline_actions_summary",
            iconResourceName = "ic_vector_reply_stroke",
            order = 200,
        )

    val REPLY_SORTING =
        SettingsGroupMetadata(
            id = "xlite.timeline.reply_sorting",
            titleResourceName = "piko_xlite_group_reply_sorting_title",
            summaryResourceName = "piko_xlite_group_reply_sorting_summary",
            iconResourceName = "ic_vector_reply_stroke",
            order = 300,
        )
}
