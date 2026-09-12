package app.morphe.extension.newx.featureswitches;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.newx.settings.NewXSettingsActivity;
import app.morphe.extension.newx.settings.NewXSettingsUi;
import app.morphe.extension.newx.ui.ButtonView;
import app.morphe.extension.newx.ui.DialogView;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.newx.settings.NewXCustomScreenFragment;

@SuppressWarnings("deprecation")
public final class FeatureSwitchFragment extends NewXCustomScreenFragment implements FeatureSwitchAdapter.Listener {
    private final FeatureSwitchStore store = FeatureSwitchStore.shared();
    private FeatureSwitchAdapter adapter;
    private EditText search;
    private TextView emptyState;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        Context context = requireContext();
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(NewXSettingsUi.backgroundColor(context));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, matchParent());

        search = NewXSettingsUi.textInput(
                context,
                StringRef.str("piko_newx_feature_switch_search_hint"),
                InputType.TYPE_CLASS_TEXT
        );
        search.setSingleLine(true);
        content.addView(search, new LinearLayout.LayoutParams(-1, -2));
        content.addView(NewXSettingsUi.divider(context));

        FrameLayout listContainer = new FrameLayout(context);
        content.addView(listContainer, new LinearLayout.LayoutParams(-1, 0, 1f));

        ListView list = new ListView(context);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setPadding(0, Theme.dpToPx(context, 4f), 0, Theme.dpToPx(context, 96f));
        adapter = new FeatureSwitchAdapter(context, this);
        list.setAdapter(adapter);
        listContainer.addView(list, matchParent());

        emptyState = new TextView(context);
        emptyState.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyState.setTextColor(Theme.secondaryText(context));
        emptyState.setGravity(Gravity.CENTER);
        int emptyPadding = Theme.dpToPx(context, 32f);
        emptyState.setPadding(emptyPadding, emptyPadding, emptyPadding, emptyPadding);
        listContainer.addView(emptyState, matchParent());

        View addButton = NewXSettingsUi.floatingActionButton(
                context,
                StringRef.str("piko_newx_feature_switch_add"),
                ignored -> showAddDialog()
        );
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(
                Theme.dpToPx(context, 56f),
                Theme.dpToPx(context, 56f)
        );
        addParams.gravity = Gravity.BOTTOM | Gravity.END;
        addParams.setMargins(
                0,
                0,
                Theme.dpToPx(context, 20f),
                Theme.dpToPx(context, 20f)
        );
        root.addView(addButton, addParams);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                refresh();
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        refresh();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if (activity instanceof NewXSettingsActivity settingsActivity) {
            settingsActivity.setPageTitle(StringRef.str("piko_newx_feature_switches_title"));
        }
        refresh();
    }

    @Override
    public void edit(FeatureSwitchStore.Entry entry) {
        showValueEditor(
                entry.getKey(),
                entry.getType(),
                entry.getEffectiveValue(),
                entry.isOverridden()
        );
    }

    private void showAddDialog() {
        Context context = requireContext();
        LinearLayout form = dialogForm(context);

        EditText key = NewXSettingsUi.textInput(
                context,
                StringRef.str("piko_newx_feature_switch_key_hint"),
                InputType.TYPE_CLASS_TEXT
        );
        key.setSingleLine(true);
        form.addView(key, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout typeRow = new LinearLayout(context);
        typeRow.setGravity(Gravity.CENTER_VERTICAL);
        typeRow.setMinimumHeight(Theme.dpToPx(context, 56f));
        typeRow.setPadding(
                Theme.dpToPx(context, 16f),
                0,
                Theme.dpToPx(context, 8f),
                0
        );

        TextView typeTitle = NewXSettingsUi.titleText(context);
        typeTitle.setText(StringRef.str("piko_newx_feature_switch_value_type"));
        typeTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        typeTitle.setSingleLine(true);
        typeRow.addView(typeTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        Spinner type = new Spinner(context, Spinner.MODE_DROPDOWN);
        String[] typeLabels = Arrays.stream(FeatureSwitchStore.ValueType.values())
                .map(this::typeLabel)
                .toArray(String[]::new);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<String>(
                context,
                android.R.layout.simple_spinner_item,
                typeLabels
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getView(position, convertView, parent);
                item.setTextColor(Theme.primaryText(context));
                item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                item.setTypeface(app.morphe.extension.newx.misc.UpdateFont.customTypefaceOr(item.getTypeface()));
                item.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                item.setPadding(0, 0, Theme.dpToPx(context, 8f), 0);
                return item;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getDropDownView(position, convertView, parent);
                item.setTypeface(app.morphe.extension.newx.misc.UpdateFont.customTypefaceOr(item.getTypeface()));
                return item;
            }
        };
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        type.setAdapter(typeAdapter);
        type.setMinimumHeight(Theme.dpToPx(context, 56f));
        typeRow.addView(type, new LinearLayout.LayoutParams(-2, -1));
        form.addView(typeRow, new LinearLayout.LayoutParams(-1, -2));

        TextView validation = validationText(context);
        form.addView(validation, new LinearLayout.LayoutParams(-1, -2));

        DialogView dialog = new DialogView(context)
                .setTitle(StringRef.str("piko_newx_feature_switch_add_title"))
                .setBodyView(form);
        dialog.getDialog().setCanceledOnTouchOutside(true);

        ButtonView next = new ButtonView(
                context,
                ButtonView.ButtonStyle.TEXT,
                StringRef.str("piko_newx_feature_switch_next")
        );
        next.setOnClickListener(ignored -> {
            String featureKey = key.getText().toString().trim();
            if (featureKey.isEmpty()) {
                showValidation(validation, "piko_newx_feature_switch_invalid_key");
                return;
            }
            if (store.hasEntry(featureKey)) {
                showValidation(validation, "piko_newx_feature_switch_duplicate_key");
                return;
            }
            FeatureSwitchStore.ValueType valueType =
                    FeatureSwitchStore.ValueType.values()[type.getSelectedItemPosition()];
            dialog.dismiss();
            showValueEditor(featureKey, valueType, defaultValue(valueType), false);
        });
        dialog.addButton(next).show();
    }

    private void showValueEditor(
            String featureKey,
            FeatureSwitchStore.ValueType featureType,
            @Nullable Object effectiveValue,
            boolean overridden
    ) {
        Context context = requireContext();
        LinearLayout form = dialogForm(context);

        NewXSettingsUi.SwitchRow booleanValue = null;
        EditText textValue = null;
        if (featureType == FeatureSwitchStore.ValueType.BOOLEAN) {
            booleanValue = NewXSettingsUi.switchRow(
                    context,
                    StringRef.str("piko_newx_feature_switch_boolean_value"),
                    null,
                    Boolean.TRUE.equals(effectiveValue)
            );
            booleanValue.setBackgroundColor(Color.TRANSPARENT);
            form.addView(booleanValue, new LinearLayout.LayoutParams(-1, -2));
        } else {
            textValue = NewXSettingsUi.textInput(
                    context,
                    editorHint(featureType),
                    inputType(featureType)
            );
            textValue.setText(editorValue(effectiveValue));
            if (featureType == FeatureSwitchStore.ValueType.STRING_LIST) {
                textValue.setSingleLine(false);
                textValue.setMinLines(3);
                textValue.setGravity(Gravity.TOP | Gravity.START);
            }
            form.addView(textValue, new LinearLayout.LayoutParams(-1, -2));
        }

        TextView validation = validationText(context);
        form.addView(validation, new LinearLayout.LayoutParams(-1, -2));

        DialogView dialog = new DialogView(context)
                .setTitle(StringRef.str("piko_newx_feature_switch_edit_title"))
                .setSubtitle(featureKey)
                .setBodyView(form);
        ButtonView remove = null;
        if (overridden) {
            remove = new ButtonView(
                    context,
                    ButtonView.ButtonStyle.TEXT,
                    StringRef.str("piko_newx_feature_switch_remove")
            );
            remove.setTextColor(Color.rgb(244, 33, 46));
            remove.setOnClickListener(ignored -> {
                store.removeOverride(featureKey);
                refresh();
                dialog.dismiss();
            });
        }

        dialog.getDialog().setCanceledOnTouchOutside(true);

        ButtonView save = new ButtonView(
                context,
                ButtonView.ButtonStyle.TEXT,
                StringRef.str("piko_newx_feature_switch_save")
        );
        NewXSettingsUi.SwitchRow finalBooleanValue = booleanValue;
        EditText finalTextValue = textValue;
        save.setOnClickListener(ignored -> saveValue(
                dialog,
                featureKey,
                featureType,
                finalBooleanValue,
                finalTextValue,
                validation
        ));

        if (remove != null) dialog.addButton(remove);
        dialog.addButton(save).show();
    }

    private LinearLayout dialogForm(Context context) {
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(
                Theme.dpToPx(context, 24f),
                0,
                Theme.dpToPx(context, 24f),
                0
        );
        return form;
    }

    private TextView validationText(Context context) {
        TextView validation = NewXSettingsUi.summaryText(context);
        validation.setTextColor(Color.rgb(244, 33, 46));
        validation.setPadding(0, Theme.dpToPx(context, 8f), 0, 0);
        validation.setVisibility(View.GONE);
        return validation;
    }

    private void showValidation(TextView validation, String resourceName) {
        validation.setText(StringRef.str(resourceName));
        validation.setVisibility(View.VISIBLE);
    }

    private void saveValue(
            DialogView dialog,
            String featureKey,
            FeatureSwitchStore.ValueType featureType,
            @Nullable NewXSettingsUi.SwitchRow booleanValue,
            @Nullable EditText textValue,
            TextView validation
    ) {
        try {
            Object value = parseValue(featureType, booleanValue, textValue);
            store.setOverride(featureKey, featureType, value);
            refresh();
            dialog.dismiss();
        } catch (IllegalArgumentException exception) {
            showValidation(validation, "piko_newx_feature_switch_invalid_value");
        }
    }

    private Object parseValue(
            FeatureSwitchStore.ValueType type,
            @Nullable NewXSettingsUi.SwitchRow booleanValue,
            @Nullable EditText textValue
    ) {
        if (type == FeatureSwitchStore.ValueType.BOOLEAN) {
            if (booleanValue == null) throw new IllegalArgumentException("Missing boolean input");
            return booleanValue.isChecked();
        }
        if (textValue == null) throw new IllegalArgumentException("Missing text input");
        String value = textValue.getText().toString();
        return switch (type) {
            case BOOLEAN -> throw new IllegalArgumentException("Unexpected boolean input");
            case INT -> Integer.parseInt(value.trim());
            case LONG -> Long.parseLong(value.trim());
            case FLOAT -> Float.parseFloat(value.trim());
            case DOUBLE -> Double.parseDouble(value.trim());
            case STRING -> value;
            case STRING_LIST -> parseList(value);
        };
    }

    private List<String> parseList(String value) {
        if (value.isEmpty()) return Collections.emptyList();
        List<String> values = new ArrayList<>();
        Arrays.stream(value.split("\\R", -1))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .forEach(values::add);
        return values;
    }

    private String typeLabel(FeatureSwitchStore.ValueType type) {
        return StringRef.str(switch (type) {
            case BOOLEAN -> "piko_newx_feature_switch_type_boolean";
            case INT -> "piko_newx_feature_switch_type_int";
            case LONG -> "piko_newx_feature_switch_type_long";
            case FLOAT -> "piko_newx_feature_switch_type_float";
            case DOUBLE -> "piko_newx_feature_switch_type_double";
            case STRING -> "piko_newx_feature_switch_type_string";
            case STRING_LIST -> "piko_newx_feature_switch_type_list";
        }).toString();
    }

    private Object defaultValue(FeatureSwitchStore.ValueType type) {
        return switch (type) {
            case BOOLEAN -> false;
            case INT -> 0;
            case LONG -> 0L;
            case FLOAT -> 0f;
            case DOUBLE -> 0d;
            case STRING -> "";
            case STRING_LIST -> Collections.emptyList();
        };
    }

    private CharSequence editorHint(FeatureSwitchStore.ValueType type) {
        return StringRef.str(switch (type) {
            case INT, LONG, FLOAT, DOUBLE -> "piko_newx_feature_switch_number_value";
            case STRING -> "piko_newx_feature_switch_string_value";
            case STRING_LIST -> "piko_newx_feature_switch_list_value";
            case BOOLEAN -> "piko_newx_feature_switch_boolean_value";
        });
    }

    private String editorValue(@Nullable Object value) {
        if (value == null) return "";
        if (!(value instanceof List<?> list)) return String.valueOf(value);
        return String.join("\n", list.stream().map(String::valueOf).toList());
    }

    private int inputType(FeatureSwitchStore.ValueType type) {
        return switch (type) {
            case INT, LONG -> InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED;
            case FLOAT, DOUBLE -> InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_SIGNED
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL;
            case STRING -> InputType.TYPE_CLASS_TEXT;
            case STRING_LIST -> InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            case BOOLEAN -> InputType.TYPE_NULL;
        };
    }

    private void refresh() {
        if (adapter == null || emptyState == null) return;
        String query = search == null ? "" : search.getText().toString();
        List<FeatureSwitchStore.Entry> entries = store.snapshot(query);
        adapter.submit(entries, query);
        emptyState.setText(StringRef.str(query.trim().isEmpty()
                ? "piko_newx_feature_switch_empty"
                : "piko_newx_feature_switch_no_results"));
        emptyState.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private Context requireContext() {
        Context context = getActivity();
        if (context == null) throw new IllegalStateException("Feature switch activity is missing");
        return context;
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(-1, -1);
    }
}
