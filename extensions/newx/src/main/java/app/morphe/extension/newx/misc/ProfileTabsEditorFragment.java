package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.crimera.settings.CustomScreenFragment;
import app.morphe.extension.crimera.settings.SettingsUi;
import app.morphe.extension.crimera.ui.ButtonView;
import app.morphe.extension.crimera.ui.DialogView;
import app.morphe.extension.newx.settings.NewXSettingsActivity;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

/**
 * Drag-and-drop editor for the NewX profile tab strip. Every tab registered by the patch is listed
 * with its drag handle and a show/hide switch; rows not present on the loaded profile stay listed
 * because hiding or ordering them is a no-op until the profile shows them.
 */
@SuppressWarnings("deprecation")
public final class ProfileTabsEditorFragment extends CustomScreenFragment {
    private static final String DRAG_MIME = "piko/newx-profile-tab";
    private static final long DRAG_SCROLL_FRAME_DELAY_MS = 16L;
    private static final float DRAG_SCROLL_EDGE_DP = 80f;

    private final List<Row> rows = new ArrayList<>();
    private DropIndicatorLayout rowsContainer;
    private ScrollView scrollView;
    private ButtonView restartButton;
    private boolean dropHandled;
    private boolean hasPendingChanges;
    @Nullable private Row draggingRow;
    private float dragViewportY;
    private final Runnable dragScrollRunnable = new Runnable() {
        @Override
        public void run() {
            Row row = draggingRow;
            if (row == null || rowsContainer == null || scrollView == null) return;
            scrollForDrag(row);
            rowsContainer.postDelayed(this, DRAG_SCROLL_FRAME_DELAY_MS);
        }
    };

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        Context context = requireContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SettingsUi.backgroundColor(context));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(SettingsUi.backgroundColor(context));
        scrollView = scroll;
        rowsContainer = new DropIndicatorLayout(context);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        rowsContainer.setOnDragListener(this::onDrag);
        scroll.addView(rowsContainer, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(SettingsUi.divider(context));
        root.addView(buildRestartRow(context), new LinearLayout.LayoutParams(-1, -2));

        rebuildRows();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if (activity instanceof NewXSettingsActivity settingsActivity) {
            settingsActivity.setPageTitle(StringRef.str("piko_newx_profile_tabs_editor_title"));
        }
        rebuildRows();
    }

    private void rebuildRows() {
        DropIndicatorLayout container = rowsContainer;
        if (container == null) return;

        container.removeAllViews();
        rows.clear();

        Context context = requireContext();
        TextView hint = SettingsUi.summaryText(context);
        hint.setText(StringRef.str("piko_newx_profile_tabs_editor_hint"));
        hint.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 16f),
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 12f)
        );
        container.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        for (String tabId : orderedTabIds()) {
            Row row = createRow(context, tabId);
            rows.add(row);
            container.addView(row.root, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    /**
     * Registered tabs in the stored order, then any registered tab the stored order does not know.
     * Stored ids this build no longer registers are omitted; reordering rewrites the stored order
     * without them.
     */
    private List<String> orderedTabIds() {
        return ProfileTabsConfig.shared().orderedTabs(ProfileTabsCatalog.tabIds());
    }

    private Row createRow(Context context, String tabId) {
        Row row = new Row(tabId);

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

        row.handle = SettingsUi.summaryText(context);
        row.handle.setText("\u2261");
        row.handle.setTextSize(22f);
        row.handle.setGravity(Gravity.CENTER);
        row.handle.setContentDescription(StringRef.str("piko_newx_profile_tabs_editor_drag"));
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

        row.titleView = SettingsUi.titleText(context);
        row.titleView.setText(tabTitle(context, tabId));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMarginStart(Theme.dpToPx(context, 16f));
        root.addView(row.titleView, titleParams);
        row.titleView.setOnTouchListener(touchRecorder);
        row.titleView.setOnLongClickListener(dragListener);

        SettingsUi.SwitchRow shown = SettingsUi.switchRow(
                context,
                "",
                null,
                !ProfileTabsConfig.shared().isHidden(tabId)
        );
        shown.setOnCheckedChangeListener(checked -> {
            ProfileTabsConfig.shared().setHidden(tabId, !checked);
            markChanged();
        });
        row.shown = shown;
        root.addView(row.shown, new LinearLayout.LayoutParams(-2, -2));

        SettingsUi.applyRippleBackground(root);
        return row;
    }

    private static CharSequence tabTitle(Context context, String tabId) {
        ProfileTabsCatalog.Tab tab = ProfileTabsCatalog.tab(tabId);
        if (tab == null || tab.titleResourceId == 0) return tabId;
        try {
            return context.getString(tab.titleResourceId);
        } catch (Exception ignored) {
            return tabId;
        }
    }

    private void startDrag(Row row) {
        dropHandled = false;
        ClipData data = ClipData.newPlainText(DRAG_MIME, row.tabId);
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
                draggingRow = row;
                row.root.setAlpha(0.4f);
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                dragViewportY = event.getY() - scrollView.getScrollY();
                rowsContainer.removeCallbacks(dragScrollRunnable);
                if (scrollForDrag(row)) {
                    rowsContainer.postDelayed(dragScrollRunnable, DRAG_SCROLL_FRAME_DELAY_MS);
                }
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                stopDragScrolling();
                rowsContainer.clearDropIndicator();
                return true;
            case DragEvent.ACTION_DROP:
                stopDragScrolling();
                rowsContainer.clearDropIndicator();
                if (dropHandled) return true;
                dropHandled = true;
                applyDrop(row, event.getY());
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                stopDragScrolling();
                rowsContainer.clearDropIndicator();
                row.root.setAlpha(1f);
                return true;
            default:
                return true;
        }
    }

    private boolean scrollForDrag(Row source) {
        ScrollView scroll = scrollView;
        DropIndicatorLayout container = rowsContainer;
        if (scroll == null || container == null) return false;

        int viewportHeight = scroll.getHeight();
        int scrollRange = Math.max(0, container.getHeight() - viewportHeight);
        if (viewportHeight <= 0 || scrollRange == 0) {
            updateDropIndicator(dragViewportY + scroll.getScrollY());
            return false;
        }

        float edge = Math.min(
                Theme.dpToPx(requireContext(), DRAG_SCROLL_EDGE_DP),
                viewportHeight / 3f
        );
        int maxStep = Math.max(1, Theme.dpToPx(requireContext(), 24f));
        float distanceFromEdge;
        int direction;
        if (dragViewportY < edge) {
            distanceFromEdge = edge - dragViewportY;
            direction = -1;
        } else if (dragViewportY > viewportHeight - edge) {
            distanceFromEdge = dragViewportY - (viewportHeight - edge);
            direction = 1;
        } else {
            updateDropIndicator(dragViewportY + scroll.getScrollY());
            return false;
        }

        float intensity = Math.min(1f, distanceFromEdge / edge);
        int requestedDelta = direction * Math.max(1, Math.round(maxStep * intensity));
        int oldScrollY = scroll.getScrollY();
        scroll.scrollBy(0, requestedDelta);
        int actualDelta = scroll.getScrollY() - oldScrollY;
        updateDropIndicator(dragViewportY + scroll.getScrollY());
        return actualDelta != 0;
    }

    private void stopDragScrolling() {
        draggingRow = null;
        if (rowsContainer != null) rowsContainer.removeCallbacks(dragScrollRunnable);
    }

    private void updateDropIndicator(float y) {
        rowsContainer.showInsertionLine(insertionY(insertionIndex(y)));
    }

    private int insertionIndex(float y) {
        for (int index = 0; index < rows.size(); index++) {
            View row = rows.get(index).root;
            if (y < row.getTop() + row.getHeight() / 2f) return index;
        }
        return rows.size();
    }

    private float insertionY(int targetIndex) {
        if (rows.isEmpty()) return 0f;
        if (targetIndex <= 0) return rows.get(0).root.getTop();
        if (targetIndex >= rows.size()) return rows.get(rows.size() - 1).root.getBottom();
        return rows.get(targetIndex).root.getTop();
    }

    private void applyDrop(Row source, float y) {
        int targetIndex = insertionIndex(y);
        int currentIndex = rows.indexOf(source);
        if (currentIndex < 0) return;

        rows.remove(currentIndex);
        int insertionIndex = Math.min(targetIndex, rows.size());
        if (currentIndex < targetIndex) insertionIndex = Math.max(0, insertionIndex - 1);
        if (insertionIndex == currentIndex) {
            rows.add(currentIndex, source);
            return;
        }
        rows.add(insertionIndex, source);
        markChanged();
        persistOrder();
        rebuildRows();
    }

    private void persistOrder() {
        List<String> order = new ArrayList<>(rows.size());
        for (Row row : rows) order.add(row.tabId);
        ProfileTabsConfig.shared().saveOrder(order);
    }

    private View buildRestartRow(Context context) {
        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
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
                StringRef.str("piko_newx_profile_tabs_editor_restart")
        );
        restartButton.setEnabled(hasPendingChanges);
        restartButton.setOnClickListener(ignored -> confirmRestart());
        footer.addView(restartButton, new LinearLayout.LayoutParams(-1, -2));

        // Outside the drag-and-drop container, so a row drag can never trigger the reset.
        ButtonView resetButton = new ButtonView(
                context,
                ButtonView.ButtonStyle.TONAL,
                StringRef.str("piko_newx_profile_tabs_editor_reset")
        );
        resetButton.setOnClickListener(ignored -> confirmReset());
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-1, -2);
        resetParams.topMargin = Theme.dpToPx(context, 8f);
        footer.addView(resetButton, resetParams);
        return footer;
    }

    private void confirmReset() {
        Context context = requireContext();
        DialogView dialog =
                new DialogView(context)
                        .setTitle(StringRef.str("piko_newx_profile_tabs_editor_reset_title"))
                        .setSubtitle(StringRef.str("piko_newx_profile_tabs_editor_reset_message"));
        dialog.getDialog().setCanceledOnTouchOutside(true);

        ButtonView cancel =
                SettingsUi.dialogButton(
                        context,
                        StringRef.str("piko_newx_settings_cancel")
                );
        cancel.setOnClickListener(ignored -> dialog.dismiss());

        ButtonView reset =
                SettingsUi.dialogButton(
                        context,
                        StringRef.str("piko_newx_profile_tabs_editor_reset_confirm")
                );
        reset.setOnClickListener(ignored -> {
            dialog.dismiss();
            ProfileTabsConfig.shared().reset();
            markChanged();
            rebuildRows();
        });

        dialog.addButton(cancel).addButton(reset).show();
    }

    private void confirmRestart() {
        if (restartButton == null || !restartButton.isEnabled()) return;
        Context context = requireContext();
        DialogView dialog =
                new DialogView(context)
                        .setTitle(StringRef.str("piko_newx_profile_tabs_editor_restart_title"))
                        .setSubtitle(StringRef.str("piko_newx_profile_tabs_editor_restart_message"));
        dialog.getDialog().setCanceledOnTouchOutside(true);

        ButtonView cancel =
                SettingsUi.dialogButton(
                        context,
                        StringRef.str("piko_newx_settings_cancel")
                );
        cancel.setOnClickListener(ignored -> dialog.dismiss());

        ButtonView restart =
                SettingsUi.dialogButton(
                        context,
                        StringRef.str("piko_newx_profile_tabs_editor_restart_confirm")
                );
        restart.setOnClickListener(ignored -> {
            dialog.dismiss();
            Utils.restartApp(context);
        });

        dialog.addButton(cancel).addButton(restart).show();
    }

    private Context requireContext() {
        Context context = getActivity();
        if (context == null) throw new IllegalStateException("Profile tab editor is detached");
        return context;
    }

    private static final class Row {
        final String tabId;
        LinearLayout root;
        TextView handle;
        TextView titleView;
        SettingsUi.SwitchRow shown;
        int dragTouchX = -1;
        int dragTouchY = -1;

        Row(String tabId) {
            this.tabId = tabId;
        }
    }
}
