/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notification;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import app.morphe.extension.shared.Logger;

/**
 * Puts chats in Android's share sheet. Instagram's own share shortcuts carry categories that no
 * share target of its manifest declares, so on 452 no chat ever shows there. piko's chat shortcuts
 * get the categories Instagram does declare (read from its shortcuts XML, not hard-coded), and a
 * share through one reaches Instagram's own handler with the chat id it expects.
 */
public final class ShareTargets {
    private static volatile Set<String> categories;
    private static boolean registered;

    private ShareTargets() {
    }

    /** Called from piko's application start hook, before any activity, as a share can start the app. */
    public static void onAppCreate(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || registered) return;
        registered = true;
        try {
            Application app = (Application) context.getApplicationContext();
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityPreCreated(Activity activity, Bundle state) {
                    rewrite(activity);
                }

                @Override public void onActivityCreated(Activity activity, Bundle state) { }
                @Override public void onActivityStarted(Activity activity) { }
                @Override public void onActivityResumed(Activity activity) { }
                @Override public void onActivityPaused(Activity activity) { }
                @Override public void onActivityStopped(Activity activity) { }
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
                @Override public void onActivityDestroyed(Activity activity) { }
            });
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to watch for shares to chats", ex);
        }
    }

    /**
     * A share through a piko shortcut names it, not the chat: Instagram's handler expects the chat
     * id it uses for its own share shortcuts, so the prefix is dropped before it reads the intent.
     */
    private static void rewrite(Activity activity) {
        try {
            Intent intent = activity.getIntent();
            String id = intent == null ? null : intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID);
            if (id == null || !id.startsWith(ConversationNotifications.SHORTCUT_PREFIX)) return;
            String threadId = id.substring(ConversationNotifications.SHORTCUT_PREFIX.length());
            intent.putExtra(Intent.EXTRA_SHORTCUT_ID, threadId);
            NotificationLog.event("SHARE to chat " + threadId + " via " + activity.getClass().getSimpleName());
            // Shares rank a shortcut higher in the share sheet.
            activity.getSystemService(ShortcutManager.class).reportShortcutUsed(id);
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to direct a share to its chat", ex);
        }
    }

    /** The share-target categories Instagram's shortcuts XML declares; empty when none is found. */
    static Set<String> categories(Context context) {
        Set<String> known = categories;
        if (known != null) return known;
        Set<String> found = new LinkedHashSet<>();
        try {
            PackageManager pm = context.getPackageManager();
            Intent launcher = pm.getLaunchIntentForPackage(context.getPackageName());
            ComponentName component = launcher == null ? null : launcher.getComponent();
            ActivityInfo info = component == null ? null : pm.getActivityInfo(component, PackageManager.GET_META_DATA);
            int xml = info == null || info.metaData == null ? 0 : info.metaData.getInt("android.app.shortcuts");
            if (xml != 0) {
                try (XmlResourceParser parser = context.getResources().getXml(xml)) {
                    boolean inShareTarget = false;
                    for (int event = parser.getEventType(); event != XmlResourceParser.END_DOCUMENT; event = parser.next()) {
                        if (event == XmlResourceParser.START_TAG) {
                            if ("share-target".equals(parser.getName())) inShareTarget = true;
                            else if (inShareTarget && "category".equals(parser.getName())) {
                                String name = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name");
                                if (!TextUtils.isEmpty(name)) found.add(name);
                            }
                        } else if (event == XmlResourceParser.END_TAG && "share-target".equals(parser.getName())) {
                            inShareTarget = false;
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Failed to read Instagram's share targets", ex);
        }
        categories = Collections.unmodifiableSet(found);
        NotificationLog.event("SHARE targets " + found);
        return categories;
    }
}
