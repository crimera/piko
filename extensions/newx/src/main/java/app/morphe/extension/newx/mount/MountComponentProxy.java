package app.morphe.extension.newx.mount;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;

import androidx.annotation.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.newx.settings.NewXLogger;

@SuppressWarnings("unused")
public final class MountComponentProxy {
    private static final String EXTENSION_PREFIX = "app.morphe.extension.";
    private static final String IDENTIFIER_PREFIX = "piko-mount-proxy:";

    public static final int KIND_ACTIVITY = 1;
    public static final int KIND_RECEIVER = 2;

    private static final int UNSAFE_ACTIVITY_FLAGS =
            ActivityInfo.FLAG_NO_HISTORY
                    | ActivityInfo.FLAG_FINISH_ON_TASK_LAUNCH
                    | ActivityInfo.FLAG_CLEAR_TASK_ON_LAUNCH
                    | ActivityInfo.FLAG_ALLOW_TASK_REPARENTING
                    | ActivityInfo.FLAG_STATE_NOT_NEEDED
                    | ActivityInfo.FLAG_RELINQUISH_TASK_IDENTITY
                    | ActivityInfo.FLAG_FINISH_ON_CLOSE_SYSTEM_DIALOGS
                    | ActivityInfo.FLAG_ENABLE_VR_MODE
                    | ActivityInfo.FLAG_MULTIPROCESS;

    private static final Map<String, Boolean> registered = new ConcurrentHashMap<>();

    @Nullable
    private static volatile String activityProxy;
    @Nullable
    private static volatile String receiverProxy;

    private MountComponentProxy() {
    }

    public static void route(@Nullable Intent intent, @Nullable Context context) {
        if (intent == null || context == null) return;
        try {
            ComponentName component = intent.getComponent();
            if (component == null) return;
            String className = component.getClassName();
            if (!className.startsWith(EXTENSION_PREFIX)) return;
            String packageName = context.getPackageName();
            if (!packageName.equals(component.getPackageName())) return;

            int kind = kindOf(className);
            if (kind == 0) return;

            PackageManager packageManager = context.getPackageManager();
            if (isRegistered(packageManager, component, kind)) return;

            String proxy = proxyFor(context, kind);
            if (proxy == null) {
                NewXLogger.printException(() -> "No mount proxy component available for " + className);
                return;
            }
            intent.setClassName(packageName, proxy);
            intent.setIdentifier(IDENTIFIER_PREFIX + className);
            NewXLogger.printInfo(() -> "Routing " + className + " through mount proxy " + proxy);
        } catch (Throwable throwable) {
            NewXLogger.printException(() -> "Mount proxy routing failed", throwable);
        }
    }

    public static String resolveActivityClassName(String className, @Nullable Intent intent) {
        return resolveClassName(className, intent, KIND_ACTIVITY);
    }

    public static String resolveReceiverClassName(String className, @Nullable Intent intent) {
        return resolveClassName(className, intent, KIND_RECEIVER);
    }

    public static String resolveClassName(String className, @Nullable Intent intent, int kind) {
        if (intent == null || className == null) return className;
        try {
            String identifier = intent.getIdentifier();
            if (identifier == null || !identifier.startsWith(IDENTIFIER_PREFIX)) return className;
            ComponentName component = intent.getComponent();
            if (component == null || !className.equals(component.getClassName())) return className;
            if (kind != KIND_ACTIVITY && kind != KIND_RECEIVER) return className;

            String target = identifier.substring(IDENTIFIER_PREFIX.length());
            if (!target.startsWith(EXTENSION_PREFIX)) return className;

            Context context = Utils.getContext();
            if (context == null) return className;
            if (!className.equals(proxyFor(context, kind))) return className;
            if (kindOf(target) != kind) return className;
            return target;
        } catch (Throwable throwable) {
            NewXLogger.printException(() -> "Mount proxy resolution failed", throwable);
            return className;
        }
    }

    private static int kindOf(String className) {
        try {
            Class<?> type = Class.forName(className, false, MountComponentProxy.class.getClassLoader());
            if (Activity.class.isAssignableFrom(type)) return KIND_ACTIVITY;
            if (BroadcastReceiver.class.isAssignableFrom(type)) return KIND_RECEIVER;
        } catch (Throwable ignored) {
        }
        return 0;
    }

    @SuppressWarnings("deprecation")
    private static boolean isRegistered(PackageManager packageManager, ComponentName component, int kind) {
        String className = component.getClassName();
        Boolean cached = registered.get(className);
        if (cached != null) return cached;

        boolean result;
        try {
            if (kind == KIND_ACTIVITY) {
                packageManager.getActivityInfo(component, 0);
            } else {
                packageManager.getReceiverInfo(component, 0);
            }
            result = true;
        } catch (PackageManager.NameNotFoundException notFound) {
            result = false;
        }
        registered.put(className, result);
        return result;
    }

    @Nullable
    private static String proxyFor(Context context, int kind) throws PackageManager.NameNotFoundException {
        String cached = kind == KIND_ACTIVITY ? activityProxy : receiverProxy;
        if (cached != null) return cached;
        synchronized (MountComponentProxy.class) {
            cached = kind == KIND_ACTIVITY ? activityProxy : receiverProxy;
            if (cached != null) return cached;
            String selected = selectProxy(context, kind);
            if (kind == KIND_ACTIVITY) {
                activityProxy = selected;
            } else {
                receiverProxy = selected;
            }
            return selected;
        }
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static String selectProxy(Context context, int kind) throws PackageManager.NameNotFoundException {
        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName();
        ApplicationInfo application = context.getApplicationInfo();
        int flags = PackageManager.GET_META_DATA
                | (kind == KIND_ACTIVITY ? PackageManager.GET_ACTIVITIES : PackageManager.GET_RECEIVERS);
        PackageInfo packageInfo = packageManager.getPackageInfo(packageName, flags);
        ActivityInfo[] components = kind == KIND_ACTIVITY ? packageInfo.activities : packageInfo.receivers;
        if (components == null) return null;

        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ActivityInfo info : components) {
            if (info == null || info.name == null || info.name.startsWith(EXTENSION_PREFIX)) continue;
            if (info.exported) continue;
            if (info.permission != null) continue;
            if (!Objects.equals(info.processName, application.processName)) continue;
            if (!isEnabled(packageManager, packageName, info)) continue;

            int score = 0;
            if (kind == KIND_ACTIVITY) {
                score = activityScore(context, application, info);
                if (score == Integer.MIN_VALUE) continue;
            }
            if (hasActiveAppFamily(info.metaData)) score += 8;
            if (score > bestScore) {
                bestScore = score;
                best = info.name;
            }
        }

        if (best == null) return null;
        ComponentName chosen = new ComponentName(packageName, best);
        if (kind == KIND_ACTIVITY) {
            packageManager.getActivityInfo(chosen, 0);
        } else {
            packageManager.getReceiverInfo(chosen, 0);
        }
        String selected = best;
        NewXLogger.printInfo(() -> "Selected mount proxy " + selected);
        return selected;
    }

    private static boolean isEnabled(PackageManager packageManager, String packageName, ActivityInfo info) {
        int setting = packageManager.getComponentEnabledSetting(new ComponentName(packageName, info.name));
        switch (setting) {
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return true;
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                return info.enabled;
            default:
                return false;
        }
    }

    private static int activityScore(Context context, ApplicationInfo application, ActivityInfo info) {
        if (info.launchMode != ActivityInfo.LAUNCH_MULTIPLE) return Integer.MIN_VALUE;
        if ((info.flags & UNSAFE_ACTIVITY_FLAGS) != 0) return Integer.MIN_VALUE;
        if (info.documentLaunchMode != ActivityInfo.DOCUMENT_LAUNCH_NONE) return Integer.MIN_VALUE;
        if (info.targetActivity != null) return Integer.MIN_VALUE;
        String defaultAffinity = application.taskAffinity != null ? application.taskAffinity : application.packageName;
        if (!Objects.equals(info.taskAffinity, defaultAffinity)) return Integer.MIN_VALUE;

        int score = 0;
        int orientation = info.screenOrientation;
        if (orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                || orientation == ActivityInfo.SCREEN_ORIENTATION_USER
                || orientation == ActivityInfo.SCREEN_ORIENTATION_FULL_USER) {
            score += 4;
        }

        int window = windowStyle(context, info.getThemeResource());
        if ((window & WINDOW_NO_DISPLAY) != 0 || (window & WINDOW_FLOATING) != 0) return Integer.MIN_VALUE;
        if ((window & WINDOW_TRANSLUCENT) == 0) score += 2;
        return score;
    }

    private static final int WINDOW_TRANSLUCENT = 1;
    private static final int WINDOW_FLOATING = 1 << 1;
    private static final int WINDOW_NO_DISPLAY = 1 << 2;

    private static int windowStyle(Context context, int themeResource) {
        if (themeResource == 0) return 0;
        try {
            Resources.Theme theme = context.getResources().newTheme();
            theme.applyStyle(themeResource, true);
            TypedArray values = theme.obtainStyledAttributes(new int[]{
                    android.R.attr.windowIsTranslucent,
                    android.R.attr.windowIsFloating,
                    android.R.attr.windowNoDisplay,
            });
            try {
                int style = 0;
                if (values.getBoolean(0, false)) style |= WINDOW_TRANSLUCENT;
                if (values.getBoolean(1, false)) style |= WINDOW_FLOATING;
                if (values.getBoolean(2, false)) style |= WINDOW_NO_DISPLAY;
                return style;
            } finally {
                values.recycle();
            }
        } catch (Throwable ignored) {
            return WINDOW_TRANSLUCENT;
        }
    }

    private static boolean hasActiveAppFamily(@Nullable Bundle metaData) {
        if (metaData == null) return false;
        Object families = metaData.get("appFamilies");
        return families != null && String.valueOf(families).contains("x-lite");
    }
}
