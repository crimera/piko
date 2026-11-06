/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

/**
 * Wrapper around a single Instagram DM (DirectItem).
 *
 * <p>All placeholder names below (e.g. {@code "fieldName"}) are rewritten at patch time by
 * {@code directItemEntity}, so at runtime this class only does plain reflection on names
 * already correct for the installed app version. See {@code MediaData} for the reference pattern.
 *
 * <p>Reads go through the base class because the MQTT subclass shadows several base fields with
 * same-named fields of a different type. Reading off the (patched) base class avoids that mix-up.
 */
public class DirectItem extends Entity {
    private final Object obj;

    public DirectItem(Object obj) {
        super(obj);
        this.obj = obj;
    }

    // getBaseClassName()/mediaClassName() are patch-time constants, the same for every instance,
    // so the classes they name are resolved once per name and reused across all DirectItems -
    // every reflective read below (one per message, per field, in every open chat) otherwise
    // repeated Class.forName() for a class already resolved.
    private static final java.util.concurrent.ConcurrentHashMap<String, Class<?>> CLASS_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static Class<?> resolveClass(String className) throws ClassNotFoundException {
        Class<?> cached = CLASS_CACHE.get(className);
        if (cached != null) return cached;

        Class<?> resolved = Class.forName(className);
        CLASS_CACHE.putIfAbsent(className, resolved);
        return CLASS_CACHE.get(className);
    }

    /** Binary name of the DirectItem base class (patched, e.g. {@code X.9ZA}). */
    private String getBaseClassName() {
        return "className";
    }

    /** Loads the base class via the app classloader. */
    private Class<?> baseClass() throws Exception {
        return resolveClass(this.getBaseClassName());
    }

    /** Reads a base-class field, bypassing MQTT subclass field shadowing. */
    private Object readBaseField(String fieldName) throws Exception {
        return super.getField(this.baseClass(), this.obj, fieldName);
    }

    public String getItemId() {
        try {
            Object v = this.readBaseField("itemIdField");
            return v instanceof String ? (String) v : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String getClientContext() {
        try {
            Object v = this.readBaseField("clientContextField");
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
        // REST stores text on the base class; MQTT leaves it null and uses a subclass payload field.
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

    /** Raw server timestamp string (microseconds). */
    private String getTimestampRaw() {
        try {
            Object v = this.readBaseField("fieldName");
            return v instanceof String ? (String) v : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Timestamp in milliseconds, or {@code now} when unavailable. */
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

    /** True when sent by the logged-in user. */
    public boolean isSentByViewer() {
        try {
            Object v = this.readBaseField("fieldName");
            return v instanceof Boolean && (Boolean) v;
        } catch (Exception e) {
            return false;
        }
    }

    /** Clears/sets the hide_in_thread flag (anti-revoke). */
    public void setHideInThread(boolean hidden) {
        try {
            java.lang.reflect.Field f = this.baseClass().getDeclaredField("fieldName");
            f.setAccessible(true);
            f.set(this.obj, hidden);
        } catch (Exception ignored) {
        }
    }

    /** Restores the text on both the base and MQTT subclass fields (anti-revoke). */
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

    // Media URL resolution. Every supported shape carries an unobfuscated Media object (the stable
    // anchor); field names below are placeholders rewritten at patch time by directItemEntity.
    //   * DIRECT  — a Media field (media, media_share, raven_media).
    //   * WRAPPED — a reshare/voice wrapper holding a Media (clip, reel_share, voice_media).
    // Not resolved (no stable type anchor): animated_media, story_share, xma, link -> "[type]" label.

    /** Binary name of the concrete item class (e.g. X/6fW) that declares the media fields. */
    private String mediaClassName() { return "mediaClassName"; }

    private Class<?> mediaClass() throws Exception { return resolveClass(this.mediaClassName()); }

    // Field-name providers — one placeholder each, rewritten at patch time via changeFirstString.
    private String fieldMedia()        { return "fieldName"; } // media        -> Media
    private String fieldMediaShare()   { return "fieldName"; } // media_share  -> Media
    private String fieldRavenMedia()   { return "fieldName"; } // raven_media  -> Media
    private String fieldClip()         { return "fieldName"; } // clip         -> wrapper
    private String fieldClipMedia()    { return "fieldName"; } //   wrapper.Media (by type)
    private String fieldReel()         { return "fieldName"; } // reel_share   -> wrapper
    private String fieldReelMedia()    { return "fieldName"; } //   wrapper.Media (by type)
    private String fieldVoice()        { return "fieldName"; } // voice_media  -> wrapper
    private String fieldVoiceMedia()   { return "fieldName"; } //   wrapper.Media (by type)
    private String fieldVisual()       { return "fieldName"; } // visual_media -> wrapper
    private String fieldVisualMedia()  { return "fieldName"; } //   wrapper.Media (by type)

    /** Link from a Media field declared directly on the item class. */
    private String directMediaUrl(String fieldName) {
        try {
            Object media = super.getField(this.mediaClass(), this.obj, fieldName);
            if (media == null) return null;
            return new MediaData(media).getMediaLink();
        } catch (Exception e) {
            return null;
        }
    }

    /** Link from a Media nested one level inside an obfuscated reshare/voice wrapper. */
    private String wrappedMediaUrl(String wrapperField, String innerMediaField) {
        try {
            Object wrapper = super.getField(this.mediaClass(), this.obj, wrapperField);
            if (wrapper == null) return null;
            Object media = super.getField(wrapper.getClass(), wrapper, innerMediaField);
            if (media == null) return null;
            return new MediaData(media).getMediaLink();
        } catch (Exception e) {
            return null;
        }
    }

    /** Audio URL for a voice note (Media.getMessageAudioUrl; no image/video candidate). */
    private String wrappedAudioUrl(String wrapperField, String innerMediaField) {
        try {
            Object wrapper = super.getField(this.mediaClass(), this.obj, wrapperField);
            if (wrapper == null) return null;
            Object media = super.getField(wrapper.getClass(), wrapper, innerMediaField);
            if (media == null) return null;
            return new MediaData(media).getMessageAudioUrl();
        } catch (Exception e) {
            return null;
        }
    }

    /** Whether a photo/video item (media, disappearing media) is a video; null if unknown. */
    public Boolean isVideo() {
        try {
            Object media = super.getField(this.mediaClass(), this.obj, this.fieldMedia());
            if (media == null) media = super.getField(this.mediaClass(), this.obj, this.fieldRavenMedia());
            if (media == null) {
                Object wrapper = super.getField(this.mediaClass(), this.obj, this.fieldVisual());
                if (wrapper != null) media = super.getField(wrapper.getClass(), wrapper, this.fieldVisualMedia());
            }
            return media == null ? null : new MediaData(media).isVideo();
        } catch (Throwable e) {
            return null;
        }
    }

    /** First usable media URL (image/video/audio) across all supported DM shapes. */
    public String getMediaUrl() {
        String[] urls = {
            directMediaUrl(this.fieldMedia()),
            directMediaUrl(this.fieldMediaShare()),
            directMediaUrl(this.fieldRavenMedia()),
            wrappedMediaUrl(this.fieldVisual(), this.fieldVisualMedia()),
            wrappedMediaUrl(this.fieldClip(),  this.fieldClipMedia()),
            wrappedMediaUrl(this.fieldReel(),  this.fieldReelMedia()),
            wrappedAudioUrl(this.fieldVoice(), this.fieldVoiceMedia()),
        };
        for (String u : urls) if (u != null && u.startsWith("http")) return u;
        return null;
    }

    // xma reshare: the item holds a List of xma elements, each with a permalink String
    // (JSON "target_url").
    private String fieldXma()     { return "fieldName"; } // xma List on item
    private String fieldXmaLink() { return "fieldName"; } // permalink on each element

    private String fieldXmaPreview() { return "fieldName"; } // thumbnail on each element (JSON "preview_url_info")

    /**
     * An image to show for the item in a notification: a photo, a video's cover, or a shared
     * reel/post thumbnail. Never disappearing media (raven/visual), which stays view-once.
     */
    public String imageUrl() {
        String type = getItemType();
        if (type == null) return null;
        try {
            switch (type) {
                case "media":
                    return imageOf(super.getField(this.mediaClass(), this.obj, this.fieldMedia()));
                case "media_share":
                    return imageOf(super.getField(this.mediaClass(), this.obj, this.fieldMediaShare()));
                case "clip":
                    return imageOf(wrapped(this.fieldClip(), this.fieldClipMedia()));
                case "reel_share":
                    return imageOf(wrapped(this.fieldReel(), this.fieldReelMedia()));
                case "animated_media":
                    return stickerUrl();
                case "store_sticker":
                    return storeStickerUrl();
                default:
                    return type.startsWith("xma_") || type.equals("generic_xma") ? xmaPreview() : null;
            }
        } catch (Throwable e) {
            return null;
        }
    }

    /** Class declaring the animated_media field (X.08Ai on 452), not necessarily the media class. */
    private String animatedClassName() { return "animatedClassName"; }
    private String fieldAnimated()    { return "fieldName"; } // animated_media -> sticker/GIF item
    private String fieldAnimatedGif() { return "fieldName"; } //   its GifUrlImpl (JSON "gif_url")
    private String fieldAnimatedImages() { return "fieldName"; } //   its "images" object
    private String fieldImagesGif()   { return "fieldName"; } //     "fixed_height" GifUrlImpl
    private String fieldGifUrl()      { return "fieldName"; } //   GifUrlImpl "url"
    private String fieldGifWebp()     { return "fieldName"; } //   GifUrlImpl "webp"

    private String storeClassName()    { return "storeClassName"; }
    private String fieldStoreSticker() { return "fieldName"; } // store_sticker -> DirectStoreStickerResponseItem
    private String fieldStoreImage()   { return "fieldName"; } //   its "image_url"

    /** Image of one of Instagram's own stickers. */
    private String storeStickerUrl() throws Exception {
        Object sticker = super.getField(resolveClass(this.storeClassName()), this.obj, this.fieldStoreSticker());
        if (sticker == null) return null;
        Object v = super.getField(sticker.getClass(), sticker, this.fieldStoreImage());
        if (v instanceof String && ((String) v).startsWith("http")) return (String) v;
        return null;
    }

    /** Still image of a sticker or GIF: its "url", else its "webp". */
    private String stickerUrl() throws Exception {
        Object animated = super.getField(resolveClass(this.animatedClassName()), this.obj, this.fieldAnimated());
        if (animated == null) return null;
        Object gif = super.getField(animated.getClass(), animated, this.fieldAnimatedGif());
        if (gif == null) {
            Object images = super.getField(animated.getClass(), animated, this.fieldAnimatedImages());
            if (images != null) gif = super.getField(images.getClass(), images, this.fieldImagesGif());
        }
        if (gif == null) return null;
        for (String field : new String[]{this.fieldGifUrl(), this.fieldGifWebp()}) {
            Object v = super.getField(gif.getClass(), gif, field);
            if (v instanceof String && ((String) v).startsWith("http")) return (String) v;
        }
        return null;
    }

    private Object wrapped(String wrapperField, String innerMediaField) throws Exception {
        Object wrapper = super.getField(this.mediaClass(), this.obj, wrapperField);
        return wrapper == null ? null : super.getField(wrapper.getClass(), wrapper, innerMediaField);
    }

    private static String imageOf(Object media) throws Exception {
        if (media == null) return null;
        String url = new MediaData(media).getImageLink();
        return url != null && url.startsWith("http") ? url : null;
    }

    private String xmaPreview() {
        try {
            Object list = super.getField(this.mediaClass(), this.obj, this.fieldXma());
            if (!(list instanceof java.util.List)) return null;
            for (Object element : (java.util.List<?>) list) {
                if (element == null) continue;
                Object v = super.getField(element.getClass(), element, this.fieldXmaPreview());
                if (v != null && !(v instanceof String)) {
                    // An ExtendedImageUrl; ImageUrl.getUrl() keeps its name.
                    java.lang.reflect.Method getUrl = v.getClass().getMethod("getUrl");
                    v = getUrl.invoke(v);
                }
                if (v instanceof String && ((String) v).startsWith("http")) return (String) v;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Permalink for a shared post/reel, e.g. https://www.instagram.com/reel/<code>/. */
    public String xmaReshareLink() {
        try {
            Object list = super.getField(this.mediaClass(), this.obj, this.fieldXma());
            if (!(list instanceof java.util.List)) return null;
            for (Object element : (java.util.List<?>) list) {
                if (element == null) continue;
                Object v = super.getField(element.getClass(), element, this.fieldXmaLink());
                if (!(v instanceof String)) continue;
                String s = (String) v;
                // only actual post/reel links; other xma types (e.g. xma_link) point elsewhere
                if (s.startsWith("https://www.instagram.com/")
                        && (s.contains("/p/") || s.contains("/reel/") || s.contains("/reels/") || s.contains("/tv/"))) {
                    int q = s.indexOf('?');
                    return q > 0 ? s.substring(0, q) : s;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
