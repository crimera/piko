/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.customise.chatColors;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.utils.HexColor;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/** The chat colors settings, kept until one changes because Instagram asks for the theme of a chat many times a second. */
public final class ChatColorsConfig {
    public static final String CHOICE_NATIVE = "native";
    public static final String CHOICE_PIKO = "piko";

    private static final String KEY_PREFIX = "chat_colors_";

    // Held by this class because the preferences only keep their listeners weakly.
    private static final SharedPreferences.OnSharedPreferenceChangeListener LISTENER =
            (preferences, key) -> {
                if (key == null || key.startsWith(KEY_PREFIX)) {
                    invalidate();
                    Utils.runOnBackgroundThread(() -> ChatColors.prepare(current()));
                }
            };
    private static final Object LOCK = new Object();
    private static volatile ChatColorsConfig current;
    // Counts the changes, so a snapshot read while one happened is not kept.
    private static int generation;
    private static boolean listening;

    /** The colors as RRGGBB or AARRGGBB, or null where the setting is left to Instagram. */
    final String sentStart;
    final String sentEnd;
    final String received;
    final String sentText;
    final String receivedText;
    final String background;
    final String backgroundEnd;
    final String backgroundImage;
    final String header;
    final String inputBar;
    final String names;
    final boolean overrideThemes;
    private final Map<String, String> chats;
    private final String signature;

    private ChatColorsConfig() {
        sentStart = color(Settings.CHAT_COLORS_SENT_START.key);
        sentEnd = color(Settings.CHAT_COLORS_SENT_END.key);
        received = color(Settings.CHAT_COLORS_RECEIVED.key);
        sentText = color(Settings.CHAT_COLORS_SENT_TEXT.key);
        receivedText = color(Settings.CHAT_COLORS_RECEIVED_TEXT.key);
        // Instagram draws the background, the header and the input bar opaque, so any alpha is dropped.
        background = opaque(color(Settings.CHAT_COLORS_BACKGROUND.key));
        backgroundEnd = background == null ? null : opaque(color(Settings.CHAT_COLORS_BACKGROUND_END.key));
        // Only a picture that is there counts, as a restored backup names one that was never copied back.
        String image = SharedPref.getStringPref(Settings.CHAT_COLORS_BACKGROUND_IMAGE.key, "");
        Context context = Utils.getContext();
        backgroundImage = context != null && ChatBackgroundImage.fileOf(context, image) != null ? image : null;
        header = opaque(color(Settings.CHAT_COLORS_HEADER.key));
        inputBar = opaque(color(Settings.CHAT_COLORS_INPUT_BAR.key));
        names = color(Settings.CHAT_COLORS_NAMES.key);
        overrideThemes = SharedPref.getBooleanPref(Settings.CHAT_COLORS_OVERRIDE_THEMES);
        chats = readChats();
        signature = String.join("|", String.valueOf(sentStart), String.valueOf(sentEnd), String.valueOf(received),
                String.valueOf(sentText), String.valueOf(receivedText), String.valueOf(background),
                String.valueOf(backgroundEnd), String.valueOf(backgroundImage), String.valueOf(header),
                String.valueOf(inputBar), String.valueOf(names));
    }

    public static ChatColorsConfig current() {
        ChatColorsConfig config = current;
        if (config != null) return config;
        // Listening starts before the settings are read, so a change cannot slip in between.
        boolean listens = listen();
        int seen;
        synchronized (LOCK) {
            seen = generation;
        }
        config = new ChatColorsConfig();
        // Only kept when something will say that it went stale, and nothing did while it was read.
        if (listens) {
            synchronized (LOCK) {
                if (generation == seen) current = config;
            }
        }
        return config;
    }

    static void invalidate() {
        synchronized (LOCK) {
            generation++;
            current = null;
        }
    }

    private static synchronized boolean listen() {
        if (!listening) {
            try {
                listening = SharedPref.registerOnSharedPreferenceChangeListener(LISTENER);
            } catch (RuntimeException exception) {
                Logger.printException(() -> "Failed listening to the chat colors settings", exception);
            }
        }
        return listening;
    }

    String choiceOf(String chatId) {
        return chatId == null ? null : chats.get(chatId);
    }

    boolean hasColors() {
        return sentStart != null || received != null || sentText != null || receivedText != null
                || background != null || backgroundImage != null || header != null || inputBar != null || names != null;
    }

    String signature() {
        return signature;
    }

    static void saveChoice(String chatId, String choice) {
        if (chatId == null) return;
        Map<String, String> chats = readChats();
        if (choice == null) chats.remove(chatId);
        else chats.put(chatId, choice);
        SharedPref.setStringPref(Settings.CHAT_COLORS_CHATS.key, new JSONObject(chats).toString());
    }

    private static String color(String key) {
        return HexColor.normalize(SharedPref.getStringPref(key, ""));
    }

    private static String opaque(String color) {
        return color != null && color.length() == 8 ? color.substring(2) : color;
    }

    private static Map<String, String> readChats() {
        Map<String, String> chats = new HashMap<>();
        try {
            JSONObject saved = new JSONObject(SharedPref.getStringPref(Settings.CHAT_COLORS_CHATS));
            for (Iterator<String> ids = saved.keys(); ids.hasNext(); ) {
                String id = ids.next();
                String choice = saved.optString(id);
                if (CHOICE_NATIVE.equals(choice) || CHOICE_PIKO.equals(choice)) chats.put(id, choice);
            }
        } catch (Exception exception) {
            Logger.printException(() -> "Failed reading the chat color choices", exception);
        }
        return chats;
    }
}
