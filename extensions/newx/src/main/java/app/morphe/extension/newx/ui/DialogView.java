package app.morphe.extension.newx.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Reusable Dialog builder and view container.
 */
public class DialogView {

    private final Context context;
    private final Dialog dialog;
    private final LinearLayout mainContainer;
    private final LinearLayout headerContainer;
    private final TextView titleView;
    private final TextView subtitleView;
    private final FrameLayout bodyContainer;
    private final LinearLayout actionContainer;
    private final View topDivider;
    private final View bottomDivider;
    @Nullable
    private MaxHeightScrollView scrollableBody;

    public DialogView(Context context) {
        this.context = context;
        this.dialog = new Dialog(context);

        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.55f);
        }

        // Main card container
        mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(Theme.dpToPx(context, 28f));
        cardBg.setColor(Theme.surfaceContainerHigh(context));
        mainContainer.setBackground(cardBg);

        mainContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 1. Header Container
        headerContainer = new LinearLayout(context);
        headerContainer.setOrientation(LinearLayout.VERTICAL);
        headerContainer.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 16f)
        );

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleView.setTextColor(Theme.primaryText(context));
        headerContainer.addView(titleView);

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitleView.setTextColor(Theme.secondaryText(context));
        subtitleView.setPadding(0, Theme.dpToPx(context, 6f), 0, 0);
        subtitleView.setVisibility(View.GONE);
        headerContainer.addView(subtitleView);

        mainContainer.addView(headerContainer);

        topDivider = createDivider();
        mainContainer.addView(topDivider);

        // 2. Body Container
        bodyContainer = new FrameLayout(context);
        mainContainer.addView(bodyContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        bottomDivider = createDivider();
        mainContainer.addView(bottomDivider);

        // 3. Action Container
        actionContainer = new LinearLayout(context);
        actionContainer.setOrientation(LinearLayout.HORIZONTAL);
        actionContainer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actionContainer.setPadding(
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 12f),
                Theme.dpToPx(context, 24f),
                Theme.dpToPx(context, 24f)
        );
        mainContainer.addView(actionContainer);

        dialog.setContentView(mainContainer);
    }

    public DialogView setTitle(CharSequence title) {
        titleView.setText(title);
        return this;
    }

    public DialogView setSubtitle(@Nullable CharSequence subtitle) {
        if (TextUtils.isEmpty(subtitle)) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(View.VISIBLE);
        }
        return this;
    }

    public DialogView setBodyView(View view) {
        scrollableBody = null;
        setDividersVisible(false);
        bodyContainer.removeAllViews();
        bodyContainer.addView(view);
        return this;
    }

    public DialogView setScrollableBodyView(View view) {
        setDividersVisible(false);
        MaxHeightScrollView scrollView = new MaxHeightScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        int maxScreenHeight = context.getResources().getDisplayMetrics().heightPixels;
        scrollView.setMaxHeightPx((int) (maxScreenHeight * 0.6f));
        scrollView.addView(view);
        scrollView.addOnLayoutChangeListener((changedView, left, top, right, bottom,
                                               oldLeft, oldTop, oldRight, oldBottom) ->
                updateScrollableDividers());

        scrollableBody = scrollView;
        bodyContainer.removeAllViews();
        bodyContainer.addView(scrollView);
        return this;
    }

    public DialogView addButton(ButtonView button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        if (actionContainer.getChildCount() > 0) {
            params.setMarginStart(Theme.dpToPx(context, 8f));
        }
        actionContainer.addView(button, params);
        return this;
    }

    public Dialog getDialog() {
        return dialog;
    }

    public void show() {
        if (!dialog.isShowing()) {
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                int targetWidth = Math.min(
                        screenWidth - Theme.dpToPx(context, 56f),
                        Theme.dpToPx(context, 560f)
                );
                window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            mainContainer.post(this::updateScrollableDividers);
        }
    }

    private View createDivider() {
        View divider = new View(context);
        divider.setBackgroundColor(Theme.dividerColor(context));
        divider.setVisibility(View.GONE);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Theme.dpToPx(context, 1f)
        ));
        return divider;
    }

    private void updateScrollableDividers() {
        if (scrollableBody == null) {
            setDividersVisible(false);
            return;
        }

        boolean hasOverflow = scrollableBody.canScrollVertically(1)
                || scrollableBody.canScrollVertically(-1);
        setDividersVisible(hasOverflow);
    }

    private void setDividersVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        topDivider.setVisibility(visibility);
        bottomDivider.setVisibility(visibility);
    }

    public void dismiss() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    /** ScrollView capped at a maximum height to avoid overflowing screen. */
    private static class MaxHeightScrollView extends ScrollView {
        private int maxHeightPx = Integer.MAX_VALUE;

        MaxHeightScrollView(Context context) {
            super(context);
        }

        void setMaxHeightPx(int maxHeightPx) {
            this.maxHeightPx = maxHeightPx;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int heightSpec = heightMeasureSpec;
            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
                int heightSize = Math.min(MeasureSpec.getSize(heightMeasureSpec), maxHeightPx);
                heightSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthMeasureSpec, heightSpec);
        }
    }
}
