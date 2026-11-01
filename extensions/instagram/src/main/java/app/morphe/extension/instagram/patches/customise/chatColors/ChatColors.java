/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.customise.chatColors;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;

import com.instagram.model.direct.DirectThreadKey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.utils.HexColor;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

/**
 * Instagram reads the theme of a chat through a single method, so a chat is recolored by returning another theme for
 * it: a copy of the shared default theme with the chosen colors in it.
 */
@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class ChatColors {
    private static final String GRADIENT_PREFIX = "piko_chat_background_";
    private static final int GRADIENT_WIDTH = 360;
    private static final int GRADIENT_HEIGHT = 800;
    // The gradient is stretched over the whole screen, so an opaque header or input bar hides its ends. These are the
    // heights to leave out: the toolbar under the status bar, and the composer above the navigation bar.
    private static final int TOOLBAR_DP = 56;
    private static final int COMPOSER_DP = 53;
    // Instagram never lists these in the menu of a chat, so none of its own buttons is taken over.
    private static final String[] MENU_BUTTONS = {"VIDEO_CALL", "AUDIO_CALL"};
    private static final String MENU_ICON = "instagram_palette_outline_24";
    private static final String LIGHT_SENT_TEXT = "light_primary_text_message_from_me_color";
    private static final String DARK_SENT_TEXT = "dark_primary_text_message_from_me_color";
    // Pressing one of these buttons makes Instagram open another list of buttons through the same menu, in the same call.
    private static final List<String> SUB_MENU_BUTTONS = Arrays.asList("ORGANIZE", "MUTE");
    private static final Handler MAIN_THREAD = new Handler(Looper.getMainLooper());
    private static final String[] BACKGROUND_ASSETS = {
            "light_large_background_image_uri", "dark_large_background_image_uri",
            "light_large_background_video_uri", "dark_large_background_video_uri",
            "custom_background_drawable_url",
            "light_theme_background_drawable_resource_id", "dark_theme_background_drawable_resource_id",
    };

    /** The theme built last, with what it was built from, so a reader sees them together without taking a lock. */
    private static final class Built {
        final Object theme;
        final Object from;
        final String signature;

        Built(Object theme, Object from, String signature) {
            this.theme = theme;
            this.from = from;
            this.signature = signature;
        }
    }

    private static final Object BUILD_LOCK = new Object();
    // Separate from BUILD_LOCK so that work on files never holds up a reader of a theme that is already built.
    private static final Object FILE_LOCK = new Object();
    private static volatile ThemeFields knownFields;
    private static volatile Built built;
    // Kept until the next change, as a screen may still be loading it.
    private static File currentGradient;
    private static String toneName;
    private static String toneColor;
    private static volatile String menuButton;
    private static volatile boolean pressingSubMenuButton;

    private ChatColors() { }

    /** Replaced by the patch with pairs of the key the server sends a theme field under and the field's name. */
    private static String themeFields() {
        return "__piko_chat_theme_fields__";
    }

    private static final class ThemeFields {
        final Constructor<?> constructor;
        final Field[] all;
        final Map<String, Field> byKey = new HashMap<>();

        ThemeFields(Class<?> type) throws ReflectiveOperationException {
            constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            List<Field> copied = new ArrayList<>();
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                copied.add(field);
            }
            all = copied.toArray(new Field[0]);
            for (String pair : themeFields().split(";")) {
                int split = pair.indexOf('=');
                if (split > 0) byKey.put(pair.substring(0, split), declared(type, pair.substring(split + 1)));
            }
        }

        private static Field declared(Class<?> type, String name) throws NoSuchFieldException {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        private Field fieldOf(String key) throws NoSuchFieldException {
            Field field = byKey.get(key);
            if (field == null) throw new NoSuchFieldException(key);
            return field;
        }

        void set(Object theme, String key, Object value) throws ReflectiveOperationException {
            fieldOf(key).set(theme, value);
        }

        void setBoth(Object theme, String lightKey, String darkKey, String color) throws ReflectiveOperationException {
            set(theme, lightKey, color);
            set(theme, darkKey, color);
        }

        void clear(Object theme, String key) throws ReflectiveOperationException {
            Field field = fieldOf(key);
            field.set(theme, field.getType() == int.class ? (Object) 0 : null);
        }
    }

    /** Returns the theme to use for the chat, given the one Instagram keeps for it and the shared default theme. */
    public static Object resolve(Object threadKey, Object theme, Object defaultTheme) {
        try {
            if (theme == null || !(threadKey instanceof DirectThreadKey)) return theme;
            ChatColorsConfig config = ChatColorsConfig.current();
            if (!config.hasColors()) return theme;

            boolean themed = theme != defaultTheme;
            String choice = config.choiceOf(((DirectThreadKey) threadKey).A00);
            boolean piko = ChatColorsConfig.CHOICE_PIKO.equals(choice)
                    || (choice == null && (!themed || config.overrideThemes));
            // Not built from the theme of the chat, so none of its colors are left among the new ones.
            return piko ? recolored(defaultTheme, config) : theme;
        } catch (Throwable throwable) {
            Logger.printException(() -> "ChatColors.resolve failed", throwable);
            return theme;
        }
    }

    private static Object recolored(Object base, ChatColorsConfig config)
            throws ReflectiveOperationException, IOException {
        Built current = built;
        if (current != null && current.from == base && current.signature.equals(config.signature())) {
            return current.theme;
        }
        synchronized (BUILD_LOCK) {
            current = built;
            if (current == null || current.from != base || !current.signature.equals(config.signature())) {
                current = new Built(build(base, config), base, config.signature());
                built = current;
            }
            return current.theme;
        }
    }

    private static Object build(Object base, ChatColorsConfig config) throws ReflectiveOperationException, IOException {
        if (knownFields == null) knownFields = new ThemeFields(base.getClass());
        ThemeFields fields = knownFields;
        Object theme = fields.constructor.newInstance();
        for (Field field : fields.all) field.set(theme, field.get(base));

        if (config.sentStart != null) {
            String end = config.sentEnd != null ? config.sentEnd : config.sentStart;
            fields.set(theme, "gradient_colors", new ArrayList<>(Arrays.asList(config.sentStart, end)));
            fields.set(theme, "dark_gradient_colors", new ArrayList<>(Arrays.asList(config.sentStart, end)));
        }
        if (config.received != null) {
            fields.setBoth(theme, "light_theme_incoming_message_bubble_color",
                    "dark_theme_incoming_message_bubble_color", config.received);
        }
        // The default text color was chosen for the default bubble color, so text left on automatic is picked again.
        String sentText = config.sentText != null ? config.sentText
                : config.sentStart != null ? readableOnBubble(config.sentStart, config.sentEnd, config) : null;
        if (sentText != null) {
            fields.setBoth(theme, "light_primary_text_message_from_me_color",
                    "dark_primary_text_message_from_me_color", sentText);
        }
        String receivedText = config.receivedText != null ? config.receivedText
                : config.received != null ? readableOnBubble(config.received, null, config) : null;
        if (receivedText != null) {
            fields.setBoth(theme, "light_primary_text_message_from_others_color",
                    "dark_primary_text_message_from_others_color", receivedText);
        }
        // A picture replaces the gradient, and the color only shows until the picture is drawn.
        File image = config.backgroundImage == null ? null
                : ChatBackgroundImage.fileOf(Utils.getContext(), config.backgroundImage);
        if (config.background != null || image != null) {
            String color = config.background != null ? config.background : "000000";
            fields.setBoth(theme, "background_color", "dark_background_color", color);
            fields.setBoth(theme, "fallback_color", "dark_fallback_color", color);
            for (String asset : BACKGROUND_ASSETS) fields.clear(theme, asset);
            // Instagram draws a picture as the background of a theme, so a gradient is given as one too.
            String picture = image != null ? Uri.fromFile(image).toString()
                    : config.backgroundEnd != null ? gradientPictureFor(config) : null;
            if (picture != null) {
                fields.setBoth(theme, "light_large_background_image_uri", "dark_large_background_image_uri", picture);
            }
        }
        // Names and dates are drawn straight on the background.
        String names = config.names != null ? config.names
                : image != null ? translucent(toneOf(image))
                : config.background != null ? translucent(readableOn(config.background, config.backgroundEnd)) : null;
        if (names != null) {
            fields.setBoth(theme, "light_message_attribution_color", "dark_message_attribution_color", names);
        }
        if (config.header != null) {
            fields.setBoth(theme, "light_theme_nav_bar_color", "dark_theme_nav_bar_color", config.header);
            fields.setBoth(theme, "light_theme_nav_bar_subtitle_color", "dark_theme_nav_bar_subtitle_color",
                    translucent(readableOn(config.header, null)));
        }
        if (config.inputBar != null) {
            fields.setBoth(theme, "composer_color", "dark_composer_color", config.inputBar);
            // Without a color of its own the border is drawn around the bar in the color of the default theme.
            fields.setBoth(theme, "light_composer_border_color", "dark_composer_border_color", config.inputBar);
            fields.setBoth(theme, "light_input_placeholder_text_color", "dark_input_placeholder_text_color",
                    translucent(readableOn(config.inputBar, null)));
        }
        // Instagram takes a theme with the id of the default theme for the default theme and leaves it as it is.
        fields.set(theme, "id", idOf(config));
        return theme;
    }

    private static String gradientPictureFor(ChatColorsConfig config) throws IOException {
        Resources resources = Utils.getContext().getResources();
        DisplayMetrics screen = resources.getDisplayMetrics();
        float density = screen.density;
        float height = Math.max(screen.widthPixels, screen.heightPixels);
        float statusBar = systemBarHeight(resources, "status_bar_height", 24 * density);
        // The larger bar of a navigation by buttons is used: too much left out only flattens the end of the gradient.
        float navigationBar = systemBarHeight(resources, "navigation_bar_height", 48 * density);
        // Only a bar that has a color is opaque, so only then does the gradient start or end past it, in thousandths.
        int hiddenTop = config.header == null ? 0 : Math.round(1000 * (statusBar + TOOLBAR_DP * density) / height);
        int hiddenBottom = config.inputBar == null ? 0 : Math.round(1000 * (COMPOSER_DP * density + navigationBar) / height);
        return gradientPicture(config.background, config.backgroundEnd, Math.min(hiddenTop, 300),
                Math.min(hiddenBottom, 300));
    }

    private static float systemBarHeight(Resources resources, String name, float fallback) {
        int id = resources.getIdentifier(name, "dimen", "android");
        return id != 0 ? resources.getDimensionPixelSize(id) : fallback;
    }

    private static String gradientPicture(String top, String bottom, int hiddenTop, int hiddenBottom)
            throws IOException {
        synchronized (FILE_LOCK) {
            File directory = Utils.getContext().getFilesDir();
            File picture = new File(directory,
                    GRADIENT_PREFIX + top + '_' + bottom + '_' + hiddenTop + '_' + hiddenBottom + ".png");
            if (!picture.isFile() || picture.length() == 0) {
                ChatBackgroundImage.deleteWithPrefix(directory, GRADIENT_PREFIX, picture, currentGradient);
                // Written under another name first, so a write that fails does not leave a picture that looks complete.
                File temporary = new File(picture.getPath() + ".tmp");
                Bitmap bitmap = Bitmap.createBitmap(GRADIENT_WIDTH, GRADIENT_HEIGHT, Bitmap.Config.ARGB_8888);
                try {
                    Paint paint = new Paint();
                    paint.setShader(new LinearGradient(0, GRADIENT_HEIGHT * hiddenTop / 1000f,
                            0, GRADIENT_HEIGHT * (1000 - hiddenBottom) / 1000f,
                            HexColor.toArgb(top), HexColor.toArgb(bottom), Shader.TileMode.CLAMP));
                    new Canvas(bitmap).drawRect(0, 0, GRADIENT_WIDTH, GRADIENT_HEIGHT, paint);
                    boolean written;
                    try (FileOutputStream output = new FileOutputStream(temporary)) {
                        written = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                    }
                    if (!written || !temporary.renameTo(picture)) throw new IOException("Could not write " + picture);
                } finally {
                    bitmap.recycle();
                    temporary.delete();
                }
            }
            currentGradient = picture;
            return Uri.fromFile(picture).toString();
        }
    }

    private static String toneOf(File image) {
        synchronized (FILE_LOCK) {
            if (!image.getName().equals(toneName)) {
                toneColor = ChatBackgroundImage.readableOn(image);
                toneName = image.getName();
            }
            return toneColor;
        }
    }

    /** Makes the files a theme needs ahead of time, so the first theme asked for after a change does not wait. */
    static void prepare(ChatColorsConfig config) {
        try {
            File image = config.backgroundImage == null ? null
                    : ChatBackgroundImage.fileOf(Utils.getContext(), config.backgroundImage);
            if (image != null) {
                toneOf(image);
            } else if (config.background != null && config.backgroundEnd != null) {
                gradientPictureFor(config);
            }
        } catch (Throwable throwable) {
            Logger.printException(() -> "ChatColors.prepare failed", throwable);
        }
    }

    public static void onSettingsReset(Context context) {
        synchronized (FILE_LOCK) {
            ChatBackgroundImage.deleteFiles(context);
            ChatBackgroundImage.deleteWithPrefix(context.getFilesDir(), GRADIENT_PREFIX);
            currentGradient = null;
        }
        ChatColorsConfig.invalidate();
    }

    private static String readableOn(String start, String end) {
        if (end == null) return HexColor.readableText(brightness(start));
        return HexColor.readableTextOnAll(HexColor.toArgb(start), HexColor.toArgb(end));
    }

    private static String readableOnBubble(String start, String end, ChatColorsConfig config) {
        if (config.background == null) return readableOn(start, end);
        int background = HexColor.toArgb(config.background);
        if (end == null) return HexColor.readableTextOnAll(over(start, background));
        return HexColor.readableTextOnAll(over(start, background), over(end, background));
    }

    private static int over(String color, int background) {
        int argb = HexColor.toArgb(color);
        int alpha = argb >>> 24;
        int blended = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int channel = ((argb >> shift) & 0xFF) * alpha + ((background >> shift) & 0xFF) * (255 - alpha);
            blended |= (channel / 255) << shift;
        }
        return blended;
    }

    private static String translucent(String color) {
        return "B3" + color;
    }

    private static double brightness(String color) {
        return HexColor.brightness(HexColor.toArgb(color));
    }

    /** A numeric id like Instagram's that changes with the colors, so nothing kept by theme id goes stale. */
    private static String idOf(ChatColorsConfig config) {
        long hash = 1125899906842597L;
        for (char character : config.signature().toCharArray()) hash = 31 * hash + character;
        return String.format(Locale.ROOT, "9%014d", Math.floorMod(hash, 100_000_000_000_000L));
    }

    /** Instagram tints the composer's camera button with the sent text color, so this gives it the automatic one. */
    public static int composerIconColor(int color) {
        try {
            ChatColorsConfig config = ChatColorsConfig.current();
            Built current = built;
            ThemeFields fields = knownFields;
            if (config.sentText == null || current == null || fields == null) return color;
            if (color != HexColor.toArgb(config.sentText)) return color;

            String automatic;
            if (config.sentStart != null) {
                automatic = readableOnBubble(config.sentStart, config.sentEnd, config);
            } else {
                Object original = fields.fieldOf(UI.isDarkMode() ? DARK_SENT_TEXT : LIGHT_SENT_TEXT).get(current.from);
                automatic = original == null ? null : HexColor.normalize(original.toString());
            }
            return automatic == null ? color : HexColor.toArgb(automatic);
        } catch (Throwable throwable) {
            Logger.printException(() -> "ChatColors.composerIconColor failed", throwable);
            return color;
        }
    }

    public static List menuOpening(List buttons) {
        menuButton = null;
        // A list opened by pressing a button of the menu is not the menu of the chat, so it gets no entry.
        if (pressingSubMenuButton) return buttons;
        try {
            if (buttons == null || buttons.isEmpty()) return buttons;
            Class<? extends Enum> type = ((Enum) buttons.get(0)).getDeclaringClass();
            Set<String> present = new HashSet<>();
            for (Object button : buttons) present.add(String.valueOf(button));
            for (String candidate : MENU_BUTTONS) {
                if (present.contains(candidate)) continue;
                // A copy, because the list of Instagram can be read-only.
                List extended = new ArrayList(buttons);
                extended.add(Enum.valueOf(type, candidate));
                menuButton = candidate;
                return extended;
            }
        } catch (Throwable throwable) {
            Logger.printException(() -> "ChatColors.menuOpening failed", throwable);
        }
        return buttons;
    }

    public static String[] menuLabels(String[] labels, List buttons) {
        String added = menuButton;
        if (added == null || labels == null || buttons == null) return labels;
        for (int index = Math.min(buttons.size(), labels.length) - 1; index >= 0; index--) {
            if (added.equals(String.valueOf(buttons.get(index)))) {
                labels[index] = str("piko_chat_colors");
                break;
            }
        }
        return labels;
    }

    public static Drawable menuIcon(Context context, Object button) {
        String added = menuButton;
        if (added == null || !added.equals(String.valueOf(button))) return null;
        try {
            int icon = ResourceUtils.getIdentifier(ResourceType.DRAWABLE, MENU_ICON);
            return icon == 0 ? null : context.getDrawable(icon);
        } catch (Throwable throwable) {
            Logger.printException(() -> "ChatColors.menuIcon failed", throwable);
            return null;
        }
    }

    public static boolean onMenuButton(Context context, Object button, Object threadKey) {
        String pressed = String.valueOf(button);
        if (SUB_MENU_BUTTONS.contains(pressed)) {
            // Instagram opens the list of such a button before this press is handled, so the flag is dropped once the
            // main thread is done with it, whether a list was opened or not.
            pressingSubMenuButton = true;
            MAIN_THREAD.post(() -> pressingSubMenuButton = false);
        }
        String added = menuButton;
        if (added == null || !added.equals(pressed)) return false;
        menuButton = null;
        try {
            if (context != null && threadKey instanceof DirectThreadKey) {
                String chatId = ((DirectThreadKey) threadKey).A00;
                // A chat with no id yet, only its people, cannot be told apart from another, so nothing is chosen for it.
                if (chatId != null) chooseColorsOf(context, chatId);
            }
        } catch (Throwable throwable) {
            Logger.printException(() -> "ChatColors.onMenuButton failed", throwable);
        }
        // The button is borrowed from Instagram, so its own action must not run.
        return true;
    }

    private static void chooseColorsOf(Context context, String chatId) {
        String choice = ChatColorsConfig.current().choiceOf(chatId);
        int checked = ChatColorsConfig.CHOICE_NATIVE.equals(choice) ? 1
                : ChatColorsConfig.CHOICE_PIKO.equals(choice) ? 2 : 0;
        CharSequence[] items = {
                str("piko_chat_colors_chat_default"),
                str("piko_chat_colors_chat_native"),
                str("piko_chat_colors_chat_piko"),
        };
        new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(context))
                .setTitle(str("piko_chat_colors_chat_title"))
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    dialog.dismiss();
                    try {
                        ChatColorsConfig.saveChoice(chatId, which == 1 ? ChatColorsConfig.CHOICE_NATIVE
                                : which == 2 ? ChatColorsConfig.CHOICE_PIKO : null);
                        PikoUtils.toast(str("piko_chat_colors_chat_saved"));
                    } catch (Throwable throwable) {
                        Logger.printException(() -> "ChatColors.saveChoice failed", throwable);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
