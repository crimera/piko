package app.morphe.extension.newx.mount;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import app.morphe.extension.shared.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
public final class MountComponentProxyTest {
    private static final String MARKER = "piko-mount-proxy:";
    private static final String ACTIVITY_TARGET = TestMountActivity.class.getName();
    private static final String RECEIVER_TARGET = "app.morphe.extension.newx.misc.NewXCrashCopyReceiver";

    private static final String PROXY_ACTIVITY = "com.twitter.android.ProxyActivity";
    private static final String EXPORTED_ACTIVITY = "com.twitter.android.ExportedActivity";
    private static final String OTHER_PROCESS_ACTIVITY = "com.twitter.android.RemoteProcessActivity";
    private static final String SINGLE_TASK_ACTIVITY = "com.twitter.android.SingleTaskActivity";
    private static final String PROXY_RECEIVER = "com.twitter.android.ProxyReceiver";
    private static final String EXPORTED_RECEIVER = "com.twitter.android.ExportedReceiver";

    private Context context;
    private String packageName;

    @Before
    public void setUp() throws Exception {
        resetProxyCache();
        context = RuntimeEnvironment.getApplication();
        packageName = context.getPackageName();
        Utils.setContext(context);

        ApplicationInfo application = context.getApplicationInfo();
        String affinity = application.taskAffinity != null ? application.taskAffinity : packageName;

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.applicationInfo = application;
        packageInfo.activities = new ActivityInfo[]{
                activity(EXPORTED_ACTIVITY, application, affinity, true, application.processName, ActivityInfo.LAUNCH_MULTIPLE),
                activity(OTHER_PROCESS_ACTIVITY, application, affinity, false, ":remote", ActivityInfo.LAUNCH_MULTIPLE),
                activity(SINGLE_TASK_ACTIVITY, application, affinity, false, application.processName, ActivityInfo.LAUNCH_SINGLE_TASK),
                activity(PROXY_ACTIVITY, application, affinity, false, application.processName, ActivityInfo.LAUNCH_MULTIPLE),
        };
        packageInfo.receivers = new ActivityInfo[]{
                receiver(EXPORTED_RECEIVER, application, true),
                receiver(PROXY_RECEIVER, application, false),
        };
        shadowOf(context.getPackageManager()).installPackage(packageInfo);
    }

    @After
    public void tearDown() throws Exception {
        Utils.reset();
        resetProxyCache();
    }

    @Test
    public void swapsOnlyForTheSelectedProxyOfTheMatchingKind() {
        assertEquals(ACTIVITY_TARGET, resolve(PROXY_ACTIVITY, marked(PROXY_ACTIVITY, ACTIVITY_TARGET),
                MountComponentProxy.KIND_ACTIVITY));
        assertEquals(RECEIVER_TARGET, resolve(PROXY_RECEIVER, marked(PROXY_RECEIVER, RECEIVER_TARGET),
                MountComponentProxy.KIND_RECEIVER));

        for (String decoy : new String[]{EXPORTED_ACTIVITY, OTHER_PROCESS_ACTIVITY, SINGLE_TASK_ACTIVITY}) {
            assertEquals(decoy, resolve(decoy, marked(decoy, ACTIVITY_TARGET), MountComponentProxy.KIND_ACTIVITY));
        }
        assertEquals(EXPORTED_RECEIVER, resolve(EXPORTED_RECEIVER, marked(EXPORTED_RECEIVER, RECEIVER_TARGET),
                MountComponentProxy.KIND_RECEIVER));

        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, marked(EXPORTED_ACTIVITY, ACTIVITY_TARGET),
                MountComponentProxy.KIND_ACTIVITY));

        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, marked(PROXY_ACTIVITY, RECEIVER_TARGET),
                MountComponentProxy.KIND_RECEIVER));
        assertEquals(PROXY_RECEIVER, resolve(PROXY_RECEIVER, marked(PROXY_RECEIVER, ACTIVITY_TARGET),
                MountComponentProxy.KIND_ACTIVITY));
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, marked(PROXY_ACTIVITY, ACTIVITY_TARGET), 0));
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, marked(PROXY_ACTIVITY, ACTIVITY_TARGET), 3));

        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, marked(PROXY_ACTIVITY, RECEIVER_TARGET),
                MountComponentProxy.KIND_ACTIVITY));
        assertEquals(PROXY_RECEIVER, resolve(PROXY_RECEIVER, marked(PROXY_RECEIVER, ACTIVITY_TARGET),
                MountComponentProxy.KIND_RECEIVER));

        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, marked(PROXY_ACTIVITY, "android.app.Activity"),
                MountComponentProxy.KIND_ACTIVITY));

        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY,
                marked(PROXY_ACTIVITY, "app.morphe.extension.newx.mount.DoesNotExist"),
                MountComponentProxy.KIND_ACTIVITY));

        Intent noComponent = new Intent();
        noComponent.setIdentifier(MARKER + ACTIVITY_TARGET);
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, noComponent, MountComponentProxy.KIND_ACTIVITY));

        Intent plain = new Intent().setClassName(packageName, PROXY_ACTIVITY);
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, plain, MountComponentProxy.KIND_ACTIVITY));
        plain.setIdentifier("something-else:" + ACTIVITY_TARGET);
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, plain, MountComponentProxy.KIND_ACTIVITY));
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, null, MountComponentProxy.KIND_ACTIVITY));
    }

    @Test
    public void kindSpecificResolversMatchTheKindedResolver() {
        Intent activityIntent = marked(PROXY_ACTIVITY, ACTIVITY_TARGET);
        assertEquals(ACTIVITY_TARGET, MountComponentProxy.resolveActivityClassName(PROXY_ACTIVITY, activityIntent));
        assertEquals(resolve(PROXY_ACTIVITY, activityIntent, MountComponentProxy.KIND_ACTIVITY),
                MountComponentProxy.resolveActivityClassName(PROXY_ACTIVITY, activityIntent));
        Intent receiverIntent = marked(PROXY_RECEIVER, RECEIVER_TARGET);
        assertEquals(RECEIVER_TARGET, MountComponentProxy.resolveReceiverClassName(PROXY_RECEIVER, receiverIntent));
        assertEquals(resolve(PROXY_RECEIVER, receiverIntent, MountComponentProxy.KIND_RECEIVER),
                MountComponentProxy.resolveReceiverClassName(PROXY_RECEIVER, receiverIntent));

        assertEquals(PROXY_RECEIVER, MountComponentProxy.resolveReceiverClassName(PROXY_RECEIVER,
                marked(PROXY_RECEIVER, ACTIVITY_TARGET)));
        assertEquals(PROXY_ACTIVITY, MountComponentProxy.resolveActivityClassName(PROXY_ACTIVITY,
                marked(PROXY_ACTIVITY, RECEIVER_TARGET)));
        assertEquals(PROXY_ACTIVITY, MountComponentProxy.resolveReceiverClassName(PROXY_ACTIVITY, activityIntent));
        assertEquals(PROXY_RECEIVER, MountComponentProxy.resolveActivityClassName(PROXY_RECEIVER, receiverIntent));

        assertEquals(EXPORTED_ACTIVITY, MountComponentProxy.resolveActivityClassName(EXPORTED_ACTIVITY,
                marked(EXPORTED_ACTIVITY, ACTIVITY_TARGET)));
        assertEquals(PROXY_ACTIVITY, MountComponentProxy.resolveActivityClassName(PROXY_ACTIVITY, null));
    }

    @Test
    public void noSharedContextMeansNoSwap() {
        Intent intent = marked(PROXY_ACTIVITY, ACTIVITY_TARGET);
        assertEquals(ACTIVITY_TARGET, resolve(PROXY_ACTIVITY, intent, MountComponentProxy.KIND_ACTIVITY));

        Utils.setContext(null);
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, intent, MountComponentProxy.KIND_ACTIVITY));
    }

    private static String resolve(String className, Intent intent, int kind) {
        return MountComponentProxy.resolveClassName(className, intent, kind);
    }

    private Intent marked(String component, String target) {
        Intent intent = new Intent().setClassName(packageName, component);
        intent.setIdentifier(MARKER + target);
        return intent;
    }

    private ActivityInfo activity(String name, ApplicationInfo application, String affinity,
                                  boolean exported, String processName, int launchMode) {
        ActivityInfo info = new ActivityInfo();
        info.name = name;
        info.packageName = packageName;
        info.applicationInfo = application;
        info.exported = exported;
        info.enabled = true;
        info.processName = processName;
        info.taskAffinity = affinity;
        info.launchMode = launchMode;
        return info;
    }

    private ActivityInfo receiver(String name, ApplicationInfo application, boolean exported) {
        ActivityInfo info = new ActivityInfo();
        info.name = name;
        info.packageName = packageName;
        info.applicationInfo = application;
        info.exported = exported;
        info.enabled = true;
        info.processName = application.processName;
        return info;
    }

    private static void resetProxyCache() throws Exception {
        for (String name : new String[]{"activityProxy", "receiverProxy"}) {
            Field field = MountComponentProxy.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, null);
        }
    }
}
