package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.newx.settings.NewXCustomScreenFragment;
import app.morphe.extension.newx.settings.NewXSettingsActivity;
import app.morphe.extension.newx.settings.NewXSettingsUi;
import app.morphe.extension.newx.ui.ButtonView;
import app.morphe.extension.newx.ui.DialogView;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

/**
 * Drag-and-drop editor for the effective NewX bottom navigation destinations. The app still owns
 * up to five native slots underneath; this screen presents those slots as a single destination list.
 */
@SuppressWarnings("deprecation")
public final class NavBarEditorFragment extends NewXCustomScreenFragment {
    private static final String DRAG_MIME = "piko/newx-navbar-item";

    private final List<Row> shownRows = new ArrayList<>();
    private final List<Row> availableRows = new ArrayList<>();
    private DropIndicatorLayout rowsContainer;
    private ButtonView restartButton;
    private View restartFooter;
    @Nullable private TextView availableHeader;
    private boolean dropHandled;
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
        rowsContainer = new DropIndicatorLayout(context);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        rowsContainer.setOnDragListener(this::onDrag);
        scroll.addView(rowsContainer, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        restartFooter = buildRestartRow(context);
        root.addView(NewXSettingsUi.divider(context));
        root.addView(restartFooter, new LinearLayout.LayoutParams(-1, -2));

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
        DropIndicatorLayout container = rowsContainer;
        if (container == null) return;

        container.removeAllViews();
        shownRows.clear();
        availableRows.clear();

        Context context = requireContext();
        TextView hint = NewXSettingsUi.summaryText(context);
        hint.setText(StringRef.str("piko_newx_nav_editor_hint"));
        hint.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 16f),
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 12f)
        );
        container.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        NavBarConfig config = NavBarConfig.shared();
        List<String> nativeTabs = NavBarCatalog.liveTabIds();
        List<String> orderedTabs = config.orderedTabs(nativeTabs);
        Set<String> shownTabIds = new HashSet<>(config.shownTabs(nativeTabs));

        addSectionHeader(context, "piko_newx_nav_editor_shown");
        for (String tabId : orderedTabs) {
            if (!shownTabIds.contains(tabId)) continue;
            Row row = createTabRow(context, tabId, true);
            shownRows.add(row);
            addRow(container, row);
        }

        container.addView(NewXSettingsUi.divider(context));
        availableHeader = addSectionHeader(context, "piko_newx_nav_editor_available");

        for (String tabId : orderedTabs) {
            boolean shownWithoutReplacement =
                    shownTabIds.contains(tabId) && config.destinationFor(tabId) == null;
            if (shownWithoutReplacement) continue;
            Row row = createTabRow(context, tabId, false);
            availableRows.add(row);
            addRow(container, row);
        }

        Set<String> assignedDestinations = assignedDestinations(config);
        for (NavBarCatalog.Destination destination : NavBarCatalog.destinations()) {
            if (assignedDestinations.contains(destination.id)) continue;
            Row row = createDestinationRow(context, destination.id);
            availableRows.add(row);
            addRow(container, row);
        }

    }

    private TextView addSectionHeader(Context context, String titleResourceName) {
        TextView header = NewXSettingsUi.summaryText(context);
        header.setText(StringRef.str(titleResourceName));
        header.setTextColor(Theme.primaryAccent(context));
        header.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 18f),
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 8f)
        );
        rowsContainer.addView(header, new LinearLayout.LayoutParams(-1, -2));
        return header;
    }

    private void addRow(LinearLayout container, Row row) {
        container.addView(row.root, new LinearLayout.LayoutParams(-1, -2));
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
                StringRef.str("piko_newx_nav_editor_restart")
        );
        restartButton.setEnabled(hasPendingChanges);
        restartButton.setOnClickListener(ignored -> confirmRestart());
        footer.addView(restartButton, new LinearLayout.LayoutParams(-1, -2));
        return footer;
    }

    private void confirmRestart() {
        if (restartButton == null || !restartButton.isEnabled()) return;
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

    private Row createTabRow(Context context, String tabId, boolean showReplacement) {
        Row row = new Row(tabId, null, showReplacement);
        createRowView(context, row);
        return row;
    }

    private Row createDestinationRow(Context context, String destinationId) {
        Row row = new Row(null, destinationId, false);
        createRowView(context, row);
        return row;
    }

    private void createRowView(Context context, Row row) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setMinimumHeight(Theme.dpToPx(context, 64f));
        root.setPadding(
                Theme.dpToPx(context, 12f),
                Theme.dpToPx(context, 8f),
                Theme.dpToPx(context, 16f),
                Theme.dpToPx(context, 8f)
        );
        row.root = root;

        row.handle = NewXSettingsUi.summaryText(context);
        row.handle.setText("\u2261");
        row.handle.setTextSize(22f);
        row.handle.setGravity(Gravity.CENTER);
        row.handle.setContentDescription(StringRef.str("piko_newx_nav_editor_drag"));
        View.OnLongClickListener dragListener = ignored -> {
            startDrag(row);
            return true;
        };
        row.handle.setOnLongClickListener(dragListener);
        View.OnTouchListener touchRecorder = (view, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                row.dragTouchX = view.getLeft() + Math.round(event.getX());
                row.dragTouchY = view.getTop() + Math.round(event.getY());
            }
            return false;
        };
        root.setOnTouchListener(touchRecorder);
        row.handle.setOnTouchListener(touchRecorder);
        root.setOnLongClickListener(dragListener);
        root.addView(row.handle, new LinearLayout.LayoutParams(Theme.dpToPx(context, 40f), -2));

        row.iconView = new ImageView(context);
        row.iconView.setImageTintList(ColorStateList.valueOf(Theme.primaryText(context)));
        root.addView(
                row.iconView,
                new LinearLayout.LayoutParams(Theme.dpToPx(context, 24f), Theme.dpToPx(context, 24f))
        );

        row.titleView = NewXSettingsUi.titleText(context);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMarginStart(Theme.dpToPx(context, 16f));
        root.addView(row.titleView, titleParams);
        row.iconView.setOnTouchListener(touchRecorder);
        row.titleView.setOnTouchListener(touchRecorder);
        row.iconView.setOnLongClickListener(dragListener);
        row.titleView.setOnLongClickListener(dragListener);

        NewXSettingsUi.applyRippleBackground(root);
        row.refresh();
    }

    private Set<String> assignedDestinations(NavBarConfig config) {
        Set<String> assigned = new HashSet<>();
        for (String tabId : config.shownTabs(NavBarCatalog.liveTabIds())) {
            String destinationId = config.destinationFor(tabId);
            if (destinationId == null) continue;
            if (NavBarCatalog.destination(destinationId) == null) continue;
            assigned.add(destinationId);
        }
        return assigned;
    }

    private void startDrag(Row row) {
        String itemId = row.tabId == null ? row.destinationId : row.tabId;
        if (itemId == null) return;
        dropHandled = false;
        ClipData data = ClipData.newPlainText(DRAG_MIME, itemId);
        int touchPointX = row.dragTouchX;
        if (touchPointX < 0) touchPointX = Theme.dpToPx(requireContext(), 64f);
        int touchPointY = row.dragTouchY;
        if (touchPointY < 0) touchPointY = row.root.getHeight() / 2;
        View.DragShadowBuilder shadow = new RowDragShadow(row.root, touchPointX, touchPointY);
        row.root.startDragAndDrop(data, shadow, row, 0);
    }

    private void markChanged() {
        hasPendingChanges = true;
        if (restartButton != null) restartButton.setEnabled(true);
    }

    private boolean onDrag(View view, DragEvent event) {
        Object state = event.getLocalState();
        if (!(state instanceof Row row)) return false;

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                row.root.setAlpha(0.4f);
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                updateDropIndicator(row, event.getY());
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                rowsContainer.clearDropIndicator();
                return true;
            case DragEvent.ACTION_DROP:
                rowsContainer.clearDropIndicator();
                if (dropHandled) return true;
                dropHandled = true;
                applyDrop(row, event.getY());
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                rowsContainer.clearDropIndicator();
                row.root.setAlpha(1f);
                return true;
            default:
                return true;
        }
    }

    private void updateDropIndicator(Row source, float y) {
        if (!isShownSection(y)) {
            if (source.isShown() && availableHeader != null) {
                rowsContainer.showDropTarget(availableHeader);
                return;
            }
            rowsContainer.clearDropIndicator();
            return;
        }

        int targetIndex = shownInsertionIndex(y);
        boolean usesExistingSlot =
                source.tabId != null && findShownNativeRow(source.tabId) != null;
        boolean replacesTarget =
                !source.isShown()
                        && !usesExistingSlot
                        && shownRows.size() == NavBarConfig.MAX_VISIBLE_ITEMS;
        if (replacesTarget && !shownRows.isEmpty()) {
            Row target = shownRows.get(Math.min(targetIndex, shownRows.size() - 1));
            rowsContainer.showDropTarget(target.root);
            return;
        }

        rowsContainer.showInsertionLine(shownInsertionY(targetIndex));
    }

    private float shownInsertionY(int targetIndex) {
        if (shownRows.isEmpty()) {
            TextView header = availableHeader;
            return header == null ? 0f : header.getTop();
        }
        if (targetIndex <= 0) return shownRows.get(0).root.getTop();
        if (targetIndex >= shownRows.size()) {
            View lastRow = shownRows.get(shownRows.size() - 1).root;
            return lastRow.getBottom();
        }
        return shownRows.get(targetIndex).root.getTop();
    }

    private void applyDrop(Row source, float y) {
        if (isShownSection(y)) {
            int targetIndex = shownInsertionIndex(y);
            if (source.isShown()) {
                reorderShown(source, targetIndex);
                return;
            }
            if (source.isDestination()) {
                addDestinationToShown(source.destinationId, targetIndex);
                return;
            }
            showNativeTab(source.tabId, targetIndex);
            return;
        }

        if (!source.isShown()) return;
        removeFromShown(source);
    }

    private boolean isShownSection(float y) {
        TextView header = availableHeader;
        return header == null || y < header.getTop();
    }

    private int shownInsertionIndex(float y) {
        for (int index = 0; index < shownRows.size(); index++) {
            View row = shownRows.get(index).root;
            if (y < row.getTop() + row.getHeight() / 2f) return index;
        }
        return shownRows.size();
    }

    private void reorderShown(Row source, int targetIndex) {
        int currentIndex = shownRows.indexOf(source);
        if (currentIndex < 0) return;

        shownRows.remove(currentIndex);
        int insertionIndex = Math.min(targetIndex, shownRows.size());
        if (currentIndex < targetIndex) insertionIndex = Math.max(0, insertionIndex - 1);
        if (currentIndex == insertionIndex) {
            shownRows.add(currentIndex, source);
            return;
        }
        shownRows.add(insertionIndex, source);
        markChanged();
        persistLayout();
        rebuildRows();
    }

    private void addDestinationToShown(@Nullable String destinationId, int targetIndex) {
        if (destinationId == null) return;
        NavBarCatalog.Destination destination = NavBarCatalog.destination(destinationId);
        if (destination == null) return;

        NavBarConfig config = NavBarConfig.shared();
        if (shownRows.size() == NavBarConfig.MAX_VISIBLE_ITEMS) {
            if (shownRows.isEmpty()) return;
            Row target = shownRows.get(Math.min(targetIndex, shownRows.size() - 1));
            if (target.tabId == null) return;
            config.setReplacement(target.tabId, destinationId);
            markChanged();
            rebuildRows();
            return;
        }

        Row freeSlot = firstAvailableNativeRow();
        if (freeSlot == null || freeSlot.tabId == null) return;

        config.setHidden(freeSlot.tabId, false);
        config.setReplacement(freeSlot.tabId, destinationId);
        shownRows.add(Math.min(targetIndex, shownRows.size()), freeSlot);
        markChanged();
        persistLayout();
        rebuildRows();
    }

    @Nullable
    private Row firstAvailableNativeRow() {
        for (Row row : availableRows) {
            if (row.tabId != null && findShownNativeRow(row.tabId) == null) return row;
        }
        return null;
    }

    private void showNativeTab(@Nullable String tabId, int targetIndex) {
        if (tabId == null) return;
        NavBarConfig config = NavBarConfig.shared();
        Row existingSlot = findShownNativeRow(tabId);
        if (existingSlot != null) {
            config.setReplacement(tabId, null);
            int currentIndex = shownRows.indexOf(existingSlot);
            shownRows.remove(currentIndex);
            int insertionIndex = Math.min(targetIndex, shownRows.size());
            if (currentIndex < targetIndex) insertionIndex = Math.max(0, insertionIndex - 1);
            shownRows.add(insertionIndex, existingSlot);
            markChanged();
            persistLayout();
            rebuildRows();
            return;
        }

        Row source = findAvailableNativeRow(tabId);
        if (source == null) return;
        config.setReplacement(tabId, null);

        if (shownRows.size() == NavBarConfig.MAX_VISIBLE_ITEMS) {
            if (shownRows.isEmpty()) return;
            Row target = shownRows.get(Math.min(targetIndex, shownRows.size() - 1));
            if (target.tabId == null) return;
            config.setReplacement(target.tabId, null);
            config.setHidden(target.tabId, true);
            shownRows.remove(target);
        }

        config.setHidden(tabId, false);
        shownRows.add(Math.min(targetIndex, shownRows.size()), source);
        markChanged();
        persistLayout();
        rebuildRows();
    }

    @Nullable
    private Row findAvailableNativeRow(String tabId) {
        for (Row row : availableRows) {
            if (tabId.equals(row.tabId)) return row;
        }
        return null;
    }

    @Nullable
    private Row findShownNativeRow(String tabId) {
        for (Row row : shownRows) {
            if (tabId.equals(row.tabId)) return row;
        }
        return null;
    }

    private void removeFromShown(Row source) {
        if (source.tabId == null) return;
        NavBarConfig config = NavBarConfig.shared();
        String destinationId = config.destinationFor(source.tabId);
        if (destinationId != null) config.setReplacement(source.tabId, null);
        config.setHidden(source.tabId, true);
        shownRows.remove(source);
        markChanged();
        persistLayout();
        rebuildRows();
    }

    private void persistLayout() {
        NavBarConfig config = NavBarConfig.shared();
        List<String> order = new ArrayList<>();
        Set<String> shownTabIds = new HashSet<>();
        for (Row row : shownRows) {
            if (row.tabId == null) continue;
            order.add(row.tabId);
            shownTabIds.add(row.tabId);
        }
        for (Row row : availableRows) {
            if (row.tabId == null || order.contains(row.tabId)) continue;
            order.add(row.tabId);
        }
        for (String tabId : config.orderedTabs(NavBarCatalog.tabIds())) {
            if (!order.contains(tabId)) order.add(tabId);
        }

        config.saveOrder(order);
        for (String tabId : NavBarCatalog.liveTabIds()) {
            config.setHidden(tabId, !shownTabIds.contains(tabId));
        }
    }

    private final class Row {
        @Nullable final String tabId;
        @Nullable final String destinationId;
        final boolean showReplacement;
        LinearLayout root;
        TextView handle;
        ImageView iconView;
        TextView titleView;
        int dragTouchX = -1;
        int dragTouchY = -1;

        Row(
                @Nullable String tabId,
                @Nullable String destinationId,
                boolean showReplacement
        ) {
            this.tabId = tabId;
            this.destinationId = destinationId;
            this.showReplacement = showReplacement;
        }

        boolean isShown() {
            return shownRows.contains(this);
        }

        boolean isDestination() {
            return tabId == null;
        }

        void refresh() {
            Context context = requireContext();
            NavBarConfig config = NavBarConfig.shared();
            NavBarCatalog.Tab tab = tabId == null ? null : NavBarCatalog.tab(tabId);
            NavBarCatalog.Destination destination =
                    isDestination()
                            ? NavBarCatalog.destination(destinationId)
                            : showReplacement
                                    ? NavBarCatalog.destination(config.destinationFor(tabId))
                                    : null;

            if (destination != null) {
                iconView.setImageResource(destination.drawableRes);
                titleView.setText(context.getString(destination.titleResourceId));
                return;
            }
            if (tab != null) {
                iconView.setImageResource(tab.drawableRes);
                titleView.setText(StringRef.str(tab.labelResourceName));
                return;
            }

            iconView.setImageDrawable(null);
            titleView.setText(isDestination() ? destinationId : tabId);
        }
    }

    private Context requireContext() {
        Context context = getActivity();
        if (context == null) throw new IllegalStateException("Navigation bar editor is detached");
        return context;
    }

    private static final class RowDragShadow extends View.DragShadowBuilder {
        private final int touchPointX;
        private final int touchPointY;

        RowDragShadow(View view, int touchPointX, int touchPointY) {
            super(view);
            this.touchPointX = touchPointX;
            this.touchPointY = touchPointY;
        }

        @Override
        public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
            super.onProvideShadowMetrics(shadowSize, shadowTouchPoint);
            shadowTouchPoint.x = Math.max(0, Math.min(touchPointX, shadowSize.x));
            shadowTouchPoint.y = Math.max(0, Math.min(touchPointY, shadowSize.y));
        }
    }

    private static final class DropIndicatorLayout extends LinearLayout {
        private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float horizontalInset;
        private final float strokeWidth;
        private float indicatorTop = -1f;
        private float indicatorBottom = -1f;

        DropIndicatorLayout(Context context) {
            super(context);
            horizontalInset = Theme.dpToPx(context, 12f);
            strokeWidth = Theme.dpToPx(context, 3f);
            indicatorPaint.setColor(Theme.primaryAccent(context));
            indicatorPaint.setStrokeWidth(strokeWidth);
        }

        void showInsertionLine(float y) {
            indicatorTop = y;
            indicatorBottom = y;
            invalidate();
        }

        void showDropTarget(View target) {
            indicatorTop = target.getTop();
            indicatorBottom = target.getBottom();
            invalidate();
        }

        void clearDropIndicator() {
            if (indicatorTop < 0f) return;
            indicatorTop = -1f;
            indicatorBottom = -1f;
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (indicatorTop < 0f) return;

            float left = horizontalInset;
            float right = getWidth() - horizontalInset;
            float radius = strokeWidth;
            if (indicatorTop == indicatorBottom) {
                indicatorPaint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(
                        left,
                        indicatorTop - strokeWidth / 2f,
                        right,
                        indicatorTop + strokeWidth / 2f,
                        radius,
                        radius,
                        indicatorPaint
                );
                return;
            }

            indicatorPaint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(
                    left,
                    indicatorTop + strokeWidth / 2f,
                    right,
                    indicatorBottom - strokeWidth / 2f,
                    Theme.dpToPx(getContext(), 8f),
                    Theme.dpToPx(getContext(), 8f),
                    indicatorPaint
            );
        }
    }
}
