package app.morphe.extension.newx.settings;

import android.app.Activity;
import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.text.InputType;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.lang.reflect.Constructor;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.newx.ui.ButtonView;
import app.morphe.extension.newx.ui.DialogView;
import app.morphe.extension.newx.ui.Theme;

@SuppressWarnings("deprecation")
public final class SettingsRenderer {
    interface GroupNavigator {
        void open(SettingsNode.Group group);
    }

    interface ScreenNavigator {
        void open(SettingsNode.CustomScreen screen);
    }

    private SettingsRenderer() {
    }

    public static void render(
            PreferenceScreen screen,
            GroupNavigator groupNavigator
    ) {
        Context preferenceContext = screen.getContext();
        List<SettingsNode.Category> categories = SettingsRegistry.catalog();
        for (SettingsNode.Category category : categories) {
            screen.addPreference(group(preferenceContext, category, groupNavigator));
        }
        Utils.setPreferenceTitlesToMultiLineIfNeeded(screen);
    }

    public static void renderGroup(
            Activity activity,
            PreferenceScreen screen,
            SettingsNode.Group group,
            GroupNavigator groupNavigator,
            ScreenNavigator screenNavigator
    ) {
        renderChildren(
                activity,
                screen.getContext(),
                screen,
                group.children,
                groupNavigator,
                screenNavigator
        );
        Utils.setPreferenceTitlesToMultiLineIfNeeded(screen);
    }

    public static int renderSearchResults(
            Activity activity,
            PreferenceScreen screen,
            String query,
            List<SettingsSearchMatcher.Match> matches,
            ScreenNavigator screenNavigator
    ) {
        Context preferenceContext = screen.getContext();
        boolean isRtl = preferenceContext.getResources()
                .getConfiguration()
                .getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int addedResults = 0;
        for (SettingsSearchMatcher.Match match : matches) {
            SettingsNode.Item item = match.result.item;
            Preference preference;
            if (item instanceof SettingsNode.CustomScreen customScreen) {
                preference = customScreen(preferenceContext, customScreen, screenNavigator);
            } else {
                preference = item(activity, preferenceContext, item);
            }
            preference.setOrder(addedResults);
            preference.setTitle(highlight(
                    preferenceContext,
                    match.result.title,
                    query
            ));
            preference.setSummary(highlight(
                    preferenceContext,
                    searchSummary(match.result, isRtl),
                    query
            ));
            screen.addPreference(preference);
            addedResults++;
        }
        Utils.setPreferenceTitlesToMultiLineIfNeeded(screen);
        return addedResults;
    }

    private static String searchSummary(
            SettingsSearchIndex.Result result,
            boolean isRtl
    ) {
        String path = result.path;
        if (isRtl) path = path.replace(" \u2192 ", " \u2190 ");
        if (path.isEmpty()) return result.summary;
        if (result.summary.isEmpty()) return path;
        return path + " - " + result.summary;
    }

    private static CharSequence highlight(
            Context context,
            String text,
            String query
    ) {
        if (text == null || text.isEmpty()) return "";
        List<SettingsSearchMatcher.MatchRange> ranges =
                SettingsSearchMatcher.findHighlightRanges(text, query);
        if (ranges.isEmpty()) return text;
        SpannableString highlighted = new SpannableString(text);
        int color = Theme.primaryAccent(context);
        for (SettingsSearchMatcher.MatchRange range : ranges) {
            if (range.start < 0 || range.end > text.length() || range.end <= range.start) {
                continue;
            }
            highlighted.setSpan(
                    new ForegroundColorSpan(color),
                    range.start,
                    range.end,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return highlighted;
    }

    private static void renderChildren(
            Activity activity,
            Context preferenceContext,
            PreferenceGroup parent,
            List<SettingsNode> children,
            GroupNavigator groupNavigator,
            ScreenNavigator screenNavigator
    ) {
        for (SettingsNode child : children) {
            if (child instanceof SettingsNode.Group group) {
                parent.addPreference(group(preferenceContext, group, groupNavigator));
                continue;
            }
            if (child instanceof SettingsNode.CustomScreen screen) {
                parent.addPreference(customScreen(preferenceContext, screen, screenNavigator));
                continue;
            }
            parent.addPreference(item(activity, preferenceContext, (SettingsNode.Item) child));
        }
    }

    private static Preference group(
            Context context,
            SettingsNode.Group group,
            GroupNavigator navigator
    ) {
        Preference preference = new NewXPreferenceStyle.Navigation(context);
        applyMetadata(preference, group);
        if (group.iconResourceName != null) {
            preference.setIcon(ResourceUtils.getIdentifierOrThrow(
                    context,
                    ResourceType.DRAWABLE,
                    group.iconResourceName
            ));
        }
        preference.setOnPreferenceClickListener(ignored -> {
            navigator.open(group);
            return true;
        });
        return preference;
    }

    private static Preference customScreen(
            Context context,
            SettingsNode.CustomScreen screen,
            ScreenNavigator navigator
    ) {
        Preference preference = new NewXPreferenceStyle.Navigation(context);
        applyMetadata(preference, screen);
        if (screen.iconResourceName != null) {
            preference.setIcon(ResourceUtils.getIdentifierOrThrow(
                    context,
                    ResourceType.DRAWABLE,
                    screen.iconResourceName
            ));
        }
        preference.setOnPreferenceClickListener(ignored -> {
            navigator.open(screen);
            return true;
        });
        return preference;
    }

    private static Preference item(
            Activity activity,
            Context preferenceContext,
            SettingsNode.Item item
    ) {
        Preference preference;
        if (item instanceof SettingsNode.Toggle toggle) {
            preference = toggle(activity, preferenceContext, toggle);
        } else if (item instanceof SettingsNode.TextInput textInput) {
            preference = textInput(activity, preferenceContext, textInput);
        } else if (item instanceof SettingsNode.SingleChoice singleChoice) {
            preference = singleChoice(activity, preferenceContext, singleChoice);
        } else if (item instanceof SettingsNode.MultiChoice multiChoice) {
            preference = multiChoice(activity, preferenceContext, multiChoice);
        } else if (item instanceof SettingsNode.Action action) {
            preference = action(activity, preferenceContext, action);
        } else {
            throw new IllegalStateException("Unknown NewX settings node: " + item.getClass());
        }
        applyMetadata(preference, item);
        if (item instanceof SettingsNode.ValueItem<?> valueItem) {
            preference.setEnabled(valueItem.setting.isAvailable());
        }
        return preference;
    }

    private static SwitchPreference toggle(
            Activity activity,
            Context context,
            SettingsNode.Toggle item
    ) {
        SwitchPreference preference = new NewXPreferenceStyle.Toggle(context);
        preference.setPersistent(false);
        preference.setChecked(item.setting.get());
        preference.setOnPreferenceChangeListener((ignored, newValue) -> {
            boolean value = (Boolean) newValue;
            if (item.setting.get() == value) return true;
            item.setting.save(value);
            promptForRestart(activity, item.setting);
            return true;
        });
        return preference;
    }

    private static Preference textInput(
            Activity activity,
            Context context,
            SettingsNode.TextInput item
    ) {
        Preference preference = new NewXPreferenceStyle.TextInput(context);
        preference.setPersistent(false);
        preference.setOnPreferenceClickListener(ignored -> {
            showTextInputDialog(activity, item);
            return true;
        });
        return preference;
    }

    private static void showTextInputDialog(
            Activity activity,
            SettingsNode.TextInput item
    ) {
        Context context = activity;
        LinearLayout form = dialogForm(context);
        EditText input = NewXSettingsUi.textInput(
                context,
                null,
                item.inputKind == SettingsNode.InputKind.MULTILINE
                        ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        : InputType.TYPE_CLASS_TEXT
        );
        String currentValue = item.setting.get();
        input.setText(currentValue);
        input.setSelection(input.length());
        form.addView(input, new LinearLayout.LayoutParams(-1, -2));

        DialogView dialog = new DialogView(context)
                .setTitle(item.title.toString())
                .setBodyView(form);
        dialog.getDialog().setCanceledOnTouchOutside(true);

        ButtonView cancel = dialogButton(context, "piko_newx_settings_cancel");
        cancel.setOnClickListener(ignored -> dialog.dismiss());

        ButtonView save = dialogButton(context, "piko_newx_settings_ok");
        save.setOnClickListener(ignored -> {
            String value = input.getText().toString();
            if (item.setting.get().equals(value)) {
                dialog.dismiss();
                return;
            }
            if (!validateTextInput(activity, item, value)) return;
            item.setting.save(value);
            dialog.dismiss();
            promptForRestart(activity, item.setting);
        });

        dialog.addButton(cancel).addButton(save).show();
    }

    private static LinearLayout dialogForm(Context context) {
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

    private static ButtonView dialogButton(Context context, String textResourceName) {
        return new ButtonView(
                context,
                ButtonView.ButtonStyle.TEXT,
                StringRef.str(textResourceName)
        );
    }

    private static boolean validateTextInput(
            Activity activity,
            SettingsNode.TextInput item,
            String value
    ) {
        String descriptor = item.validatorClassDescriptor;
        if (descriptor == null) return true;

        try {
            SettingsValueValidator validator = instantiateValidator(activity, descriptor);
            String errorMessage = validator.errorMessage(value);
            if (errorMessage == null) return true;
            Utils.showToastShort(errorMessage);
        } catch (Exception exception) {
            Logger.printException(
                    () -> "NewX text input validation failed: " + item.id,
                    exception
            );
            Utils.showToastShort(StringRef.str("piko_newx_setting_validation_failed"));
        }
        return false;
    }

    private static SettingsValueValidator instantiateValidator(
            Activity activity,
            String descriptor
    ) throws ReflectiveOperationException {
        String className = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        Class<?> validatorClass = Class.forName(className, true, activity.getClassLoader());
        if (!SettingsValueValidator.class.isAssignableFrom(validatorClass)) {
            throw new IllegalArgumentException("Not an NewX settings validator: " + className);
        }
        Constructor<?> constructor = validatorClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (SettingsValueValidator) constructor.newInstance();
    }

    private static Preference singleChoice(
            Activity activity,
            Context context,
            SettingsNode.SingleChoice item
    ) {
        Preference preference = new NewXPreferenceStyle.SingleChoice(context);
        preference.setPersistent(false);
        preference.setOnPreferenceClickListener(ignored -> {
            showSingleChoiceDialog(activity, item);
            return true;
        });
        return preference;
    }

    private static void showSingleChoiceDialog(
            Activity activity,
            SettingsNode.SingleChoice item
    ) {
        Context context = activity;
        DialogView dialog = new DialogView(context)
                .setTitle(item.title.toString());
        dialog.getDialog().setCanceledOnTouchOutside(true);

        LinearLayout options = choiceList(context);
        dialog.setScrollableBodyView(options);
        for (SettingsNode.ChoiceOption option : item.options) {
            NewXSettingsUi.ChoiceRow row = NewXSettingsUi.choiceRow(
                    context,
                    option.title.toString(),
                    option.id.equals(item.setting.get()),
                    false
            );
            row.setOnCheckedChangeListener(checked -> {
                if (!checked) return;
                if (!item.setting.get().equals(option.id)) {
                    item.setting.save(option.id);
                    dialog.dismiss();
                    promptForRestart(activity, item.setting);
                    return;
                }
                dialog.dismiss();
            });
            options.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        ButtonView cancel = dialogButton(context, "piko_newx_settings_cancel");
        cancel.setOnClickListener(ignored -> dialog.dismiss());
        dialog.addButton(cancel).show();
    }

    private static Preference multiChoice(
            Activity activity,
            Context context,
            SettingsNode.MultiChoice item
    ) {
        Preference preference = new NewXPreferenceStyle.MultiChoice(context);
        preference.setPersistent(false);
        preference.setOnPreferenceClickListener(ignored -> {
            showMultiChoiceDialog(activity, item);
            return true;
        });
        return preference;
    }

    private static void showMultiChoiceDialog(
            Activity activity,
            SettingsNode.MultiChoice item
    ) {
        Context context = activity;
        Set<String> selectedValues = new LinkedHashSet<>(item.setting.get());
        DialogView dialog = new DialogView(context)
                .setTitle(item.title.toString());
        dialog.getDialog().setCanceledOnTouchOutside(true);

        LinearLayout options = choiceList(context);
        dialog.setScrollableBodyView(options);
        for (SettingsNode.ChoiceOption option : item.options) {
            NewXSettingsUi.ChoiceRow row = NewXSettingsUi.choiceRow(
                    context,
                    option.title.toString(),
                    selectedValues.contains(option.id),
                    true
            );
            row.setOnCheckedChangeListener(checked -> {
                if (checked) {
                    selectedValues.add(option.id);
                    return;
                }
                selectedValues.remove(option.id);
            });
            options.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        ButtonView cancel = dialogButton(context, "piko_newx_settings_cancel");
        cancel.setOnClickListener(ignored -> dialog.dismiss());
        ButtonView save = dialogButton(context, "piko_newx_settings_ok");
        save.setOnClickListener(ignored -> {
            Set<String> immutableValues = StringSetSetting.immutableCopy(selectedValues);
            if (item.setting.get().equals(immutableValues)) {
                dialog.dismiss();
                return;
            }
            item.setting.save(immutableValues);
            dialog.dismiss();
            promptForRestart(activity, item.setting);
        });
        dialog.addButton(cancel).addButton(save).show();
    }

    private static LinearLayout choiceList(Context context) {
        LinearLayout options = new LinearLayout(context);
        options.setOrientation(LinearLayout.VERTICAL);
        return options;
    }

    private static Preference action(
            Activity activity,
            Context context,
            SettingsNode.Action item
    ) {
        Preference preference = new NewXPreferenceStyle.Action(context);
        preference.setPersistent(false);
        preference.setOnPreferenceClickListener(ignored -> {
            try {
                instantiateAction(activity, item.handlerClassDescriptor).run(activity);
            } catch (Exception exception) {
                Logger.printException(() -> "NewX settings action failed: " + item.id, exception);
                Utils.showToastShort(StringRef.str("piko_newx_action_failed"));
            }
            return true;
        });
        return preference;
    }

    private static SettingsActionHandler instantiateAction(Activity activity, String descriptor)
            throws ReflectiveOperationException {
        String className = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        Class<?> handlerClass = Class.forName(className, true, activity.getClassLoader());
        if (!SettingsActionHandler.class.isAssignableFrom(handlerClass)) {
            throw new IllegalArgumentException("Not an NewX settings action: " + className);
        }
        Constructor<?> constructor = handlerClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (SettingsActionHandler) constructor.newInstance();
    }

    private static void promptForRestart(Activity activity, Setting<?> setting) {
        if (!setting.rebootApp) return;

        DialogView dialog = new DialogView(activity)
                .setTitle(StringRef.str("piko_newx_restart_title"))
                .setSubtitle(StringRef.str("piko_newx_restart_summary"));
        dialog.getDialog().setCanceledOnTouchOutside(true);

        ButtonView cancel = dialogButton(activity, "piko_newx_settings_cancel");
        cancel.setOnClickListener(ignored -> dialog.dismiss());
        ButtonView restart = new ButtonView(
                activity,
                ButtonView.ButtonStyle.FILLED,
                StringRef.str("piko_newx_restart_now")
        );
        restart.setOnClickListener(ignored -> {
            dialog.dismiss();
            Utils.restartApp(activity);
        });
        dialog.addButton(cancel).addButton(restart).show();
    }

    private static void applyMetadata(Preference preference, SettingsNode node) {
        preference.setKey(node.id);
        preference.setOrder(node.order);
        preference.setTitle(node.title.toString());
        if (node.summary != null) preference.setSummary(node.summary.toString());
    }
}
