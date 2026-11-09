package app.morphe.extension.newx.translation;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import app.morphe.extension.crimera.settings.SettingsRegistry;

import kotlin.jvm.functions.Function1;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@RunWith(RobolectricTestRunner.class)
@Config(
        sdk = 35,
        manifest = Config.NONE,
        instrumentedPackages = {
            "app.morphe.extension.newx.translation",
            "app.morphe.extension.crimera.settings"
        },
        shadows = {
            AutoTranslateTest.AutoBridge.class,
            AutoTranslateTest.PostBridge.class,
            AutoTranslateTest.ConfigBridge.class,
            AutoTranslateTest.SettingsBridge.class
        })
@LooperMode(LooperMode.Mode.PAUSED)
/**
 * Guards the missing-Grok-request path, request sharing, stale results and original toggles. Native
 * patch-time bridges are controlled shadows; no live provider or device is used.
 */
public class AutoTranslateTest {
    static class State {
        int kind;
        String text;

        State(int k, String t) {
            kind = k;
            text = t;
        }
    }

    static class Box {
        Object value;

        Box(Object v) {
            value = v;
        }
    }

    static class Model {
        String id = "post-1";
        String text = "Hello world", language = "en";
        boolean nativeTranslated;
    }

    static class Presenter {
        Model model = new Model();
    }

    static class Ui {
        Object state;
        boolean shown;

        Ui(Object s, boolean b) {
            state = s;
            shown = b;
        }
    }

    static final State IDLE = new State(0, ""),
            LOADING = new State(1, ""),
            ERROR = new State(3, "");
    static final AtomicInteger calls = new AtomicInteger();
    static CountDownLatch release;
    static final Map<String, Boolean> settings = new HashMap<>();
    static String target = "it", providerId = "google";
    static TranslationProvider provider;
    static boolean failRequests;

    @Before
    public void before() throws Exception {
        settings.clear();
        settings.put(AutoTranslate.SETTING, true);
        target = "it";
        providerId = "google";
        failRequests = false;
        for (String name : List.of("BINDINGS", "UI_STATES", "REQUESTS")) {
            Field f = AutoTranslate.class.getDeclaredField(name);
            f.setAccessible(true);
            ((Map<?, ?>) f.get(null)).clear();
        }
        Field c = AutoTranslate.class.getDeclaredField("CACHE");
        c.setAccessible(true);
        ((android.util.LruCache<?, ?>) c.get(null)).evictAll();
        Field h = AutoTranslate.class.getDeclaredField("MAIN");
        h.setAccessible(true);
        ((android.os.Handler) h.get(null)).removeCallbacksAndMessages(null);
        calls.set(0);
        release = new CountDownLatch(0);
        provider =
                new TranslationProvider() {
                    public String id() {
                        return "google";
                    }

                    public String displayName() {
                        return "Google";
                    }

                    public boolean supportsText() {
                        return true;
                    }

                    public TranslationResult translateText(String t, String s, String target)
                            throws java.io.IOException {
                        calls.incrementAndGet();
                        try {
                            release.await(3, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            throw new java.io.IOException(e);
                        }
                        if (failRequests)
                            throw new java.io.IOException("controlled provider failure");
                        return new TranslationResult("translated-" + target, "en");
                    }

                    public TranslationResult translatePost(String id, String t, String target)
                            throws java.io.IOException {
                        return translateText(t, null, target);
                    }
                };
    }

    @Test
    public void automaticPostStartsWithoutNativeRequestAndToggleKeepsResult() throws Exception {
        Presenter p = new Presenter();
        Ui ui = (Ui) AutoTranslate.compose(p, new Object());
        assertEquals(0, calls.get());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCalls(1);
        awaitCompleted(p);
        ui = (Ui) AutoTranslate.compose(p, new Object());
        assertEquals("translated-it", ((State) ui.state).text);
        assertTrue(ui.shown);
        Field f = AutoTranslate.class.getDeclaredField("BINDINGS");
        f.setAccessible(true);
        Object binding = ((Map<?, ?>) f.get(null)).get(p);
        ((Function1<Object, Object>) binding).invoke(1);
        assertFalse(((Ui) AutoTranslate.compose(p, new Object())).shown);
        ((Function1<Object, Object>) binding).invoke(2);
        assertTrue(((Ui) AutoTranslate.compose(p, new Object())).shown);
        assertEquals(1, calls.get());
    }

    static void awaitCalls(int count) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (calls.get() < count && System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(5);
        }
        assertEquals(count, calls.get());
    }

    static void awaitCompleted(Presenter p) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < until) {
            shadowOf(Looper.getMainLooper()).idle();
            Ui ui = (Ui) AutoTranslate.compose(p, new Object());
            if (ui != null && ((State) ui.state).kind == 2) return;
            Thread.sleep(5);
        }
        fail("No completed translation");
    }

    @After
    public void drainWorkers() throws Exception {
        release.countDown();
        Field f = PostTranslator.class.getDeclaredField("EXECUTOR");
        f.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) f.get(null);
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while ((executor.getActiveCount() > 0 || !executor.getQueue().isEmpty())
                && System.nanoTime() < until) Thread.sleep(5);
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void duplicatePresentersShareRequestAndCache() throws Exception {
        release = new CountDownLatch(1);
        Presenter a = new Presenter(), b = new Presenter();
        AutoTranslate.compose(a, new Object());
        AutoTranslate.compose(b, new Object());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCalls(1);
        release.countDown();
        awaitCompleted(a);
        awaitCompleted(b);
        assertEquals(1, calls.get());
        Presenter c = new Presenter();
        AutoTranslate.compose(c, new Object());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCompleted(c);
        assertEquals(1, calls.get());
    }

    @Test
    public void disabledSameLanguageNativeAndNonTextPostsDoNotRequest() throws Exception {
        Presenter p = new Presenter();
        settings.put(AutoTranslate.SETTING, false);
        assertNull(AutoTranslate.compose(p, new Object()));
        settings.put(AutoTranslate.SETTING, true);
        p.model.language = "it-IT";
        assertNull(AutoTranslate.compose(p, new Object()));
        p.model.language = "en";
        p.model.nativeTranslated = true;
        assertNull(AutoTranslate.compose(p, new Object()));
        p.model.nativeTranslated = false;
        p.model.text = "123 😀";
        assertNull(AutoTranslate.compose(p, new Object()));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertEquals(0, calls.get());
    }

    @Test
    public void recycledPresenterRejectsOldContent() throws Exception {
        release = new CountDownLatch(1);
        Presenter p = new Presenter();
        AutoTranslate.compose(p, new Object());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCalls(1);
        p.model = new Model();
        p.model.text = "A different post";
        AutoTranslate.compose(p, new Object());
        release.countDown();
        drainWorkers();
        Ui ui = (Ui) AutoTranslate.compose(p, new Object());
        assertEquals(0, ((State) ui.state).kind);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCalls(2);
        awaitCompleted(p);
    }

    @Test
    public void targetChangeRejectsPendingResultAndUsesSeparateCache() throws Exception {
        release = new CountDownLatch(1);
        Presenter p = new Presenter();
        AutoTranslate.compose(p, new Object());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCalls(1);
        target = "fr";
        AutoTranslate.compose(p, new Object());
        release.countDown();
        drainWorkers();
        assertEquals(0, ((State) ((Ui) AutoTranslate.compose(p, new Object())).state).kind);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCalls(2);
        awaitCompleted(p);
        assertEquals(
                "translated-fr",
                ((State) ((Ui) AutoTranslate.compose(p, new Object())).state).text);
    }

    @Test
    public void equivalentModelReplacementPreservesOriginalToggle() throws Exception {
        Presenter p = new Presenter();
        AutoTranslate.compose(p, new Object());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCalls(1);
        awaitCompleted(p);
        binding(p).invoke(1);
        p.model = new Model();
        assertFalse(((Ui) AutoTranslate.compose(p, new Object())).shown);
        assertEquals(1, calls.get());
    }

    @Test
    public void providerChangeDoesNotReusePreviousProviderCache() throws Exception {
        Presenter p = new Presenter();
        AutoTranslate.compose(p, new Object());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCalls(1);
        awaitCompleted(p);
        providerId = "another-provider";
        AutoTranslate.compose(p, new Object());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCalls(2);
        awaitCompleted(p);
    }

    @Test
    public void disablingPendingWorkReturnsNativePath() throws Exception {
        release = new CountDownLatch(1);
        Presenter p = new Presenter();
        AutoTranslate.compose(p, new Object());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCalls(1);
        settings.put(AutoTranslate.SETTING, false);
        assertNull(AutoTranslate.compose(p, new Object()));
        release.countDown();
        drainWorkers();
        assertNull(AutoTranslate.compose(p, new Object()));
    }

    @Test
    public void originalRemainsSelectedWhenPendingRequestCompletes() throws Exception {
        release = new CountDownLatch(1);
        Presenter p = new Presenter();
        AutoTranslate.compose(p, new Object());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCalls(1);
        binding(p).invoke(1);
        release.countDown();
        awaitCompleted(p);
        assertFalse(((Ui) AutoTranslate.compose(p, new Object())).shown);
        assertEquals(1, calls.get());
    }

    @Test
    public void providerFailureHasBoundedRetriesAndManualRetry() throws Exception {
        failRequests = true;
        Presenter p = new Presenter();
        AutoTranslate.compose(p, new Object());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCalls(1);
        drainWorkers();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
        awaitCalls(2);
        drainWorkers();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2500));
        awaitCalls(3);
        drainWorkers();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
        assertEquals(3, calls.get());
        failRequests = false;
        binding(p).invoke(2);
        shadowOf(Looper.getMainLooper()).idle();
        awaitCalls(4);
        awaitCompleted(p);
    }

    @Test
    public void unknownLanguageCanTranslateWhileZxxCannot() throws Exception {
        Presenter p = new Presenter();
        p.model.language = "zxx";
        assertNull(AutoTranslate.compose(p, new Object()));
        p.model.language = null;
        AutoTranslate.compose(p, new Object());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60));
        awaitCalls(1);
        awaitCompleted(p);
    }

    @Test
    public void manualInlineStillWorksWhenAutomaticIsDisabled() throws Exception {
        settings.put(AutoTranslate.SETTING, false);
        settings.put(PostTranslator.INLINE_SETTING, true);
        Presenter p = new Presenter();
        AtomicReference<Object> result = new AtomicReference<>();
        assertTrue(
                PostTranslator.translate(
                        p,
                        (Function1<Object, Object>)
                                value -> {
                                    result.set(value);
                                    return null;
                                }));
        awaitCalls(1);
        drainWorkers();
        assertEquals(2, ((State) result.get()).kind);
        assertNull(AutoTranslate.compose(p, new Object()));
    }

    @SuppressWarnings("unchecked")
    static Function1<Object, Object> binding(Presenter p) throws Exception {
        Field f = AutoTranslate.class.getDeclaredField("BINDINGS");
        f.setAccessible(true);
        return (Function1<Object, Object>) ((Map<?, ?>) f.get(null)).get(p);
    }

    @org.robolectric.annotation.internal.DoNotInstrument
    @Implements(value = TranslatorConfig.class, isInAndroidSdk = false)
    public static class ConfigBridge {
        @Implementation
        protected void __constructor__() {}

        @Implementation
        protected static TranslatorConfig shared() {
            return org.robolectric.util.ReflectionHelpers.callConstructor(TranslatorConfig.class);
        }

        @Implementation
        protected String selectedId() {
            return providerId;
        }

        @Implementation
        protected String resolvedTargetLanguage() {
            return target;
        }

        @Implementation
        protected TranslatorConfig.CustomProvider customProvider(String id) {
            return null;
        }

        @Implementation
        protected TranslationProvider selected() {
            return provider;
        }

        @Implementation
        protected List<TranslationProvider> providers() {
            return List.of(provider);
        }
    }

    @org.robolectric.annotation.internal.DoNotInstrument
    @Implements(value = SettingsRegistry.class, isInAndroidSdk = false)
    public static class SettingsBridge {
        @Implementation
        protected static boolean getBooleanOrDefault(String key, boolean fallback) {
            return settings.getOrDefault(key, fallback);
        }
    }

    @org.robolectric.annotation.internal.DoNotInstrument
    @Implements(value = AutoTranslate.class, isInAndroidSdk = false)
    public static class AutoBridge {
        @Implementation
        protected static Object newState(Object v) {
            return new Box(v);
        }

        @Implementation
        protected static Object getState(Object v) {
            return ((Box) v).value;
        }

        @Implementation
        protected static void setState(Object s, Object v) {
            ((Box) s).value = v;
        }

        @Implementation
        protected static Object initialState() {
            return IDLE;
        }

        @Implementation
        protected static Object loadingState() {
            return LOADING;
        }

        @Implementation
        protected static boolean isError(Object s) {
            return ((State) s).kind == 3;
        }

        @Implementation
        protected static boolean isCompleted(Object s) {
            return ((State) s).kind == 2;
        }

        @Implementation
        protected static Object readIdentity(Object model) {
            return ((Model) model).id;
        }

        @Implementation
        protected static boolean hasNativeTranslation(Object m) {
            return ((Model) m).nativeTranslated;
        }

        @Implementation
        protected static int eventKind(Object e) {
            return (int) e;
        }

        @Implementation
        protected static Object render(
                Object s, Object show, Object p, Object c, Object callback, boolean enabled) {
            return new Ui(((Box) s).value, enabled && (Boolean) ((Box) show).value);
        }
    }

    @org.robolectric.annotation.internal.DoNotInstrument
    @Implements(value = PostTranslator.class, isInAndroidSdk = false)
    public static class PostBridge {
        @Implementation
        protected static Object readPostModel(Object p) {
            return ((Presenter) p).model;
        }

        @Implementation
        protected static String readText(Object p) {
            return ((Presenter) p).model.text;
        }

        @Implementation
        protected static String readSourceLanguage(Object p) {
            return ((Presenter) p).model.language;
        }

        @Implementation
        protected static List<String> readPollChoices(Object p) {
            return Collections.emptyList();
        }

        @Implementation
        protected static String readNoteText(Object p) {
            return null;
        }

        @Implementation
        protected static Object readDisplayTextRange(Object p) {
            return null;
        }

        @Implementation
        protected static Object readEntityList(Object p) {
            return null;
        }

        @Implementation
        protected static void publishLoading(Object c) {
            ((Function1<Object, Object>) c).invoke(LOADING);
        }

        @Implementation
        protected static void publishError(Object c) {
            ((Function1<Object, Object>) c).invoke(ERROR);
        }

        @Implementation
        protected static void publishCompleted(
                Object c, String text, List<String> polls, String language) {
            ((Function1<Object, Object>) c).invoke(new State(2, text));
        }
    }
}
