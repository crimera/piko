package app.crimera.patches.newx.settings

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
            id = "newx.timeline",
            titleResourceName = "piko_newx_category_timeline_title",
            summaryResourceName = "piko_newx_category_timeline_summary",
            iconResourceName = "ic_vector_home_stroke",
            order = 100,
        )

    val CONTENT =
        SettingsCategory(
            id = "newx.content",
            titleResourceName = "piko_newx_category_content_title",
            summaryResourceName = "piko_newx_category_content_summary",
            iconResourceName = "ic_vector_bulleted_list",
            order = 200,
        )

    val APPEARANCE =
        SettingsCategory(
            id = "newx.appearance",
            titleResourceName = "piko_newx_category_appearance_title",
            summaryResourceName = "piko_newx_category_appearance_summary",
            iconResourceName = "ic_vector_paintbrush_box",
            order = 300,
        )

    val POST_ACTIONS_MEDIA =
        SettingsCategory(
            id = "newx.post_actions_media",
            titleResourceName = "piko_newx_category_post_actions_media_title",
            summaryResourceName = "piko_newx_category_post_actions_media_summary",
            iconResourceName = "ic_vector_post_with_media",
            order = 400,
        )

    val NAVIGATION =
        SettingsCategory(
            id = "newx.navigation",
            titleResourceName = "piko_newx_category_navigation_title",
            summaryResourceName = "piko_newx_category_navigation_summary",
            iconResourceName = "ic_vector_menu",
            order = 500,
        )

    val ADVANCED =
        SettingsCategory(
            id = "newx.advanced",
            titleResourceName = "piko_newx_category_advanced_title",
            summaryResourceName = "piko_newx_category_advanced_summary",
            iconResourceName = "ic_vector_toolbox_stroke",
            order = 600,
        )
}

internal object Groups {
    val FEATURE_SWITCHES =
        SettingsGroupMetadata(
            id = "newx.advanced.feature_switches",
            titleResourceName = "piko_newx_feature_switches_title",
            summaryResourceName = "piko_newx_feature_switches_summary",
            iconResourceName = "ic_vector_flask_stroke",
            order = 100,
        )

    val CONTENT_FILTERING =
        SettingsGroupMetadata(
            id = "newx.content.filtering",
            titleResourceName = "piko_newx_group_content_filtering_title",
            summaryResourceName = "piko_newx_group_content_filtering_summary",
            iconResourceName = "ic_vector_filter",
            order = 100,
        )

    val DYNAMIC_COLORS =
        SettingsGroupMetadata(
            id = "newx.appearance.dynamic_colors",
            titleResourceName = "piko_newx_group_dynamic_colors_title",
            summaryResourceName = "piko_newx_group_dynamic_colors_summary",
            iconResourceName = "ic_vector_paintbrush_box",
            order = 100,
        )

    val INLINE_ACTIONS =
        SettingsGroupMetadata(
            id = "newx.post_actions_media.inline_actions",
            titleResourceName = "piko_newx_group_inline_actions_title",
            summaryResourceName = "piko_newx_group_inline_actions_summary",
            iconResourceName = "ic_vector_more",
            order = 200,
        )

    val INLINE_DOWNLOAD =
        SettingsGroupMetadata(
            id = "newx.post_actions_media.inline_download",
            titleResourceName = "piko_newx_group_inline_download_title",
            summaryResourceName = "piko_newx_group_inline_download_summary",
            iconResourceName = null,
            order = 250,
        )

    val REPLY_SORTING =
        SettingsGroupMetadata(
            id = "newx.post_actions_media.reply_sorting",
            titleResourceName = "piko_newx_group_reply_sorting_title",
            summaryResourceName = "piko_newx_group_reply_sorting_summary",
            iconResourceName = "ic_vector_sort_arrows",
            order = 300,
        )

    val DEBUG_TOOLS =
        SettingsGroupMetadata(
            id = "newx.advanced.debug_tools",
            titleResourceName = "piko_newx_group_debug_tools_title",
            summaryResourceName = "piko_newx_group_debug_tools_summary",
            iconResourceName = "ic_vector_bug_stroke",
            order = 300,
        )
}
