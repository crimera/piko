/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.feed.rounded;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.Settings;

public final class CarouselPeek {
    static final String PAGE = "carousel_image_media_group";
    static final String VIDEO_PAGE = "carousel_video_media_group";
    static final float INSET = 12, GAP = 8, PEEK = 16;
    static final int PAGER_DEPTH = 6;

    private static final Map<View, Before> pagers = new WeakHashMap<>();
    private static final Map<View, Boolean> pages = new WeakHashMap<>();
    private static final Map<View, Boolean> known = new WeakHashMap<>();
    private static final Map<Object, Boolean> widened = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Class<?>, Pager> kinds = new WeakHashMap<>();
    private static volatile int cachedId;
    private static volatile int cachedVideoId;

    private static final class Before {
        final float spacing;
        final int buffer;
        final Object bias;
        float scale;
        float pivot;
        int width;

        Before(float spacing, int buffer, Object bias) {
            this.spacing = spacing;
            this.buffer = buffer;
            this.bias = bias;
        }
    }

    static final class Pager {
        static final String NEXT_ONLY = "BIAS_NEXT_ONLY";

        final Method size, spacing, spacingNow, bufferNow, buffer, bias;
        final Field biasNow;
        final Object nextOnly;

        Pager(Method size, Method spacing, Method spacingNow, Method bufferNow, Method buffer, Method bias,
              Field biasNow, Object nextOnly) {
            this.size = size;
            this.spacing = spacing;
            this.spacingNow = spacingNow;
            this.bufferNow = bufferNow;
            this.buffer = buffer;
            this.bias = bias;
            this.biasNow = biasNow;
            this.nextOnly = nextOnly;
        }

        static Pager of(Class<?> kind) {
            Method size;
            try {
                size = kind.getMethod("setOverridePageSize", int.class);
            } catch (NoSuchMethodException notAPager) {
                return null;
            }
            try {
                Method buffer = null, bias = null;
                for (Method method : kind.getMethods()) {
                    Class<?>[] takes = method.getParameterTypes();
                    if (method.getName().equals("setExtraBufferSize$default") && takes.length == 6
                            && takes[0].isAssignableFrom(kind) && takes[1] == int.class && takes[2] == boolean.class
                            && takes[3] == Boolean.class && takes[4] == int.class && takes[5] == Object.class) {
                        buffer = method;
                    } else if (method.getName().equals("setBufferBias") && takes.length == 1 && takes[0].isEnum()) {
                        bias = method;
                    }
                }
                if (buffer == null || bias == null) return null;
                Class<?> biasKind = bias.getParameterTypes()[0];
                Field biasNow = null;
                for (Field field : kind.getFields()) {
                    if (field.getType() != biasKind || Modifier.isStatic(field.getModifiers())) continue;
                    if (biasNow != null) return null;
                    biasNow = field;
                }
                if (biasNow == null) return null;
                Object nextOnly = Enum.valueOf((Class) biasKind, NEXT_ONLY);
                return new Pager(size, kind.getMethod("setPageSpacing", float.class), kind.getMethod("getPageSpacing"),
                        kind.getMethod("getExtraBufferSize"), buffer, bias, biasNow, nextOnly);
            } catch (NoSuchMethodException | IllegalArgumentException gone) {
                return null;
            }
        }

        void buffer(View pager, int count) throws ReflectiveOperationException {
            buffer.invoke(null, pager, count, false, null, 6, null);
        }
    }

    private CarouselPeek() {
    }

    static boolean wanted() {
        return Boolean.TRUE.equals(SharedPref.getBooleanPref(Settings.CAROUSEL_PEEK));
    }

    static void register() {
        RoundedViewWatcher.register(MEMBER);
    }

    static void watch(Activity activity) {
        try {
            if (!RoundedViewWatcher.joined(activity, MEMBER)) {
                RoundedViewWatcher.join(activity, MEMBER);
            }
        } catch (Throwable ignored) {
        }
    }

    static void unwatch(Activity activity) {
        try {
            RoundedViewWatcher.leave(activity, MEMBER);
        } catch (Throwable ignored) {
        }
    }

    private static Pager kind(View view) {
        Class<?> kind = view.getClass();
        if (kinds.containsKey(kind)) return kinds.get(kind);
        Pager pager = Pager.of(kind);
        kinds.put(kind, pager);
        return pager;
    }

    private static ViewGroup pagerOf(View frame) {
        ViewParent parent = frame.getParent();
        for (int i = 0; i < PAGER_DEPTH && parent instanceof ViewGroup; i++, parent = parent.getParent()) {
            if (kind((View) parent) != null) return (ViewGroup) parent;
        }
        return null;
    }

    private static boolean peek(ViewGroup pager, float density) throws ReflectiveOperationException {
        Pager methods = kind(pager);
        int width = pager.getWidth();
        if (methods == null || width <= 0) return false;
        float inset = INSET * density, gap = GAP * density;
        int page = Math.round(width - inset - gap - PEEK * density);
        if (page <= 0) return false;
        float scale = page / (float) width;
        float pivot = inset / (1 - scale);
        boolean changed = false;
        Before before = pagers.get(pager);
        if (before == null) {
            before = new Before((Float) methods.spacingNow.invoke(pager), (Integer) methods.bufferNow.invoke(pager),
                    methods.biasNow.get(pager));
            pagers.put(pager, before);
        }
        if (before.width != width) {
            before.width = width;
            before.scale = scale;
            before.pivot = pivot;
            methods.size.invoke(pager, page);
            methods.bias.invoke(pager, methods.nextOnly);
            if (before.buffer < 1) widened.put(pager, Boolean.TRUE);
            methods.buffer(pager, Math.max(1, before.buffer));
            methods.spacing.invoke(pager, gap);
            changed = true;
        } else if (methods.biasNow.get(pager) != methods.nextOnly) {
            methods.bias.invoke(pager, methods.nextOnly);
        }
        known.put(pager, Boolean.TRUE);
        for (int i = 0; i < pager.getChildCount(); i++) {
            View child = pager.getChildAt(i);
            float middle = child.getHeight() / 2f;
            if (child.getScaleX() == scale && child.getPivotX() == pivot && child.getPivotY() == middle
                    && pages.containsKey(child)) continue;
            pages.put(child, Boolean.TRUE);
            child.setPivotX(pivot);
            child.setPivotY(middle);
            child.setScaleX(scale);
            child.setScaleY(scale);
            changed = true;
        }
        return changed;
    }

    public static int buffer(Object pager, int count) {
        try {
            if (count >= 1 || pager == null || !widened.containsKey(pager)) return count;
            return 1;
        } catch (Throwable ignored) {
            return count;
        }
    }

    private static boolean refreshKnown() {
        boolean changed = false;
        for (View pagerView : new ArrayList<>(known.keySet())) {
            if (pagerView == null) continue;
            try {
                Pager methods = kind(pagerView);
                if (methods == null) continue;
                ViewGroup pager = (ViewGroup) pagerView;
                if (methods.biasNow.get(pager) != methods.nextOnly) {
                    methods.bias.invoke(pager, methods.nextOnly);
                }
                Before before = pagers.get(pager);
                if (before == null || before.scale <= 0f || before.scale >= 1f) continue;
                float scale = before.scale;
                float pivot = before.pivot;
                for (int i = 0; i < pager.getChildCount(); i++) {
                    View child = pager.getChildAt(i);
                    float middle = child.getHeight() / 2f;
                    if (child.getScaleX() == scale && child.getPivotX() == pivot && child.getPivotY() == middle
                            && pages.containsKey(child)) continue;
                    pages.put(child, Boolean.TRUE);
                    child.setPivotX(pivot);
                    child.setPivotY(middle);
                    child.setScaleX(scale);
                    child.setScaleY(scale);
                    changed = true;
                }
            } catch (Throwable ignored) {
            }
        }
        return changed;
    }

    private static void putBack() {
        for (Map.Entry<View, Before> entry : new ArrayList<>(pagers.entrySet())) {
            View pager = entry.getKey();
            Pager methods = pager == null ? null : kind(pager);
            if (methods == null) continue;
            try {
                methods.size.invoke(pager, -1);
                methods.bias.invoke(pager, entry.getValue().bias);
                methods.spacing.invoke(pager, entry.getValue().spacing);
            } catch (Throwable ignored) {
            }
        }
        for (View page : new ArrayList<>(pages.keySet())) {
            if (page == null) continue;
            page.setScaleX(1);
            page.setScaleY(1);
            page.resetPivot();
        }
        pagers.clear();
        pages.clear();
        known.clear();
    }

    private static final RoundedViewWatcher.Member MEMBER = new RoundedViewWatcher.Member() {
        @Override
        public boolean preDraw(Activity activity, boolean laidOut) {
            boolean changed = false;
            try {
                if (!wanted()) {
                    if (!pagers.isEmpty() || !pages.isEmpty()) putBack();
                    return false;
                }
                if (!laidOut) return refreshKnown();
                int id = resolveId(activity, PAGE, false);
                if (id == 0) return false;
                int video = resolveId(activity, VIDEO_PAGE, true);
                float density = activity.getResources().getDisplayMetrics().density;
                changed = walk(activity.getWindow().getDecorView(), id, video, density, new int[]{StoryCorners.MAX_VIEWS});
            } catch (Throwable ignored) {
            }
            return changed;
        }

        private int resolveId(Activity activity, String name, boolean optional) {
            int cached = name.equals(PAGE) ? cachedId : cachedVideoId;
            if (cached != 0) return cached;
            int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
            if (id != 0) {
                if (name.equals(PAGE)) cachedId = id;
                else cachedVideoId = id;
            }
            return id;
        }

        private boolean walk(View view, int id, int video, float density, int[] budget) throws ReflectiveOperationException {
            if (budget[0]-- <= 0) return false;
            if (view.getId() == id || (video != 0 && view.getId() == video)) {
                ViewGroup pager = pagerOf(view);
                return pager != null && peek(pager, density);
            }
            boolean changed = false;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    changed |= walk(group.getChildAt(i), id, video, density, budget);
                }
            }
            return changed;
        }
    };
}
