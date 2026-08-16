package app.morphe.extension.xlite.misc;

import android.content.Context;
import android.content.res.Resources;

import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public final class DrawerItemFilter {
    private DrawerItemFilter() {
    }

    // ALPHA/LEGACY PATH: legacy drawer rows expose their localized title.
    // TODO: Remove this API when no supported release uses title-based drawer rows.
    public static boolean shouldHide(String title, Set<String> hiddenItemIds) {
        if (title == null || hiddenItemIds == null || hiddenItemIds.isEmpty()) return false;

        try {
            for (String hiddenItemId : hiddenItemIds) {
                if (hiddenItemId != null && matches(title, hiddenItemId)) return true;
            }
        } catch (Exception exception) {
            Logger.printException(() -> "Failed to customize X-Lite drawer", exception);
        }
        return false;
    }

    // BETA PATH: the settings footer exposes a stable logical item ID instead of a title.
    public static boolean shouldHideId(String itemId, Set<String> hiddenItemIds) {
        return itemId != null && hiddenItemIds != null && hiddenItemIds.contains(itemId);
    }

    private static boolean matches(String title, String itemId) {
        return switch (itemId) {
            case "PROFILE" -> matchesResource(title, "x_lite_drawer_profile_title", "drawer_profile_title");
            case "PREMIUM" -> matchesResource(
                    title,
                    "x_lite_settings_subscription_title",
                    "settings_subscription_title",
                    "settings_subscription_premium_plus_title"
            );
            case "MONEY" -> matchesResource(title, "drawer_money_title");
            case "COMMUNITIES" -> matchesResource(title, "drawer_communities_title");
            case "BOOKMARKS" -> matchesResource(title, "x_lite_bookmarks_title", "bookmarks_title");
            case "COMMUNITY_NOTES" -> matchesResource(title, "birdwatch_pivot_header_title");
            case "OFFLINE_VIDEOS" -> matchesResource(
                    title,
                    "x_lite_offline_videos_title",
                    "offline_videos_title"
            );
            case "LISTS" -> matchesResource(title, "x_lite_drawer_lists", "drawer_lists");
            case "BOOST" -> matchesResource(
                    title,
                    "x_lite_quick_promote_boost_button_text",
                    "quick_promote_boost_button_text"
            );
            case "SPACES" -> matchesResource(title, "spaces_tab_name");
            case "FOLLOW_REQUESTS" -> matchesResource(
                    title,
                    "x_lite_follow_requests_title",
                    "follow_requests_title"
            );
            case "MONETIZATION" -> matchesResource(title, "monetization_drawer_menu_title");
            case "CREATOR_STUDIO" -> matchesResource(title, "creator_studio_drawer_menu_title");
            case "ANALYTICS" -> matchesResource(
                    title,
                    "x_lite_subscriptions_drawer_menu_group_analytics",
                    "subscriptions_drawer_menu_group_analytics"
            );
            case "SWITCH_TO_X" -> matchesResource(title, "switch_back_to_x_drawer_item_title");
            case "SETTINGS" -> matchesResource(title, "drawer_settings_title");
            case "HELP_CENTER" -> matchesResource(title, "x_lite_help_center", "help_center");
            case "FEEDBACK" -> matchesResource(title, "drawer_feedback_and_issues");
            case "MEDIA_TRANSPARENCY" -> matchesResource(title, "drawer_media_transparency_title");
            case "IMPRINT" -> matchesResource(title, "drawer_imprint_title");
            default -> false;
        };
    }

    private static boolean matchesResource(String title, String... resourceNames) {
        Context context = Utils.getContext();
        if (context == null) return false;

        Resources resources = context.getResources();
        String packageName = context.getPackageName();
        for (String resourceName : resourceNames) {
            int resourceId = resources.getIdentifier(resourceName, "string", packageName);
            if (resourceId != 0 && title.equals(resources.getString(resourceId))) return true;
        }
        return false;
    }
}
