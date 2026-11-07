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

/**
 * Guards the AppComponentFactory receive side of the root-mount proxy: the hook runs for every
 * activity and receiver the app instantiates, so {@code resolveClassName} must never swap a class
 * unless the intent was addressed to the selected stock proxy of the matching kind. A swap on any
 * other component (an exported one, one in another process, one with non-standard launch mode, the
 * wrong kind, or when the shared context is missing) would let a crafted intent identifier
 * instantiate an unexported extension class or hijack unrelated app launches into Piko screens.
 *
 * <p>The package manager is populated with decoy components that are all listed BEFORE the real
 * proxy, so removing the matching guard in proxy selection makes a decoy win and the positive swap
 * (and the decoy's own unchanged assertion) fail. The proxy choice is cached in static fields of
 * {@code MountComponentProxy}; {@link #resetProxyCache} clears them so every test selects afresh.
 *
 * <p>Not discriminating on their own (the broad {@code catch (Throwable)} in {@code resolveClassName}
 * or a later guard still returns the class name): the null-component case, kind values 0 and 3, and
 * the no-marker / foreign-identifier cases. They pin the contract but do not prove those individual
 * guards; the proxy-selection decoys, className/component mismatch, wrong kind, target kind and
 * null-context assertions do.
 */
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
                // Decoys first: a tie on score is won by the first component listed.
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
        // Positive: marked intent addressed to the selected activity / receiver proxy.
        assertEquals(ACTIVITY_TARGET, resolve(PROXY_ACTIVITY, marked(PROXY_ACTIVITY, ACTIVITY_TARGET),
                MountComponentProxy.KIND_ACTIVITY));
        assertEquals(RECEIVER_TARGET, resolve(PROXY_RECEIVER, marked(PROXY_RECEIVER, RECEIVER_TARGET),
                MountComponentProxy.KIND_RECEIVER));

        // Decoy components are never swapped, even with a valid marker addressed to themselves:
        // exported (reachable by other apps), other process, non-standard launch mode.
        for (String decoy : new String[]{EXPORTED_ACTIVITY, OTHER_PROCESS_ACTIVITY, SINGLE_TASK_ACTIVITY}) {
            assertEquals(decoy, resolve(decoy, marked(decoy, ACTIVITY_TARGET), MountComponentProxy.KIND_ACTIVITY));
        }
        assertEquals(EXPORTED_RECEIVER, resolve(EXPORTED_RECEIVER, marked(EXPORTED_RECEIVER, RECEIVER_TARGET),
                MountComponentProxy.KIND_RECEIVER));

        // Class being instantiated differs from the intent's component: the intent addresses the
        // exported component's marker at the proxy class, so only the className/component equality
        // check keeps this from swapping the real proxy.
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, marked(EXPORTED_ACTIVITY, ACTIVITY_TARGET),
                MountComponentProxy.KIND_ACTIVITY));

        // Wrong kind: the target matches the passed kind, so only the "className is the proxy
        // selected for THIS kind" check refuses these.
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, marked(PROXY_ACTIVITY, RECEIVER_TARGET),
                MountComponentProxy.KIND_RECEIVER));
        assertEquals(PROXY_RECEIVER, resolve(PROXY_RECEIVER, marked(PROXY_RECEIVER, ACTIVITY_TARGET),
                MountComponentProxy.KIND_ACTIVITY));
        // Unknown kind values.
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, marked(PROXY_ACTIVITY, ACTIVITY_TARGET), 0));
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, marked(PROXY_ACTIVITY, ACTIVITY_TARGET), 3));

        // Target kind differs from the proxy kind (activity proxy asked to host a receiver class).
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, marked(PROXY_ACTIVITY, RECEIVER_TARGET),
                MountComponentProxy.KIND_ACTIVITY));
        assertEquals(PROXY_RECEIVER, resolve(PROXY_RECEIVER, marked(PROXY_RECEIVER, ACTIVITY_TARGET),
                MountComponentProxy.KIND_RECEIVER));

        // Targets outside the extension package are never trusted, even with the marker.
        // android.app.Activity is a loadable Activity, so only the extension-prefix check refuses it.
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, marked(PROXY_ACTIVITY, "android.app.Activity"),
                MountComponentProxy.KIND_ACTIVITY));

        // Unloadable target class.
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY,
                marked(PROXY_ACTIVITY, "app.morphe.extension.newx.mount.DoesNotExist"),
                MountComponentProxy.KIND_ACTIVITY));

        // Null component (marker present, but no component to compare against).
        Intent noComponent = new Intent();
        noComponent.setIdentifier(MARKER + ACTIVITY_TARGET);
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, noComponent, MountComponentProxy.KIND_ACTIVITY));

        // No marker, unrelated identifier, no intent: untouched.
        Intent plain = new Intent().setClassName(packageName, PROXY_ACTIVITY);
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, plain, MountComponentProxy.KIND_ACTIVITY));
        plain.setIdentifier("something-else:" + ACTIVITY_TARGET);
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, plain, MountComponentProxy.KIND_ACTIVITY));
        assertEquals(PROXY_ACTIVITY, resolve(PROXY_ACTIVITY, null, MountComponentProxy.KIND_ACTIVITY));
    }

    @Test
    public void kindSpecificResolversMatchTheKindedResolver() {
        // Positive: each wrapper swaps exactly like resolveClassName(..., KIND_*).
        Intent activityIntent = marked(PROXY_ACTIVITY, ACTIVITY_TARGET);
        assertEquals(ACTIVITY_TARGET, MountComponentProxy.resolveActivityClassName(PROXY_ACTIVITY, activityIntent));
        assertEquals(resolve(PROXY_ACTIVITY, activityIntent, MountComponentProxy.KIND_ACTIVITY),
                MountComponentProxy.resolveActivityClassName(PROXY_ACTIVITY, activityIntent));
        Intent receiverIntent = marked(PROXY_RECEIVER, RECEIVER_TARGET);
        assertEquals(RECEIVER_TARGET, MountComponentProxy.resolveReceiverClassName(PROXY_RECEIVER, receiverIntent));
        assertEquals(resolve(PROXY_RECEIVER, receiverIntent, MountComponentProxy.KIND_RECEIVER),
                MountComponentProxy.resolveReceiverClassName(PROXY_RECEIVER, receiverIntent));

        // Wrong kind: each wrapper keeps the class when the target is of the other kind, or when
        // the class is the other kind's proxy (proves the wrapper passes its own kind, not the other).
        assertEquals(PROXY_RECEIVER, MountComponentProxy.resolveReceiverClassName(PROXY_RECEIVER,
                marked(PROXY_RECEIVER, ACTIVITY_TARGET)));
        assertEquals(PROXY_ACTIVITY, MountComponentProxy.resolveActivityClassName(PROXY_ACTIVITY,
                marked(PROXY_ACTIVITY, RECEIVER_TARGET)));
        assertEquals(PROXY_ACTIVITY, MountComponentProxy.resolveReceiverClassName(PROXY_ACTIVITY, activityIntent));
        assertEquals(PROXY_RECEIVER, MountComponentProxy.resolveActivityClassName(PROXY_RECEIVER, receiverIntent));

        // Decoy and null intent stay untouched through the wrappers too.
        assertEquals(EXPORTED_ACTIVITY, MountComponentProxy.resolveActivityClassName(EXPORTED_ACTIVITY,
                marked(EXPORTED_ACTIVITY, ACTIVITY_TARGET)));
        assertEquals(PROXY_ACTIVITY, MountComponentProxy.resolveActivityClassName(PROXY_ACTIVITY, null));
    }

    @Test
    public void noSharedContextMeansNoSwap() {
        // Sanity: with the context this exact intent swaps...
        Intent intent = marked(PROXY_ACTIVITY, ACTIVITY_TARGET);
        assertEquals(ACTIVITY_TARGET, resolve(PROXY_ACTIVITY, intent, MountComponentProxy.KIND_ACTIVITY));

        // ...and without it the swap is refused. The proxy stays cached from the call above on
        // purpose: if the null-context guard were removed, the cache would let the swap through
        // (a fresh selection would NPE instead, and that is swallowed, masking the missing guard).
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

    /** The selected proxies are cached in static fields; clear them so each test selects afresh. */
    private static void resetProxyCache() throws Exception {
        for (String name : new String[]{"activityProxy", "receiverProxy"}) {
            Field field = MountComponentProxy.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, null);
        }
    }
}
