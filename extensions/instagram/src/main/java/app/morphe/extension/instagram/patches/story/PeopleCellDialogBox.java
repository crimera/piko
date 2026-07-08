/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.story;

import android.app.Dialog;
import android.content.Context;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import com.instagram.igds.components.peoplecell.IgdsPeopleCell;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.CustomDialog;
import app.morphe.extension.shared.ui.Dim;
import static app.morphe.extension.instagram.utils.IgStr.str;

/**
 * Builds and shows a {@link CustomDialog} populated directly with pre-built
 * {@link IgdsPeopleCell} views. Shows a "No data" placeholder when the list is null or empty.
 *
 * <p>Uses the plain, centered {@link CustomDialog} instead of the {@code SheetBottomDialog}
 * bottom-sheet machinery -- no drag handling, so no drag-related rendering issues to chase.</p>
 */
@SuppressWarnings("unused")
public class PeopleCellDialogBox {
    private static final float DIALOG_WIDTH_PERCENT = 0.7f;
    /**
     * Builds and shows the dialog.
     *
     * @param context     The context used to create the dialog.
     * @param peopleCells Already-populated {@link IgdsPeopleCell} views to display.
     *                    Null or empty shows "No data".
     * @return The shown {@link Dialog}, in case the caller wants to dismiss it later.
     */
    public static void showPeopleDialog(@NonNull Context context,
                                          @Nullable ArrayList<IgdsPeopleCell> peopleCells) {
        Pair<Dialog, LinearLayout> result = CustomDialog.create(
                context,
                str("piko_vsm_title"),                      // title
                null,                                       // message (using our own content instead)
                null,                                       // editText
                str("piko_ok"),                             // okButtonText
                () -> {},                                   // okButtonOnCLick
                null,                                       // onCancelClick
                null,                                       // neutralButtonText
                null,                                       // onNeutralClick
                true,                                       // dismissDialogOnNeutralClick
                true                                        // accentOkButton
        );

        Dialog dialog = result.first;
        LinearLayout mainLayout = result.second;

        View listView = (peopleCells == null || peopleCells.isEmpty())
                ? createEmptyView(context)
                : createPeopleListView(context, peopleCells);

        // mainLayout currently holds [titleView, buttonContainer] (addContent() was a no-op
        // since message/editText were both null) -- insert our content at index 1, right
        // after the title and before the buttons.
        mainLayout.addView(listView, 1);

        // Explicitly cap the window width -- don't rely on CustomDialog's own window sizing.
        constrainWindowWidth(dialog);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    /**
     * Caps the dialog window to {@link #DIALOG_WIDTH_PERCENT} of the screen width instead of
     * letting it stretch full-width.
     */
    private static void constrainWindowWidth(@NonNull Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;

        WindowManager.LayoutParams params = window.getAttributes();
        params.width = (int) (Dim.getScreenWidth() * DIALOG_WIDTH_PERCENT);
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);
    }

    /**
     * Wraps the {@link IgdsPeopleCell} rows in a {@link ScrollView} so a longer list doesn't
     * grow the dialog past a reasonable size. No intermediate row container beyond the
     * ScrollView is needed since {@code IgdsPeopleCell} already extends {@link LinearLayout}.
     */
    private static View createPeopleListView(@NonNull Context context,
                                             @NonNull ArrayList<IgdsPeopleCell> peopleCells) {
        LinearLayout listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        for (IgdsPeopleCell cell : peopleCells) {
            // Each cell may already be attached to a parent (e.g. if reused from a
            // RecyclerView or another layout) -- detach it first so addView doesn't throw.
            ViewGroup parent = (ViewGroup) cell.getParent();
            if (parent != null) parent.removeView(cell);
            listContainer.addView(cell);
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.addView(listContainer);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, Dim.dp16);
        scrollView.setLayoutParams(params);

        return scrollView;
    }

    /**
     * Simple centered "No data" placeholder shown when the list is empty.
     */
    private static View createEmptyView(@NonNull Context context) {
        TextView emptyView = new TextView(context);
        emptyView.setText(str("piko_vsm_no_mentions"));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextSize(16);
        emptyView.setTextColor(Utils.getAppForegroundColor());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Dim.dp40, 0, Dim.dp40);
        emptyView.setLayoutParams(params);

        return emptyView;
    }
}