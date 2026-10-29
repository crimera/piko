package app.morphe.extension.newx.translation;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.EnumMap;
import java.util.Map;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.newx.settings.NewXSettingsActivity;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.crimera.settings.CustomScreenFragment;
import app.morphe.extension.crimera.settings.SettingsUi;
import app.morphe.extension.crimera.ui.ButtonView;
import app.morphe.extension.crimera.ui.ChoiceRow;
import app.morphe.extension.crimera.ui.TrayView;

/** Settings screen that picks the post translator and the language it translates to. */
@SuppressWarnings("deprecation")
public final class TranslatorFragment extends CustomScreenFragment {
    private final TranslatorConfig config = TranslatorConfig.shared();
    private LinearLayout providerRows;
    private TextView languageValue;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        Context context = requireContext();
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(SettingsUi.backgroundColor(context));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Theme.dpToPx(context, 24f));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, matchParent());

        content.addView(sectionHeader(context, "Translator"), new LinearLayout.LayoutParams(-1, -2));
        providerRows = new LinearLayout(context);
        providerRows.setOrientation(LinearLayout.VERTICAL);
        content.addView(providerRows, new LinearLayout.LayoutParams(-1, -2));

        ButtonView addButton = SettingsUi.dialogButton(context, "Add custom translator");
        addButton.setOnClickListener(ignored -> showProviderTray(null));
        content.addView(addButton, new LinearLayout.LayoutParams(-1, Theme.dpToPx(context, 48f)));

        content.addView(SettingsUi.divider(context));
        content.addView(sectionHeader(context, "Translate to"), new LinearLayout.LayoutParams(-1, -2));
        content.addView(languageRow(context), new LinearLayout.LayoutParams(-1, -2));

        refresh();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if (activity instanceof NewXSettingsActivity settingsActivity) {
            settingsActivity.setPageTitle("Translator");
        }
        refresh();
    }

    private void refresh() {
        if (providerRows == null || languageValue == null) return;
        Context context = requireContext();
        providerRows.removeAllViews();
        String selectedId = config.selectedId();
        for (TranslationProvider provider : config.providers()) {
            TranslatorConfig.CustomProvider custom = config.customProvider(provider.id());
            ChoiceRow row = SettingsUi.choiceRow(
                    context,
                    rowTitle(context, provider.displayName(), summaryFor(provider.id(), custom)),
                    provider.id().equals(selectedId),
                    false
            );
            row.setOnCheckedChangeListener(checked -> {
                config.select(provider.id());
                refresh();
            });
            // Only user-added translators can be edited or removed.
            if (custom != null) {
                row.setOnLongClickListener(ignored -> {
                    showProviderTray(custom);
                    return true;
                });
            }
            providerRows.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        languageValue.setText(languageName(config.targetLanguageSetting()));
    }

    private LinearLayout languageRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 12f),
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 16f)
        );
        SettingsUi.applyRippleBackground(row);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(ignored -> showLanguageTray());

        languageValue = SettingsUi.titleText(context);
        row.addView(languageValue, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void showLanguageTray() {
        Context context = requireContext();
        String current = config.targetLanguageSetting();
        LinearLayout options = new LinearLayout(context);
        options.setOrientation(LinearLayout.VERTICAL);

        TrayView tray = new TrayView(context)
                .setTitle("Translate to")
                .setScrollableBodyView(options);
        for (String[] choice : TranslatorConfig.targetLanguageChoices()) {
            ChoiceRow row = SettingsUi.choiceRow(context, choice[1], choice[0].equals(current), false);
            row.setOnCheckedChangeListener(checked -> {
                if (!checked) return;
                config.setTargetLanguage(choice[0]);
                tray.dismiss();
                refresh();
            });
            options.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        tray.show();
    }

    private void showProviderTray(@Nullable TranslatorConfig.CustomProvider editing) {
        Context context = requireContext();
        ProviderForm form = new ProviderForm(context, editing);
        TrayView tray = new TrayView(context)
                .setTitle(editing == null ? "Add translator" : "Edit translator")
                .setScrollableBodyView(form.body);

        ButtonView save = SettingsUi.sheetButton(context, "Save");
        save.setOnClickListener(ignored -> {
            TranslatorConfig.CustomProvider draft = form.draft();
            try {
                TranslatorConfig.CustomProvider saved = config.saveCustom(
                        draft.id,
                        draft.type,
                        draft.name,
                        draft.url,
                        draft.model,
                        draft.apiKey
                );
                if (editing == null) config.select(saved.id);
                tray.dismiss();
                refresh();
            } catch (IllegalArgumentException exception) {
                Utils.showToastShort(errorMessage(exception));
            }
        });
        tray.addButton(save);

        ButtonView test = new ButtonView(context, ButtonView.ButtonStyle.TONAL, "Test");
        test.setPill(true);
        test.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        test.setOnClickListener(ignored -> runTest(form.draft()));
        tray.addButton(test);

        if (editing != null) {
            ButtonView delete = SettingsUi.dialogButton(context, "Delete");
            delete.setTextColor(Color.rgb(244, 33, 46));
            delete.setOnClickListener(ignored -> {
                config.removeCustom(editing.id);
                tray.dismiss();
                refresh();
            });
            tray.addButton(delete);
        }
        tray.show();
    }

    /** Translates a fixed phrase with the draft settings, off the UI thread, and reports the outcome. */
    private void runTest(TranslatorConfig.CustomProvider draft) {
        Utils.runOnBackgroundThread(() -> {
            try {
                TranslationResult result = TranslatorConfig.providerFor(draft)
                        .translateText("Hola mundo", null, "en");
                Utils.runOnMainThread(() -> Utils.showToastShort("Test OK: " + result.text));
            } catch (Exception exception) {
                Utils.runOnMainThread(() -> Utils.showToastShort("Test failed: " + errorMessage(exception)));
            }
        });
    }

    private static TextView sectionHeader(Context context, CharSequence text) {
        TextView header = SettingsUi.titleText(context);
        header.setText(text);
        header.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 16f),
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 4f)
        );
        return header;
    }

    /** Provider name with its summary on a second, smaller line in the secondary color. */
    private static CharSequence rowTitle(Context context, String name, @Nullable String summary) {
        if (TextUtils.isEmpty(summary)) return name;
        SpannableStringBuilder title = new SpannableStringBuilder(name).append("\n");
        int start = title.length();
        title.append(summary);
        title.setSpan(new RelativeSizeSpan(0.875f), start, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setSpan(
                new ForegroundColorSpan(Theme.secondaryText(context)),
                start,
                title.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return title;
    }

    @Nullable
    private static String summaryFor(String id, @Nullable TranslatorConfig.CustomProvider custom) {
        if (custom != null) return customSummary(custom);
        if (TranslatorConfig.GOOGLE_ID.equals(id)) return "Free · translates post text";
        if (TranslatorConfig.FXTWITTER_ID.equals(id)) return "Free · translates the whole post by ID";
        return null;
    }

    private static String customSummary(TranslatorConfig.CustomProvider custom) {
        StringBuilder summary = new StringBuilder(custom.type.label);
        if (!TextUtils.isEmpty(custom.url)) summary.append(" · ").append(custom.url);
        if (!TextUtils.isEmpty(custom.model)) summary.append(" · ").append(custom.model);
        return summary.toString();
    }

    private static String languageName(String tag) {
        for (String[] choice : TranslatorConfig.targetLanguageChoices()) {
            if (choice[0].equals(tag)) return choice[1];
        }
        return tag;
    }

    private static String errorMessage(Exception exception) {
        return TextUtils.isEmpty(exception.getMessage())
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private static LinearLayout.LayoutParams fieldParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Theme.dpToPx(context, 12f);
        return params;
    }

    private Context requireContext() {
        Context context = getActivity();
        if (context == null) throw new IllegalStateException("Translator activity is missing");
        return context;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    /** Body of the add/edit tray: the type selector plus the fields the selected type uses. */
    private static final class ProviderForm {
        private static final String OPENAI_URL = "https://api.openai.com/v1";
        private static final String LIBRETRANSLATE_URL = "https://libretranslate.com";
        private static final String OPENAI_HINT =
                "Any OpenAI-compatible API, e.g. https://api.deepseek.com, "
                        + "https://openrouter.ai/api/v1, http://localhost:11434/v1";
        private static final String DEEPL_HINT =
                "Free keys end in :fx and use the free endpoint automatically.";

        final LinearLayout body;
        @Nullable private final String id;
        private final Map<TranslatorConfig.CustomType, ChoiceRow> typeRows =
                new EnumMap<>(TranslatorConfig.CustomType.class);
        private final EditText name;
        private final EditText url;
        private final TextView urlHint;
        private final EditText model;
        private final EditText apiKey;
        private TranslatorConfig.CustomType type;

        ProviderForm(Context context, @Nullable TranslatorConfig.CustomProvider editing) {
            id = editing == null ? null : editing.id;
            type = editing == null ? TranslatorConfig.CustomType.OPENAI : editing.type;

            body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(
                    Theme.dpToPx(context, 24f),
                    Theme.dpToPx(context, 8f),
                    Theme.dpToPx(context, 24f),
                    0
            );

            for (TranslatorConfig.CustomType option : TranslatorConfig.CustomType.values()) {
                ChoiceRow row = SettingsUi.choiceRow(context, option.label, false, false);
                row.setOnCheckedChangeListener(checked -> select(option));
                typeRows.put(option, row);
                body.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }

            name = SettingsUi.textInput(context, "Name", InputType.TYPE_CLASS_TEXT);
            name.setText(editing == null ? "" : editing.name);
            body.addView(name, fieldParams(context));

            url = SettingsUi.textInput(
                    context,
                    "URL",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
            );
            url.setText(editing == null ? "" : editing.url);
            body.addView(url, fieldParams(context));

            urlHint = SettingsUi.summaryText(context);
            body.addView(urlHint, fieldParams(context));

            model = SettingsUi.textInput(context, "Model", InputType.TYPE_CLASS_TEXT);
            model.setText(editing == null ? "" : editing.model);
            body.addView(model, fieldParams(context));

            apiKey = SettingsUi.textInput(
                    context,
                    "API key",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            );
            apiKey.setText(editing == null ? "" : editing.apiKey);
            body.addView(apiKey, fieldParams(context));

            applyType();
        }

        /** Builds the save/test input. Fields the selected type does not use are blanked. */
        TranslatorConfig.CustomProvider draft() {
            return new TranslatorConfig.CustomProvider(
                    id,
                    type,
                    name.getText().toString(),
                    type == TranslatorConfig.CustomType.DEEPL ? "" : url.getText().toString(),
                    type == TranslatorConfig.CustomType.OPENAI ? model.getText().toString() : "",
                    apiKey.getText().toString()
            );
        }

        private void select(TranslatorConfig.CustomType option) {
            type = option;
            applyType();
        }

        private void applyType() {
            for (Map.Entry<TranslatorConfig.CustomType, ChoiceRow> entry : typeRows.entrySet()) {
                entry.getValue().setChecked(entry.getKey() == type);
            }

            // Replace an empty URL or an untouched default from any type; keep a user-edited URL.
            String defaultUrl = defaultUrl(type);
            String currentUrl = url.getText().toString();
            if (defaultUrl != null && (currentUrl.isEmpty()
                    || currentUrl.equals(OPENAI_URL)
                    || currentUrl.equals(LIBRETRANSLATE_URL))) {
                url.setText(defaultUrl);
            }
            url.setVisibility(type == TranslatorConfig.CustomType.DEEPL ? View.GONE : View.VISIBLE);

            String hint = hint(type);
            urlHint.setVisibility(hint == null ? View.GONE : View.VISIBLE);
            if (hint != null) urlHint.setText(hint);

            model.setVisibility(type == TranslatorConfig.CustomType.OPENAI ? View.VISIBLE : View.GONE);
            apiKey.setHint(type == TranslatorConfig.CustomType.LIBRETRANSLATE
                    ? "API key (optional)"
                    : "API key");
        }

        @Nullable
        private static String defaultUrl(TranslatorConfig.CustomType type) {
            return switch (type) {
                case OPENAI -> OPENAI_URL;
                case LIBRETRANSLATE -> LIBRETRANSLATE_URL;
                case DEEPL -> null;
            };
        }

        @Nullable
        private static String hint(TranslatorConfig.CustomType type) {
            return switch (type) {
                case OPENAI -> OPENAI_HINT;
                case DEEPL -> DEEPL_HINT;
                case LIBRETRANSLATE -> null;
            };
        }
    }
}
