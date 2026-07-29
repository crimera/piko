package app.morphe.extension.xlite.featureswitches;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.Button;
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
import app.morphe.extension.xlite.settings.XLiteSettingsActivity;
import app.morphe.extension.xlite.settings.XLiteSettingsUi;
import app.morphe.extension.xlite.ui.Theme;

@SuppressWarnings("deprecation")
public final class FeatureSwitchFragment extends Fragment implements FeatureSwitchAdapter.Listener {
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
        root.setBackgroundColor(XLiteSettingsUi.backgroundColor(context));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, matchParent());

        search = XLiteSettingsUi.textInput(
                context,
                StringRef.str("piko_xlite_feature_switch_search_hint"),
                InputType.TYPE_CLASS_TEXT
        );
        search.setSingleLine(true);
        content.addView(search, new LinearLayout.LayoutParams(-1, -2));
        content.addView(XLiteSettingsUi.divider(context));

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

        View addButton = XLiteSettingsUi.floatingActionButton(
                context,
                StringRef.str("piko_xlite_feature_switch_add"),
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
        if (activity instanceof XLiteSettingsActivity settingsActivity) {
            settingsActivity.setPageTitle(StringRef.str("piko_xlite_feature_switches_title"));
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
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 8f),
                Theme.dpToPx(context, 24f),
                0
        );

        EditText key = XLiteSettingsUi.textInput(
                context,
                StringRef.str("piko_xlite_feature_switch_key_hint"),
                InputType.TYPE_CLASS_TEXT
        );
        key.setSingleLine(true);
        form.addView(key, new LinearLayout.LayoutParams(-1, -2));

        Spinner type = new Spinner(context);
        String[] typeLabels = Arrays.stream(FeatureSwitchStore.ValueType.values())
                .map(this::typeLabel)
                .toArray(String[]::new);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                typeLabels
        );
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        type.setAdapter(typeAdapter);
        form.addView(type, new LinearLayout.LayoutParams(-1, -2));

        TextView validation = new TextView(context);
        validation.setTextColor(Color.rgb(244, 33, 46));
        validation.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        validation.setPadding(0, Theme.dpToPx(context, 8f), 0, 0);
        validation.setVisibility(View.GONE);
        form.addView(validation, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(StringRef.str("piko_xlite_feature_switch_add_title"))
                .setView(form)
                .setPositiveButton(StringRef.str("piko_xlite_feature_switch_next"), null)
                .setNegativeButton(StringRef.str("piko_xlite_feature_switch_cancel"), null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button next = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (next == null) return;
            next.setTextColor(Theme.primaryAccent(context));
            next.setOnClickListener(button -> {
                String featureKey = key.getText().toString().trim();
                if (featureKey.isEmpty()) {
                    validation.setText(StringRef.str("piko_xlite_feature_switch_invalid_key"));
                    validation.setVisibility(View.VISIBLE);
                    return;
                }
                if (store.hasEntry(featureKey)) {
                    validation.setText(StringRef.str("piko_xlite_feature_switch_duplicate_key"));
                    validation.setVisibility(View.VISIBLE);
                    return;
                }
                FeatureSwitchStore.ValueType valueType =
                        FeatureSwitchStore.ValueType.values()[type.getSelectedItemPosition()];
                dialog.dismiss();
                showValueEditor(featureKey, valueType, defaultValue(valueType), false);
            });
        });
        dialog.show();
    }

    private void showValueEditor(
            String featureKey,
            FeatureSwitchStore.ValueType featureType,
            @Nullable Object effectiveValue,
            boolean overridden
    ) {
        Context context = requireContext();
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 8f),
                Theme.dpToPx(context, 24f),
                0
        );

        TextView key = XLiteSettingsUi.summaryText(context);
        key.setText(featureKey);
        key.setTextIsSelectable(true);
        form.addView(key, new LinearLayout.LayoutParams(-1, -2));

        XLiteSettingsUi.SwitchRow booleanValue = null;
        EditText textValue = null;
        if (featureType == FeatureSwitchStore.ValueType.BOOLEAN) {
            booleanValue = XLiteSettingsUi.switchRow(
                    context,
                    StringRef.str("piko_xlite_feature_switch_boolean_value"),
                    null,
                    Boolean.TRUE.equals(effectiveValue)
            );
            form.addView(booleanValue, new LinearLayout.LayoutParams(-1, -2));
        } else {
            textValue = XLiteSettingsUi.textInput(
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

        TextView validation = new TextView(context);
        validation.setTextColor(Color.rgb(244, 33, 46));
        validation.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        validation.setPadding(0, Theme.dpToPx(context, 8f), 0, 0);
        validation.setVisibility(View.GONE);
        form.addView(validation, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(StringRef.str("piko_xlite_feature_switch_edit_title"))
                .setView(form)
                .setPositiveButton(StringRef.str("piko_xlite_feature_switch_save"), null)
                .setNegativeButton(StringRef.str("piko_xlite_feature_switch_cancel"), null);
        if (overridden) {
            builder.setNeutralButton(StringRef.str("piko_xlite_feature_switch_remove"), null);
        }

        AlertDialog dialog = builder.create();
        XLiteSettingsUi.SwitchRow finalBooleanValue = booleanValue;
        EditText finalTextValue = textValue;
        dialog.setOnShowListener(ignored -> configureDialog(
                dialog,
                featureKey,
                featureType,
                overridden,
                finalBooleanValue,
                finalTextValue,
                validation
        ));
        dialog.show();
        if (dialog.getWindow() == null) return;
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.surfaceContainerHigh(context));
        background.setCornerRadius(Theme.dpToPx(context, 28f));
        dialog.getWindow().setBackgroundDrawable(background);
    }

    private void configureDialog(
            AlertDialog dialog,
            String featureKey,
            FeatureSwitchStore.ValueType featureType,
            boolean overridden,
            @Nullable XLiteSettingsUi.SwitchRow booleanValue,
            @Nullable EditText textValue,
            TextView validation
    ) {
        Context context = dialog.getContext();
        Button save = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (save != null) {
            save.setTextColor(Theme.primaryAccent(context));
            save.setOnClickListener(ignored -> {
                try {
                    Object value = parseValue(featureType, booleanValue, textValue);
                    store.setOverride(featureKey, featureType, value);
                    refresh();
                    dialog.dismiss();
                } catch (IllegalArgumentException exception) {
                    validation.setText(StringRef.str("piko_xlite_feature_switch_invalid_value"));
                    validation.setVisibility(View.VISIBLE);
                }
            });
        }

        Button cancel = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (cancel != null) cancel.setTextColor(Theme.secondaryText(context));
        if (!overridden) return;

        Button remove = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (remove == null) return;
        remove.setTextColor(Color.rgb(244, 33, 46));
        remove.setOnClickListener(ignored -> {
            store.removeOverride(featureKey);
            refresh();
            dialog.dismiss();
        });
    }

    private Object parseValue(
            FeatureSwitchStore.ValueType type,
            @Nullable XLiteSettingsUi.SwitchRow booleanValue,
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
            case BOOLEAN -> "piko_xlite_feature_switch_type_boolean";
            case INT -> "piko_xlite_feature_switch_type_int";
            case LONG -> "piko_xlite_feature_switch_type_long";
            case FLOAT -> "piko_xlite_feature_switch_type_float";
            case DOUBLE -> "piko_xlite_feature_switch_type_double";
            case STRING -> "piko_xlite_feature_switch_type_string";
            case STRING_LIST -> "piko_xlite_feature_switch_type_list";
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
            case INT, LONG, FLOAT, DOUBLE -> "piko_xlite_feature_switch_number_value";
            case STRING -> "piko_xlite_feature_switch_string_value";
            case STRING_LIST -> "piko_xlite_feature_switch_list_value";
            case BOOLEAN -> "piko_xlite_feature_switch_boolean_value";
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
                ? "piko_xlite_feature_switch_empty"
                : "piko_xlite_feature_switch_no_results"));
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
