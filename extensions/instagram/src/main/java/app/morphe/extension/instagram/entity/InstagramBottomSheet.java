/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.entity;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.utils.Pref;

/**
 * A lightweight bottom-sheet style dialog that visually matches Instagram's native
 * "more options" sheet (rounded top corners, drag handle, slide-up animation, dark theme,
 * colored square icons per row) without needing to reflect into Instagram's own obfuscated
 * dialog classes.
 *
 * Fully self-contained — safe against Instagram app updates/obfuscation changes.
 */
public class InstagramBottomSheet {

    private static final int BACKGROUND_COLOR = Color.parseColor("#1C1C1E");
    private static final int HANDLE_COLOR = Color.parseColor("#4D4D4D");
    private static final int TEXT_COLOR = Color.WHITE;
    private static final int DESTRUCTIVE_TEXT_COLOR = Color.parseColor("#ED4956");
    // Neutral badge color used when the user turns on "Monochrome icons" —
    // keeps every row's icon square the same shade instead of each type
    // having its own bright color.
    private static final int MONOCHROME_BADGE_COLOR = Color.parseColor("#3A3A3C");

    /** Something that can be run when a row is tapped; may throw — caught centrally. */
    public interface OnItemClick {
        void onClick() throws Exception;
    }

    /** What to draw inside a row's colored square icon. */
    public enum IconType { PERSON, TEXT, DOCUMENT, DOWNLOAD, REFRESH, PHOTO, MUSIC, LAYERS }

    /** Describes a row's leading icon: shape/glyph + background color. */
    public static class IconSpec {
        final IconType type;
        final String label;
        final int color;

        private IconSpec(IconType type, String label, int color) {
            this.type = type;
            this.label = label;
            this.color = color;
        }

        public static IconSpec person(int color) { return new IconSpec(IconType.PERSON, null, color); }
        public static IconSpec text(String label, int color) { return new IconSpec(IconType.TEXT, label, color); }
        public static IconSpec document(int color) { return new IconSpec(IconType.DOCUMENT, null, color); }
        public static IconSpec download(int color) { return new IconSpec(IconType.DOWNLOAD, null, color); }
        public static IconSpec refresh(int color) { return new IconSpec(IconType.REFRESH, null, color); }
        public static IconSpec photo(int color) { return new IconSpec(IconType.PHOTO, null, color); }
        public static IconSpec music(int color) { return new IconSpec(IconType.MUSIC, null, color); }
        public static IconSpec layers(int color) { return new IconSpec(IconType.LAYERS, null, color); }
    }

    private final Context context;
    private final Dialog dialog;
    private final LinearLayout container;

    public InstagramBottomSheet(Context context) {
        this.context = context;
        this.dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar);

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackground(roundedTopBackground());
        container.setPadding(0, dp(8), 0, dp(24));

        View handle = new View(context);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = dp(10);
        handleParams.bottomMargin = dp(10);
        handle.setBackground(pillDrawable());
        container.addView(handle, handleParams);

        dialog.setContentView(container);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.gravity = Gravity.BOTTOM;
            window.setAttributes(lp);
        }

        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
    }

    /** Optional bold title row at the top (below the drag handle). */
    public void setTitle(CharSequence title) {
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(TEXT_COLOR);
        titleView.setTextSize(18);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(dp(24), dp(4), dp(24), dp(14));
        container.addView(titleView, 1); // right after the drag handle
    }

    /** Row without an icon. */
    public void addItem(CharSequence text, OnItemClick onClick) {
        addItem(text, null, onClick, false);
    }

    /** Row with a colored icon badge. */
    public void addItem(CharSequence text, IconSpec icon, OnItemClick onClick) {
        addItem(text, icon, onClick, false);
    }

    /** Row with a colored icon badge; destructive=true renders the label in red. */
    public void addItem(CharSequence text, IconSpec icon, OnItemClick onClick, boolean destructive) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(24), dp(10), dp(24), dp(10));

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        if (outValue.resourceId != 0) {
            row.setBackgroundResource(outValue.resourceId);
        }

        if (icon != null) {
            IconBadge badge = new IconBadge(context, icon);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(36), dp(36));
            badgeParams.rightMargin = dp(16);
            row.addView(badge, badgeParams);
        }

        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(destructive ? DESTRUCTIVE_TEXT_COLOR : TEXT_COLOR);
        label.setTextSize(16);
        row.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(v -> {
            dismiss();
            try {
                if (onClick != null) onClick.onClick();
            } catch (Exception e) {
                Logger.printException(() -> "Error handling bottom sheet item click", e);
            }
        });

        container.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    public void show() {
        dialog.show();
        View decor = dialog.getWindow() != null ? dialog.getWindow().getDecorView() : null;
        if (decor != null) {
            decor.setTranslationY(dp(400));
            decor.animate()
                    .translationY(0)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public Dialog getDialog() {
        return dialog;
    }

    // ---------- helpers ----------

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    private GradientDrawable roundedTopBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(BACKGROUND_COLOR);
        float r = dp(16);
        drawable.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return drawable;
    }

    private GradientDrawable pillDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(HANDLE_COLOR);
        drawable.setCornerRadius(dp(2));
        return drawable;
    }

    /** Small square view: colored rounded background + a simple white glyph/letters. */
    private static class IconBadge extends View {
        private final IconSpec spec;
        private final Paint strokePaint;
        private final Paint fillPaint;
        private final Paint textPaint;

        IconBadge(Context context, IconSpec spec) {
            super(context);
            this.spec = spec;

            GradientDrawable bg = new GradientDrawable();
            boolean monochrome = false;
            try {
                monochrome = Pref.monochromeMoreOptionsIcons();
            } catch (Exception ignored) {
                // Fall back to the per-type color if the pref can't be read for any reason.
            }
            bg.setColor(monochrome ? MONOCHROME_BADGE_COLOR : spec.color);
            bg.setCornerRadius(applyDimen(context, 9));
            setBackground(bg);

            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setColor(Color.WHITE);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(applyDimen(context, 1.7f));
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);

            fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setColor(Color.WHITE);
            fillPaint.setStyle(Paint.Style.FILL);

            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
        }

        private static float applyDimen(Context context, float dpVal) {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, dpVal, context.getResources().getDisplayMetrics());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;

            switch (spec.type) {
                case TEXT: {
                    textPaint.setTextSize(h * 0.40f);
                    Paint.FontMetrics fm = textPaint.getFontMetrics();
                    float ty = cy - (fm.ascent + fm.descent) / 2f;
                    canvas.drawText(spec.label, cx, ty, textPaint);
                    break;
                }
                case PERSON: {
                    canvas.drawCircle(cx, cy - h * 0.12f, h * 0.14f, fillPaint);
                    float bw = w * 0.32f;
                    float bh = h * 0.20f;
                    RectF body = new RectF(cx - bw, cy + h * 0.05f, cx + bw, cy + h * 0.05f + bh);
                    canvas.drawRoundRect(body, bh / 2f, bh / 2f, fillPaint);
                    break;
                }
                case DOCUMENT: {
                    float dw = w * 0.28f;
                    float dh = h * 0.34f;
                    RectF doc = new RectF(cx - dw, cy - dh, cx + dw, cy + dh);
                    canvas.drawRoundRect(doc, dp2(getContext()), dp2(getContext()), strokePaint);
                    canvas.drawLine(cx - dw * 0.55f, cy - dh * 0.10f, cx + dw * 0.55f, cy - dh * 0.10f, strokePaint);
                    canvas.drawLine(cx - dw * 0.55f, cy + dh * 0.45f, cx + dw * 0.55f, cy + dh * 0.45f, strokePaint);
                    break;
                }
                case DOWNLOAD: {
                    canvas.drawLine(cx, cy - h * 0.22f, cx, cy + h * 0.08f, strokePaint);
                    Path arrow = new Path();
                    arrow.moveTo(cx - w * 0.13f, cy - h * 0.04f);
                    arrow.lineTo(cx, cy + h * 0.12f);
                    arrow.lineTo(cx + w * 0.13f, cy - h * 0.04f);
                    canvas.drawPath(arrow, strokePaint);
                    canvas.drawLine(cx - w * 0.20f, cy + h * 0.24f, cx + w * 0.20f, cy + h * 0.24f, strokePaint);
                    break;
                }
                case PHOTO: {
                    RectF frame = new RectF(cx - w * 0.28f, cy - h * 0.22f, cx + w * 0.28f, cy + h * 0.22f);
                    canvas.drawRoundRect(frame, dp2(getContext()), dp2(getContext()), strokePaint);
                    canvas.drawCircle(cx - w * 0.12f, cy - h * 0.08f, h * 0.06f, fillPaint);
                    Path mountain = new Path();
                    mountain.moveTo(cx - w * 0.22f, cy + h * 0.18f);
                    mountain.lineTo(cx - w * 0.02f, cy - h * 0.02f);
                    mountain.lineTo(cx + w * 0.10f, cy + h * 0.10f);
                    mountain.lineTo(cx + w * 0.22f, cy - h * 0.06f);
                    mountain.lineTo(cx + w * 0.22f, cy + h * 0.18f);
                    mountain.close();
                    canvas.drawPath(mountain, fillPaint);
                    break;
                }
                case MUSIC: {
                    canvas.drawCircle(cx - w * 0.14f, cy + h * 0.16f, h * 0.11f, fillPaint);
                    canvas.drawLine(cx - w * 0.03f, cy + h * 0.16f, cx - w * 0.03f, cy - h * 0.22f, strokePaint);
                    Path flag = new Path();
                    flag.moveTo(cx - w * 0.03f, cy - h * 0.22f);
                    flag.quadTo(cx + w * 0.22f, cy - h * 0.14f, cx - w * 0.03f, cy - h * 0.02f);
                    canvas.drawPath(flag, strokePaint);
                    break;
                }
                case LAYERS: {
                    float layerW = w * 0.30f;
                    float layerH = h * 0.10f;
                    for (int i = 0; i < 3; i++) {
                        float offsetY = cy - h * 0.16f + i * (h * 0.16f);
                        Path diamond = new Path();
                        diamond.moveTo(cx, offsetY - layerH);
                        diamond.lineTo(cx + layerW, offsetY);
                        diamond.lineTo(cx, offsetY + layerH);
                        diamond.lineTo(cx - layerW, offsetY);
                        diamond.close();
                        if (i == 2) {
                            canvas.drawPath(diamond, fillPaint);
                        } else {
                            canvas.drawPath(diamond, strokePaint);
                        }
                    }
                    break;
                }
                case REFRESH: {
                    RectF oval = new RectF(cx - w * 0.20f, cy - h * 0.20f, cx + w * 0.20f, cy + h * 0.20f);
                    canvas.drawArc(oval, -40, 280, false, strokePaint);
                    double rad = Math.toRadians(-40);
                    float ax = (float) (cx + w * 0.20f * Math.cos(rad));
                    float ay = (float) (cy + h * 0.20f * Math.sin(rad));
                    Path tri = new Path();
                    tri.moveTo(ax, ay - h * 0.07f);
                    tri.lineTo(ax + w * 0.09f, ay);
                    tri.lineTo(ax - w * 0.02f, ay + h * 0.08f);
                    tri.close();
                    canvas.drawPath(tri, fillPaint);
                    break;
                }
            }
        }

        private static float dp2(Context context) {
            return applyDimen(context, 2f);
        }
    }
}
