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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final Map<Class<?>, Field[]> keyPaths = new ConcurrentHashMap<>();

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
    private static final long BADGE_SETTLE_MS = 1500;
    private static final ThreadLocal<String> badgeUser = new ThreadLocal<>();
    private static final Map<Class<?>, Field[]> ownerPaths = new ConcurrentHashMap<>();
    private static int taps;
    private static long lastTapAt;
    // True while the inbox shows only the hidden chats of modeUser.
    private static boolean hiddenMode;
    private static String modeUser;
    private static String lastUser;

    /** What an account needs to redraw its inbox and its badge; kept apart so a refresh of one account never touches another. */
    private static final class Account {
        // The last job that built the inbox rows, with its original list, run again to redraw the inbox.
        WeakReference<Object> rowsJob = new WeakReference<>(null);
        Field listField;
        List<Object> original;
        // The jobs that work out the badge state, and the unread count last shown on its messages button.
        volatile Object badgeJob;
        volatile Object badgeManager;
        volatile int shownCount = -1;
    }

    private static final Map<String, Account> accounts = new ConcurrentHashMap<>();

    private static Account accountOf(String userId) {
        return accounts.computeIfAbsent(userId, id -> new Account());
    }

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
                        refresh(modeUser);
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
            refresh(modeUser);
            return;
        }
        Runnable open = () -> {
            if (lastUser == null) return;
            modeUser = lastUser;
            hiddenMode = true;
            PikoUtils.toast(str("piko_hidden_chats_opened"));
            refresh(modeUser);
        };
        // The hidden chats are as private as the inbox, so they ask for the same lock.
        if (Pref.inboxLock()) InboxLock.confirm(activity, open);
        else open.run();
    }

    /** Makes the inbox and the badge of an account build themselves again, after the mode or its hidden chats changed. */
    private static void refresh(String userId) {
        try {
            Account account = userId == null ? null : accounts.get(userId);
            if (account == null) return;
            Object job = account.rowsJob.get();
            if (job instanceof Runnable && account.listField != null) {
                account.listField.set(job, account.original);
                Handler handler = workerOf(job);
                if (handler != null) handler.post((Runnable) job);
                else new Thread((Runnable) job).start();
            }
            // The badge state is stored too, so it has to be worked out again for the stored count to be right.
            Runnable badgePass = () -> rebuildBadge(account);
            badgePass.run();
            // Instagram animates the badge for a moment after a message; a second pass applies the final state once it ends.
            new Handler(Looper.getMainLooper()).postDelayed(badgePass, BADGE_SETTLE_MS);
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats refresh failed", t);
        }
    }

    /** Runs again the two things that work out the badge state: the job of the store and the one of the message events. */
    private static void rebuildBadge(Account account) {
        try {
            Object job = account.badgeJob;
            if (job instanceof Runnable) new Thread((Runnable) job).start();
            Object manager = account.badgeManager;
            if (manager == null) return;
            // The event handler takes a time and a reason; without a time it always works the state out.
            for (Method method : manager.getClass().getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                        || types.length != 2 || types[0] != Long.class || types[1] != String.class) continue;
                method.setAccessible(true);
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        method.invoke(manager, null, "hidden_chats");
                    } catch (Throwable t) {
                        Logger.printException(() -> "HiddenChats badge event failed", t);
                    }
                });
                return;
            }
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats rebuildBadge failed", t);
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
            refresh(userId);
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
            String quoted = quotedId(threadId);
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
     * Called before the notice of a deleted message is shown; true skips it when the chat is hidden. The saved
     * messages do not say which account they belong to, so the hidden chats of every account are checked.
     */
    public static boolean shouldMuteDeletion(String threadId) {
        try {
            if (threadId == null || threadId.isEmpty() || !Pref.hiddenChats() || !Pref.hiddenChatsMute()) return false;
            String quoted = quotedId(threadId);
            for (String userId : HiddenChatsPref.users()) {
                for (String chat : HiddenChatsPref.get(userId)) {
                    if (chat.contains(quoted)) return true;
                }
            }
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats.shouldMuteDeletion failed", t);
        }
        return false;
    }

    /** The id of a chat between quotes, as it is written in a saved key; a push gives it as "<user id>;thread_id:<chat id>". */
    private static String quotedId(String threadId) {
        int marker = threadId.indexOf(THREAD_MARKER);
        return "'" + (marker < 0 ? threadId : threadId.substring(marker + THREAD_MARKER.length())) + "'";
    }

    /**
     * Called while Instagram builds the state of the messages button badge, with the chats it will count;
     * returns them without the hidden ones, so the count, its cap and the muted chats are worked out from the rest.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List filterThreads(List threads, Object session) {
        try {
            if (!Pref.hiddenChats() || threads == null || threads.isEmpty() || !(session instanceof UserSession)) return threads;
            Set<String> hidden = HiddenChatsPref.ids(((UserSession) session).getUserId());
            if (hidden.isEmpty()) return threads;
            List kept = new ArrayList(threads.size());
            for (Object thread : threads) {
                Object key = threadKeyOf(thread);
                if (key == null || !hidden.contains(HiddenChatsPref.idOf(String.valueOf(key)))) kept.add(thread);
            }
            return kept.size() == threads.size() ? threads : kept;
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats.filterThreads failed", t);
            return threads;
        }
    }

    /** Called when the job that builds the badge state runs; it is kept to run it again after a chat is hidden or shown. */
    public static void badgeJob(Object job) {
        String userId = markOwner(job);
        if (userId != null) accountOf(userId).badgeJob = job;
    }

    /** Called when a message event makes Instagram build the badge state its own way, from a list of its own. */
    public static void badgeEvent(Object manager) {
        String userId = markOwner(manager);
        if (userId != null) accountOf(userId).badgeManager = manager;
    }

    /** Notes which account the state this thread is about to build belongs to, and returns it. */
    private static String markOwner(Object owner) {
        String userId = null;
        try {
            Field[] path = ownerPaths.get(owner.getClass());
            if (path == null) {
                path = pathTo(owner, UserSession.class);
                ownerPaths.put(owner.getClass(), path);
            }
            Object value = owner;
            if (path != NO_PATH) for (Field field : path) value = value == null ? null : field.get(value);
            if (value instanceof UserSession) userId = ((UserSession) value).getUserId();
        } catch (Throwable ignored) { }
        badgeUser.set(userId);
        return userId;
    }

    /**
     * Called with every badge state before it is used, whoever built it; takes the hidden chats out of its lists and
     * works the count out again from the list that is counted ({@code counted}), keeping Instagram's cap.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void filterState(Object state, String counted) {
        try {
            String userId = badgeUser.get();
            if (!Pref.hiddenChats() || state == null || userId == null) return;
            Set<String> hidden = HiddenChatsPref.ids(userId);
            if (hidden.isEmpty()) return;
            Field countField = null;
            int removedCounted = 0;
            int countedSize = 0;
            for (Field field : state.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                if (field.getType() == int.class) {
                    countField = field;
                    continue;
                }
                if (field.getType() != List.class) continue;
                List list = (List) field.get(state);
                if (list == null || list.isEmpty()) continue;
                List kept = new ArrayList(list.size());
                for (Object entry : list) {
                    Object key = threadKeyOf(entry);
                    if (key == null || !hidden.contains(HiddenChatsPref.idOf(String.valueOf(key)))) kept.add(entry);
                }
                if (kept.size() == list.size()) continue;
                if (field.getName().equals(counted)) {
                    countedSize = list.size();
                    removedCounted = list.size() - kept.size();
                }
                field.set(state, kept);
            }
            if (countField == null || removedCounted == 0) return;
            // Instagram shows at most its cap, so a count below the size of the list was capped.
            int count = countField.getInt(state);
            int remaining = countedSize - removedCounted;
            countField.setInt(state, count < countedSize ? Math.min(remaining, count) : remaining);
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats.filterState failed", t);
        }
    }

    /**
     * Called with the state of the messages button badge, which already leaves out the hidden chats.
     * Returns true when a message of a hidden chat gave the same number, so the badge is left alone and does not animate.
     */
    public static boolean onBadge(Object controller, Object badge) {
        try {
            if (!Pref.hiddenChats() || controller == null) return false;
            UserSession session = (UserSession) fieldOfType(controller, UserSession.class);
            if (session == null) return false;
            Account account = accountOf(session.getUserId());
            // Without a state the button is painted from the stored count, so what it shows is not known.
            if (badge == null || HiddenChatsPref.ids(session.getUserId()).isEmpty()) {
                account.shownCount = -1;
                return false;
            }
            int count = -1;
            for (Field field : badge.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) continue;
                field.setAccessible(true);
                count = field.getInt(badge);
            }
            int previous = account.shownCount;
            account.shownCount = count;
            return count > 0 && count == previous;
        } catch (Throwable t) {
            Logger.printException(() -> "HiddenChats.onBadge failed", t);
            return false;
        }
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
            Account account = accountOf(userId);
            account.rowsJob = new WeakReference<>(task);
            account.listField = listField;
            account.original = list;
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
