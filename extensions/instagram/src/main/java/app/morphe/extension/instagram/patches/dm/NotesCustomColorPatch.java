/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notes;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.ResourceUtils;

@SuppressWarnings("unused")
public class NotesCustomColorPatch {

    private static final String POG_VIEW_CLASS =
            "instagram.features.direct.inbox.notes.creation.presentation.view.NotesCreationPogView";

    private static final String DEFAULT_FALLBACK_THEME_ID = "default_custom_theme_id";

    private static View sColorButton1;
    private static View sColorButton2;
    private static ViewGroup sParent;
    private static ViewTreeObserver.OnGlobalLayoutListener sGlobalLayoutListener;

    private static volatile boolean sCustomColorEnabled = false;
    private static volatile String sCustomColorHex = null;
    private static volatile boolean sBubbleColorEnabled = false;
    private static volatile String sBubbleColorHex = null;

    private static final int[] RAINBOW_SPECTRUM_COLORS = new int[] {
            0xFFFF453A,
            0xFFFF9F0A,
            0xFFFFD60A,
            0xFF30D158,
            0xFF64D2FF,
            0xFF0A84FF,
            0xFFBF5AF2,
            0xFFFF2D55,
            0xFFFF453A
    };

    public static String injectDefaultThemeIdIfNeeded(String currentThemeId) {
        if ((sCustomColorEnabled || sBubbleColorEnabled) && currentThemeId == null) {
            return DEFAULT_FALLBACK_THEME_ID;
        }
        return currentThemeId;
    }

    public static void attachThemeButtonLongClick(View themeButton) {
        if (!Pref.enableNotesCustomTextColor()) return;

        themeButton.post(() -> addVisibleColorButton(themeButton));

        themeButton.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {}

            @Override
            public void onViewDetachedFromWindow(View v) {
                removeVisibleColorButton();
            }
        });
    }

    private static void addVisibleColorButton(View themeButton) {
        removeVisibleColorButton();

        if (!(themeButton.getParent() instanceof ViewGroup)) return;

        ViewGroup parent = (ViewGroup) themeButton.getParent();

        View pogView = findViewByClassName(themeButton.getRootView(), POG_VIEW_CLASS);
        View anchor = pogView != null ? pogView : themeButton;

        Context context = themeButton.getContext();
        int size = dp(context, 40);

        View colorButton1 = buildCircularButton(context, themeButton, "instagram_text_pano_filled_24", false);
        colorButton1.setId(View.generateViewId());

        View colorButton2 = buildCircularButton(context, themeButton, "fb_ic_thought_bubble_filled_16", true);
        colorButton2.setId(View.generateViewId());

        ViewGroup.LayoutParams params1;
        ViewGroup.LayoutParams params2;
        try {
            params1 = themeButton.getLayoutParams().getClass()
                    .getConstructor(int.class, int.class)
                    .newInstance(size, size);
            params2 = themeButton.getLayoutParams().getClass()
                    .getConstructor(int.class, int.class)
                    .newInstance(size, size);
        } catch (Exception e) {
            return;
        }

        parent.addView(colorButton1, params1);
        parent.addView(colorButton2, params2);

        ViewTreeObserver.OnGlobalLayoutListener listener =
                () -> updateColorButtonState(colorButton1, colorButton2, anchor, parent, context);
        parent.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        listener.onGlobalLayout();

        sColorButton1 = colorButton1;
        sColorButton2 = colorButton2;
        sParent = parent;
        sGlobalLayoutListener = listener;
    }

    private static void updateColorButtonState(View colorButton1, View colorButton2, View anchor, ViewGroup parent, Context context) {
        if (!anchor.isShown() || anchor.getWidth() == 0) {
            if (colorButton1.getVisibility() != View.GONE) colorButton1.setVisibility(View.GONE);
            if (colorButton2.getVisibility() != View.GONE) colorButton2.setVisibility(View.GONE);
            return;
        }

        if (colorButton1.getVisibility() != View.VISIBLE) colorButton1.setVisibility(View.VISIBLE);
        if (colorButton2.getVisibility() != View.VISIBLE) colorButton2.setVisibility(View.VISIBLE);

        int[] anchorLoc = new int[2];
        anchor.getLocationOnScreen(anchorLoc);
        int[] parentLoc = new int[2];
        parent.getLocationOnScreen(parentLoc);

        int size = dp(context, 40);
        int margin = dp(context, 4);

        float newX1 = anchorLoc[0] - parentLoc[0] + anchor.getWidth() - dp(context, 16);
        float newY = anchorLoc[1] - parentLoc[1] - dp(context, 8);
        float newX2 = newX1 + size + margin;

        if (colorButton1.getX() != newX1) colorButton1.setX(newX1);
        if (colorButton1.getY() != newY) colorButton1.setY(newY);

        if (colorButton2.getX() != newX2) colorButton2.setX(newX2);
        if (colorButton2.getY() != newY) colorButton2.setY(newY);
    }

    private static void removeVisibleColorButton() {
        if (sParent != null && sGlobalLayoutListener != null) {
            sParent.getViewTreeObserver().removeOnGlobalLayoutListener(sGlobalLayoutListener);
        }
        if (sColorButton1 != null && sColorButton1.getParent() instanceof ViewGroup) {
            ((ViewGroup) sColorButton1.getParent()).removeView(sColorButton1);
        }
        if (sColorButton2 != null && sColorButton2.getParent() instanceof ViewGroup) {
            ((ViewGroup) sColorButton2.getParent()).removeView(sColorButton2);
        }
        sColorButton1 = null;
        sColorButton2 = null;
        sParent = null;
        sGlobalLayoutListener = null;
    }

    private static View buildCircularButton(Context context, View themeButton, String iconName, boolean isBubbleColor) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0x99000000);

        ImageView colorButton = new ImageView(context);
        colorButton.setBackground(circle);
        int paddingPx = dp(context, 8);
        colorButton.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        try {
            Drawable icon = ResourceUtils.getDrawable(iconName);
            Drawable gradientIcon = applyGradient(context, icon);
            if (gradientIcon != null) colorButton.setImageDrawable(gradientIcon);
        } catch (Exception ignored) {
        }
        colorButton.setOnClickListener(v -> showColorDialog(themeButton, isBubbleColor));
        return colorButton;
    }

    private static Drawable applyGradient(Context context, Drawable icon) {
        if (icon == null) return null;
        int size = dp(context, 24);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        icon.setBounds(0, 0, size, size);
        icon.draw(canvas);

        Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float center = size / 2f;

        SweepGradient sweepGradient = new SweepGradient(center, center, RAINBOW_SPECTRUM_COLORS, null);

        Matrix matrix = new Matrix();
        matrix.postRotate(-90, center, center);
        sweepGradient.setLocalMatrix(matrix);

        gradientPaint.setShader(sweepGradient);
        gradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawRect(0, 0, size, size, gradientPaint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    private static void triggerThemeRefresh(View screenView) {
        if (screenView == null) return;
        refreshViewHierarchy(screenView.getRootView());
    }

    private static void refreshViewHierarchy(View view) {
        if (view == null) return;

        if (sCustomColorEnabled && isValidHex(sCustomColorHex) && view instanceof TextView) {
            try {
                int textColor = 0xFF000000 | (Integer.parseInt(sCustomColorHex.replace("#", ""), 16) & 0xFFFFFF);
                ((TextView) view).setTextColor(textColor);
            } catch (Exception ignored) {
            }
        }

        if (sBubbleColorEnabled && isValidHex(sBubbleColorHex) && view.getClass().getName().contains("NoteBubbleView")) {
            try {
                int bubbleColor = 0xFF000000 | (Integer.parseInt(sBubbleColorHex.replace("#", ""), 16) & 0xFFFFFF);
                if (view.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) view.getBackground()).setColor(bubbleColor);
                } else {
                    view.setBackgroundColor(bubbleColor);
                }
            } catch (Exception ignored) {
            }
        }

        view.invalidate();
        view.requestLayout();

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                refreshViewHierarchy(group.getChildAt(i));
            }
        }
    }

    private static View findViewByClassName(View view, String className) {
        if (view.getClass().getName().equals(className)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findViewByClassName(group.getChildAt(i), className);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static void showColorDialog(View themeButton, boolean isBubbleColor) {
        Context context = themeButton.getContext();
        Context themed = InstagramPreferenceStyle.dialogContext(context);

        String initialHex;
        if (isBubbleColor && isValidHex(sBubbleColorHex)) {
            initialHex = sBubbleColorHex;
        } else if (!isBubbleColor && isValidHex(sCustomColorHex)) {
            initialHex = sCustomColorHex;
        } else {
            initialHex = "#ff0000";
        }

        float[] currentHsv = new float[3];
        try {
            Color.colorToHSV(Color.parseColor(initialHex), currentHsv);
        } catch (Exception e) {
            Color.colorToHSV(0xFFFF0000, currentHsv);
        }

        final ColorPickerState state = new ColorPickerState(currentHsv[0], currentHsv[1], currentHsv[2]);

        LinearLayout contentContainer = new LinearLayout(themed);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        int padOuter = dp(context, 8);
        contentContainer.setPadding(padOuter, padOuter, padOuter, padOuter);

        LinearLayout card = new LinearLayout(themed);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int padInner = dp(context, 16);
        card.setPadding(padInner, padInner, padInner, padInner);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF000000);
        cardBg.setCornerRadius(dp(context, 24));
        card.setBackground(cardBg);
        contentContainer.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView titleView = new TextView(themed);
        titleView.setText(isBubbleColor ? str("piko_notes_bubble_color_title") : str("piko_notes_text_color_title"));
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        card.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(new View(themed), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 16)));

        SatValBoxView satValBox = new SatValBoxView(themed, state);
        LinearLayout.LayoutParams satValLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 180));
        card.addView(satValBox, satValLp);

        card.addView(new View(themed), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 16)));

        HueBarView hueBar = new HueBarView(themed, state);
        LinearLayout.LayoutParams hueLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 24));
        card.addView(hueBar, hueLp);

        card.addView(new View(themed), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 20)));

        LinearLayout hexContainer = new LinearLayout(themed);
        hexContainer.setOrientation(LinearLayout.HORIZONTAL);
        hexContainer.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(0xFF1A1A1A);
        inputBg.setStroke(dp(context, 1), 0xFF444444);
        inputBg.setCornerRadius(dp(context, 12));
        hexContainer.setBackground(inputBg);

        int inputPad = dp(context, 12);
        hexContainer.setPadding(inputPad, inputPad, inputPad, inputPad);

        TextView hashPrefix = new TextView(themed);
        hashPrefix.setText("#");
        hashPrefix.setTextColor(0xFFFFFFFF);
        hashPrefix.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hashPrefix.setPadding(0, 0, dp(context, 4), 0);

        EditText hexInput = new EditText(themed);
        hexInput.setSingleLine(true);
        hexInput.setTextColor(0xFFFFFFFF);
        hexInput.setHintTextColor(0xFF888888);
        hexInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hexInput.setBackground(null);
        hexInput.setPadding(0, 0, 0, 0);
        hexInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});

        hexContainer.addView(hashPrefix);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hexContainer.addView(hexInput, inputLp);

        card.addView(hexContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        state.listener = () -> {
            String hexStr = state.getHex();
            if (state.isUserInteracting) {
                hexInput.setText(hexStr);
            } else {
                String currentInput = hexInput.getText().toString().replace("#", "");
                if (!currentInput.equalsIgnoreCase(hexStr)) {
                    hexInput.setText(hexStr);
                    hexInput.setSelection(hexInput.getText().length());
                }
            }
            satValBox.invalidate();
            hueBar.invalidate();
        };

        hexInput.addTextChangedListener(new TextWatcher() {
            private boolean selfEditing = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (selfEditing) return;

                String raw = s.toString();

                if (raw.contains("#")) {
                    selfEditing = true;
                    String cleaned = raw.replace("#", "").trim();
                    hexInput.setText(cleaned);
                    hexInput.setSelection(cleaned.length());
                    selfEditing = false;
                    raw = cleaned;
                }

                if (state.isUserInteracting) return;

                String hex = raw.trim();
                if (hex.length() == 6) {
                    try {
                        int parsed = Color.parseColor("#" + hex);
                        float[] hsv = new float[3];
                        Color.colorToHSV(parsed, hsv);
                        state.setHsv(hsv[0], hsv[1], hsv[2], true);
                    } catch (Exception ignored) {
                    }
                }
            }
        });

        state.notifyChanged();

        new AlertDialog.Builder(themed)
                .setView(contentContainer)
                .setNeutralButton(str("piko_reset"), (dialog, which) -> {
                    if (isBubbleColor) {
                        sBubbleColorEnabled = false;
                        sBubbleColorHex = null;
                    } else {
                        sCustomColorEnabled = false;
                        sCustomColorHex = null;
                    }
                    triggerThemeRefresh(themeButton);
                })
                .setNegativeButton(str("piko_cancel"), null)
                .setPositiveButton(str("piko_ok"), (dialog, which) -> {
                    if (isBubbleColor) {
                        sBubbleColorHex = "#" + state.getHex();
                        sBubbleColorEnabled = true;
                    } else {
                        sCustomColorHex = "#" + state.getHex();
                        sCustomColorEnabled = true;
                    }
                    triggerThemeRefresh(themeButton);
                })
                .show();
    }

    private static class ColorPickerState {
        float hue;
        float sat;
        float val;
        boolean isUserInteracting = false;
        Runnable listener;

        ColorPickerState(float hue, float sat, float val) {
            this.hue = hue;
            this.sat = sat;
            this.val = val;
        }

        void setHsv(float h, float s, float v, boolean notifySelf) {
            if (s > 0f) {
                this.hue = Math.max(0f, Math.min(360f, h));
            }
            this.sat = Math.max(0f, Math.min(1f, s));
            this.val = Math.max(0f, Math.min(1f, v));
            if (notifySelf && listener != null) {
                listener.run();
            }
        }

        void setHueOnly(float h) {
            this.hue = Math.max(0f, Math.min(360f, h));
            if (listener != null) listener.run();
        }

        void notifyChanged() {
            if (listener != null) listener.run();
        }

        int getColor() {
            return Color.HSVToColor(new float[]{hue, sat, val});
        }

        String getHex() {
            return String.format("%06X", (0xFFFFFF & getColor()));
        }
    }

    private static class SatValBoxView extends View {
        private final ColorPickerState state;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        public SatValBoxView(Context context, ColorPickerState state) {
            super(context);
            this.state = state;
            cursorPaint.setStyle(Paint.Style.STROKE);
            cursorPaint.setStrokeWidth(dp(context, 2));
            cursorPaint.setColor(Color.WHITE);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    state.isUserInteracting = true;
                case MotionEvent.ACTION_MOVE:
                    int w = getWidth();
                    int h = getHeight();
                    if (w <= 0 || h <= 0) return true;

                    float sat = event.getX() / w;
                    float val = 1f - (event.getY() / h);

                    state.setHsv(
                            state.hue,
                            Math.max(0f, Math.min(1f, sat)),
                            Math.max(0f, Math.min(1f, val)),
                            true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    state.isUserInteracting = false;
                    return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float cursorRadius = dp(getContext(), 8);
            float corner = dp(getContext(), 6);

            rect.set(0, 0, w, h);

            paint.setShader(null);
            paint.setColor(Color.HSVToColor(new float[]{state.hue, 1f, 1f}));
            canvas.drawRoundRect(rect, corner, corner, paint);

            LinearGradient satGrad = new LinearGradient(
                    rect.left, 0, rect.right, 0,
                    Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            paint.setShader(satGrad);
            canvas.drawRoundRect(rect, corner, corner, paint);

            LinearGradient valGrad = new LinearGradient(
                    0, rect.top, 0, rect.bottom,
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
            paint.setShader(valGrad);
            canvas.drawRoundRect(rect, corner, corner, paint);
            paint.setShader(null);

            float posX = state.sat * w;
            float posY = (1f - state.val) * h;

            float outer = cursorRadius + cursorPaint.getStrokeWidth() / 2f;
            float clampedX = Math.max(outer, Math.min(w - outer, posX));
            float clampedY = Math.max(outer, Math.min(h - outer, posY));

            canvas.drawCircle(clampedX, clampedY, cursorRadius, cursorPaint);
        }
    }

    private static class HueBarView extends View {
        private final ColorPickerState state;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        public HueBarView(Context context, ColorPickerState state) {
            super(context);
            this.state = state;

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(context, 3));
            strokePaint.setColor(Color.WHITE);

            fillPaint.setStyle(Paint.Style.FILL);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    state.isUserInteracting = true;
                case MotionEvent.ACTION_MOVE:
                    int w = getWidth();
                    if (w <= 0) return true;

                    float hue = (event.getX() / w) * 360f;
                    hue = Math.max(0f, Math.min(360f, hue));

                    state.setHueOnly(hue);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    state.isUserInteracting = false;
                    return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float cursorRadius = dp(getContext(), 10);
            float corner = dp(getContext(), 12);

            rect.set(0, 0, w, h);

            int[] colors = new int[]{
                    0xFFFF0000, 0xFFFFFF00, 0xFF00FF00,
                    0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF,
                    0xFFFF0000
            };

            LinearGradient hueGrad = new LinearGradient(
                    rect.left, 0, rect.right, 0,
                    colors, null, Shader.TileMode.CLAMP);

            paint.setShader(hueGrad);
            canvas.drawRoundRect(rect, corner, corner, paint);
            paint.setShader(null);

            float posX = (state.hue / 360f) * w;

            float outer = cursorRadius + strokePaint.getStrokeWidth() / 2f;
            float clampedX = Math.max(outer, Math.min(w - outer, posX));

            float innerRadius = dp(getContext(), 7);

            canvas.drawCircle(clampedX, h / 2f, cursorRadius, strokePaint);
            fillPaint.setColor(Color.HSVToColor(new float[]{state.hue, 1f, 1f}));
            canvas.drawCircle(clampedX, h / 2f, innerRadius, fillPaint);
        }
    }

    public static Integer overrideTextColorArgb(Integer original) {
        if (!sCustomColorEnabled) return original;
        try {
            return 0xFF000000 | (Integer.parseInt(sCustomColorHex.replace("#", ""), 16) & 0xFFFFFF);
        } catch (Exception e) {
            return original;
        }
    }

    public static String overrideTextColorHex(String original) {
        if (!sCustomColorEnabled) return original;
        return isValidHex(sCustomColorHex) ? sCustomColorHex : original;
    }

    public static Integer overrideBubbleColorArgb(Integer original) {
        if (!sBubbleColorEnabled) return original;
        try {
            return 0xFF000000 | (Integer.parseInt(sBubbleColorHex.replace("#", ""), 16) & 0xFFFFFF);
        } catch (Exception e) {
            return original;
        }
    }

    public static String overrideBubbleColorHex(String original) {
        if (!sBubbleColorEnabled) return original;
        return isValidHex(sBubbleColorHex) ? sBubbleColorHex : original;
    }

    public static Integer overrideSecondaryColorIfOwnNote(Integer resolvedPrimaryColor, Integer original) {
        if (!sCustomColorEnabled || resolvedPrimaryColor == null || !isValidHex(sCustomColorHex)) return original;
        try {
            int customArgb = 0xFF000000 | (Integer.parseInt(sCustomColorHex.replace("#", ""), 16) & 0xFFFFFF);
            return resolvedPrimaryColor == customArgb ? Integer.valueOf(customArgb) : original;
        } catch (Exception e) {
            return original;
        }
    }

    public static void applyBubbleTextColor(Integer resolvedPrimaryColor, TextView view) {
        if (!sCustomColorEnabled || resolvedPrimaryColor == null || view == null || !isValidHex(sCustomColorHex)) return;
        try {
            int customArgb = 0xFF000000 | (Integer.parseInt(sCustomColorHex.replace("#", ""), 16) & 0xFFFFFF);
            if (resolvedPrimaryColor == customArgb) view.setTextColor(customArgb);
        } catch (Exception ignored) {
        }
    }

    private static boolean isValidHex(String hex) {
        if (hex == null) return false;
        String stripped = hex.startsWith("#") ? hex.substring(1) : hex;
        if (stripped.length() != 6) return false;
        try {
            Integer.parseInt(stripped, 16);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}