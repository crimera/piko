package app.morphe.extension.xlite.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.text.InputType;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;

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
import app.morphe.extension.shared.ui.CustomDialog;
import app.morphe.extension.xlite.ui.Theme;

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
        Preference preference = new XLitePreferenceStyle.Navigation(context);
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
        Preference preference = new XLitePreferenceStyle.Navigation(context);
        applyMetadata(preference, screen);
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
            throw new IllegalStateException("Unknown X-Lite settings node: " + item.getClass());
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
        SwitchPreference preference = new XLitePreferenceStyle.Toggle(context);
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

    private static EditTextPreference textInput(
            Activity activity,
            Context context,
            SettingsNode.TextInput item
    ) {
        EditTextPreference preference = new XLitePreferenceStyle.TextInput(context);
        preference.setPersistent(false);
        preference.setText(item.setting.get());
        if (item.inputKind == SettingsNode.InputKind.MULTILINE) {
            preference.getEditText().setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            );
        } else {
            preference.getEditText().setInputType(InputType.TYPE_CLASS_TEXT);
        }
        preference.setOnPreferenceChangeListener((ignored, newValue) -> {
            String value = String.valueOf(newValue);
            if (item.setting.get().equals(value)) return true;
            item.setting.save(value);
            promptForRestart(activity, item.setting);
            return true;
        });
        return preference;
    }

    private static ListPreference singleChoice(
            Activity activity,
            Context context,
            SettingsNode.SingleChoice item
    ) {
        ListPreference preference = new XLitePreferenceStyle.SingleChoice(context);
        preference.setPersistent(false);
        CharSequence[] entries = new CharSequence[item.options.size()];
        CharSequence[] values = new CharSequence[item.options.size()];
        for (int index = 0; index < item.options.size(); index++) {
            SettingsNode.ChoiceOption option = item.options.get(index);
            entries[index] = option.title.toString();
            values[index] = option.id;
        }
        preference.setEntries(entries);
        preference.setEntryValues(values);
        preference.setValue(item.setting.get());
        preference.setOnPreferenceChangeListener((ignored, newValue) -> {
            String value = String.valueOf(newValue);
            if (item.setting.get().equals(value)) return true;
            item.setting.save(value);
            promptForRestart(activity, item.setting);
            return true;
        });
        return preference;
    }

    private static MultiSelectListPreference multiChoice(
            Activity activity,
            Context context,
            SettingsNode.MultiChoice item
    ) {
        MultiSelectListPreference preference = new XLitePreferenceStyle.MultiChoice(context);
        preference.setPersistent(false);
        CharSequence[] entries = new CharSequence[item.options.size()];
        CharSequence[] values = new CharSequence[item.options.size()];
        for (int index = 0; index < item.options.size(); index++) {
            SettingsNode.ChoiceOption option = item.options.get(index);
            entries[index] = option.title.toString();
            values[index] = option.id;
        }
        preference.setEntries(entries);
        preference.setEntryValues(values);
        preference.setValues(new LinkedHashSet<>(item.setting.get()));
        preference.setOnPreferenceChangeListener((ignored, newValue) -> {
            if (!(newValue instanceof Set<?> rawValues)) return false;
            Set<String> valuesToSave = new LinkedHashSet<>();
            for (Object rawValue : rawValues) {
                if (!(rawValue instanceof String value)) return false;
                valuesToSave.add(value);
            }
            Set<String> immutableValues = StringSetSetting.immutableCopy(valuesToSave);
            if (item.setting.get().equals(immutableValues)) return true;
            item.setting.save(immutableValues);
            promptForRestart(activity, item.setting);
            return true;
        });
        return preference;
    }

    private static Preference action(
            Activity activity,
            Context context,
            SettingsNode.Action item
    ) {
        Preference preference = new XLitePreferenceStyle.Action(context);
        preference.setPersistent(false);
        preference.setOnPreferenceClickListener(ignored -> {
            try {
                instantiateAction(activity, item.handlerClassDescriptor).run(activity);
            } catch (Exception exception) {
                Logger.printException(() -> "X-Lite settings action failed: " + item.id, exception);
                Utils.showToastShort(StringRef.str("piko_xlite_action_failed"));
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
            throw new IllegalArgumentException("Not an X-Lite settings action: " + className);
        }
        Constructor<?> constructor = handlerClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (SettingsActionHandler) constructor.newInstance();
    }

    private static void promptForRestart(Activity activity, Setting<?> setting) {
        if (!setting.rebootApp) return;
        Dialog dialog = CustomDialog.create(
                activity,
                StringRef.str("piko_xlite_restart_title"),
                StringRef.str("piko_xlite_restart_summary"),
                null,
                StringRef.str("piko_xlite_restart_now"),
                () -> Utils.restartApp(activity),
                () -> { },
                null,
                null,
                true
        ).first;
        dialog.show();
    }

    private static void applyMetadata(Preference preference, SettingsNode node) {
        preference.setKey(node.id);
        preference.setOrder(node.order);
        preference.setTitle(node.title.toString());
        if (node.summary != null) preference.setSummary(node.summary.toString());
    }
}
