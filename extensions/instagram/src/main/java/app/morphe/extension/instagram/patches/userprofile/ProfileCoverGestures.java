/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.userprofile;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import app.morphe.extension.shared.Logger;

/** Two fingers over the cover pan and zoom it; heard on the window so a first finger on a button can't keep the gesture. */
final class ProfileCoverGestures implements InvocationHandler {

    private static final int MODE_UNDECIDED = 0;
    private static final int MODE_DRAG = 1;
    private static final int MODE_PINCH = 2;

    private static WeakReference<ViewGroup> headerList = new WeakReference<>(null);

    private final Window.Callback target;
    private boolean active;
    private boolean needsBaseline;
    private int mode = MODE_UNDECIDED;
    private float startX;
    private float startY;
    private float startDistance;
    private float lastX;
    private float lastY;
    private float lastDistance;

    private ProfileCoverGestures(Window.Callback target) {
        this.target = target;
    }

    /** Starts listening on the activity that shows this header; harmless to call on every bind. */
    static void install(ViewGroup header) {
        headerList = new WeakReference<>(header);
        Activity activity = activityOf(header.getContext());
        if (activity == null) return;

        Window window = activity.getWindow();
        Window.Callback current = window.getCallback();
        if (current == null
                || (Proxy.isProxyClass(current.getClass()) && Proxy.getInvocationHandler(current) instanceof ProfileCoverGestures)) {
            return;
        }
        window.setCallback((Window.Callback) Proxy.newProxyInstance(
                ProfileCoverGestures.class.getClassLoader(),
                new Class<?>[]{Window.Callback.class},
                new ProfileCoverGestures(current)));
    }

    private static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("dispatchTouchEvent") && args != null && args.length == 1) {
            try {
                if (handle((MotionEvent) args[0])) return true;
            } catch (Exception e) {
                Logger.printException(() -> "Profile cover gesture failed", e);
            }
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private boolean handle(MotionEvent event) {
        int action = event.getActionMasked();
        ViewGroup list = headerList.get();
        ProfileCover.CoverBackground cover = list != null && list.isShown() ? ProfileCover.coverOf(list) : null;

        if (!active) {
            if (cover == null || action != MotionEvent.ACTION_POINTER_DOWN || event.getPointerCount() != 2) return false;
            if (!bothFingersInside(list, cover, event)) return false;

            // Whatever the first finger was doing is over; the cover gets the rest of the gesture.
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            target.dispatchTouchEvent(cancel);
            cancel.recycle();
            active = true;
            needsBaseline = true;
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                needsBaseline = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (cover != null && event.getPointerCount() >= 2) track(list, cover, event);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                active = false;
                if (cover != null) cover.save();
                break;
            default:
                break;
        }
        return true;
    }

    private static int slopOf(ViewGroup view) {
        return ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
    }

    private static boolean bothFingersInside(ViewGroup list, ProfileCover.CoverBackground cover, MotionEvent event) {
        int[] location = new int[2];
        list.getLocationInWindow(location);
        Rect zone = new Rect(location[0], location[1], location[0] + cover.zoneWidth(), location[1] + cover.zoneHeight());
        Rect visible = new Rect();
        if (!list.getLocalVisibleRect(visible)) return false;
        visible.offset(location[0], location[1]);
        if (!zone.intersect(visible)) return false;

        for (int i = 0; i < 2; i++) {
            if (!zone.contains((int) event.getX(i), (int) event.getY(i))) return false;
        }
        return true;
    }

    private void track(ViewGroup list, ProfileCover.CoverBackground cover, MotionEvent event) {
        int[] location = new int[2];
        list.getLocationInWindow(location);
        float centerX = location[0] + cover.zoneWidth() / 2f;
        float centerY = location[1] + cover.zoneHeight() / 2f;

        float x = (event.getX(0) + event.getX(1)) / 2f - centerX;
        float y = (event.getY(0) + event.getY(1)) / 2f - centerY;
        float distance = (float) Math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1));

        if (needsBaseline) {
            startDistance = distance;
            startX = x;
            startY = y;
            mode = MODE_UNDECIDED;
        } else if (lastDistance > 0) {
            // Dragging alone changes the finger distance a little, so the first clear movement decides drag or pinch.
            if (mode == MODE_UNDECIDED) {
                float dragged = (float) Math.hypot(x - startX, y - startY);
                float pinched = Math.abs(distance - startDistance);
                if (Math.max(dragged, pinched) > slopOf(list)) mode = pinched > dragged ? MODE_PINCH : MODE_DRAG;
            }
            float factor = mode == MODE_PINCH ? distance / lastDistance : 1f;
            cover.adjust(lastX, lastY, x, y, factor);
        }
        needsBaseline = false;
        lastX = x;
        lastY = y;
        lastDistance = distance;
    }
}
