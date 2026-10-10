/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.ui.Dim;

/** Shows under a photo or video how it was sent, since "Make ephemeral media permanent" hides that. */
@SuppressWarnings("unused")
public final class EphemeralMediaCaption {
    private static final String BUBBLE_ID = "message_content_visual_thumbnail_bubble_container";
    private static final String PLACEHOLDER_ID = "message_placeholder_container";
    private static final String IMAGE_URL_CLASS = "com.instagram.model.mediasize.ExtendedImageUrl";
    private static final String CAPTION_TAG = "piko_ephemeral_caption";
    private static final String MODE_PERMANENT = "permanent";
    private static final long MICROS = 1_000_000L;
    private static final int SEARCH_DEPTH = 3;
    private static final int SEARCH_LIMIT = 1500;

    private EphemeralMediaCaption() { }

    /** Called while a message is parsed, before its view mode is changed, so it still has the mode it was sent with. */
    public static void record(Long expireAt, String viewMode) {
        try {
            if (expireAt == null || viewMode == null || !Pref.ephemeralMediaCaption()) return;
            if (expireAt * 1000 < System.currentTimeMillis()) return;
            // Once saved, the message comes back as permanent, so only the first mode seen is the real one.
            String saved = EphemeralModesPref.get(expireAt);
            if (saved.isEmpty() || (saved.equals(MODE_PERMANENT) && !viewMode.equals(MODE_PERMANENT))) {
                EphemeralModesPref.put(expireAt, viewMode);
            }
        } catch (Throwable t) {
            Logger.printException(() -> "EphemeralMediaCaption.record failed", t);
        }
    }

    /** Called when a photo or video message is bound to its views. */
    public static void onBind(Object holder, Object model) {
        try {
            if (!Pref.ephemeralMediaCaption() || !Pref.makeEphemeralMediaPermanent()) return;
            View bubble = bubbleOf(holder);
            if (bubble == null) return;
            ViewGroup placeholder = placeholderOf(bubble);
            if (placeholder == null) return;

            Long expireAt = expireAtOf(model);
            String text = expireAt == null ? null : textFor(expireAt, bubble);
            // Rows are reused, so a row without a caption must hide the one left by its previous message.
            View existing = placeholder.findViewWithTag(CAPTION_TAG);
            if (text == null) {
                if (existing != null) existing.setVisibility(View.GONE);
                return;
            }
            TextView caption = (TextView) (existing != null ? existing : newCaption(placeholder, bubble));
            caption.setText(text);
            caption.setVisibility(View.VISIBLE);
        } catch (Throwable t) {
            Logger.printException(() -> "EphemeralMediaCaption.onBind failed", t);
        }
    }

    private static String textFor(long expireAt, View bubble) {
        String how;
        switch (EphemeralModesPref.get(expireAt)) {
            case "once": how = str("piko_ephemeral_received_once"); break;
            case "replayable": how = str("piko_ephemeral_received_twice"); break;
            case MODE_PERMANENT: how = str("piko_ephemeral_received_chat"); break;
            default: return null;
        }

        long expireMillis = expireAt * 1000;
        if (expireMillis < System.currentTimeMillis()) return how + " · " + str("piko_ephemeral_expired");
        String until = DateUtils.formatDateTime(bubble.getContext(), expireMillis,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH);
        return how + " · " + String.format(str("piko_ephemeral_available_until"), until);
    }

    private static View bubbleOf(Object holder) throws Exception {
        int bubbleId = ResourceUtils.getIdentifier(ResourceType.ID, BUBBLE_ID);
        for (Field field : holder.getClass().getDeclaredFields()) {
            if (!View.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            View view = (View) field.get(holder);
            if (view != null && view.getId() == bubbleId) return view;
        }
        return null;
    }

    private static ViewGroup placeholderOf(View bubble) {
        int placeholderId = ResourceUtils.getIdentifier(ResourceType.ID, PLACEHOLDER_ID);
        View parent = bubble.getParent() instanceof View ? (View) bubble.getParent() : null;
        View grandParent = parent != null && parent.getParent() instanceof View ? (View) parent.getParent() : null;
        return grandParent instanceof ViewGroup && grandParent.getId() == placeholderId ? (ViewGroup) grandParent : null;
    }

    /** A new caption view under the photo. */
    private static View newCaption(ViewGroup placeholder, View bubble) {
        TextView caption = new TextView(placeholder.getContext());
        caption.setTag(CAPTION_TAG);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        caption.setTextColor(UI.getThemedColour("igds_color_secondary_text"));
        caption.setPadding(0, Dim.dp4, 0, Dim.dp4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        View row = (View) bubble.getParent();
        // Lines up with the photo, whose row starts after the avatar space.
        row.post(() -> {
            params.setMarginStart(row.getLeft() + bubble.getLeft());
            caption.setLayoutParams(params);
        });
        placeholder.addView(caption, placeholder.indexOfChild(row) + 1, params);
        return caption;
    }

    /** The second the message's media expires at, read from the image address in the message. */
    private static Long expireAtOf(Object model) throws Exception {
        if (model == null) return null;
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        ArrayDeque<Object> queue = new ArrayDeque<>();
        ArrayDeque<Integer> depths = new ArrayDeque<>();
        queue.add(model);
        depths.add(0);
        seen.put(model, true);
        while (!queue.isEmpty() && seen.size() < SEARCH_LIMIT) {
            Object current = queue.poll();
            int depth = depths.poll();
            if (depth >= SEARCH_DEPTH) continue;
            for (Field field : fieldsOf(current.getClass())) {
                Object value = field.get(current);
                if (value == null || seen.containsKey(value) || isFramework(value.getClass())) continue;
                if (value.getClass().getName().equals(IMAGE_URL_CLASS)) {
                    Long expireAt = expireAtIn(value);
                    if (expireAt != null) return expireAt;
                }
                seen.put(value, true);
                queue.add(value);
                depths.add(depth + 1);
            }
        }
        return null;
    }

    /** The expiry second of the media, if the image address holds one that was recorded. */
    private static Long expireAtIn(Object imageUrl) throws Exception {
        for (Field field : imageUrl.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(imageUrl);
            if (!(value instanceof Long)) continue;
            long micros = (Long) value;
            if (micros % MICROS == 0 && !EphemeralModesPref.get(micros / MICROS).isEmpty()) return micros / MICROS;
        }
        return null;
    }

    private static List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && !isFramework(c); c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                field.setAccessible(true);
                fields.add(field);
            }
        }
        return fields;
    }

    private static boolean isFramework(Class<?> type) {
        String name = type.getName();
        return name.startsWith("java.") || name.startsWith("android.") || name.startsWith("kotlin.") || name.startsWith("androidx.");
    }
}
