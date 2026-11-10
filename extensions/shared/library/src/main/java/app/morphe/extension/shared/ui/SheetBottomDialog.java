package app.morphe.extension.shared.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.morphe.extension.shared.Utils;

/**
 * A utility class for creating a bottom sheet dialog that slides up from the bottom of the screen.
 * The dialog supports drag-to-dismiss functionality, animations, and nested scrolling for scrollable content.
 */
@SuppressWarnings("unused")
public class SheetBottomDialog {

    public static SlideDialog createSlideDialog(@NonNull Context context, @NonNull View contentView, int animationDuration) {
        SlideDialog dialog = new SlideDialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout wrapperLayout = new LinearLayout(context);
        wrapperLayout.setOrientation(LinearLayout.VERTICAL);

        // Drag container.
        DraggableLinearLayout dragContainer = new DraggableLinearLayout(context, animationDuration);
        dragContainer.setOrientation(LinearLayout.VERTICAL);
        dragContainer.setDialog(dialog);

        // Top spacer - drag handle and tap-to-dismiss area above the sheet content.
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Dim.dp40));
        spacer.setOnClickListener(v -> dialog.cancel());
        dragContainer.addView(spacer);

        // Content view.
        ViewGroup parent = (ViewGroup) contentView.getParent();
        if (parent != null) parent.removeView(contentView);
        dragContainer.addView(contentView);

        wrapperLayout.addView(dragContainer);
        dialog.setContentView(wrapperLayout);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setWindowAnimations(0);
            Utils.setDialogWindowParameters(window, Gravity.BOTTOM, 0, 100, false);
        }

        dialog.setAnimView(dragContainer);
        dialog.setAnimationDuration(animationDuration);

        return dialog;
    }

    public static DraggableLinearLayout createMainLayout(@NonNull Context context, @Nullable Integer backgroundColor) {
        DraggableLinearLayout mainLayout = new DraggableLinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(Dim.dp8, 0, Dim.dp8, Dim.dp8);
        mainLayout.setLayoutParams(layoutParams);

        ShapeDrawable background = new ShapeDrawable(new RoundRectShape(
                Dim.roundedCorners(12), null, null));
        int color = (backgroundColor != null) ? backgroundColor : (Utils.isDarkModeEnabled() ? 0xFF080808 : Color.WHITE);
        background.getPaint().setColor(color);
        mainLayout.setBackground(background);

        // Add handle bar.
        LinearLayout handleContainer = new LinearLayout(context);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.setMargins(0, Dim.dp8, 0, 0);
        handleContainer.setLayoutParams(containerParams);
        handleContainer.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        View handleBar = new View(context);
        ShapeDrawable handleBackground = new ShapeDrawable(new RoundRectShape(
                Dim.roundedCorners(4), null, null));
        handleBackground.getPaint().setColor(Utils.adjustColorBrightness(color, 0.9f, 1.25f));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(Dim.dp40, Dim.dp4);
        handleBar.setLayoutParams(handleParams);
        handleBar.setBackground(handleBackground);

        handleContainer.addView(handleBar);
        mainLayout.addView(handleContainer);

        return mainLayout;
    }

    public static class DraggableLinearLayout extends LinearLayout {
        private static final int MIN_FLING_VELOCITY = 800; // px/s
        private static final float DISMISS_HEIGHT_FRACTION = 0.5f; // 50% of height.

        private float initialTouchRawY; // Raw Y on ACTION_DOWN.
        private float dragOffset; // Current drag translation.
        private boolean isDragging;
        private boolean isDragEnabled;

        private final int animationDuration;
        private final Scroller scroller;
        private final VelocityTracker velocityTracker;
        private final Runnable settleRunnable;

        private SlideDialog dialog;
        private float dismissThreshold;

        /**
         * Constructs a new {@link DraggableLinearLayout} with the specified context.
         */
        public DraggableLinearLayout(@NonNull Context context) {
            this(context, 0);
        }

        public DraggableLinearLayout(@NonNull Context context, int animDuration) {
            super(context);
            scroller = new Scroller(context, new DecelerateInterpolator());
            velocityTracker = VelocityTracker.obtain();
            this.animationDuration = animDuration;
            settleRunnable = this::runSettleAnimation;

            setClickable(true);

            // Enable drag only after slide-in animation finishes.
            isDragEnabled = false;
            postDelayed(() -> isDragEnabled = true, animDuration + 50);
        }

        public void setDialog(@NonNull SlideDialog dialog) {
            this.dialog = dialog;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            dismissThreshold = h * DISMISS_HEIGHT_FRACTION;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (!isDragEnabled) return false;

            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouchRawY = ev.getRawY();
                    isDragging = false;
                    scroller.forceFinished(true);
                    removeCallbacks(settleRunnable);
                    velocityTracker.clear();
                    velocityTracker.addMovement(ev);
                    dragOffset = getTranslationY();
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dy = ev.getRawY() - initialTouchRawY;
                    if (dy > ViewConfiguration.get(getContext()).getScaledTouchSlop()
                            && !canChildScrollUp()) {
                        isDragging = true;
                        return true; // Intercept touches for drag.
                    }
                    break;
            }
            return false;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (!isDragEnabled) return super.onTouchEvent(ev);
            velocityTracker.addMovement(ev);

            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (isDragging) {
                        float deltaY = ev.getRawY() - initialTouchRawY;
                        dragOffset = Math.max(0, deltaY); // Prevent upward drag.
                        setTranslationY(dragOffset); // 1:1 following finger.
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    velocityTracker.computeCurrentVelocity(1000);
                    float velocityY = velocityTracker.getYVelocity();

                    if (dragOffset > dismissThreshold || velocityY > MIN_FLING_VELOCITY) {
                        startDismissAnimation();
                    } else {
                        startReturnAnimation();
                    }
                    isDragging = false;
                    return true;
            }
            // Consume the touch event to prevent focus changes on child views.
            return true;
        }

        private void startDismissAnimation() {
            scroller.startScroll(0, (int) dragOffset,
                    0, getHeight() - (int) dragOffset, animationDuration);
            post(settleRunnable);
        }

        private void startReturnAnimation() {
            scroller.startScroll(0, (int) dragOffset,
                    0, -(int) dragOffset, animationDuration);
            post(settleRunnable);
        }

        private void runSettleAnimation() {
            if (scroller.computeScrollOffset()) {
                dragOffset = scroller.getCurrY();
                setTranslationY(dragOffset);

                if (dragOffset >= getHeight() && dialog != null) {
                    dialog.dismiss();
                    scroller.forceFinished(true);
                } else {
                    post(settleRunnable);
                }
            } else {
                dragOffset = getTranslationY();
            }
        }

        /**
         * Checks if any child view can scroll upward, preventing drag if scrolling is possible.
         *
         * @return True if a child can scroll upward, false otherwise.
         */
        private boolean canChildScrollUp() {
            View target = findScrollableChild(this);
            return target != null && target.canScrollVertically(-1);
        }

        private View findScrollableChild(ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.canScrollVertically(-1)) return child;
                if (child instanceof ViewGroup) {
                    View scroll = findScrollableChild((ViewGroup) child);
                    if (scroll != null) return scroll;
                }
            }
            return null;
        }
    }

    public static class SlideDialog extends Dialog {
        private View animView;
        private boolean isDismissing = false;
        private int duration;
        private final int screenHeight;

        public SlideDialog(@NonNull Context context) {
            super(context);
            screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        }

        public void setAnimView(@NonNull View view) {
            this.animView = view;
        }

        public void setAnimationDuration(int duration) {
            this.duration = duration;
        }

        @Override
        public void show() {
            super.show();
            if (animView == null) return;
            animView.setTranslationY(screenHeight);
            animView.animate()
                    .translationY(0)
                    .setDuration(duration)
                    .setListener(null)
                    .start();
        }

        @Override
        public void cancel() {
            super.cancel();
        }

        @Override
        public void dismiss() {
            if (isDismissing) return;
            isDismissing = true;

            if (animView == null) {
                super.dismiss();
                isDismissing = false;
                return;
            }

            animView.animate()
                    .translationY(screenHeight)
                    .setDuration(duration)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            SlideDialog.super.dismiss();
                            isDismissing = false;
                        }
                    })
                    .start();
        }
    }
}
