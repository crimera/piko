/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

/**
 * Entity wrapper around Instagram's DirectItem (a single DM).
 *
 * <p>Unlike the previous implementation, NO obfuscated field/method name is discovered at
 * runtime. Every placeholder String below ({@code "className"}, {@code "fieldName"}, …) is
 * rewritten at patch time by {@code directItemEntity} once the names are read out of the
 * target APK's bytecode. At runtime this class only performs plain reflection on names that
 * are already correct for the installed Instagram version. See {@code MediaData} for the
 * reference pattern.
 *
 * <h2>Why reads go through the base class</h2>
 * The concrete runtime type of a DM is either the REST base class or an MQTT subclass that
 * <em>shadows</em> several base fields with same-named fields of a different type (e.g. the
 * base {@code item_id} String is shadowed by a boolean on the subclass). A naive
 * {@code obj.getClass().getDeclaredField(...)} would therefore read the wrong field on the
 * subclass. We resolve the base class once (its binary name is patched into
 * {@link #getBaseClassName()}) and always read declared fields off <em>that</em> class, so
 * the same entity works for both delivery paths.
 */
public class DirectItem extends Entity {
    private final Object obj;

    public DirectItem(Object obj) {
        super(obj);
        this.obj = obj;
    }

    /** Binary name of the DirectItem base class (patched, e.g. {@code X.9ZA}). */
    private String getBaseClassName() {
        return "className";
    }

    /** The base class that declares the scalar fields, loaded via the app classloader. */
    private Class<?> baseClass() throws Exception {
        return Class.forName(this.getBaseClassName());
    }

    /** Read a base-class field off this item, defeating subclass field shadowing. */
    private Object readBaseField(String fieldName) throws Exception {
        return super.getField(this.baseClass(), this.obj, fieldName);
    }

    public String getItemId() {
        try {
            Object v = this.readBaseField("fieldName");
            return v instanceof String ? (String) v : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String getUserId() {
        try {
            Object v = this.readBaseField("fieldName");
            return v instanceof String ? (String) v : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String getText() {
        // REST items store text on the base class. MQTT items leave that null and put the text in
        // a polymorphic payload field on the subclass — both names are resolved at patch time.
        try {
            Object v = this.readBaseField("baseTextField");
            if (v instanceof CharSequence) return v.toString();
        } catch (Exception ignored) {
        }
        try {
            Object v = super.getField(this.obj, "subTextField");
            if (v instanceof CharSequence) return v.toString();
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Raw server timestamp string (microseconds). Use {@link #getTimestampMs()} for millis. */
    private String getTimestampRaw() {
        try {
            Object v = this.readBaseField("fieldName");
            return v instanceof String ? (String) v : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Message timestamp in milliseconds, or {@code now} when unavailable. */
    public long getTimestampMs() {
        String raw = this.getTimestampRaw();
        if (raw != null && !raw.isEmpty()) {
            try {
                return Long.parseLong(raw) / 1000L; // microseconds -> milliseconds
            } catch (NumberFormatException ignored) {
            }
        }
        return System.currentTimeMillis();
    }

    /** True when the sender has unsent (hidden) this message. */
    public boolean isHideInThread() {
        try {
            Object v = this.readBaseField("fieldName");
            return v instanceof Boolean && (Boolean) v;
        } catch (Exception e) {
            return false;
        }
    }

    /** True when this message was sent by the logged-in user (an outgoing message). */
    public boolean isSentByViewer() {
        try {
            Object v = this.readBaseField("fieldName");
            return v instanceof Boolean && (Boolean) v;
        } catch (Exception e) {
            return false;
        }
    }

    /** Sets/clears the hide_in_thread flag on the item (used by anti-revoke). */
    public void setHideInThread(boolean hidden) {
        try {
            java.lang.reflect.Field f = this.baseClass().getDeclaredField("fieldName");
            f.setAccessible(true);
            f.set(this.obj, hidden);
        } catch (Exception ignored) {
        }
    }

    /** Restores the message text on the item (used by anti-revoke). Sets both the base-class text
     *  field and the MQTT subclass payload field so the message renders on either delivery path. */
    public void setText(String text) {
        try {
            java.lang.reflect.Field f = this.baseClass().getDeclaredField("baseTextField");
            f.setAccessible(true);
            f.set(this.obj, text);
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Field f = this.obj.getClass().getDeclaredField("subTextField");
            f.setAccessible(true);
            f.set(this.obj, text);
        } catch (Exception ignored) {
        }
    }

    /** item_type as a stable token (enum {@code toString()}), or null. */
    public String getItemType() {
        try {
            Object v = this.readBaseField("fieldName");
            return v != null ? v.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The DirectThreadKey object, or null. */
    private Object getThreadKey() throws Exception {
        return this.readBaseField("fieldName");
    }

    /** The numeric thread id this message belongs to, or null. */
    public String getThreadId() {
        try {
            Object key = this.getThreadKey();
            if (key == null) return null;
            Object v = super.getField(key, "fieldName");
            return v instanceof String ? (String) v : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The attached {@code com.instagram.feed.media.Media} object for a photo/video DM, or null.
     * Lives in a base-class Object field whose obfuscated name is patched in at build time. Voice
     * media uses a different (obfuscated) type in a separate field and is NOT returned here.
     */
    private Object getMediaObject() throws Exception {
        return this.readBaseField("mediaField");
    }

    /**
     * Best-effort media URL (image/video) for a photo/video DM, or null when the item carries no
     * such media. Reuses {@link MediaData} since the DM media object is the same feed Media class.
     * Callers should fall back to a heuristic search for shapes this doesn't cover (e.g. voice).
     */
    public String getMediaUrl() {
        try {
            Object media = this.getMediaObject();
            if (media == null) return null;
            return new MediaData(media).getMediaLink();
        } catch (Exception e) {
            return null;
        }
    }
}
