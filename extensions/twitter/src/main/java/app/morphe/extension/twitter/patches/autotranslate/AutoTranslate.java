package app.morphe.extension.twitter.patches.autotranslate;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.SwitchPreference;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.LruCache;
import android.view.View;
import android.widget.TextView;
import android.view.MotionEvent;
import android.text.StaticLayout;
import com.twitter.tweetview.core.QuoteView;
import android.view.ViewTreeObserver;
import com.twitter.model.core.e;
import com.twitter.ui.widget.TextContentView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** Experimental, version-specific UI hook for X 12.19.1 / Piko. */
public final class AutoTranslate {
    private static final int TAG = 0x7e0a70a1;
    private static final int WATCH_TAG = 0x7e0a70a2;
    private static final String PREFS = "x_auto_translate_it";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(
            4, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(24));
    private static final WeakHashMap<View, Boolean> VIEWS = new WeakHashMap<>();
    private static final HashMap<String, ArrayList<Waiter>> IN_FLIGHT = new HashMap<>();
    private static final HashMap<String, Long> FAILURES = new HashMap<>();
    private static final HashMap<String, Integer> ATTEMPTS = new HashMap<>();
    private static final HashMap<String, Runnable> RETRY_TASKS = new HashMap<>();
    private static final LruCache<String, String> CACHE = new LruCache<String, String>(512 * 1024) {
        @Override protected int sizeOf(String key, String value) { return 2 * (key.length() + value.length()); }
    };
    private static long pausedUntil;
    private static String pausedMessage = "Traduzione in pausa";
    private static final HashMap<String, String> FAILURE_MESSAGES = new HashMap<>();

    private static final class Binding {
        final WeakReference<View> view;
        final long id;
        final String language;
        int revision;
        CharSequence original;
        String key;
        String translated;
        boolean originalShown;
        Runnable scheduled;
        CharSequence rendered;
        CharSequence quoteDescription;
        ClickableSpan pressed;
        int pressedRevision;
        float downX, downY;
        Binding(View view, long id, String language) {
            this.view = new WeakReference<>(view);
            this.id = id;
            this.language = language;
        }
    }

    private static final class Waiter {
        final WeakReference<Binding> binding;
        final int revision;
        Waiter(Binding value) { binding = new WeakReference<>(value); revision = value.revision; }
    }

    private static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFS, 0).getBoolean("enabled", true);
    }

    /** Called before the app formats a particular tweet. Hidden content is excluded. */
    public static void capture(View view, e tweet, boolean hidden) {
        try {
            if (!(view instanceof TextContentView) && !(view instanceof QuoteView) && !(view instanceof TextView)) return;
            View textView = view;
            Binding previous = (Binding) view.getTag(TAG);
            if (previous != null && previous.scheduled != null) { MAIN.removeCallbacks(previous.scheduled); previous.scheduled = null; }
            if (hidden || tweet == null || tweet.a == null) {
                view.setTag(TAG, null);
                return;
            }
            String language = tweet.a.r == null ? "und" : tweet.a.r.toLowerCase(Locale.ROOT);
            long id = tweet.a.H1;
            if (previous == null || previous.id != id || !previous.language.equals(language)) {
                view.setTag(TAG, new Binding(textView, id, language));
            }
            VIEWS.put(textView, Boolean.TRUE);
            if (view.getTag(WATCH_TAG) == null) {
                Watcher watcher = new Watcher(textView);
                view.setTag(WATCH_TAG, watcher);
                view.addOnAttachStateChangeListener(watcher);
                if (view.getWindowToken() != null) watcher.register();
            }
        } catch (Throwable ignored) { /* A failed optional hook must preserve normal rendering. */ }
    }

    /** Replaces one text setter, retaining the app's original text and spans. */
    public static void bind(TextContentView view, CharSequence text) { bindView(view, text); }

    /** Final focal-post setter, including the native deferred-attachment path. */
    public static void bindFocal(TextView view, CharSequence text, TextView.BufferType type) { bindView(view, text); }

    public static void clearFocal(View view) {
        try {
            Binding binding = (Binding) view.getTag(TAG);
            if (binding != null && binding.scheduled != null) MAIN.removeCallbacks(binding.scheduled);
            view.setTag(TAG, null);
            VIEWS.remove(view);
        } catch (Throwable ignored) { }
    }

    private static void bindView(View view, CharSequence text) {
        write(view, text);
        if (!(view instanceof QuoteView)) describeView(view, text);
        try {
            Binding binding = (Binding) view.getTag(TAG);
            if (binding == null) return;
            if (binding.scheduled != null) { MAIN.removeCallbacks(binding.scheduled); binding.scheduled = null; }
            String plain = text == null ? "" : text.toString();
            String key = binding.id + "\u0000" + binding.language + "\u0000it\u0000" + plain;
            boolean changed = !key.equals(binding.key);
            binding.revision++;
            binding.key = key;
            binding.original = text instanceof Spanned ? new SpannedString(text) : plain;
            if (changed) { binding.originalShown = false; binding.translated = null; }
            if (!eligible(view, binding)) return;
            String cached = CACHE.get(key);
            if (cached != null) { binding.translated = cached; render(binding, null); return; }
            long now = SystemClock.elapsedRealtime();
            Long failedUntil = FAILURES.get(key);
            if (now < pausedUntil || (failedUntil != null && now < failedUntil)) {
                render(binding, now < pausedUntil ? pausedMessage : FAILURE_MESSAGES.get(key));
                return;
            }
            schedule(binding);
        } catch (Throwable ignored) { }
    }

    private static void schedule(Binding binding) {
        View view = binding.view.get();
        if (view == null || binding.original == null || binding.translated != null || !eligible(view, binding)) return;
        if (binding.scheduled != null) return;
        if (IN_FLIGHT.containsKey(binding.key)) { request(binding); return; }
        long now = SystemClock.elapsedRealtime();
        Long failedUntil = FAILURES.get(binding.key);
        if (now < pausedUntil || (failedUntil != null && now < failedUntil)) return;
        final Waiter waiter = new Waiter(binding);
        binding.scheduled = new Runnable() {
            @Override public void run() {
                try {
                    Binding value = current(waiter);
                    if (value != null) {
                        value.scheduled = null;
                        if (visible(value.view.get())) request(value);
                    }
                } catch (Throwable ignored) { }
            }
        };
        MAIN.postDelayed(binding.scheduled, 60);
    }

    private static final class Watcher implements View.OnAttachStateChangeListener, ViewTreeObserver.OnScrollChangedListener {
        final WeakReference<View> view;
        ViewTreeObserver observer;
        Watcher(View value) { view = new WeakReference<>(value); }
        void register() {
            View value = view.get();
            if (value == null) return;
            ViewTreeObserver next = value.getViewTreeObserver();
            if (next == observer) return;
            unregister();
            observer = next;
            observer.addOnScrollChangedListener(this);
        }
        void unregister() {
            if (observer != null && observer.isAlive()) observer.removeOnScrollChangedListener(this);
            observer = null;
        }
        @Override public void onViewAttachedToWindow(View value) { register(); onScrollChanged(); }
        @Override public void onViewDetachedFromWindow(View value) {
            unregister();
            Binding binding = (Binding) value.getTag(TAG);
            if (binding != null && binding.scheduled != null) { MAIN.removeCallbacks(binding.scheduled); binding.scheduled = null; }
        }
        @Override public void onScrollChanged() {
            try {
                View value = view.get();
                if (value != null) {
                    Binding binding = (Binding) value.getTag(TAG);
                    if (binding != null) schedule(binding);
                }
            } catch (Throwable ignored) { }
        }
    }

    private static boolean eligible(View view, Binding binding) {
        if (!enabled(view.getContext()) || "it".equals(binding.language)
                || binding.language.startsWith("it-") || "zxx".equals(binding.language)) return false;
        String plain = binding.original.toString();
        if (plain.length() > 6000) return false;
        int letters = 0;
        for (int i = 0; i < plain.length() && letters < 3; i++) {
            if (Character.isLetter(plain.charAt(i))) letters++;
        }
        return letters >= 3;
    }

    private static boolean visible(View view) {
        return view != null && view.getWindowToken() != null && view.isShown()
                && view.getGlobalVisibleRect(new Rect());
    }

    private static Binding current(Waiter waiter) {
        Binding binding = waiter.binding.get();
        if (binding == null || binding.revision != waiter.revision) return null;
        View view = binding.view.get();
        return view != null && view.getTag(TAG) == binding && matchesQuote(view, binding) && enabled(view.getContext()) ? binding : null;
    }

    private static void request(final Binding binding) {
        final String key = binding.key;
        ArrayList<Waiter> waiting = IN_FLIGHT.get(key);
        if (waiting != null) {
            for (Waiter waiter : waiting) {
                if (waiter.binding.get() == binding && waiter.revision == binding.revision) return;
            }
            waiting.add(new Waiter(binding));
            render(binding, RETRY_TASKS.containsKey(key) ? "Traduzione in preparazione… riprovo automaticamente" : "Traduzione in corso…");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        Long failedUntil = FAILURES.get(key);
        if (now < pausedUntil || (failedUntil != null && now < failedUntil)) {
            render(binding, now < pausedUntil ? pausedMessage : FAILURE_MESSAGES.get(key));
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(new Waiter(binding));
        IN_FLIGHT.put(key, waiting);
        final String source = binding.language;
        final String original = binding.original.toString();
        render(binding, "Traduzione in corso…");
        startNetwork(key, source, original);
    }

    private static void startNetwork(final String key, final String source, final String original) {
        try {
            WORKERS.execute(new Runnable() {
                @Override public void run() {
                    String translated = null;
                    Exception error = null;
                    try {
                        final CountDownLatch gate = new CountDownLatch(1);
                        final AtomicBoolean allowed = new AtomicBoolean(false);
                        MAIN.post(new Runnable() {
                            @Override public void run() {
                                try {
                                    ArrayList<Waiter> waiting = IN_FLIGHT.get(key);
                                    if (waiting != null && SystemClock.elapsedRealtime() >= pausedUntil) {
                                        for (Waiter waiter : waiting) {
                                            Binding value = current(waiter);
                                            if (value != null && visible(value.view.get())) { allowed.set(true); break; }
                                        }
                                    }
                                } finally { gate.countDown(); }
                            }
                        });
                        if (gate.await(2, TimeUnit.SECONDS) && allowed.get())
                            translated = TranslationClient.translate(source, original);
                    }
                    catch (Exception failure) { error = failure; }
                    final String result = translated;
                    final Exception failure = error;
                    MAIN.post(new Runnable() {
                        @Override public void run() { finish(key, result, failure); }
                    });
                }
            });
        } catch (RuntimeException rejected) { finish(key, null, rejected); }
    }

    private static boolean hasVisibleWaiter(String key) {
        ArrayList<Waiter> waiting = IN_FLIGHT.get(key);
        if (waiting == null) return false;
        for (Waiter waiter : waiting) {
            Binding value = current(waiter);
            if (value != null && visible(value.view.get())) return true;
        }
        return false;
    }

    private static void clearRetry(String key) {
        Runnable task = RETRY_TASKS.remove(key);
        if (task != null) MAIN.removeCallbacks(task);
    }

    private static void cancelCycle(String key, String status) {
        clearRetry(key);
        ATTEMPTS.remove(key);
        ArrayList<Waiter> waiting = IN_FLIGHT.remove(key);
        if (waiting != null) for (Waiter waiter : waiting) {
            Binding value = current(waiter);
            if (value != null) render(value, status);
        }
    }

    private static void finish(final String key, String translated, Exception error) {
        ArrayList<Waiter> waiting = IN_FLIGHT.get(key);
        if (waiting == null) return;
        clearRetry(key);
        if (translated == null && error instanceof TranslationClient.Unavailable) {
            if (!hasVisibleWaiter(key)) { cancelCycle(key, null); return; }
            Integer previous = ATTEMPTS.get(key);
            int failures = previous == null ? 1 : previous + 1;
            ATTEMPTS.put(key, failures);
            if (failures < 3 && SystemClock.elapsedRealtime() >= pausedUntil && hasVisibleWaiter(key)) {
                Binding first = null;
                for (Waiter waiter : waiting) {
                    Binding candidate = current(waiter);
                    if (candidate != null) { first = candidate; break; }
                }
                if (first != null) {
                    final String source = first.language;
                    final String original = first.original.toString();
                    Runnable task = new Runnable() {
                        @Override public void run() {
                            RETRY_TASKS.remove(key);
                            if (SystemClock.elapsedRealtime() < pausedUntil) { cancelCycle(key, pausedMessage); return; }
                            if (!hasVisibleWaiter(key)) { cancelCycle(key, null); return; }
                            startNetwork(key, source, original);
                        }
                    };
                    RETRY_TASKS.put(key, task);
                    for (Waiter waiter : waiting) {
                        Binding value = current(waiter);
                        if (value != null) render(value, "Traduzione in preparazione… riprovo automaticamente");
                    }
                    MAIN.postDelayed(task, failures == 1 ? 1000 : 2500);
                    return;
                }
            }
        }
        IN_FLIGHT.remove(key);
        ATTEMPTS.remove(key);
        if (translated != null) CACHE.put(key, translated);
        else if (error != null) {
            long now = SystemClock.elapsedRealtime();
            if (FAILURES.size() >= 128) { FAILURES.clear(); FAILURE_MESSAGES.clear(); }
            FAILURE_MESSAGES.put(key, TranslationClient.describe(error));
            FAILURES.put(key, now + 60000);
            if (error instanceof TranslationClient.Failure) {
                int status = ((TranslationClient.Failure) error).status;
                if (status == 429 || status == 302 || status == 403) {
                    pausedUntil = now + 60000;
                    pausedMessage = TranslationClient.describe(error);
                }
            }
        }
        for (Waiter waiter : waiting) {
            Binding value = current(waiter);
            if (value == null) continue;
            value.translated = translated;
            render(value, translated == null && error != null ? TranslationClient.describe(error) : null);
        }
    }

    private static void render(Binding binding, String status) {
        try { renderChecked(binding, status); }
        catch (Throwable ignored) {
            View view = binding.view.get();
            if (view != null && view.getTag(TAG) == binding && binding.original != null && matchesQuote(view, binding)) {
                try {
                    write(view, binding.original);
                    describeView(view, binding.original);
                } catch (Throwable ignoredAgain) { }
            }
        }
    }

    private static void renderChecked(Binding binding, String status) {
        View view = binding.view.get();
        if (view == null || view.getTag(TAG) != binding || binding.original == null || !matchesQuote(view, binding)) return;
        if (!enabled(view.getContext())) { write(view, binding.original); return; }
        String translated = binding.translated;
        if (translated != null && translated.equals(binding.original.toString())) {
            write(view, binding.original);
            describeView(view, binding.original);
            binding.rendered = binding.original;
            return;
        }
        CharSequence mainText = translated != null && !binding.originalShown ? translated : binding.original;
        SpannableStringBuilder output = new SpannableStringBuilder("");
        if (appendStatus(output, binding, status)) output.append('\n');
        int bodyStart = output.length();
        output.append(mainText);
        if (translated != null && !binding.originalShown && binding.original instanceof Spanned) {
            Spanned original = (Spanned) binding.original;
            for (ClickableSpan span : original.getSpans(0, original.length(), ClickableSpan.class)) {
                int start = original.getSpanStart(span), end = original.getSpanEnd(span);
                if (start < 0 || end <= start) continue;
                String label = original.subSequence(start, end).toString();
                int at = translated.indexOf(label);
                if (at >= 0 && translated.indexOf(label, at + 1) < 0) output.setSpan(span, bodyStart + at, bodyStart + at + label.length(), 33);
            }
        }
        write(view, output);
        describeView(view, output);
        binding.rendered = output;
    }

    private static String sourceLanguageName(String language) {
        if (language == null || language.isEmpty() || "und".equals(language) || "auto".equals(language)) return "non rilevata";
        try {
            Locale locale = Locale.forLanguageTag(language);
            String name = locale.getDisplayLanguage(Locale.ITALIAN);
            if (name.isEmpty() || name.equalsIgnoreCase(locale.getLanguage())) return "non rilevata";
            return name.toLowerCase(Locale.ITALIAN);
        } catch (RuntimeException ignored) { return "non rilevata"; }
    }

    private static boolean appendStatus(SpannableStringBuilder output, Binding binding, String status) {
        int start = output.length();
        if (binding.translated != null) {
            output.append("Lingua originale: ").append(sourceLanguageName(binding.language)).append("  ");
            output.setSpan(new ForegroundColorSpan(0xff90a4ae), start, output.length(), 33);
            int action = output.length();
            output.append(binding.originalShown ? "Mostra traduzione" : "Mostra originale");
            output.setSpan(new ToggleSpan(binding), action, output.length(), 33);
        } else if (status != null) {
            output.append(status);
            if (status.startsWith("Traduzione non ")) {
                output.append(" · ");
                int action = output.length();
                output.append("Riprova");
                output.setSpan(new RetrySpan(binding), action, output.length(), 33);
            }
        }
        if (output.length() > start) {
            output.setSpan(new RelativeSizeSpan(0.8f), start, output.length(), 33);
            return true;
        }
        return false;
    }

    private static boolean matchesQuote(View view, Binding binding) {
        if (!(view instanceof QuoteView)) return true;
        QuoteView quote = (QuoteView) view;
        return quote.a != null && quote.a.a != null && quote.a.a.H1 == binding.id && binding.language.equals(quote.a.a.r == null ? "und" : quote.a.a.r.toLowerCase(Locale.ROOT)) && quote.P == null;
    }

    private static void describeView(View view, CharSequence text) {
        Binding binding = (Binding) view.getTag(TAG);
        if (view instanceof QuoteView && binding != null && binding.quoteDescription != null) {
            String base = binding.quoteDescription.toString();
            String original = binding.original == null ? "" : binding.original.toString();
            int at = original.isEmpty() ? -1 : base.indexOf(original);
            if (at >= 0) text = base.substring(0, at) + text + base.substring(at + original.length());
            else if (!base.isEmpty()) text = base + "\n" + text;
        }
        view.setContentDescription(text);
    }

    private static void write(View view, CharSequence text) {
        if (view instanceof TextContentView) ((TextContentView) view).setTextWithVisibility(text);
        else if (view instanceof TextView) ((TextView) view).setText(text, TextView.BufferType.SPANNABLE);
        else if (view instanceof QuoteView) {
            QuoteView quote = (QuoteView) view;
            quote.O = text;
            quote.T = null;
            quote.requestLayout();
            quote.invalidate();
        }
    }

    /** Called after QuoteView finishes binding its own quoted model and formatted body. */
    public static void bindQuote(QuoteView view) {
        try {
            Binding previous = (Binding) view.getTag(TAG);
            if (view.a == null || view.a.a == null || view.O == null || view.P != null) {
                if (previous != null && previous.scheduled != null) MAIN.removeCallbacks(previous.scheduled);
                view.setTag(TAG, null);
                return;
            }
            // The native binder may skip formatting an unchanged model. Do not translate our header again.
            if (previous != null && matchesQuote(view, previous) && view.O == previous.rendered) {
                if (enabled(view.getContext())) schedule(previous);
                return;
            }
            CharSequence original = view.O;
            capture(view, view.a, false);
            Binding binding = (Binding) view.getTag(TAG);
            if (binding != null) binding.quoteDescription = view.getContentDescription();
            bindView(view, original);
        } catch (Throwable ignored) { }
    }

    private static ClickableSpan quoteAction(QuoteView view, MotionEvent event) {
        StaticLayout layout = view.T;
        if (layout == null || !(layout.getText() instanceof Spanned)) return null;
        float x = event.getX() - view.t0, y = event.getY() - view.V0;
        if (y < 0 || y >= layout.getHeight() || x < 0 || x >= layout.getWidth()) return null;
        int line = layout.getLineForVertical((int) y);
        if (line >= view.n1 || x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null;
        int offset = layout.getOffsetForHorizontal(line, x);
        Spanned text = (Spanned) layout.getText();
        for (ClickableSpan span : text.getSpans(offset, offset, ClickableSpan.class)) {
            if ((span instanceof ToggleSpan || span instanceof RetrySpan) && offset >= text.getSpanStart(span) && offset < text.getSpanEnd(span)) return span;
        }
        return null;
    }

    /** Consume only translation action taps; normal quote/media taps keep their native behavior. */
    public static boolean quoteTouch(QuoteView view, MotionEvent event) {
        try {
            Binding binding = (Binding) view.getTag(TAG);
            if (binding == null || !matchesQuote(view, binding) || !enabled(view.getContext())) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                binding.pressed = quoteAction(view, event);
                binding.pressedRevision = binding.revision;
                binding.downX = event.getX(); binding.downY = event.getY();
                return binding.pressed != null;
            }
            ClickableSpan pressed = binding.pressed;
            if (pressed == null) return false;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                binding.pressed = null;
                if (action == MotionEvent.ACTION_UP && binding.pressedRevision == binding.revision && quoteAction(view, event) == pressed) pressed.onClick(view);
            } else if (action == MotionEvent.ACTION_MOVE) {
                float slop = android.view.ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
                if (Math.abs(event.getX() - binding.downX) > slop || Math.abs(event.getY() - binding.downY) > slop) binding.pressed = null;
            }
            return true;
        } catch (Throwable ignored) { return false; }
    }

    private static final class RetrySpan extends com.twitter.ui.view.a {
        private final Binding binding;
        RetrySpan(Binding binding) { super(0); this.binding = binding; }
        @Override public void onClick(View view) {
            try {
                if (view.getTag(TAG) != binding || !enabled(view.getContext())) return;
                FAILURES.remove(binding.key);
                FAILURE_MESSAGES.remove(binding.key);
                request(binding); // Global service cooldowns still apply.
            } catch (Throwable ignored) { }
        }
    }

    private static final class ToggleSpan extends com.twitter.ui.view.a {
        private final Binding binding;
        ToggleSpan(Binding binding) { super(0); this.binding = binding; }
        @Override public void onClick(View view) {
            try {
                if (view.getTag(TAG) != binding) return;
                binding.originalShown = !binding.originalShown;
                render(binding, null);
            } catch (Throwable ignored) { }
        }
    }

    /** Returned to Piko's own preference builder so its normal search index sees it. */
    public static Preference preference(PreferenceGroup group) {
        final Context context = group.getContext();
        SwitchPreference toggle = new SwitchPreference(context);
        toggle.setKey("x_auto_translate_posts_it");
        toggle.setTitle("Traduzione automatica in italiano");
        toggle.setSummary("Traduce il testo dei post tramite Google online, usando l’interfaccia web come TWP. Il testo viene inviato a Google. Tocca Mostra originale per rileggere il post.");
        toggle.setPersistent(false);
        toggle.setChecked(enabled(context));
        toggle.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override public boolean onPreferenceChange(Preference preference, Object value) {
                final boolean on = Boolean.TRUE.equals(value);
                context.getSharedPreferences(PREFS, 0).edit().putBoolean("enabled", on).apply();
                for (View view : new ArrayList<>(VIEWS.keySet())) {
                    Binding binding = (Binding) view.getTag(TAG);
                    if (binding == null || binding.original == null || !matchesQuote(view, binding)) continue;
                    if (binding.scheduled != null) { MAIN.removeCallbacks(binding.scheduled); binding.scheduled = null; }
                    if (on) bindView(view, binding.original);
                    else {
                        binding.revision++;
                        write(view, binding.original);
                        describeView(view, binding.original);
                    }
                }
                if (!on) {
                    for (String key : new ArrayList<>(RETRY_TASKS.keySet())) cancelCycle(key, null);
                }
                return true;
            }
        });
        return toggle;
    }
}
