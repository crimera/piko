package app.morphe.extension.newx.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.morphe.extension.newx.misc.UpdateFont;

/**
 * Reusable Bottom Sheet / Tray builder and view container for NewX UI.
 * Features:
 * - Renders full width, behind the system navigation bar (edge-to-edge).
 * - Dynamic bottom padding based on the system navigation bar height.
 * - Interactive swipe/drag-down-to-dismiss gesture support.
 * - Touch-up only tap outside dismissal (prevents accidental dismissals on touch down/drag).
 * - Pure slide entrance and exit transitions (no alpha fades).
 * - Android Jetpack Compose Material 3 motion interpolation graphs:
 *   * Settle / Expand: EasingEmphasizedDecelerate (CubicBezier 0.05, 0.7, 0.1, 1.0)
 *   * Dismiss / Exit:  EasingEmphasizedAccelerate (CubicBezier 0.3, 0.0, 0.8, 0.15)
 */
public class BottomSheetView {

    private static final float DEFAULT_DIM_AMOUNT = 0.55f;

    // Android Compose / Material 3 Motion Tokens
    private static final Interpolator COMPOSE_SETTLE_INTERPOLATOR =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    ? new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
                    : new DecelerateInterpolator();

    private static final Interpolator COMPOSE_DISMISS_INTERPOLATOR =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    ? new PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f)
                    : new AccelerateInterpolator();

    private final Context context;
    private final BottomSheetDialog dialog;
    private final FrameLayout rootLayout;
    private final SheetContainer mainContainer;
    private final FrameLayout dragHandleContainer;
    private final LinearLayout headerContainer;
    private final TextView titleView;
    private final TextView subtitleView;
    private final FrameLayout bodyContainer;
    private final LinearLayout actionContainer;
    private final View topDivider;
    private final View bottomDivider;
    @Nullable
    private MaxHeightScrollView scrollableBody;
    private boolean isDismissing = false;
    private boolean canceledOnTouchOutside = true;

    public BottomSheetView(Context context) {
        this.context = context;
        this.dialog = new BottomSheetDialog(context);

        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(DEFAULT_DIM_AMOUNT);
            window.setWindowAnimations(0); // Disable framework dialog animations
            window.setGravity(Gravity.BOTTOM);

            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    window.setNavigationBarColor(Color.TRANSPARENT);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.setNavigationBarContrastEnforced(false);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // Root backdrop container (fills entire dialog window to capture touch outside)
        rootLayout = new FrameLayout(context);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Main sheet container with swipe-to-dismiss gesture support
        mainContainer = new SheetContainer(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);

        float cornerRadius = Theme.dpToPx(context, 24f);
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setCornerRadii(new float[]{cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f});
        sheetBg.setColor(Theme.surfaceContainer(context));
        mainContainer.setBackground(sheetBg);

        FrameLayout.LayoutParams mainParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        mainContainer.setLayoutParams(mainParams);

        // Tap-up-only dismissal on outside touch (with touch slop tracking to prevent accidental dismissals)
        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        final boolean[] touchDownOutside = new boolean[]{false};
        final float[] touchDownCoords = new float[]{0f, 0f};

        rootLayout.setOnTouchListener((v, event) -> {
            if (!canceledOnTouchOutside || isDismissing) return false;
            int action = event.getActionMasked();
            float rawX = event.getRawX();
            float rawY = event.getRawY();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    if (!isTouchInsideView(mainContainer, (int) rawX, (int) rawY)) {
                        touchDownOutside[0] = true;
                        touchDownCoords[0] = rawX;
                        touchDownCoords[1] = rawY;
                        return true;
                    }
                    touchDownOutside[0] = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (touchDownOutside[0]) {
                        float dx = Math.abs(rawX - touchDownCoords[0]);
                        float dy = Math.abs(rawY - touchDownCoords[1]);
                        if (dx > touchSlop || dy > touchSlop) {
                            touchDownOutside[0] = false;
                        }
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    if (touchDownOutside[0]) {
                        touchDownOutside[0] = false;
                        if (!isTouchInsideView(mainContainer, (int) rawX, (int) rawY)) {
                            dismiss();
                            return true;
                        }
                    }
                    break;

                case MotionEvent.ACTION_CANCEL:
                    touchDownOutside[0] = false;
                    break;
            }
            return false;
        });

        // 1. Drag handle indicator (pill shape 36dp x 4dp)
        dragHandleContainer = new FrameLayout(context);
        dragHandleContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        dragHandleContainer.setPadding(0, Theme.dpToPx(context, 10f), 0, Theme.dpToPx(context, 4f));

        View pill = new View(context);
        int pillWidth = Theme.dpToPx(context, 36f);
        int pillHeight = Theme.dpToPx(context, 4f);
        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(pillWidth, pillHeight, Gravity.CENTER_HORIZONTAL);
        pill.setLayoutParams(pillParams);

        GradientDrawable pillDrawable = new GradientDrawable();
        pillDrawable.setShape(GradientDrawable.RECTANGLE);
        pillDrawable.setCornerRadius(Theme.dpToPx(context, 2f));
        int pillColor = Theme.isDark(context) ? Color.rgb(60, 64, 68) : Color.rgb(207, 217, 222);
        pillDrawable.setColor(pillColor);
        pill.setBackground(pillDrawable);

        dragHandleContainer.addView(pill);
        mainContainer.addView(dragHandleContainer);

        // 2. Header Container (Title & Subtitle)
        headerContainer = new LinearLayout(context);
        headerContainer.setOrientation(LinearLayout.VERTICAL);
        headerContainer.setPadding(
                Theme.dpToPx(context, 20f),
                Theme.dpToPx(context, 6f),
                Theme.dpToPx(context, 20f),
                Theme.dpToPx(context, 10f)
        );

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setTypeface(UpdateFont.customTypefaceOr(android.graphics.Typeface.DEFAULT_BOLD));
        titleView.setTextColor(Theme.primaryText(context));
        headerContainer.addView(titleView);

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitleView.setTypeface(UpdateFont.customTypefaceOr(subtitleView.getTypeface()));
        subtitleView.setTextColor(Theme.secondaryText(context));
        subtitleView.setPadding(0, Theme.dpToPx(context, 4f), 0, 0);
        subtitleView.setVisibility(View.GONE);
        headerContainer.addView(subtitleView);

        mainContainer.addView(headerContainer);

        topDivider = createDivider();
        mainContainer.addView(topDivider);

        // 3. Body Container
        bodyContainer = new FrameLayout(context);
        mainContainer.addView(bodyContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        bottomDivider = createDivider();
        mainContainer.addView(bottomDivider);

        // 4. Action Container (initial padding with fallback navbar height)
        actionContainer = new LinearLayout(context);
        actionContainer.setOrientation(LinearLayout.HORIZONTAL);
        actionContainer.setGravity(Gravity.CENTER_VERTICAL);
        actionContainer.setVisibility(View.GONE);
        setActionBottomPadding(getNavigationBarHeight(context));
        mainContainer.addView(actionContainer);

        rootLayout.addView(mainContainer);
        dialog.setContentView(rootLayout);
        dialog.setCanceledOnTouchOutside(false);
    }

    public BottomSheetView setTitle(CharSequence title) {
        titleView.setText(title);
        return this;
    }

    public BottomSheetView setSubtitle(@Nullable CharSequence subtitle) {
        if (TextUtils.isEmpty(subtitle)) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(View.VISIBLE);
        }
        return this;
    }

    public BottomSheetView setShowDragHandle(boolean show) {
        dragHandleContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        return this;
    }

    public BottomSheetView setCanceledOnTouchOutside(boolean cancel) {
        this.canceledOnTouchOutside = cancel;
        return this;
    }

    public BottomSheetView setBodyView(View view) {
        scrollableBody = null;
        setDividersVisible(false);
        bodyContainer.removeAllViews();
        bodyContainer.addView(view);
        return this;
    }

    public BottomSheetView setScrollableBodyView(View view) {
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

    public BottomSheetView addButton(ButtonView button) {
        actionContainer.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        if (actionContainer.getChildCount() > 0) {
            params.setMarginStart(Theme.dpToPx(context, 10f));
        }
        actionContainer.addView(button, params);
        return this;
    }

    public Dialog getDialog() {
        return dialog;
    }

    public void show() {
        if (!dialog.isShowing()) {
            isDismissing = false;
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                window.setGravity(Gravity.BOTTOM);
                View decorView = window.getDecorView();
                if (decorView != null) {
                    decorView.setPadding(0, 0, 0, 0);
                    decorView.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    );
                    applyDynamicNavbarPadding(decorView);
                }
            }

            // Compose Material 3 pure slide-up entrance animation (no alpha fade)
            mainContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    mainContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                    int height = mainContainer.getHeight();
                    float startY = (height > 0 ? height : 1000) + Theme.dpToPx(context, 100f);
                    mainContainer.setTranslationY(startY);
                    mainContainer.animate()
                            .translationY(0f)
                            .setDuration(220)
                            .setInterpolator(COMPOSE_SETTLE_INTERPOLATOR)
                            .start();
                    return true;
                }
            });

            mainContainer.post(this::updateScrollableDividers);
        }
    }

    private void applyDynamicNavbarPadding(View decorView) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                decorView.setOnApplyWindowInsetsListener((v, insets) -> {
                    try {
                        int bottomInset = 0;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            bottomInset = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
                        } else {
                            bottomInset = insets.getSystemWindowInsetBottom();
                        }
                        if (bottomInset > 0) {
                            setActionBottomPadding(bottomInset);
                        }
                    } catch (Throwable ignored) {
                    }
                    return insets;
                });

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.view.WindowInsets insets = decorView.getRootWindowInsets();
                    if (insets != null) {
                        int bottomInset = 0;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            bottomInset = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
                        } else {
                            bottomInset = insets.getSystemWindowInsetBottom();
                        }
                        if (bottomInset > 0) {
                            setActionBottomPadding(bottomInset);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void setActionBottomPadding(int navbarHeightPx) {
        int padHoriz = Theme.dpToPx(context, 16f);
        int padTop = Theme.dpToPx(context, 8f);
        int baseBottom = Theme.dpToPx(context, 12f);
        if (actionContainer.getVisibility() != View.GONE) {
            actionContainer.setPadding(padHoriz, padTop, padHoriz, baseBottom + navbarHeightPx);
            mainContainer.setPadding(0, 0, 0, 0);
        } else {
            mainContainer.setPadding(0, 0, 0, baseBottom + navbarHeightPx);
        }
    }

    private static int getNavigationBarHeight(Context context) {
        try {
            int resourceId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (resourceId > 0) {
                return context.getResources().getDimensionPixelSize(resourceId);
            }
        } catch (Throwable ignored) {
        }
        return 0;
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
            if (isDismissing) return;
            isDismissing = true;
            int height = mainContainer.getHeight();
            float targetY = (height > 0 ? height : 1000) + Theme.dpToPx(context, 100f);

            mainContainer.animate()
                    .translationY(targetY)
                    .setDuration(160)
                    .setInterpolator(COMPOSE_DISMISS_INTERPOLATOR)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            dialog.superDismiss();
                        }
                    })
                    .start();
        }
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    private static boolean isTouchInsideView(View view, int rawX, int rawY) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        int w = view.getWidth();
        int h = view.getHeight();
        return rawX >= x && rawX <= (x + w) && rawY >= y && rawY <= (y + h);
    }

    /**
     * Custom Dialog that intercepts hardware/gesture back, escape, and backspace keys,
     * ensuring all exit paths trigger the smooth Compose animation.
     */
    private class BottomSheetDialog extends Dialog {
        private boolean isSuperDismissing = false;

        public BottomSheetDialog(Context context) {
            super(context);
        }

        @Override
        public void onBackPressed() {
            BottomSheetView.this.dismiss();
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_DEL) {
                event.startTracking();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_DEL) {
                if (!event.isCanceled()) {
                    BottomSheetView.this.dismiss();
                    return true;
                }
            }
            return super.onKeyUp(keyCode, event);
        }

        @Override
        public void dismiss() {
            if (isSuperDismissing) {
                super.dismiss();
            } else {
                BottomSheetView.this.dismiss();
            }
        }

        void superDismiss() {
            isSuperDismissing = true;
            try {
                super.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Custom container with swipe-down-to-dismiss gesture handling.
     */
    private class SheetContainer extends LinearLayout {
        private final int touchSlop;
        private float initialDownX = 0f;
        private float initialDownY = 0f;
        private float lastMotionY = 0f;
        private boolean isDragging = false;
        @Nullable
        private VelocityTracker velocityTracker;

        public SheetContainer(Context context) {
            super(context);
            this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (isDismissing) return true;

            int action = ev.getActionMasked();
            float x = ev.getRawX();
            float y = ev.getRawY();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    initialDownX = x;
                    initialDownY = y;
                    lastMotionY = y;
                    isDragging = false;
                    initVelocityTracker();
                    if (velocityTracker != null) {
                        velocityTracker.addMovement(ev);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dy = y - initialDownY;
                    float dx = x - initialDownX;

                    if (dy > touchSlop && dy > Math.abs(dx)) {
                        // Check if touch target is a scrollable child that can scroll up
                        if (scrollableBody != null && isTouchInsideView(scrollableBody, (int) ev.getRawX(), (int) ev.getRawY())) {
                            if (!scrollableBody.canScrollVertically(-1)) {
                                isDragging = true;
                                lastMotionY = y;
                                return true;
                            }
                        } else {
                            isDragging = true;
                            lastMotionY = y;
                            return true;
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    recycleVelocityTracker();
                    break;
            }

            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (isDismissing) return true;

            if (velocityTracker != null) {
                velocityTracker.addMovement(ev);
            }

            int action = ev.getActionMasked();
            float y = ev.getRawY();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    initialDownY = y;
                    lastMotionY = y;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float totalDy = y - initialDownY;
                    if (totalDy >= 0) {
                        setTranslationY(totalDy);
                    } else {
                        // Elastic resistance when pulling up
                        setTranslationY(totalDy * 0.15f);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float currentTranslationY = getTranslationY();
                    float yVelocity = 0f;

                    if (velocityTracker != null) {
                        velocityTracker.computeCurrentVelocity(1000);
                        yVelocity = velocityTracker.getYVelocity();
                        recycleVelocityTracker();
                    }

                    int height = getHeight();
                    boolean shouldDismiss = (yVelocity > 1000f) || (currentTranslationY > (height * 0.35f));

                    if (shouldDismiss && height > 0) {
                        animateDismiss(height);
                    } else {
                        animateCancel();
                    }
                    isDragging = false;
                    return true;
            }

            return super.onTouchEvent(ev);
        }

        private void animateDismiss(int height) {
            isDismissing = true;
            float targetY = height + Theme.dpToPx(context, 100f);

            animate()
                    .translationY(targetY)
                    .setDuration(160)
                    .setInterpolator(COMPOSE_DISMISS_INTERPOLATOR)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            dialog.superDismiss();
                        }
                    })
                    .start();
        }

        private void animateCancel() {
            animate()
                    .translationY(0f)
                    .setDuration(200)
                    .setInterpolator(COMPOSE_SETTLE_INTERPOLATOR)
                    .setListener(null)
                    .start();
        }

        private void initVelocityTracker() {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
        }

        private void recycleVelocityTracker() {
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
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
