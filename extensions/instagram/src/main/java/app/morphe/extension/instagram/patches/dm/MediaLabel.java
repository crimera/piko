/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import static app.morphe.extension.instagram.utils.IgStr.str;

/** Shared, localised labels for DM message types (used by the screen and the notifications). */
final class MediaLabel {

    private MediaLabel() {}

    /** Localised noun for a DM message type, e.g. "photo", "voice message". */
    static String noun(String type) {
        if (type == null) return str("piko_media_unknown");
        switch (type) {
            case "media":
            case "image":          return str("piko_media_photo");
            case "raven_media":    return str("piko_media_disappearing_photo");
            case "video":          return str("piko_media_video");
            case "voice_media":
            case "audio":          return str("piko_media_voice");
            case "animated_media": return str("piko_media_gif");
            case "reel_share":
            case "clip":
            case "xma_clip":       return str("piko_media_reel");
            case "story_share":    return str("piko_media_story");
            case "media_share":    return str("piko_media_post");
            case "like":           return str("piko_media_like");
            case "link":           return str("piko_media_link");
            case "action_log":     return str("piko_media_activity");
            default:               return type;
        }
    }

    /** Present-tense label for the saved-messages list, e.g. "[photo]". */
    static String label(String type) {
        return str("piko_media_label", noun(type));
    }

    /** Deleted-form label for unsend notifications, e.g. "[photo deleted]". */
    static String deleted(String type) {
        if (type == null) return str("piko_media_deleted_generic");
        return str("piko_media_deleted", noun(type));
    }
}
