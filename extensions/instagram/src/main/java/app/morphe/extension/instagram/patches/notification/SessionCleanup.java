/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.Build;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.instagram.common.session.UserSession;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Forgets an account when it logs out: its stored chat state, piko's chat shortcuts and the cached
 * images and avatars. Without this they outlive the account (names and photos stay in the share
 * sheet and conversation widgets).
 *
 * <p>No bytecode hook: Instagram ends a session by calling {@code onUserSessionWillEnd(isLoggedOut)}
 * on every object the session holds that implements its session-end interface (named at patch
 * time). A proxy of that interface is put into each session, the way Instagram scopes its own
 * objects ({@code UserSession.getScopedLazy}).
 */
final class SessionCleanup {
    // Sessions already given a listener; weak, so an ended session is not kept alive.
    private static final Map<UserSession, Boolean> registered = Collections.synchronizedMap(new WeakHashMap<>());

    private SessionCleanup() {
    }

    /** Instagram's session-end interface, e.g. X.0Ick on 452, named at patch time. */
    private static String sessionEndInterface() {
        return "sessionEndInterface";
    }

    static void register(UserSession session) {
        if (session == null || registered.put(session, Boolean.TRUE) != null) return;
        try {
            String viewerId = session.getUserId();
            Class<?> listenerType = Class.forName(sessionEndInterface());
            Class<?> factoryType = Class.forName("kotlin.jvm.functions.Function0");
            ClassLoader loader = listenerType.getClassLoader();

            Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{listenerType}, handler((method, args) -> {
                if ("onUserSessionWillEnd".equals(method.getName()) && args != null && args.length == 1
                        && Boolean.TRUE.equals(args[0])) {
                    onLoggedOut(viewerId);
                }
                return null;
            }));
            Object factory = Proxy.newProxyInstance(loader, new Class<?>[]{factoryType}, handler((method, args) -> listener));

            Method scoped = session.getClass().getMethod("getScopedLazy", Class.class, factoryType);
            Object lazy = scoped.invoke(session, SessionCleanup.class, factory);
            // The session only tells objects it has created.
            lazy.getClass().getMethod("getValue").invoke(lazy);
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to watch for a logout", ex);
            NotificationLog.event("FAILED to watch for a logout: " + ex);
        }
    }

    private interface Call {
        Object run(Method method, Object[] args) throws Throwable;
    }

    /** A handler that also answers Object's methods, as every proxy must. */
    private static InvocationHandler handler(Call call) {
        return (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "equals": return proxy == args[0];
                    case "hashCode": return System.identityHashCode(proxy);
                    default: return "piko session cleanup";
                }
            }
            return call.run(method, args);
        };
    }

    private static void onLoggedOut(String viewerId) {
        NotificationLog.event("LOGOUT of " + viewerId + ": forgetting its chats");
        ConversationNotifications.forgetViewer(viewerId);
        Context context = Utils.getContext();
        removeShortcuts(context);
        deleteDirectory(new File(context.getCacheDir(), ChatShortcuts.AVATAR_DIRECTORY));
        Avatars.forget();
        GroupPhotos.forget();
        ChatMessages.forgetPeople();
        deleteDirectory(NotificationImages.dir(context));
    }

    /** All of piko's chat shortcuts: they are not kept per account, and the next posts publish them again. */
    private static void removeShortcuts(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        try {
            ShortcutManager manager = context.getSystemService(ShortcutManager.class);
            List<String> ids = new ArrayList<>();
            for (ShortcutInfo shortcut : manager.getShortcuts(ShortcutManager.FLAG_MATCH_DYNAMIC
                    | ShortcutManager.FLAG_MATCH_CACHED)) {
                if (shortcut.getId().startsWith(ConversationNotifications.SHORTCUT_PREFIX)) ids.add(shortcut.getId());
            }
            if (ids.isEmpty()) return;
            // Long-lived shortcuts stay with the system (conversations, widgets) unless removed this way.
            manager.removeLongLivedShortcuts(ids);
            NotificationLog.event("LOGOUT removed " + ids.size() + " chat shortcut(s)");
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to remove chat shortcuts", ex);
        }
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File file : files) file.delete();
    }
}
