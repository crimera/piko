package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.DragEvent;
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
import java.util.List;

import app.morphe.extension.newx.settings.NewXCustomScreenFragment;
import app.morphe.extension.newx.settings.NewXSettingsActivity;
import app.morphe.extension.newx.settings.NewXSettingsUi;
import app.morphe.extension.newx.ui.ButtonView;
import app.morphe.extension.newx.ui.DialogView;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

/**
 * Drag-and-drop editor for the NewX bottom navigation bar: item order, visibility, replacement
 * destination, and replacement icon. Changes are picked up on the next app start.
 */
@SuppressWarnings("deprecation")
public final class NavBarEditorFragment extends NewXCustomScreenFragment {
    private static final String DRAG_MIME = "piko/newx-navbar-item";

    private final List<Row> rows = new ArrayList<>();
    private LinearLayout rowsContainer;

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

        TextView hint = NewXSettingsUi.summaryText(context);
        hint.setText(StringRef.str("piko_newx_nav_editor_hint"));
        hint.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 16f),
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 12f)
        );
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(context);
        rowsContainer = new LinearLayout(context);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        rowsContainer.setOnDragListener(this::onDrag);
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
            settingsActivity.setPageTitle(StringRef.str("piko_newx_nav_editor_title"));
        }
        rebuildRows();
    }

    private void rebuildRows() {
        LinearLayout container = rowsContainer;
        if (container == null) return;
        container.removeAllViews();
        rows.clear();

        Context context = requireContext();
        List<String> tabIds = NavBarConfig.shared().orderedTabs(NavBarCatalog.tabIds());
        for (String tabId : tabIds) {
            Row row = createRow(context, tabId);
            row.divider = NewXSettingsUi.divider(context);
            rows.add(row);
            container.addView(row.root, new LinearLayout.LayoutParams(-1, -2));
            container.addView(row.divider, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private View buildRestartRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Theme.dpToPx(context, 56f));
        row.setPadding(
                Theme.dpToPx(context, 20f),
                Theme.dpToPx(context, 14f),
                Theme.dpToPx(context, 20f),
                Theme.dpToPx(context, 14f)
        );
        NewXSettingsUi.applyRippleBackground(row);

        TextView restart = NewXSettingsUi.titleText(context);
        restart.setText(StringRef.str("piko_newx_nav_editor_restart"));
        restart.setTextColor(Theme.primaryAccent(context));
        row.addView(restart, new LinearLayout.LayoutParams(-1, -2));

        row.setOnClickListener(ignored -> confirmRestart());
        return row;
    }

    private void confirmRestart() {
        Context context = requireContext();
        DialogView dialog =
                new DialogView(context)
                        .setTitle(StringRef.str("piko_newx_nav_editor_restart_title"))
                        .setSubtitle(StringRef.str("piko_newx_nav_editor_restart_message"));
        dialog.getDialog().setCanceledOnTouchOutside(true);

        ButtonView cancel =
                NewXSettingsUi.dialogButton(
                        context,
                        StringRef.str("piko_newx_settings_cancel")
                );
        cancel.setOnClickListener(ignored -> dialog.dismiss());

        ButtonView restart =
                NewXSettingsUi.dialogButton(
                        context,
                        StringRef.str("piko_newx_nav_editor_restart_confirm")
                );
        restart.setOnClickListener(ignored -> {
            dialog.dismiss();
            Utils.restartApp(context);
        });

        dialog.addButton(cancel).addButton(restart).show();
    }

    private Row createRow(Context context, String tabId) {
        NavBarCatalog.Tab tab = NavBarCatalog.tab(tabId);
        String title = tab == null ? tabId : StringRef.str(tab.labelResourceName);

        Row row = new Row(tabId);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setMinimumHeight(Theme.dpToPx(context, 72f));
        root.setPadding(
                Theme.dpToPx(context, 12f),
                Theme.dpToPx(context, 10f),
                Theme.dpToPx(context, 16f),
                Theme.dpToPx(context, 10f)
        );
        row.root = root;

        row.handle = NewXSettingsUi.summaryText(context);
        row.handle.setText("\u2261");
        row.handle.setTextSize(22f);
        row.handle.setGravity(Gravity.CENTER);
        row.handle.setContentDescription(StringRef.str("piko_newx_nav_editor_drag"));
        row.handle.setOnLongClickListener(ignored -> {
            startDrag(row);
            return true;
        });
        root.addView(row.handle, new LinearLayout.LayoutParams(Theme.dpToPx(context, 40f), -2));

        row.iconView = new ImageView(context);
        row.iconView.setImageTintList(ColorStateList.valueOf(Theme.primaryText(context)));
        root.addView(
                row.iconView,
                new LinearLayout.LayoutParams(Theme.dpToPx(context, 24f), Theme.dpToPx(context, 24f))
        );

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.setMarginStart(Theme.dpToPx(context, 16f));
        labelParams.setMarginEnd(Theme.dpToPx(context, 12f));
        root.addView(labels, labelParams);

        row.titleView = NewXSettingsUi.titleText(context);
        row.titleView.setText(title);
        labels.addView(row.titleView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout choices = new LinearLayout(context);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setPadding(0, Theme.dpToPx(context, 5f), 0, 0);

        row.opensView = choiceText(context);
        row.opensView.setOnClickListener(ignored -> showDestinationDialog(row));
        choices.addView(row.opensView, new LinearLayout.LayoutParams(-2, -2));

        row.iconChoiceView = choiceText(context);
        LinearLayout.LayoutParams iconChoiceParams = new LinearLayout.LayoutParams(-2, -2);
        iconChoiceParams.setMarginStart(Theme.dpToPx(context, 16f));
        row.iconChoiceView.setOnClickListener(ignored -> showIconDialog(row));
        choices.addView(row.iconChoiceView, iconChoiceParams);

        labels.addView(choices, new LinearLayout.LayoutParams(-1, -2));

        row.visibleSwitch = new NewXSettingsUi.SwitchControl(context);
        row.visibleSwitch.setInteractive(true);
        row.visibleSwitch.setContentDescription(StringRef.str("piko_newx_nav_editor_visible"));
        row.visibleSwitch.setOnCheckedChangeListener(checked -> {
            if (row.updating) return;
            NavBarConfig.shared().setHidden(row.tabId, !checked);
            row.refresh();
        });
        root.addView(
                row.visibleSwitch,
                new LinearLayout.LayoutParams(Theme.dpToPx(context, 52f), Theme.dpToPx(context, 32f))
        );

        row.refresh();
        return row;
    }

    private TextView choiceText(Context context) {
        TextView view = NewXSettingsUi.summaryText(context);
        view.setPadding(
                0,
                Theme.dpToPx(context, 4f),
                Theme.dpToPx(context, 4f),
                Theme.dpToPx(context, 4f)
        );
        NewXSettingsUi.applyRippleBackground(view);
        return view;
    }

    private void startDrag(Row row) {
        ClipData data = ClipData.newPlainText(DRAG_MIME, row.tabId);
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(row.root);
        row.root.startDragAndDrop(data, shadow, row, 0);
    }

    private boolean onDrag(View view, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED: {
                Object state = event.getLocalState();
                if (state instanceof Row row) row.root.setAlpha(0.4f);
                return true;
            }
            case DragEvent.ACTION_DRAG_LOCATION: {
                Object state = event.getLocalState();
                if (!(state instanceof Row row)) return true;
                int target = rowIndexAt(event.getY());
                if (target >= 0) moveRow(row, target);
                return true;
            }
            case DragEvent.ACTION_DROP:
            case DragEvent.ACTION_DRAG_ENDED: {
                Object state = event.getLocalState();
                if (state instanceof Row row) {
                    row.root.setAlpha(1f);
                    row.refresh();
                }
                persistOrder();
                return true;
            }
            default:
                return false;
        }
    }

    private int rowIndexAt(float y) {
        if (rows.isEmpty()) return -1;
        if (y < rows.get(0).root.getTop()) return 0;
        int lastIndex = rows.size() - 1;
        if (y > rows.get(lastIndex).root.getBottom()) return lastIndex;
        for (int index = 0; index <= lastIndex; index++) {
            View rowView = rows.get(index).root;
            if (y >= rowView.getTop() && y <= rowView.getBottom()) return index;
        }
        return -1;
    }

    private void moveRow(Row row, int target) {
        int current = rows.indexOf(row);
        if (current < 0 || current == target) return;

        View divider = row.divider;
        if (divider == null) return;

        rowsContainer.removeView(row.root);
        rowsContainer.removeView(divider);
        rows.remove(current);
        rows.add(target, row);
        int childIndex = Math.min(target * 2, rowsContainer.getChildCount());
        rowsContainer.addView(row.root, childIndex);
        rowsContainer.addView(divider, childIndex + 1);
    }

    private void persistOrder() {
        List<String> order = new ArrayList<>(rows.size());
        for (Row row : rows) order.add(row.tabId);
        NavBarConfig.shared().saveOrder(order);
    }

    private void showDestinationDialog(Row row) {
        if (!row.opensView.isEnabled()) return;
        Context context = requireContext();
        NavBarConfig config = NavBarConfig.shared();
        String current = config.destinationFor(row.tabId);

        DialogView dialog =
                new DialogView(context)
                        .setTitle(StringRef.str("piko_newx_nav_editor_opens_dialog"));
        dialog.getDialog().setCanceledOnTouchOutside(true);

        LinearLayout options = optionList(context);
        dialog.setScrollableBodyView(options);
        addOption(
                context,
                options,
                StringRef.str("piko_newx_nav_editor_opens_default"),
                0,
                current == null,
                () -> {
                    config.setReplacement(row.tabId, "", config.iconFor(row.tabId));
                    dialog.dismiss();
                    row.refresh();
                }
        );
        for (NavBarCatalog.Destination destination : NavBarCatalog.destinations()) {
            addOption(
                    context,
                    options,
                    context.getString(destination.titleResourceId),
                    destination.drawableRes,
                    destination.id.equals(current),
                    () -> {
                        config.setReplacement(
                                row.tabId,
                                destination.id,
                                config.iconFor(row.tabId)
                        );
                        dialog.dismiss();
                        row.refresh();
                    }
            );
        }

        ButtonView cancel =
                NewXSettingsUi.dialogButton(
                        context,
                        StringRef.str("piko_newx_settings_cancel")
                );
        cancel.setOnClickListener(ignored -> dialog.dismiss());
        dialog.addButton(cancel).show();
    }

    private void showIconDialog(Row row) {
        if (!row.iconChoiceView.isEnabled()) return;
        Context context = requireContext();
        NavBarConfig config = NavBarConfig.shared();
        String current = config.iconFor(row.tabId);

        DialogView dialog =
                new DialogView(context)
                        .setTitle(StringRef.str("piko_newx_nav_editor_icon_dialog"));
        dialog.getDialog().setCanceledOnTouchOutside(true);

        LinearLayout options = optionList(context);
        dialog.setScrollableBodyView(options);
        NavBarCatalog.Destination destination = NavBarCatalog.destination(current);
        int destinationDrawable = destination == null ? tabDrawable(row.tabId) : destination.drawableRes;
        addOption(
                context,
                options,
                StringRef.str("piko_newx_nav_editor_icon_destination"),
                destinationDrawable,
                NavBarConfig.ICON_DESTINATION.equals(current),
                () -> {
                    dialog.dismiss();
                    setIcon(row, NavBarConfig.ICON_DESTINATION);
                }
        );
        addOption(
                context,
                options,
                StringRef.str("piko_newx_nav_editor_icon_original"),
                tabDrawable(row.tabId),
                NavBarConfig.ICON_ORIGINAL.equals(current),
                () -> {
                    dialog.dismiss();
                    setIcon(row, NavBarConfig.ICON_ORIGINAL);
                }
        );
        for (NavBarCatalog.Icon icon : NavBarCatalog.icons()) {
            addOption(
                    context,
                    options,
                    StringRef.str(icon.labelResourceName),
                    icon.drawableRes,
                    icon.id.equals(current),
                    () -> {
                        dialog.dismiss();
                        setIcon(row, icon.id);
                    }
            );
        }

        ButtonView cancel =
                NewXSettingsUi.dialogButton(
                        context,
                        StringRef.str("piko_newx_settings_cancel")
                );
        cancel.setOnClickListener(ignored -> dialog.dismiss());
        dialog.addButton(cancel).show();
    }

    private static LinearLayout optionList(Context context) {
        LinearLayout options = new LinearLayout(context);
        options.setOrientation(LinearLayout.VERTICAL);
        return options;
    }

    private static void addOption(
            Context context,
            LinearLayout options,
            CharSequence title,
            int iconResource,
            boolean selected,
            Runnable onSelected
    ) {
        NewXSettingsUi.ChoiceRow option =
                NewXSettingsUi.choiceRow(context, title, iconResource, selected, false);
        option.setOnCheckedChangeListener(checked -> {
            if (!checked) return;
            onSelected.run();
        });
        options.addView(option, new LinearLayout.LayoutParams(-1, -2));
    }

    private void setIcon(Row row, String iconId) {
        NavBarConfig config = NavBarConfig.shared();
        config.setReplacement(row.tabId, config.destinationFor(row.tabId), iconId);
        row.refresh();
    }

    private int effectiveDrawable(String tabId) {
        NavBarConfig config = NavBarConfig.shared();
        String destinationId = config.destinationFor(tabId);
        int fallback = tabDrawable(tabId);
        if (destinationId == null) return fallback;

        String iconId = config.iconFor(tabId);
        if (NavBarConfig.ICON_ORIGINAL.equals(iconId)) return fallback;
        if (NavBarConfig.ICON_DESTINATION.equals(iconId)) {
            NavBarCatalog.Destination destination = NavBarCatalog.destination(destinationId);
            return destination == null ? fallback : destination.drawableRes;
        }
        NavBarCatalog.Icon icon = NavBarCatalog.icon(iconId);
        return icon == null ? fallback : icon.drawableRes;
    }

    private int tabDrawable(String tabId) {
        NavBarCatalog.Tab tab = NavBarCatalog.tab(tabId);
        return tab == null ? 0 : tab.drawableRes;
    }

    private Context requireContext() {
        Context context = getActivity();
        if (context == null) throw new IllegalStateException("Navigation bar editor is detached");
        return context;
    }

    private final class Row {
        final String tabId;
        LinearLayout root;
        TextView handle;
        ImageView iconView;
        TextView titleView;
        TextView opensView;
        TextView iconChoiceView;
        NewXSettingsUi.SwitchControl visibleSwitch;
        View divider;
        boolean updating;

        Row(String tabId) {
            this.tabId = tabId;
        }

        void refresh() {
            NavBarConfig config = NavBarConfig.shared();
            iconView.setImageResource(effectiveDrawable(tabId));

            NavBarCatalog.Tab tab = NavBarCatalog.tab(tabId);
            titleView.setText(tab == null ? tabId : StringRef.str(tab.labelResourceName));

            String destinationId = config.destinationFor(tabId);
            boolean hasDestination = destinationId != null;
            boolean visible = !config.isHidden(tabId);
            boolean iconEnabled = visible && hasDestination;

            opensView.setText(
                    StringRef.str("piko_newx_nav_editor_opens", destinationLabel(destinationId))
            );
            opensView.setEnabled(visible);
            opensView.setAlpha(visible ? 1f : 0.5f);

            iconChoiceView.setEnabled(iconEnabled);
            iconChoiceView.setAlpha(iconEnabled ? 1f : 0.5f);
            iconChoiceView.setText(
                    StringRef.str("piko_newx_nav_editor_icon", iconLabel(config.iconFor(tabId)))
            );

            float alpha = visible ? 1f : 0.5f;
            handle.setAlpha(alpha);
            iconView.setAlpha(alpha);
            titleView.setAlpha(alpha);

            updating = true;
            visibleSwitch.setChecked(visible, true);
            updating = false;
        }

        private String destinationLabel(@Nullable String destinationId) {
            if (destinationId == null) {
                return StringRef.str("piko_newx_nav_editor_opens_default");
            }
            NavBarCatalog.Destination destination = NavBarCatalog.destination(destinationId);
            if (destination == null) return destinationId;
            return requireContext().getString(destination.titleResourceId);
        }

        private String iconLabel(String iconId) {
            if (iconId == null || iconId.isEmpty() || NavBarConfig.ICON_DESTINATION.equals(iconId)) {
                return StringRef.str("piko_newx_nav_editor_icon_destination");
            }
            if (NavBarConfig.ICON_ORIGINAL.equals(iconId)) {
                return StringRef.str("piko_newx_nav_editor_icon_original");
            }
            NavBarCatalog.Icon icon = NavBarCatalog.icon(iconId);
            return icon == null ? iconId : StringRef.str(icon.labelResourceName);
        }
    }
}
