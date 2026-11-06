/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import app.morphe.extension.instagram.utils.IgStr;

/** What notifications show in piko's own words, from strings.xml, with English when a string is missing. */
final class Texts {
    private Texts() {
    }

    static String get(String name, String english, Object... args) {
        String text = args.length == 0 ? IgStr.str(name) : IgStr.str(name, args);
        if (!text.equals(name)) return text;
        return args.length == 0 ? english : String.format(english, args);
    }

    static String someone() {
        return get("piko_notification_someone", "Someone");
    }

    static String you() {
        return get("piko_notification_you", "You");
    }

    static String markAsRead() {
        return get("piko_notification_mark_as_read", "Mark as read");
    }

    static String moreMessages(int count) {
        return count == 1 ? get("piko_notification_one_more_message", "1 more message")
                : get("piko_notification_more_messages", "%d more messages", count);
    }

    static String chats(int count) {
        return count == 1 ? get("piko_notification_one_chat", "1 chat")
                : get("piko_notification_chats", "%d chats", count);
    }

    static String moreChats(int count) {
        return get("piko_notification_more_chats", "+%d more", count);
    }
}
