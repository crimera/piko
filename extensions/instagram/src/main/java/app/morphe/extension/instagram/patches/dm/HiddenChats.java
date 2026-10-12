/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import com.instagram.common.session.UserSession;
import com.instagram.model.direct.DirectThreadKey;

/**
 * Hides chats from the inbox, per account. A chat is hidden or shown again from its long-press menu, the hidden chats
 * are opened by tapping the messages button several times, and their notifications and unread count are left out.
 */
@SuppressWarnings("unused")
public final class HiddenChats {
    private static final int SEARCH_DEPTH = 4;
    private static final int SEARCH_LIMIT = 400;
    private static final Field[] NO_PATH = new Field[0];
    private static final Map<Class<?>, Field[]> keyPaths = new HashMap<>();

    // Taps on the messages button count as one gesture while each comes this soon after the last.
    private static final long TAP_GAP_MS = 600;
    private static final long TAP_MAX_PRESS_MS = 400;
    private static final String MESSAGES_TAB_ID = "direct_tab";
    private static final String THREAD_MARKER = "thread_id:";
    // The chat menu can only list buttons Instagram already has, so Hide and Unhide use one of these the chat lacks.
    private static final String[] HIDE_BUTTONS = {"HIDE_FROM_PRIMARY", "HIDE_FROM_MESSAGES"};
    private static final String[] UNHIDE_BUTTONS = {"SHOW_IN_PRIMARY", "SHOW_IN_MESSAGES"};
    // The incognito icon next to the account name tells that the hidden chats are open.
    private static final String ICON_TAG = "piko_hidden_chats_icon";
    private static final String TITLE_BOX_ID = "action_bar_title_notifications_container";
    private static final String TITLE_ID = "igds_action_bar_title";
    private static final Map<View, Boolean> watchedWindows = new WeakHashMap<>();
    private static boolean iconShown;

    // The button added to the menu of the chat last opened, and whether it hides the chat or shows it again.
    private static String menuButton;
    private static boolean menuButtonHides;
    private static boolean initialized;
    private static int startedActivities;
    // The unread count last shown on the messages button.
    private static int badgeCount;
    private static int taps;
    private static long lastTapAt;
    // True while the inbox shows only the hidden chats of modeUser.
    private static boolean hiddenMode;
    private static String modeUser;
    private static String lastUser;
    // The last job that built the inbox rows, with its original list, run again to redraw the inbox.
    private static WeakReference<Object> lastJob = new WeakReference<>(null);
    private static Field lastListField;
    private static List<Object> lastOriginal;

    private HiddenChats() { }

    /** Called once from the application init hook. */
    public static synchronized void init() {
        if (initialized || !Pref.hiddenChats()) return;
        initialized = true;
        try {
            Application app = (Application) Utils.getContext().getApplicationContext();
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityStarted(Activity activity) { startedActivities++; }

                @Override public void onActivityStopped(Activity activity) {
                    // A rotation stops and restarts the activity; only really leaving the app closes the hidden chats.
                    startedActivities--;
                    if (startedActivities <= 0 && !activity.isChangingConfigurations() && hiddenMode) {
                        hiddenMode = false;
                        refresh();
                    }
                }

                @Override public void onActivityResumed(Activity activity) {
                    watchTouches(activity);
                    watchHeader(activity);
                }
                @Override public void onActivityCreated(Activity a, Bundle b) { }
                @Override public void onActivityPaused(Activity a) { }
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                @Override public void onActivityDestroyed(Activity a) { }
            });
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats init failed", t);
        }
    }

    // Checked before each frame, so the icon follows the mode and survives the header being built again.
    private static void watchHeader(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        if (watchedWindows.put(decor, true) != null) return;
        int boxId = ResourceUtils.getIdentifier(ResourceType.ID, TITLE_BOX_ID);
        int titleId = ResourceUtils.getIdentifier(ResourceType.ID, TITLE_ID);
        decor.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (hiddenMode || iconShown) syncIcon(decor, boxId, titleId);
            return true;
        });
    }

    private static void syncIcon(View decor, int boxId, int titleId) {
        try {
            View box = decor.findViewById(boxId);
            if (!(box instanceof LinearLayout)) return;
            LinearLayout row = (LinearLayout) box;
            View icon = row.findViewWithTag(ICON_TAG);
            if (!hiddenMode) {
                if (icon != null) row.removeView(icon);
                iconShown = false;
                return;
            }
            View title = row.findViewById(titleId);
            if (icon != null || !(title instanceof TextView)) return;

            TextView name = (TextView) title;
            ImageView view = new ImageView(row.getContext());
            view.setTag(ICON_TAG);
            view.setImageDrawable(new IncognitoIcon(name.getCurrentTextColor()));
            int size = Math.round(name.getTextSize() * 1.2f);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.gravity = Gravity.CENTER_VERTICAL;
            params.setMarginStart(Math.round(name.getTextSize() * 0.3f));
            row.addView(view, row.indexOfChild(title) + 1, params);
            iconShown = true;
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats icon failed", t);
        }
    }

    // Taps are read from the window's touch events, so the messages button keeps working as it is.
    private static void watchTouches(Activity activity) {
        Window window = activity.getWindow();
        Window.Callback current = window.getCallback();
        if (current == null || (Proxy.isProxyClass(current.getClass())
                && Proxy.getInvocationHandler(current) instanceof TouchWatcher)) return;
        window.setCallback((Window.Callback) Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),
                new Class<?>[]{Window.Callback.class}, new TouchWatcher(activity, current)));
    }

    private static final class TouchWatcher implements InvocationHandler {
        private final Activity activity;
        private final Window.Callback target;

        TouchWatcher(Activity activity, Window.Callback target) {
            this.activity = activity;
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            Object result;
            try {
                result = method.invoke(target, arguments);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            if (arguments != null && arguments.length == 1 && arguments[0] instanceof MotionEvent
                    && method.getName().equals("dispatchTouchEvent")) {
                try {
                    onTouch(activity, (MotionEvent) arguments[0]);
                } catch (Throwable t) {
                    Logger.printException(() -> "HiddenChats touch failed", t);
                }
            }
            return result;
        }
    }

    private static void onTouch(Activity activity, MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP || !Pref.hiddenChats()) return;
        if (event.getEventTime() - event.getDownTime() > TAP_MAX_PRESS_MS) return;

        View tab = activity.getWindow().getDecorView().findViewById(ResourceUtils.getIdentifier(ResourceType.ID, MESSAGES_TAB_ID));
        Rect bounds = new Rect();
        if (tab == null || !tab.isShown() || !tab.getGlobalVisibleRect(bounds)
                || !bounds.contains((int) event.getRawX(), (int) event.getRawY())) {
            taps = 0;
            return;
        }

        long now = event.getEventTime();
        taps = now - lastTapAt <= TAP_GAP_MS ? taps + 1 : 1;
        lastTapAt = now;
        if (taps >= Pref.hiddenChatsTaps()) {
            taps = 0;
            toggleHiddenMode(activity);
        }
    }

    private static void toggleHiddenMode(Activity activity) {
        if (hiddenMode) {
            hiddenMode = false;
            PikoUtils.toast(str("piko_hidden_chats_closed"));
            refresh();
            return;
        }
        Runnable open = () -> {
            if (lastUser == null) return;
            modeUser = lastUser;
            hiddenMode = true;
            PikoUtils.toast(str("piko_hidden_chats_opened"));
            refresh();
        };
        // The hidden chats are as private as the inbox, so they ask for the same lock.
        if (Pref.inboxLock()) InboxLock.confirm(activity, open);
        else open.run();
    }

    /** Makes the inbox build its rows again, after the mode or the hidden chats changed. */
    private static void refresh() {
        try {
            Object job = lastJob.get();
            if (!(job instanceof Runnable) || lastListField == null) return;
            lastListField.set(job, lastOriginal);
            Handler handler = workerOf(job);
            if (handler != null) handler.post((Runnable) job);
            else new Thread((Runnable) job).start();
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats refresh failed", t);
        }
    }

    // The rows are built off the main thread, by a handler held by one of the objects the job points to.
    private static Handler workerOf(Object job) throws Exception {
        for (Field field : job.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
            field.setAccessible(true);
            Object owner = field.get(job);
            if (owner == null || isFramework(owner.getClass())) continue;
            for (Field inner : owner.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(inner.getModifiers()) || inner.getType() != Handler.class) continue;
                inner.setAccessible(true);
                Handler handler = (Handler) inner.get(owner);
                if (handler != null && handler.getLooper() != Looper.getMainLooper()) return handler;
            }
        }
        return null;
    }

    /**
     * Called with the buttons of a chat's menu when it opens; returns them with the button that hides the chat added,
     * or the one that shows it while the hidden chats are open. A copy is returned because Instagram's list can be read-only.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List menuOpening(List buttons, Object menu) {
        menuButton = null;
        try {
            if (!Pref.hiddenChats() || buttons == null || buttons.isEmpty() || menu == null) return buttons;
            UserSession session = (UserSession) fieldOfType(menu, UserSession.class);
            if (session == null) return buttons;
            boolean unhide = hiddenMode && session.getUserId().equals(modeUser);
            Class<? extends Enum> type = (Class<? extends Enum>) buttons.get(0).getClass();

            // Only a button the menu does not have is used, so nothing Instagram shows is taken over.
            Set<String> present = new HashSet<>();
            for (Object button : buttons) present.add(String.valueOf(button));
            for (String candidate : unhide ? UNHIDE_BUTTONS : HIDE_BUTTONS) {
                if (present.contains(candidate)) continue;
                List extended = new ArrayList(buttons);
                extended.add(Enum.valueOf(type, candidate));
                menuButton = candidate;
                menuButtonHides = !unhide;
                return extended;
            }
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats.menuOpening failed", t);
        }
        return buttons;
    }

    /** Called when a button of a chat's menu is pressed; true when it was the one added here, so nothing else handles it. */
    public static boolean onMenuButton(Object session, Object button, Object threadKey) {
        try {
            if (menuButton == null || !menuButton.equals(String.valueOf(button))) return false;
            if (!(session instanceof UserSession) || threadKey == null) return false;
            String userId = ((UserSession) session).getUserId();
            if (menuButtonHides) HiddenChatsPref.add(userId, String.valueOf(threadKey));
            else HiddenChatsPref.remove(userId, String.valueOf(threadKey));
            PikoUtils.toast(str(menuButtonHides ? "piko_hidden_chats_hidden" : "piko_hidden_chats_unhidden"));
            refresh();
            return true;
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats.onMenuButton failed", t);
            return false;
        }
    }

    /** Called before a message notification is built; true skips it when the chat is hidden by the account that got it. */
    public static boolean shouldMuteNotification(Object session, Runnable done, String threadId) {
        try {
            if (threadId == null || !Pref.hiddenChats() || !Pref.hiddenChatsMute() || !(session instanceof UserSession)) {
                return false;
            }
            // The id arrives as "<user id>;thread_id:<chat id>".
            int marker = threadId.indexOf(THREAD_MARKER);
            String quoted = "'" + (marker < 0 ? threadId : threadId.substring(marker + THREAD_MARKER.length())) + "'";
            for (String chat : HiddenChatsPref.get(((UserSession) session).getUserId())) {
                if (!chat.contains(quoted)) continue;
                // The caller expects its callback once the notification is handled.
                if (done != null) done.run();
                return true;
            }
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats.shouldMuteNotification failed", t);
        }
        return false;
    }

    /**
     * Called with the state of the messages button badge; takes the hidden chats out of its unread count.
     * {@code unreadList} is the name of the state's list of unread chats, resolved by the patch.
     * Returns true when the update only changed because of hidden chats, so the badge is left alone and does not animate.
     */
    public static boolean onBadge(Object controller, Object badge, String unreadList) {
        try {
            if (!Pref.hiddenChats() || controller == null || badge == null) return false;
            Set<String> hidden = hiddenOf(controller);
            if (hidden.isEmpty()) return false;

            // The badge object holds the count and a state object with lists of the unread chats.
            Object state = null;
            Field countField = null;
            for (Field field : badge.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                if (field.getType() == int.class) countField = field;
                else if (!field.getType().isPrimitive() && !isFramework(field.getType())) state = field.get(badge);
            }
            if (state == null || countField == null) return false;
            Field stateCount = countFieldOf(state);
            if (stateCount == null) return false;

            int count = stateCount.getInt(state);
            int removed = removeHidden(state, stateCount, hidden, unreadList);
            if (removed == 0) {
                badgeCount = count;
                return false;
            }

            int shown = stateCount.getInt(state);
            int previous = badgeCount;
            countField.setInt(badge, Math.max(0, countField.getInt(badge) - removed));
            badgeCount = shown;
            // Only a hidden chat changed: the number on screen stays, so Instagram has nothing to redraw.
            return shown > 0 && shown == previous;
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats.onBadge failed", t);
            return false;
        }
    }

    /**
     * Called when the badge is painted without a new state (first paint, or after the screen is rebuilt):
     * Instagram then reads the state from its store, so the hidden chats are taken out of that one.
     */
    public static void onStoredBadge(Object controller, Object state, String unreadList) {
        try {
            if (!Pref.hiddenChats() || controller == null || state == null) return;
            Set<String> hidden = hiddenOf(controller);
            if (hidden.isEmpty()) return;
            Field stateCount = countFieldOf(state);
            if (stateCount == null) return;
            removeHidden(state, stateCount, hidden, unreadList);
            badgeCount = stateCount.getInt(state);
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats.onStoredBadge failed", t);
        }
    }

    private static Set<String> hiddenOf(Object controller) throws Exception {
        UserSession session = (UserSession) fieldOfType(controller, UserSession.class);
        return session == null ? Collections.emptySet() : HiddenChatsPref.ids(session.getUserId());
    }

    private static Field countFieldOf(Object state) {
        Field found = null;
        for (Field field : state.getClass().getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                field.setAccessible(true);
                found = field;
            }
        }
        return found;
    }

    /**
     * Takes the hidden chats out of every chat list of the state and lowers its count by those that were unread.
     * Returns how many unread chats were removed.
     */
    @SuppressWarnings("unchecked")
    private static int removeHidden(Object state, Field stateCount, Set<String> hidden, String unreadList) throws Exception {
        int removedFromUnread = 0;
        for (Field field : state.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != List.class) continue;
            field.setAccessible(true);
            List<Object> list = (List<Object>) field.get(state);
            if (list == null || list.isEmpty()) continue;
            List<Object> kept = new ArrayList<>();
            for (Object entry : list) {
                Object key = threadKeyOf(entry);
                if (key == null || !hidden.contains(HiddenChatsPref.idOf(String.valueOf(key)))) kept.add(entry);
            }
            int removed = list.size() - kept.size();
            if (removed == 0) continue;
            field.set(state, kept);
            if (field.getName().equals(unreadList)) removedFromUnread = removed;
        }
        if (removedFromUnread > 0) {
            stateCount.setInt(state, Math.max(0, stateCount.getInt(state) - removedFromUnread));
        }
        return removedFromUnread;
    }

    private static Object fieldOfType(Object owner, Class<?> type) throws Exception {
        for (Field field : owner.getClass().getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == type) {
                field.setAccessible(true);
                return field.get(owner);
            }
        }
        return null;
    }

    /**
     * Called when the inbox starts building the rows of its chat list; the list is the last one before the screen, so
     * the hidden chats are taken out of it here, or everything but them while the hidden chats are open.
     */
    @SuppressWarnings("unchecked")
    public static void filterRows(Object task) {
        try {
            if (!Pref.hiddenChats() || task == null) return;
            Field listField = null;
            for (Field field : task.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                if (field.get(task) instanceof List) listField = field;
            }
            Field[] path = pathTo(task, UserSession.class);
            if (listField == null || path == NO_PATH) return;
            Object session = task;
            for (Field field : path) session = field.get(session);
            if (!(session instanceof UserSession)) return;

            String userId = ((UserSession) session).getUserId();
            List<Object> list = (List<Object>) listField.get(task);
            if (list == null || list.isEmpty()) return;
            lastUser = userId;
            lastJob = new WeakReference<>(task);
            lastListField = listField;
            lastOriginal = list;
            if (hiddenMode && !userId.equals(modeUser)) hiddenMode = false;
            boolean onlyHidden = hiddenMode;

            Set<String> hidden = HiddenChatsPref.ids(userId);
            if (hidden.isEmpty() && !onlyHidden) return;
            List<Object> kept = new ArrayList<>();
            for (Object thread : list) {
                Object key = threadKeyOf(thread);
                boolean drop = key != null && hidden.contains(HiddenChatsPref.idOf(String.valueOf(key))) != onlyHidden;
                if (!drop) kept.add(thread);
            }
            if (kept.size() == list.size()) return;
            // The job gets a copy: the original list is shared and is read again when the mode changes.
            listField.set(task, kept);
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats.filterRows failed", t);
        }
    }

    private static Object threadKeyOf(Object item) {
        if (item == null) return null;
        try {
            return lookUpThreadKey(item);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object lookUpThreadKey(Object item) throws Exception {
        Field[] path = keyPaths.get(item.getClass());
        if (path == null) {
            path = pathTo(item, DirectThreadKey.class);
            keyPaths.put(item.getClass(), path);
        }
        if (path == NO_PATH) return null;
        Object value = item;
        for (Field field : path) {
            value = field.get(value);
            if (value == null) return null;
        }
        return value instanceof DirectThreadKey ? value : null;
    }

    /** The chain of fields that leads from the root to an instance of the type, or NO_PATH. */
    private static Field[] pathTo(Object root, Class<?> type) throws Exception {
        Map<Object, Boolean> seen = new IdentityHashMap<>();
        ArrayDeque<Object> queue = new ArrayDeque<>();
        ArrayDeque<List<Field>> paths = new ArrayDeque<>();
        queue.add(root);
        paths.add(new ArrayList<>());
        seen.put(root, true);
        while (!queue.isEmpty() && seen.size() < SEARCH_LIMIT) {
            Object current = queue.poll();
            List<Field> path = paths.poll();
            if (path.size() >= SEARCH_DEPTH) continue;
            for (Field field : fieldsOf(current.getClass())) {
                Object value = field.get(current);
                if (value == null || seen.containsKey(value)) continue;
                List<Field> next = new ArrayList<>(path);
                next.add(field);
                if (type.isInstance(value)) return next.toArray(new Field[0]);
                if (isFramework(value.getClass())) continue;
                seen.put(value, true);
                queue.add(value);
                paths.add(next);
            }
        }
        return NO_PATH;
    }

    private static List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && !isFramework(c); c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                if (Modifier.isStatic(field.getModifiers()) || fieldType.isPrimitive()) continue;
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
