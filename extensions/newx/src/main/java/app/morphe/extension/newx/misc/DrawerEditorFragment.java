package app.morphe.extension.newx.misc;

import app.morphe.extension.newx.settings.NewXStrings;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.newx.settings.NewXCustomScreenFragment;
import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.settings.NewXSettingsActivity;
import app.morphe.extension.newx.settings.NewXSettingsUi;
import app.morphe.extension.newx.settings.SettingsNode;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.newx.settings.StringSetSetting;
import app.morphe.extension.newx.ui.ButtonView;
import app.morphe.extension.newx.ui.DialogView;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.Setting;

/**
 * Checkbox editor for the NewX navigation drawer. Native rows are shown or hidden through the
 * shared hidden-items setting; shortcut rows toggle their own settings. The drawer recomposes
 * on open, so changes apply without a restart.
 */
public final class DrawerEditorFragment extends NewXCustomScreenFragment {
    private static final String HIDDEN_ITEMS_ID = "newx.content.hidden_drawer_items";

    private static final class Shortcut {
        final String settingId;
        final String titleResourceName;
        final String catalogIconId;

        Shortcut(String settingId, String titleResourceName, String catalogIconId) {
            this.settingId = settingId;
            this.titleResourceName = titleResourceName;
            this.catalogIconId = catalogIconId;
        }
    }

    private static final Shortcut[] SHORTCUTS = {
            new Shortcut(
                    "newx.navigation.show_piko_settings_in_drawer",
                    "piko_newx_settings_title",
                    "DRAWER_SHORTCUT_PIKO"
            ),
            new Shortcut(
                    "newx.navigation.show_messages_in_drawer",
                    "piko_newx_nav_bar_dm",
                    "DRAWER_SHORTCUT_MESSAGES"
            ),
            new Shortcut(
                    "newx.navigation.show_grok_in_drawer",
                    "piko_newx_nav_bar_grok",
                    "DRAWER_SHORTCUT_GROK"
            ),
            new Shortcut(
                    "newx.navigation.show_notifications_in_drawer",
                    "piko_newx_nav_bar_notifications",
                    "DRAWER_SHORTCUT_NOTIFICATIONS"
            ),
    };

    @Nullable private LinearLayout rowsContainer;
    private ButtonView restartButton;
    private boolean hasPendingChanges;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        Context context = requireContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NewXSettingsUi.backgroundColor(context));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NewXSettingsUi.backgroundColor(context));
        rowsContainer = new LinearLayout(context);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rowsContainer, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(NewXSettingsUi.divider(context));
        root.addView(buildRestartRow(context), new LinearLayout.LayoutParams(-1, -2));

        rebuildRows();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if (activity instanceof NewXSettingsActivity settingsActivity) {
            settingsActivity.setPageTitle(NewXStrings.str("piko_newx_drawer_editor_title"));
        }
        rebuildRows();
    }

    private View buildRestartRow(Context context) {
        LinearLayout footer = new LinearLayout(context);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(
                Theme.dpToPx(context, 16f),
                Theme.dpToPx(context, 12f),
                Theme.dpToPx(context, 16f),
                Theme.dpToPx(context, 12f)
        );
        restartButton = new ButtonView(
                context,
                ButtonView.ButtonStyle.FILLED,
                NewXStrings.str("piko_newx_drawer_editor_restart")
        );
        restartButton.setEnabled(hasPendingChanges);
        restartButton.setOnClickListener(ignored -> confirmRestart());
        footer.addView(restartButton, new LinearLayout.LayoutParams(-1, -2));
        return footer;
    }

    private void markChanged() {
        hasPendingChanges = true;
        if (restartButton != null) restartButton.setEnabled(true);
    }

    private void confirmRestart() {
        if (restartButton == null || !restartButton.isEnabled()) return;
        Context context = requireContext();
        DialogView dialog =
                new DialogView(context)
                        .setTitle(NewXStrings.str("piko_newx_drawer_editor_restart_title"))
                        .setSubtitle(NewXStrings.str("piko_newx_drawer_editor_restart_message"));
        dialog.getDialog().setCanceledOnTouchOutside(true);

        ButtonView cancel =
                NewXSettingsUi.dialogButton(
                        context,
                        NewXStrings.str("piko_newx_settings_cancel")
                );
        cancel.setOnClickListener(ignored -> dialog.dismiss());

        ButtonView restart =
                NewXSettingsUi.dialogButton(
                        context,
                        NewXStrings.str("piko_newx_drawer_editor_restart_confirm")
                );
        restart.setOnClickListener(ignored -> {
            dialog.dismiss();
            Utils.restartApp(context);
        });

        dialog.addButton(cancel).addButton(restart).show();
    }

    private void rebuildRows() {
        LinearLayout container = rowsContainer;
        if (container == null) return;
        container.removeAllViews();

        Context context = requireContext();
        TextView hint = NewXSettingsUi.summaryText(context);
        hint.setText(NewXStrings.str("piko_newx_drawer_editor_hint"));
        hint.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 16f),
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 12f)
        );
        container.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        container.addView(sectionHeader(context, "piko_newx_drawer_editor_shortcuts"));
        for (Shortcut shortcut : sortedShortcuts()) {
            addShortcutRow(container, shortcut);
        }

        List<SettingsNode.ChoiceOption> options = sortedDrawerOptions();
        if (!options.isEmpty()) {
            container.addView(NewXSettingsUi.divider(context));
            container.addView(sectionHeader(context, "piko_newx_drawer_editor_items"));
            Set<String> hidden = hiddenItems();
            for (SettingsNode.ChoiceOption option : options) {
                addItemRow(container, option, !hidden.contains(option.id));
            }
        }
    }

    private static List<Shortcut> sortedShortcuts() {
        List<Shortcut> sorted = new ArrayList<>();
        Collections.addAll(sorted, SHORTCUTS);
        Collections.sort(
                sorted,
                (left, right) -> compareNames(
                        NewXStrings.str(left.titleResourceName),
                        NewXStrings.str(right.titleResourceName)
                )
        );
        return sorted;
    }

    private static List<SettingsNode.ChoiceOption> sortedDrawerOptions() {
        List<SettingsNode.ChoiceOption> sorted = new ArrayList<>(drawerOptions());
        Collections.sort(
                sorted,
                (left, right) -> {
                    boolean leftHasIcon = DrawerCatalog.drawableFor(left.id) != 0;
                    boolean rightHasIcon = DrawerCatalog.drawableFor(right.id) != 0;
                    if (leftHasIcon != rightHasIcon) return leftHasIcon ? -1 : 1;
                    return compareNames(left.title.toString(), right.title.toString());
                }
        );
        return sorted;
    }

    private static int compareNames(CharSequence left, CharSequence right) {
        String leftName = left.toString();
        String rightName = right.toString();
        int ignoringCase = leftName.compareToIgnoreCase(rightName);
        return ignoringCase != 0 ? ignoringCase : leftName.compareTo(rightName);
    }

    private TextView sectionHeader(Context context, String titleResourceName) {
        TextView header = NewXSettingsUi.summaryText(context);
        header.setText(NewXStrings.str(titleResourceName));
        header.setTextColor(Theme.primaryAccent(context));
        header.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 18f),
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 8f)
        );
        return header;
    }

    private void addShortcutRow(LinearLayout container, Shortcut shortcut) {
        Setting<?> setting = SettingsRegistry.settingOrNull(shortcut.settingId);
        if (!(setting instanceof BooleanSetting toggle)) return;
        Context context = requireContext();
        View row = switchRow(
                context,
                NewXStrings.str(shortcut.titleResourceName),
                DrawerCatalog.drawableFor(shortcut.catalogIconId),
                Boolean.TRUE.equals(toggle.get()),
                checked -> {
                    toggle.save(checked);
                    markChanged();
                }
        );
        container.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addItemRow(
            LinearLayout container,
            SettingsNode.ChoiceOption option,
            boolean shown
    ) {
        Context context = requireContext();
        View row = switchRow(
                context,
                option.title.toString(),
                DrawerCatalog.drawableFor(option.id),
                shown,
                checked -> {
                    setHidden(option.id, !checked);
                    markChanged();
                }
        );
        container.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private View switchRow(
            Context context,
            CharSequence title,
            int drawableRes,
            boolean checked,
            NewXSettingsUi.CheckedChangeListener listener
    ) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setMinimumHeight(Theme.dpToPx(context, 64f));
        root.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 8f),
                Theme.dpToPx(context, 16f),
                Theme.dpToPx(context, 8f)
        );
        NewXSettingsUi.applyRippleBackground(root);

        if (drawableRes != 0) {
            ImageView iconView = new ImageView(context);
            try {
                iconView.setImageResource(drawableRes);
            } catch (Exception exception) {
                NewXLogger.printException(
                        () -> "Failed to resolve the NewX drawer editor icon",
                        exception
                );
            }
            iconView.setImageTintList(ColorStateList.valueOf(Theme.primaryText(context)));
            root.addView(
                    iconView,
                    new LinearLayout.LayoutParams(
                            Theme.dpToPx(context, 24f),
                            Theme.dpToPx(context, 24f)
                    )
            );
        }

        TextView titleView = NewXSettingsUi.titleText(context);
        titleView.setText(title);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMarginStart(Theme.dpToPx(context, 16f));
        titleParams.setMarginEnd(Theme.dpToPx(context, 14f));
        root.addView(titleView, titleParams);

        NewXSettingsUi.SwitchControl control = new NewXSettingsUi.SwitchControl(context);
        control.setInteractive(true);
        control.setChecked(checked, false);
        control.setOnCheckedChangeListener(listener);
        root.addView(
                control,
                new LinearLayout.LayoutParams(Theme.dpToPx(context, 52f), Theme.dpToPx(context, 32f))
        );
        root.setOnClickListener(ignored -> control.toggle(true));
        return root;
    }

    private Context requireContext() {
        Context context = getActivity();
        if (context == null) throw new IllegalStateException("Drawer editor is detached");
        return context;
    }

    private static List<SettingsNode.ChoiceOption> drawerOptions() {
        SettingsNode.MultiChoice multiChoice = findHiddenItemsNode();
        if (multiChoice == null) return new ArrayList<>();
        return multiChoice.options;
    }

    @Nullable
    private static SettingsNode.MultiChoice findHiddenItemsNode() {
        for (SettingsNode.Category category : SettingsRegistry.catalog()) {
            SettingsNode.MultiChoice found = findHiddenItemsNode(category);
            if (found != null) return found;
        }
        return null;
    }

    @Nullable
    private static SettingsNode.MultiChoice findHiddenItemsNode(SettingsNode.Group group) {
        for (SettingsNode child : group.children) {
            if (child instanceof SettingsNode.MultiChoice multiChoice
                    && HIDDEN_ITEMS_ID.equals(multiChoice.id)) {
                return multiChoice;
            }
            if (child instanceof SettingsNode.Group nested) {
                SettingsNode.MultiChoice found = findHiddenItemsNode(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Set<String> hiddenItems() {
        Setting<?> setting = SettingsRegistry.settingOrNull(HIDDEN_ITEMS_ID);
        if (!(setting instanceof StringSetSetting hidden)) return new LinkedHashSet<>();
        Set<String> canonical = new LinkedHashSet<>();
        for (String optionId : hidden.get()) {
            if (optionId != null) canonical.add(DrawerCatalog.canonicalOptionId(optionId));
        }
        return canonical;
    }

    private static void setHidden(String optionId, boolean hidden) {
        Setting<?> setting = SettingsRegistry.settingOrNull(HIDDEN_ITEMS_ID);
        if (!(setting instanceof StringSetSetting hiddenSetting)) return;
        Set<String> updated = new LinkedHashSet<>(hiddenSetting.get());
        String canonicalOptionId = DrawerCatalog.canonicalOptionId(optionId);
        updated.remove(optionId);
        updated.remove(canonicalOptionId);
        if (hidden) {
            updated.add(canonicalOptionId);
        }
        hiddenSetting.save(updated);
    }
}
