/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import com.instagram.common.session.UserSession;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * Reels download button. The reels action column is a Litho UFI whose buttons are separate components.
 * The patch builds a second copy of the save button component next to the real one and registers it
 * here; every hook below receives the component being rendered and only changes the copy, so the real
 * save button renders exactly as before.
 *
 * <p>The copy keeps the save button's size, spacing, tint and layout. What the hooks swap is its icon,
 * its tap and long press handlers, its selected state and its id. The impression callback must stay
 * untouched: it runs whenever a reel is shown.
 */
public final class ReelDownload {
    /**
     * The reels icons are drawn from the 44 size family (the save icon is `instagram_save_outline_44`), whose
     * padding and stroke weight the 24 size vector does not match, so the download icon comes from it too.
     */
    private static final String DRAWABLE_DOWNLOAD_ICON = "instagram_download_outline_44";

    /** The attribute the save icon is tinted with when its component carries no color filter of its own. */
    private static final String ICON_TINT_ATTRIBUTE = "igds_color_primary_button_on_media";

    /** The copies the patch created. Weak, because the UFI builds fresh components on every render. */
    private static final Set<Object> COPIES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private ReelDownload() {}

    public static boolean isEnabled() {
        return SettingsStatus.reelDownloadButton
                && Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.ENABLE_DOWNLOAD))
                && Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.REEL_DOWNLOAD_BUTTON));
    }

    public static void register(Object component) {
        COPIES.add(component);
    }

    /** What the save component being rendered on this thread needs, or null for the real save button. */
    private static final ThreadLocal<Copy> CURRENT = new ThreadLocal<>();

    private static final class Copy {
        final Context context;
        final UserSession userSession;
        final Object media;
        final ColorFilter tint;

        Copy(Context context, UserSession userSession, Object media, ColorFilter tint) {
            this.context = context;
            this.userSession = userSession;
            this.media = media;
            this.tint = tint;
        }
    }

    /**
     * Called at the start of every save component render. The hooks further down the render method run
     * on the same thread and only receive the value they replace, so what they need is kept here.
     */
    public static void begin(Object component, Object composer) {
        CURRENT.set(
                COPIES.contains(component)
                        ? new Copy(context(composer), session(component), media(component), tint(component))
                        : null);
    }

    public static void end() {
        CURRENT.remove();
    }

    public static Drawable icon(Drawable original) {
        Copy copy = CURRENT.get();
        if (copy == null) return original;
        Context context = copy.context;
        int drawableId = ResourceUtils.getIdentifier(context, ResourceType.DRAWABLE, DRAWABLE_DOWNLOAD_ICON);
        if (drawableId == 0) throw new IllegalStateException("Missing drawable " + DRAWABLE_DOWNLOAD_ICON);
        Drawable drawable = context.getDrawable(drawableId).mutate();
        drawable.setColorFilter(copy.tint != null ? copy.tint : themeTint(copy.context));
        return drawable;
    }

    /** The color the real save icon falls back to, resolved from the theme of the context it renders in. */
    private static ColorFilter themeTint(Context context) {
        if (context == null) throw new IllegalStateException("Reel download icon has no themed context");
        int attrId = ResourceUtils.getAttrIdentifier(ICON_TINT_ATTRIBUTE);
        TypedValue value = new TypedValue();
        if (attrId == 0 || !context.getTheme().resolveAttribute(attrId, value, true)) {
            throw new IllegalStateException("Unresolved theme attribute " + ICON_TINT_ATTRIBUTE);
        }
        int color = value.resourceId != 0 ? context.getColor(value.resourceId) : value.data;
        return new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    /** The copy is never "saved", whatever the post's saved state is. */
    public static boolean selected(boolean original) {
        return CURRENT.get() == null && original;
    }

    public static CharSequence description(CharSequence original) {
        return CURRENT.get() == null ? original : str("piko_download_current_media");
    }

    /** Keeps the save button's id unique, so lookups by that id still reach the real button. */
    public static int viewId(int original) {
        return CURRENT.get() == null ? original : -1;
    }

    public static Function1<Object, Object> click(Function1<Object, Object> original) {
        Copy copy = CURRENT.get();
        if (copy == null) return original;
        return new DownloadClick(copy.context, copy.userSession, copy.media);
    }

    /** Consume long presses on the copy without opening Instagram's save-collection sheet. */
    public static Function1<Object, Object> longClick(Function1<Object, Object> original) {
        return CURRENT.get() == null ? original : ConsumeLongClick.INSTANCE;
    }

    private static void download(Context context, UserSession userSession, Object media) {
        try {
            if (context == null) throw new IllegalStateException("Reel download has no context");
            if (userSession == null) throw new IllegalStateException("Reel download has no user session");
            if (media == null) throw new IllegalStateException("Reel download has no media");
            DownloadUtils.downloadPost(context, userSession, media, 0);
        } catch (Exception e) {
            Logger.printException(() -> "Error at reel download", e);
            Utils.showToastShort(e.getMessage());
        }
    }

    private static final class DownloadClick implements Function1<Object, Object> {
        private final Context context;
        private final UserSession userSession;
        private final Object media;

        DownloadClick(Context context, UserSession userSession, Object media) {
            this.context = context;
            this.userSession = userSession;
            this.media = media;
        }

        @Override
        public Object invoke(Object ignored) {
            download(context, userSession, media);
            return Unit.INSTANCE;
        }
    }

    private static final class ConsumeLongClick implements Function1<Object, Object> {
        static final ConsumeLongClick INSTANCE = new ConsumeLongClick();

        @Override
        public Object invoke(Object ignored) {
            return Boolean.TRUE;
        }
    }

    // The patch replaces the bodies below with direct reads of the resolved fields.

    /** The `Media` the save component renders, or null. */
    static Object media(Object saveComponent) {
        return null;
    }

    static UserSession session(Object saveComponent) {
        return null;
    }

    /** The color filter that tints the save component's icon. */
    static ColorFilter tint(Object saveComponent) {
        return null;
    }

    /** The context the Litho tree renders in, taken from the component tree's composer. */
    static Context context(Object composer) {
        return null;
    }
}
