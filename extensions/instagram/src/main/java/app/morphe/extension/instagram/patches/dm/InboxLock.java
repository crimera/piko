/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.Notification;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.Dim;

/** Hides the inbox and chats behind a cover until the device's biometric or screen lock confirms it is you. */
public final class InboxLock {
    private static final String LIST_ID = "inbox_refreshable_thread_list_recyclerview";
    private static final String LIST_CONTAINER_ID = "list_container";
    // A chat opened from a notification skips the inbox, so its own screen needs the same cover.
    private static final String THREAD_ID = "thread_view_root";
    private static final String THREAD_CONTAINER_ID = "thread_fragment_container";
    private static final String COVER_TAG = "piko_inbox_lock_cover";
    private static final String MESSAGE_CHANNEL = "ig_direct";

    private static boolean initialized;
    private static boolean unlocked;
    private static boolean prompting;
    private static boolean autoPrompted;
    private static Runnable cancelAction;
    private static final Set<Activity> secured = Collections.newSetFromMap(new WeakHashMap<>());
    private static int startedActivities;
    private static WeakReference<Activity> watched = new WeakReference<>(null);
    private static ViewTreeObserver.OnPreDrawListener drawListener;
    private static final List<WeakReference<View>> covers = new ArrayList<>();
    private static WeakReference<View> bannerWindow = new WeakReference<>(null);

    private InboxLock() {
    }

    /** Called once from the application init hook. */
    public static synchronized void init() {
        if (initialized || !Pref.inboxLock()) return;
        initialized = true;
        try {
            Application app = (Application) Utils.getContext().getApplicationContext();
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityStarted(Activity activity) { startedActivities++; }

                @Override public void onActivityStopped(Activity activity) {
                    // A rotation stops and restarts the activity; only really leaving the app locks again.
                    startedActivities--;
                    if (startedActivities <= 0 && !activity.isChangingConfigurations()) unlocked = false;
                }

                @Override public void onActivityResumed(Activity activity) { watch(activity); }
                @Override public void onActivityPaused(Activity activity) { unwatch(activity); }
                @Override public void onActivityCreated(Activity a, Bundle b) { }
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                @Override public void onActivityDestroyed(Activity a) { }
            });
        } catch (Throwable t) {
            Logger.printException(() -> "InboxLock init failed", t);
        }
    }

    // The inbox is a tab of an already resumed activity; checking before each draw covers it without a visible frame.
    private static void watch(Activity activity) {
        unwatch(watched.get());
        watched = new WeakReference<>(activity);
        View decor = activity.getWindow().getDecorView();
        drawListener = () -> {
            check(activity);
            return true;
        };
        decor.getViewTreeObserver().addOnPreDrawListener(drawListener);
        check(activity);
    }

    private static void unwatch(Activity activity) {
        if (activity == null || drawListener == null || activity != watched.get()) return;
        View decor = activity.getWindow().getDecorView();
        decor.getViewTreeObserver().removeOnPreDrawListener(drawListener);
        drawListener = null;
    }

    private static void check(Activity activity) {
        try {
            View decor = activity.getWindow().getDecorView();
            if (unlocked || !Pref.inboxLock()) {
                removeCovers();
                hideFromRecents(activity, unlocked && (isVisible(decor, LIST_ID) || isVisible(decor, THREAD_ID)));
                return;
            }
            hideFromRecents(activity, false);
            cover(activity, decor, LIST_ID, LIST_CONTAINER_ID);
            cover(activity, decor, THREAD_ID, THREAD_CONTAINER_ID);

            // Ask once each time a covered screen shows up; cancelling leaves the Unlock button instead of asking in a loop.
            if (!anyCoverShown()) {
                autoPrompted = false;
            } else if (!autoPrompted) {
                autoPrompted = true;
                authenticate(activity, null);
            }
        } catch (Throwable t) {
            Logger.printException(() -> "InboxLock check failed", t);
        }
    }

    // Tab pages stay attached and VISIBLE while swiped out of view, so isShown() alone can't tell.
    private static boolean onScreen(View view) {
        Rect visible = new Rect();
        return view.isShown() && view.getGlobalVisibleRect(visible) && visible.width() * 2 > view.getWidth();
    }

    // The recents snapshot is taken after the app is left, too late for a cover, so an open inbox or chat is kept out of it.
    private static void hideFromRecents(Activity activity, boolean hide) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.setRecentsScreenshotEnabled(!hide);
            return;
        }
        // Android 12 and older can't turn the snapshot off, so the window is marked secure instead.
        Window window = activity.getWindow();
        if (hide) {
            if (secured.contains(activity) || (window.getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0) return;
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            secured.add(activity);
        } else if (secured.remove(activity)) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private static boolean isVisible(View decor, String id) {
        View view = decor.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, id));
        return view != null && partlyVisible(view);
    }

    // Any visible pixel counts, so a screen swiped in is covered before its first half shows.
    private static boolean partlyVisible(View view) {
        return view.isShown() && view.getGlobalVisibleRect(new Rect());
    }

    private static boolean anyCoverShown() {
        for (WeakReference<View> reference : covers) {
            View view = reference.get();
            if (view != null && onScreen(view)) return true;
        }
        return false;
    }

    /** Puts a cover over the container of the screen found by anchorId; true if one was added now. */
    private static boolean cover(Activity activity, View decor, String anchorId, String containerId) {
        View anchor = decor.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, anchorId));
        if (anchor == null || !partlyVisible(anchor)) return false;
        View container = decor.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, containerId));
        if (!(container instanceof FrameLayout)) return false;
        FrameLayout host = (FrameLayout) container;
        if (host.findViewWithTag(COVER_TAG) != null) return false;

        View created = buildCover(activity);
        created.setTag(COVER_TAG);
        covers.add(new WeakReference<>(created));
        host.addView(created, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Laying it out now makes this very frame draw the cover instead of waiting for the next layout pass.
        created.measure(View.MeasureSpec.makeMeasureSpec(host.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(host.getHeight(), View.MeasureSpec.EXACTLY));
        created.layout(0, 0, host.getWidth(), host.getHeight());
        // The composer keeps focus under the cover, so its keyboard would stay on screen.
        created.requestFocus();
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(decor.getWindowToken(), 0);
        return true;
    }

    private static View buildCover(Activity activity) {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(UI.getBackgroundColor());
        root.setClickable(true);
        root.setFocusableInTouchMode(true);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        ImageView lock = new ImageView(activity);
        UI.setThemedIcon(lock, UI.DRAWABLE_LOCK_ICON);
        column.addView(lock, new LinearLayout.LayoutParams(Dim.dp48 * 2, Dim.dp48 * 2));

        TextView unlock = new TextView(activity);
        unlock.setText(str("piko_inbox_lock_unlock"));
        unlock.setTextColor(UI.getThemedColour("igds_color_primary_text"));
        unlock.setTextSize(16);
        unlock.setGravity(Gravity.CENTER);
        GradientDrawable highlight = new GradientDrawable();
        highlight.setColor(InstagramPreferenceStyle.pressedBackgroundColor());
        highlight.setCornerRadius(Dim.dp48);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed}, highlight);
        background.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        unlock.setBackground(background);
        unlock.setPadding(Dim.dp24, Dim.dp16, Dim.dp24, Dim.dp16);
        unlock.setOnClickListener(v -> authenticate(activity, null));
        column.addView(unlock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private static void removeCovers() {
        for (WeakReference<View> reference : covers) {
            View view = reference.get();
            if (view != null && view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        }
        covers.clear();
        autoPrompted = false;
    }

    /** True while the lock is up, so turning it off in the settings has to be confirmed first. */
    public static boolean mustConfirmToDisable() {
        return initialized && !unlocked;
    }

    /** Asks for the device lock and runs afterUnlock once it is confirmed. */
    public static void confirm(Activity activity, Runnable afterUnlock) {
        authenticate(activity, afterUnlock);
    }

    /** Runs the action now, or after the device lock confirms it while the lock is up; onCancel runs if it isn't confirmed. */
    public static void confirmIfLocked(Activity activity, Runnable action, Runnable onCancel) {
        if (!mustConfirmToDisable()) {
            action.run();
            return;
        }
        cancelAction = onCancel;
        authenticate(activity, action);
    }

    private static void authenticate(Activity activity, Runnable afterUnlock) {
        if (prompting) return;
        KeyguardManager keyguard = (KeyguardManager) activity.getSystemService(Activity.KEYGUARD_SERVICE);
        // Without a screen lock there is nothing to check against, and locking would only trap the user.
        if (Build.VERSION.SDK_INT < 29 || keyguard == null || !keyguard.isDeviceSecure()) {
            PikoUtils.toast(str("piko_inbox_lock_no_lock"));
            unlock();
            if (afterUnlock != null) afterUnlock.run();
            return;
        }

        BiometricPrompt.Builder builder = new BiometricPrompt.Builder(activity)
                .setTitle(str("piko_inbox_lock_prompt"));
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            builder.setDeviceCredentialAllowed(true);
        }

        prompting = true;
        builder.build().authenticate(new CancellationSignal(), activity.getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        prompting = false;
                        cancelAction = null;
                        unlock();
                        if (afterUnlock != null) afterUnlock.run();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        prompting = false;
                        Runnable cancelled = cancelAction;
                        cancelAction = null;
                        if (cancelled != null) cancelled.run();
                    }
                });
    }

    /**
     * Called with every notification before it is shown. While locked, a message notification is swapped for a
     * generic one so its text and sender can't be read from the shade or the lock screen.
     */
    public static Notification hideNotification(Notification original) {
        try {
            if (!initialized || original == null || unlocked || !Pref.inboxLockNotifications()) return original;
            if (!Notification.CATEGORY_MESSAGE.equals(original.category) && !MESSAGE_CHANNEL.equals(original.getChannelId())) {
                return original;
            }

            Context context = Utils.getContext();
            String channel = original.getChannelId();
            Notification.Builder builder = channel != null
                    ? new Notification.Builder(context, channel)
                    : new Notification.Builder(context);
            builder.setSmallIcon(original.getSmallIcon())
                    .setColor(original.color)
                    .setContentTitle(context.getApplicationInfo().loadLabel(context.getPackageManager()))
                    .setContentText(str("piko_inbox_lock_notification"))
                    .setContentIntent(original.contentIntent)
                    .setDeleteIntent(original.deleteIntent)
                    .setWhen(original.when)
                    .setCategory(original.category)
                    .setGroup(original.getGroup())
                    .setSortKey(original.getSortKey())
                    .setGroupSummary((original.flags & Notification.FLAG_GROUP_SUMMARY) != 0)
                    .setAutoCancel(true)
                    .setVisibility(Notification.VISIBILITY_PRIVATE);
            return builder.build();
        } catch (Throwable t) {
            Logger.printException(() -> "InboxLock notification failed", t);
            return original;
        }
    }

    /** Called with the in-app message banner's window view before it is added; it is created once and then reused. */
    public static void setBanner(View banner) {
        bannerWindow = new WeakReference<>(banner);
        updateBanner();
    }

    /** Called before each in-app banner is shown: invisible while locked, normal otherwise. */
    public static void updateBanner() {
        View banner = bannerWindow.get();
        if (banner == null) return;
        boolean hide = initialized && !unlocked && Pref.inboxLockNotifications();
        banner.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);
    }

    private static void unlock() {
        unlocked = true;
        removeCovers();
    }
}
