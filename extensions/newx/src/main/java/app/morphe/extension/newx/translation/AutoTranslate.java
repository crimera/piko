package app.morphe.extension.newx.translation;

import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import app.morphe.extension.crimera.settings.SettingsRegistry;

import kotlin.jvm.functions.Function1;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** Owns automatic requests; all binding and Compose-state access stays on the main thread. */
public final class AutoTranslate {
    public static final String SETTING = "newx.translation.automatic";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Object, Binding> BINDINGS = new WeakHashMap<>();
    private static final Map<Object, Binding> UI_STATES = new WeakHashMap<>();
    private static final Map<String, Request> REQUESTS = new HashMap<>();
    // Provider caches already retain successful text. This small cache shares native result states
    // between presenters of the same post without retaining a presenter or a Compose callback.
    private static final LruCache<String, Object> CACHE = new LruCache<>(32);

    private AutoTranslate() {}

    /** Null means the caller must keep its complete native Compose path. */
    public static Object compose(Object presenter, Object composer) {
        Binding previous = BINDINGS.get(presenter);
        if (!enabled()) {
            if (previous != null) {
                previous.cancel();
                render(previous.state, previous.shown, presenter, composer, previous, false);
                BINDINGS.remove(presenter);
            }
            return null;
        }
        Object model = PostTranslator.readPostModel(presenter);
        String selection = PostTranslator.automaticSelection();
        String source = PostTranslator.readSourceLanguage(presenter);
        String text = PostTranslator.visibleText(model, PostTranslator.readText(presenter));
        if (!eligible(text, source, PostTranslator.resolveTargetLanguage())
                || hasNativeTranslation(model)) {
            if (previous != null) {
                previous.cancel();
                // Clear only content installed by this binding before returning to the native path.
                render(previous.state, previous.shown, presenter, composer, previous, false);
                BINDINGS.remove(presenter);
            }
            return null;
        }
        String key = requestKey(presenter, selection);
        Binding binding = previous;
        if (binding == null || !binding.key.equals(key)) {
            if (previous != null) previous.cancel();
            binding = new Binding(presenter, key, selection);
            BINDINGS.put(presenter, binding);
            MAIN.postDelayed(binding, 60);
        }
        Object result = render(binding.state, binding.shown, presenter, composer, binding, true);
        UI_STATES.put(result, binding);
        return result;
    }

    private static String requestKey(Object presenter, String selection) {
        Object model = PostTranslator.readPostModel(presenter);
        String text = PostTranslator.visibleText(model, PostTranslator.readText(presenter));
        String source = PostTranslator.readSourceLanguage(presenter);
        List<String> polls = PostTranslator.readPollChoices(presenter);
        StringBuilder key =
                new StringBuilder(selection)
                        .append('\0')
                        .append(readIdentity(model))
                        .append('\0')
                        .append(source)
                        .append('\0')
                        .append(text == null ? -1 : text.length())
                        .append(':')
                        .append(text);
        for (String poll : polls) key.append('\0').append(poll.length()).append(':').append(poll);
        return key.toString();
    }

    /**
     * Only automatic quote states use the interactive header; native read-only quotes stay intact.
     */
    public static boolean ownsState(Object state) {
        return UI_STATES.containsKey(state);
    }

    static boolean eligible(String text, String source, String target) {
        if (text == null || text.length() > 6000) return false;
        String language = source == null ? "" : source.replace('_', '-');
        if ("zxx".equalsIgnoreCase(language)) return false;
        Locale original = Locale.forLanguageTag(language);
        Locale translated = Locale.forLanguageTag(target);
        if (!original.getLanguage().isEmpty()
                && original.getLanguage().equals(translated.getLanguage())) return false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (Character.isLetter(codePoint)) return true;
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    static boolean enabled() {
        return SettingsRegistry.getBooleanOrDefault(SETTING, false);
    }

    private static final class Binding implements Runnable, Function1<Object, Object> {
        final WeakReference<Object> owner;
        final String key;
        final String selection;
        final Object state = newState(initialState());
        final Object shown = newState(Boolean.TRUE);
        int attempts;
        boolean cancelled;

        Binding(Object owner, String key, String selection) {
            this.owner = new WeakReference<>(owner);
            this.key = key;
            this.selection = selection;
        }

        boolean current() {
            Object presenter = owner.get();
            return !cancelled
                    && presenter != null
                    && BINDINGS.get(presenter) == this
                    && enabled()
                    && selection.equals(PostTranslator.automaticSelection())
                    && key.equals(requestKey(presenter, selection));
        }

        void cancel() {
            cancelled = true;
            MAIN.removeCallbacks(this);
        }

        @Override
        public void run() {
            if (!current() || !Boolean.TRUE.equals(getState(shown))) return;
            Object cached = CACHE.get(key);
            if (cached != null) {
                setState(state, cached);
                return;
            }
            attempts++;
            Request request = REQUESTS.get(key);
            if (request != null) {
                request.listeners.add(new WeakReference<>(this));
                setState(state, loadingState());
                return;
            }
            request = new Request(key);
            request.listeners.add(new WeakReference<>(this));
            REQUESTS.put(key, request);
            PostTranslator.translateAutomatic(owner.get(), request);
        }

        void accept(Object value) {
            if (!current()) return;
            setState(state, value);
            if (isError(value) && attempts < 3 && Boolean.TRUE.equals(getState(shown)))
                MAIN.postDelayed(this, attempts == 1 ? 1000 : 2500);
        }

        @Override
        public Object invoke(Object event) {
            if (!current()) return null;
            int kind = eventKind(event);
            if (kind == 1) {
                setState(shown, Boolean.FALSE);
                MAIN.removeCallbacks(this);
            } else if (kind == 2) {
                setState(shown, Boolean.TRUE);
                if (isError(getState(state))) {
                    MAIN.removeCallbacks(this);
                    attempts = 0;
                    MAIN.post(this);
                }
            }
            return null;
        }
    }

    private static final class Request implements Function1<Object, Object> {
        final String key;
        final List<WeakReference<Binding>> listeners = new ArrayList<>();

        Request(String key) {
            this.key = key;
        }

        @Override
        public Object invoke(Object value) {
            boolean terminal = isError(value) || isCompleted(value);
            if (terminal) REQUESTS.remove(key, this);
            if (isCompleted(value)) CACHE.put(key, value);
            for (WeakReference<Binding> reference : listeners) {
                Binding binding = reference.get();
                if (binding != null) binding.accept(value);
            }
            return null;
        }
    }

    // Patched from the actual X/Compose contracts. No obfuscated runtime identities or reflection.
    private static Object newState(Object initial) {
        throw unpatched();
    }

    private static Object getState(Object state) {
        throw unpatched();
    }

    private static void setState(Object state, Object value) {
        throw unpatched();
    }

    private static Object initialState() {
        throw unpatched();
    }

    private static Object loadingState() {
        throw unpatched();
    }

    private static boolean isError(Object state) {
        throw unpatched();
    }

    private static boolean isCompleted(Object state) {
        throw unpatched();
    }

    private static Object readIdentity(Object model) {
        throw unpatched();
    }

    private static boolean hasNativeTranslation(Object model) {
        throw unpatched();
    }

    private static int eventKind(Object event) {
        throw unpatched();
    }

    private static Object render(
            Object state,
            Object shown,
            Object presenter,
            Object composer,
            Object callback,
            boolean enabled) {
        throw unpatched();
    }

    private static IllegalStateException unpatched() {
        return new IllegalStateException("Unpatched automatic translation bridge");
    }
}
