package app.morphe.extension.newx.translation;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import app.morphe.extension.crimera.settings.SettingsRegistry;
import app.morphe.extension.crimera.settings.SettingsUi;
import app.morphe.extension.crimera.theme.PikoTheme;
import app.morphe.extension.crimera.ui.ButtonView;
import app.morphe.extension.crimera.ui.TrayView;
import app.morphe.extension.newx.misc.NewXPostOptionActions;
import app.morphe.extension.newx.misc.NewXPostOptions;
import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.shared.Utils;

/**
 * Runs the selected translation provider for NewX's "Translate" post-menu option and for NewX's
 * inline translate button. The post menu keeps the original text visible while the request runs,
 * then replaces it in a bottom sheet that can copy the result. The inline path publishes into
 * NewX's Grok UI state and uses Google when the selected provider cannot translate arbitrary text.
 */
public final class PostTranslator {
    public static final String MENU_SETTING = "newx.translation.menu_option";
    public static final String FORCE_BUTTON_SETTING = "newx.translation.force_button";
    public static final String INLINE_SETTING = "newx.translation.inline";
    private static final String OPTION_NAME = NewXPostOptionActions.TRANSLATE_ACTION;
    private static final String OPTION_LABEL = "Translate";
    private static final String URT_POST_CLASS = "com.x.models.timelines.items.UrtTimelinePost";
    private static final int MAX_POLL_CHOICES = 4;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final AtomicLong NEXT_GENERATION = new AtomicLong();
    private static final Map<Object, Long> LATEST_GENERATION =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            2,
            2,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16),
            new TranslationThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy()
    );

    static {
        EXECUTOR.allowCoreThreadTimeOut(true);
    }

    private PostTranslator() {
    }

    public static List<?> addOption(List<?> groups) {
        return NewXPostOptions.addOption(groups, OPTION_NAME, menuEnabled());
    }

    public static String labelFor(Object action, Object originalLabel) {
        if (isTranslateAction(action)) return OPTION_LABEL;
        return originalLabel instanceof String ? (String) originalLabel : null;
    }

    public static boolean usesIcon(Object action) {
        return isTranslateAction(action);
    }

    public static boolean handleOptionAction(Object presenter, Object action) {
        if (!isTranslateAction(action)) return false;

        try {
            NewXUtils.PresenterData presenterData = NewXUtils.findPresenterData(presenter, URT_POST_CLASS);
            Context context = presenterData.getContext();
            Object post = presenterData.getValue();
            if (context == null || post == null) {
                Utils.showToastShort("Could not find the selected post");
                return true;
            }

            Activity activity = NewXUtils.findUsableActivity(context);
            if (activity == null) {
                Utils.showToastShort("Could not find the active screen");
                return true;
            }

            String text = visibleText(readCanonicalPostOrNull(post), readPostTextOrNull(post));
            if (text == null || text.trim().isEmpty()) {
                Utils.showToastShort("This post has no text to translate");
                return true;
            }
            String postId = NewXUtils.identifierToString(readPostIdOrNull(post));

            NewXUtils.runOnUiThread(() -> showSheet(activity, text, postId));
            return true;
        } catch (IllegalAccessException | RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to open NewX translation", exception);
            Utils.showToastShort("Could not translate this post");
            return true;
        }
    }

    /** Returns true when this request was accepted in place of NewX's original Grok request. */
    public static boolean translate(Object presenter, Object callback) {
        if (!inlineEnabled()) return false;
        // A malformed caller should not silently route the action back to Grok.
        if (presenter == null || callback == null) return true;
        long generation = NEXT_GENERATION.incrementAndGet();
        LATEST_GENERATION.put(presenter, generation);
        try {
            publishLoading(callback);

            String text = visibleText(readPostModel(presenter), readText(presenter));
            String sourceLanguage = normalizeLanguage(readSourceLanguage(presenter));
            List<String> pollChoices = snapshotPollChoices(readPollChoices(presenter));
            if ((text == null || text.trim().isEmpty()) && pollChoices.isEmpty()) {
                throw new IOException("No translatable post text or poll choices");
            }

            final TranslationProvider provider = inlineTextProvider();
            final String postText = text == null ? "" : text;
            final String target = resolveTargetLanguage();
            final String source = sourceLanguage;
            final List<String> choices = pollChoices;
            EXECUTOR.execute(() -> translateRequest(
                    presenter,
                    callback,
                    generation,
                    provider,
                    postText,
                    choices,
                    target,
                    source
            ));
        } catch (Exception exception) {
            finishWithError(presenter, callback, generation, exception);
        }
        return true;
    }

    /** Keeps NewX's native eligibility result unless the force-show preference is enabled. */
    public static boolean shouldShowButton(boolean original) {
        return original || forceButtonEnabled();
    }

    /** Keeps NewX's Grok UI enabled when its original flag is set or a forced or inline translation is active. */
    public static boolean shouldEnableFeature(boolean original) {
        return original || forceButtonEnabled() || inlineEnabled();
    }

    /** Keeps NewX's poll translation enabled when its original flag is set or inline translation is active. */
    public static boolean shouldEnablePollTranslation(boolean original) {
        return original || inlineEnabled();
    }

    /** Hides settings that only control NewX's remote Grok post translation while inline translation is active. */
    public static boolean useOriginalSettings(boolean original) {
        return original && !inlineEnabled();
    }

    /** Resolves ordered poll labels from NewX's stable card-binding map through patch-time accessors. */
    public static List<String> pollChoicesFromBindings(Map<?, ?> bindings) {
        if (bindings == null) return Collections.emptyList();

        String firstLabel = bindings.containsKey("choice1_label")
                ? readBindingString(bindings.get("choice1_label"))
                : null;
        if (firstLabel == null || firstLabel.trim().isEmpty()) {
            if (hasPollLabelAfter(bindings, 1)) {
                throw new IllegalArgumentException("NewX poll choices do not start with choice1_label");
            }
            return Collections.emptyList();
        }

        List<String> choices = new ArrayList<>(MAX_POLL_CHOICES);
        choices.add(firstLabel);
        boolean missingChoice = false;
        for (int index = 2; index <= MAX_POLL_CHOICES; index++) {
            String key = "choice" + index + "_label";
            if (!bindings.containsKey(key)) {
                missingChoice = true;
                continue;
            }
            if (missingChoice) {
                throw new IllegalArgumentException("NewX poll choice labels are not contiguous");
            }
            String label = readBindingString(bindings.get(key));
            if (label == null || label.trim().isEmpty()) {
                throw new IllegalArgumentException("NewX poll contains an empty choice label");
            }
            choices.add(label);
        }
        if (bindings.containsKey("choice5_label")) {
            throw new IllegalArgumentException("NewX poll has more than four choice labels");
        }
        if (choices.size() < 2) {
            throw new IllegalArgumentException("NewX poll has fewer than two choice labels");
        }
        return Collections.unmodifiableList(choices);
    }

    private static void translateRequest(
            Object presenter,
            Object callback,
            long generation,
            TranslationProvider provider,
            String text,
            List<String> pollChoices,
            String targetLanguage,
            String sourceLanguage
    ) {
        try {
            TranslationResult postResult = text.trim().isEmpty()
                    ? new TranslationResult("", null)
                    : provider.translateText(text, null, targetLanguage);
            String translatedText = postResult.text;
            List<String> translatedPolls = new ArrayList<>(pollChoices.size());
            String detectedSource = postResult.sourceLanguage;
            for (String choice : pollChoices) {
                String sourceHint = sourceLanguage == null ? detectedSource : sourceLanguage;
                TranslationResult pollResult = provider.translateText(choice, sourceHint, targetLanguage);
                translatedPolls.add(pollResult.text);
                if (detectedSource == null) detectedSource = pollResult.sourceLanguage;
            }

            String localizedSource = localizeSourceLanguage(
                    sourceLanguage == null ? detectedSource : sourceLanguage,
                    targetLanguage,
                    provider.displayName()
            );
            postIfCurrent(presenter, generation, () -> {
                publishCompleted(callback, translatedText, translatedPolls, localizedSource);
            });
        } catch (Exception exception) {
            finishWithError(presenter, callback, generation, exception);
        }
    }

    private static List<String> snapshotPollChoices(List<String> choices) {
        if (choices == null || choices.isEmpty()) return Collections.emptyList();
        if (choices.size() > MAX_POLL_CHOICES) {
            throw new IllegalArgumentException("NewX poll has more than four choices");
        }
        List<String> snapshot = new ArrayList<>(choices.size());
        for (String choice : choices) {
            if (choice == null || choice.trim().isEmpty()) {
                throw new IllegalArgumentException("NewX poll contains an empty choice");
            }
            snapshot.add(choice);
        }
        return Collections.unmodifiableList(snapshot);
    }

    private static boolean hasPollLabelAfter(Map<?, ?> bindings, int index) {
        for (int choice = index + 1; choice <= MAX_POLL_CHOICES; choice++) {
            if (bindings.containsKey("choice" + choice + "_label")) return true;
        }
        return bindings.containsKey("choice5_label");
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) return null;
        String normalized = language.trim().replace('_', '-');
        Locale locale = Locale.forLanguageTag(normalized);
        String languageCode = locale.getLanguage();
        if (languageCode.isEmpty() || "und".equals(languageCode)) return null;
        String languageTag = locale.toLanguageTag();
        return "und".equals(languageTag) ? null : languageTag;
    }

    private static String localizeSourceLanguage(
            String sourceLanguage,
            String targetLanguage,
            String providerName
    ) {
        String language = sourceLanguage == null ? targetLanguage : sourceLanguage;
        Locale source = Locale.forLanguageTag(language);
        Locale display = Locale.getDefault();
        String localized = source.getDisplayLanguage(display);
        if (localized == null || localized.isEmpty()) localized = language;
        if (localized == null || localized.isEmpty()) return providerName;
        return localized + " · " + providerName;
    }

    private static void finishWithError(
            Object presenter,
            Object callback,
            long generation,
            Exception exception
    ) {
        NewXLogger.printException(() -> "NewX translation request failed", exception);
        postIfCurrent(presenter, generation, () -> publishError(callback));
    }

    private static void postIfCurrent(Object presenter, long generation, Runnable action) {
        boolean posted = MAIN_HANDLER.post(() -> {
            Long current = LATEST_GENERATION.get(presenter);
            if (current == null || current != generation) return;
            try {
                action.run();
            } catch (RuntimeException exception) {
                NewXLogger.printException(() -> "Failed to publish NewX translation state", exception);
            } finally {
                clearGeneration(presenter, generation);
            }
        });
        if (!posted) clearGeneration(presenter, generation);
    }

    private static void clearGeneration(Object presenter, long generation) {
        Long current = LATEST_GENERATION.get(presenter);
        if (current != null && current == generation) LATEST_GENERATION.remove(presenter);
    }

    /** Uses the selected provider when it can translate arbitrary text, otherwise Google. */
    private static TranslationProvider inlineTextProvider() {
        TranslationProvider selected = TranslatorConfig.shared().selected();
        if (selected.supportsText()) return selected;
        return googleProvider();
    }

    private static TranslationProvider googleProvider() {
        for (TranslationProvider provider : TranslatorConfig.shared().providers()) {
            if (TranslatorConfig.GOOGLE_ID.equals(provider.id())) return provider;
        }
        throw new IllegalStateException("Google translation provider is not registered");
    }

    private static String resolveTargetLanguage() {
        String target = normalizeLanguage(TranslatorConfig.shared().resolvedTargetLanguage());
        return target == null ? Locale.getDefault().toLanguageTag() : target;
    }

    private static boolean menuEnabled() {
        return SettingsRegistry.getBooleanOrDefault(MENU_SETTING, true);
    }

    private static boolean forceButtonEnabled() {
        return SettingsRegistry.getBooleanOrDefault(FORCE_BUTTON_SETTING, false);
    }

    private static boolean inlineEnabled() {
        return SettingsRegistry.getBooleanOrDefault(INLINE_SETTING, false);
    }

    private static String subtitleFor(String providerName, String sourceLanguage, String targetLanguage) {
        if (sourceLanguage == null) return "Translated by " + providerName;
        String source = Locale.forLanguageTag(sourceLanguage).getLanguage();
        String target = Locale.forLanguageTag(targetLanguage).getLanguage();
        if (source.equals(target)) {
            return "Already in " + displayLanguage(targetLanguage) + " · " + providerName;
        }
        return displayLanguage(sourceLanguage) + " → " + displayLanguage(targetLanguage) + " · " + providerName;
    }

    private static String displayLanguage(String languageTag) {
        String displayName = Locale.forLanguageTag(languageTag).getDisplayLanguage(Locale.getDefault());
        return displayName == null || displayName.isEmpty() ? languageTag : displayName;
    }

    private static void showSheet(Activity activity, String text, String postId) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        TranslationProvider provider = TranslatorConfig.shared().selected();
        String providerName = provider.displayName();
        String target = resolveTargetLanguage();

        TrayView sheet = new TrayView(activity);
        sheet.setTitle("Translation");
        sheet.setSubtitle("Translating…");

        // The original text stays visible until the provider answers.
        TextView body = SettingsUi.titleText(activity);
        body.setText(text);
        body.setTextIsSelectable(true);
        // Align with the tray header's 24dp inset.
        int inset = PikoTheme.dpToPx(activity, 24f);
        body.setPadding(inset, PikoTheme.dpToPx(activity, 12f), inset, 0);
        sheet.setScrollableBodyView(body);

        // Only read and written on the UI thread.
        String[] translatedText = new String[1];
        ButtonView copy = SettingsUi.sheetButton(activity, "Copy translation");
        copy.setEnabled(false);
        copy.setOnClickListener(ignored -> {
            String value = translatedText[0];
            if (value == null) return;
            ClipboardManager clipboard =
                    (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return;
            clipboard.setPrimaryClip(ClipData.newPlainText("Translation", value));
            Utils.showToastShort("Copied translation");
        });
        sheet.addButton(copy);
        sheet.show();

        try {
            EXECUTOR.execute(() -> {
                try {
                    TranslationResult result = provider.supportsText()
                            ? provider.translateText(text, null, target)
                            : provider.translatePost(postId, text, target);
                    NewXUtils.runOnUiThread(() -> {
                        if (!sheet.isShowing()) return;
                        translatedText[0] = result.text;
                        body.setText(result.text);
                        sheet.setSubtitle(subtitleFor(providerName, result.sourceLanguage, target));
                        copy.setEnabled(true);
                    });
                } catch (Exception exception) {
                    showTranslationFailure(sheet, exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            showTranslationFailure(sheet, exception);
        }
    }

    private static void showTranslationFailure(TrayView sheet, Exception exception) {
        NewXLogger.printException(() -> "NewX translation request failed", exception);
        NewXUtils.runOnUiThread(() -> {
            if (sheet.isShowing()) {
                sheet.setSubtitle("Could not translate this post. Check your connection and try again.");
            }
        });
    }

    private static boolean isTranslateAction(Object action) {
        return NewXPostOptions.isAction(action, OPTION_NAME);
    }

    private static String readPostTextOrNull(Object post) {
        try {
            return readPostText(post);
        } catch (ClassCastException exception) {
            return null;
        }
    }

    private static Object readCanonicalPostOrNull(Object post) {
        try {
            return readCanonicalPost(post);
        } catch (ClassCastException exception) {
            return null;
        }
    }

    /**
     * The part of the post text X renders: the display range (code points, as X's own renderer
     * applies it) drops leading reply mentions and trailing media/quote links, and t.co links are
     * shown as their display URLs. Falls back to the raw text when the model cannot be read.
     */
    static String visibleText(Object post, String text) {
        if (post == null || text == null || text.isEmpty()) return text;
        try {
            String visible = text;
            Object range = readDisplayTextRange(post);
            if (range != null) {
                int count = text.codePointCount(0, text.length());
                int start = Math.max(0, Math.min(readRangeStart(range), count));
                int end = Math.max(start, Math.min(readRangeEnd(range), count));
                if (end > start) {
                    visible = text.substring(
                            text.offsetByCodePoints(0, start),
                            text.offsetByCodePoints(0, end)
                    );
                }
            }
            Object entityList = readEntityList(post);
            List<?> urls = entityList == null ? null : readUrlEntities(entityList);
            if (urls != null) {
                for (Object entity : urls) {
                    String url = readUrlEntityUrl(entity);
                    String displayUrl = readUrlEntityDisplayUrl(entity);
                    if (url != null && !url.isEmpty() && displayUrl != null && !displayUrl.isEmpty()) {
                        visible = visible.replace(url, displayUrl);
                    }
                }
            }
            return visible.trim();
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to read the visible NewX post text", exception);
            return text;
        }
    }

    private static Object readPostIdOrNull(Object post) {
        try {
            return readPostId(post);
        } catch (ClassCastException exception) {
            return null;
        }
    }

    // Replaced at patch time with a typed read of the timeline post's text getter.
    private static String readPostText(Object post) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    // Replaced at patch time with a typed read of the timeline post's id getter.
    private static Object readPostId(Object post) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    // Display-text bridges, replaced at patch time with typed post-model reads.
    private static Object readCanonicalPost(Object timelinePost) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static Object readPostModel(Object presenter) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static Object readDisplayTextRange(Object post) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static int readRangeStart(Object range) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static int readRangeEnd(Object range) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static Object readEntityList(Object post) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static List<?> readUrlEntities(Object entityList) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static String readUrlEntityUrl(Object entity) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static String readUrlEntityDisplayUrl(Object entity) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static String readText(Object presenter) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static String readSourceLanguage(Object presenter) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static List<String> readPollChoices(Object presenter) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static String readBindingString(Object value) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static void publishLoading(Object callback) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static void publishCompleted(
            Object callback,
            String translatedText,
            List<String> translatedPolls,
            String localizedSourceLanguage
    ) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static void publishError(Object callback) {
        throw new IllegalStateException("Unpatched translation bridge");
    }

    private static final class TranslationThreadFactory implements ThreadFactory {
        private final AtomicLong nextId = new AtomicLong();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Piko-Translate-" + nextId.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
