package app.morphe.extension.xlite.api;

import java.util.Set;

/** User-defined X-Lite settings categories and keys. */
public interface XLiteSettings {
    interface Categories {
        SettingsCategory TIMELINE =
                new SettingsCategory(
                        "xlite.timeline",
                        "piko_xlite_category_timeline_title",
                        null,
                        100
                );

        SettingsCategory CONTENT =
                new SettingsCategory(
                        "xlite.content",
                        "piko_xlite_category_content_title",
                        null,
                        200
                );
    }

    interface Keys {
        SettingKey<Boolean> DISABLE_TIMELINE_REFRESH =
                new SettingKey<>("xlite.timeline.disable_refresh");

        SettingKey<Boolean> HIDE_NEW_POST_PILL =
                new SettingKey<>("xlite.timeline.hide_new_post_pill");

        SettingKey<Boolean> RESTORE_TIMELINE_POSITION =
                new SettingKey<>("xlite.timeline.restore_position");

        SettingKey<Boolean> FILTER_PROMOTED_POSTS =
                new SettingKey<>("xlite.content.filter_promoted_posts");

        SettingKey<Set<String>> HIDDEN_INLINE_ACTIONS =
                new SettingKey<>("xlite.content.hidden_inline_actions");
    }
}
