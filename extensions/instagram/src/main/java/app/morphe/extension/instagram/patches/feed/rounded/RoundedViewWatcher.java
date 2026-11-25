/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.feed.rounded;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.shared.Utils;

public final class RoundedViewWatcher {
    public interface Member {
        boolean preDraw(Activity owner, boolean laidOut);
    }

    private static final WeakHashMap<Activity, Session> sessions = new WeakHashMap<>();
    private static final ArrayList<Member> members = new ArrayList<>();
    private static volatile boolean registered;

    private RoundedViewWatcher() {
    }

    public static void register(Member member) {
        if (!members.contains(member)) members.add(member);
    }

    public static void ensureRegistered() {
        if (registered) return;
        Context context = Utils.getContext();
        if (!(context instanceof Application)) return;
        registered = true;
        ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                for (Member member : new ArrayList<>(members)) {
                    join(activity, member);
                }
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                for (Member member : new ArrayList<>(members)) {
                    leave(activity, member);
                }
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }

    public static void join(Activity activity, Member member) {
        Session session;
        synchronized (sessions) {
            session = sessions.get(activity);
            if (session == null) {
                session = new Session(activity);
                sessions.put(activity, session);
            }
        }
        session.add(member);
    }

    public static void joinAll(Activity activity) {
        for (Member member : new ArrayList<>(members)) {
            join(activity, member);
        }
    }

    public static Activity ownerOf(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper && !(context instanceof Activity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        return context instanceof Activity ? (Activity) context : null;
    }

    public static void leave(Activity activity, Member member) {
        Session session;
        synchronized (sessions) {
            session = sessions.get(activity);
        }
        if (session != null && session.remove(member)) {
            synchronized (sessions) {
                if (sessions.get(activity) == session) sessions.remove(activity);
            }
        }
    }

    public static boolean joined(Activity activity, Member member) {
        synchronized (sessions) {
            Session session = sessions.get(activity);
            return session != null && session.members.containsKey(member);
        }
    }

    private static final class Session implements
            ViewTreeObserver.OnGlobalLayoutListener,
            ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener {
        private final WeakReference<Activity> activity;
        private final Map<Member, Boolean> members = new LinkedHashMap<>();
        private ViewTreeObserver observer;
        private boolean waiting;

        Session(Activity activity) {
            this.activity = new WeakReference<>(activity);
        }

        void add(Member member) {
            Activity owner = activity.get();
            if (owner == null || members.containsKey(member)) return;
            members.put(member, Boolean.TRUE);
            View decor = owner.getWindow().getDecorView();
            if (observer != null) {
                decor.invalidate();
            } else if (!waiting) {
                if (decor.isAttachedToWindow()) {
                    listen(decor);
                } else {
                    waiting = true;
                    decor.addOnAttachStateChangeListener(this);
                }
            }
        }

        boolean remove(Member member) {
            if (members.remove(member) == null || !members.isEmpty()) return false;
            ViewTreeObserver tree = observer;
            observer = null;
            if (tree != null && tree.isAlive()) {
                tree.removeOnGlobalLayoutListener(this);
                tree.removeOnPreDrawListener(this);
            }
            Activity owner = activity.get();
            if (waiting && owner != null) owner.getWindow().getDecorView().removeOnAttachStateChangeListener(this);
            waiting = false;
            return true;
        }

        private void listen(View decor) {
            ViewTreeObserver tree = decor.getViewTreeObserver();
            tree.addOnGlobalLayoutListener(this);
            tree.addOnPreDrawListener(this);
            observer = tree;
            decor.invalidate();
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            if (!waiting) return;
            waiting = false;
            view.removeOnAttachStateChangeListener(this);
            if (observer == null && !members.isEmpty()) listen(view);
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
        }

        @Override
        public void onGlobalLayout() {
            for (Map.Entry<Member, Boolean> each : members.entrySet()) each.setValue(Boolean.TRUE);
        }

        @Override
        public boolean onPreDraw() {
            Activity owner = activity.get();
            boolean changed = false;
            for (Member member : new ArrayList<>(members.keySet())) {
                Boolean laidOut = members.get(member);
                if (laidOut == null) continue;
                members.put(member, Boolean.FALSE);
                if (owner == null) {
                    remove(member);
                } else {
                    changed |= member.preDraw(owner, laidOut);
                }
            }
            return !changed;
        }
    }
}
