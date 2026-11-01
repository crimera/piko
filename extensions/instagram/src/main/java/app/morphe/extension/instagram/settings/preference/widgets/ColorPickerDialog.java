/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

import app.morphe.extension.instagram.utils.HexColor;

/** Asks for a color on a wheel with brightness and, where allowed, opacity sliders, or as hexadecimal digits. */
public final class ColorPickerDialog {

    public interface Listener {
        void onPicked(String color);
    }

    private ColorPickerDialog() { }

    public static void paintSwatch(View swatch, String color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setStroke(InstagramPreferenceStyle.dp(swatch.getContext(), 1), InstagramPreferenceStyle.secondaryTextColor());
        shape.setColor(color == null ? Color.TRANSPARENT : HexColor.toArgb(color));
        swatch.setBackground(color == null ? shape
                : new LayerDrawable(new Drawable[]{new Checkerboard(0, 0, -1), shape}));
    }

    public static void show(Context context, CharSequence title, String current, boolean allowAlpha, Listener listener) {
        Context themed = InstagramPreferenceStyle.dialogContext(context);
        Picker picker = new Picker(themed, current, allowAlpha);

        AlertDialog dialog = new AlertDialog.Builder(themed)
                .setTitle(title)
                .setView(picker.view)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(str("piko_color_default"), (dismissed, button) -> listener.onPicked(null))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String color = picker.color();
            // A color that is not complete keeps the dialog open.
            if (color == null) {
                picker.showInvalid();
                return;
            }
            listener.onPicked(color);
            dialog.dismiss();
        }));
        // The wheel is the way in, so the keyboard waits until the digits are touched.
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
    }

    /** Square size 0 means a quarter of the drawable; a negative radius rounds the ends fully. */
    private static final class Checkerboard extends Drawable {
        private static final int LIGHT = 0xFFFFFFFF;
        private static final int DARK = 0xFFC8C8C8;

        private final Paint paint = new Paint();
        private final Path clip = new Path();
        private final RectF bounds = new RectF();
        private final int square;
        private final int height;
        private final int radius;

        Checkerboard(int square, int height, int radius) {
            this.square = square;
            this.height = height;
            this.radius = radius;
        }

        @Override public void draw(Canvas canvas) {
            Rect area = getBounds();
            bounds.set(area);
            int cell = square > 0 ? square : Math.max(1, Math.min(area.width(), area.height()) / 4);
            float corner = radius >= 0 ? radius : Math.min(area.width(), area.height()) / 2f;
            clip.reset();
            clip.addRoundRect(bounds, corner, corner, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            paint.setColor(LIGHT);
            canvas.drawRect(bounds, paint);
            paint.setColor(DARK);
            int row = 0;
            for (int top = area.top; top < area.bottom; top += cell, row++) {
                int column = 0;
                for (int left = area.left; left < area.right; left += cell, column++) {
                    if ((row + column) % 2 == 1) canvas.drawRect(left, top, left + cell, top + cell, paint);
                }
            }
            canvas.restore();
        }

        @Override public int getIntrinsicHeight() {
            return height;
        }

        @Override public void setAlpha(int alpha) { }

        @Override public void setColorFilter(ColorFilter colorFilter) { }

        @Override public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    private abstract static class UserSlide implements SeekBar.OnSeekBarChangeListener {
        abstract void moved(int progress);

        @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            if (fromUser) moved(progress);
        }

        @Override public void onStartTrackingTouch(SeekBar bar) {
            bar.getParent().requestDisallowInterceptTouchEvent(true);
        }

        @Override public void onStopTrackingTouch(SeekBar bar) { }
    }

    private static final class Picker {
        final ScrollView view;
        private final ColorWheelView wheel;
        private final SeekBar brightness;
        private final GradientDrawable brightnessTrack;
        // Null when the color cannot be made see-through.
        private final SeekBar opacity;
        private final GradientDrawable opacityTrack;
        private final View swatch;
        private final EditText input;
        private final float[] hsv = {0f, 0f, 1f};
        // 255 is opaque, and then the digits have no alpha in front.
        private int alpha = 255;
        // True while the digits are written from the wheel, so they are not read back into it.
        private boolean writing;

        Picker(Context context, String current, boolean allowAlpha) {
            int margin = InstagramPreferenceStyle.dp(context, 24);
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(margin, InstagramPreferenceStyle.dp(context, 8), margin, 0);

            wheel = new ColorWheelView(context);
            LinearLayout.LayoutParams wheelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wheelParams.gravity = Gravity.CENTER_HORIZONTAL;
            content.addView(wheel, wheelParams);

            brightnessTrack = trackOf(context);
            brightness = sliderOf(context, brightnessTrack, 100, str("piko_color_brightness"));
            content.addView(brightness, sliderParams(context));

            if (allowAlpha) {
                opacityTrack = trackOf(context);
                    Drawable squares = new Checkerboard(InstagramPreferenceStyle.dp(context, 7),
                        InstagramPreferenceStyle.dp(context, 14), InstagramPreferenceStyle.dp(context, 7));
                opacity = sliderOf(context, new LayerDrawable(new Drawable[]{squares, opacityTrack}), 255,
                        str("piko_color_opacity"));
                content.addView(opacity, sliderParams(context));
            } else {
                opacityTrack = null;
                opacity = null;
            }

            LinearLayout digits = new LinearLayout(context);
            digits.setOrientation(LinearLayout.HORIZONTAL);
            digits.setGravity(Gravity.CENTER_VERTICAL);
            // Hexadecimal digits are written left to right, with the # in front, in every language.
            digits.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            LinearLayout.LayoutParams digitsParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            digitsParams.topMargin = InstagramPreferenceStyle.dp(context, 12);
            content.addView(digits, digitsParams);

            swatch = new View(context);
            int size = InstagramPreferenceStyle.dp(context, 40);
            digits.addView(swatch, new LinearLayout.LayoutParams(size, size));

            TextView hash = new TextView(context);
            hash.setText("#");
            hash.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            LinearLayout.LayoutParams hashParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hashParams.setMarginStart(InstagramPreferenceStyle.dp(context, 16));
            digits.addView(hash, hashParams);

            input = new EditText(context);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            input.setHint(str("piko_color_hex_hint"));
            input.setFilters(new InputFilter[]{
                    // Drops what is not a digit, which also takes the # off a pasted color.
                    (source, start, end, destination, destinationStart, destinationEnd) -> {
                        StringBuilder kept = new StringBuilder();
                        for (int index = start; index < end; index++) {
                            if (HexColor.isDigit(source.charAt(index))) kept.append(source.charAt(index));
                        }
                        return kept.length() == end - start ? null : kept;
                    },
                    new InputFilter.AllCaps(),
                    new InputFilter.LengthFilter(allowAlpha ? 8 : 6),
            });
            digits.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // A color kept from before this was not allowed to be see-through loses what made it so.
            String shown = current == null || allowAlpha || current.length() == 6 ? current : current.substring(2);
            if (shown != null) {
                Color.colorToHSV(HexColor.toArgb(shown), hsv);
                alpha = alphaOf(shown);
            }
            input.setText(shown == null ? "" : shown);
            paintSwatch(swatch, shown);
            showPosition();

            wheel.setListener((hue, saturation) -> {
                hsv[0] = hue;
                hsv[1] = saturation;
                showPicked();
            });
            brightness.setOnSeekBarChangeListener(new UserSlide() {
                @Override void moved(int progress) {
                    hsv[2] = progress / 100f;
                    showPicked();
                }
            });
            if (opacity != null) {
                opacity.setOnSeekBarChangeListener(new UserSlide() {
                    @Override void moved(int progress) {
                        alpha = progress;
                        showPicked();
                    }
                });
            }
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }

                @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }

                @Override public void afterTextChanged(Editable text) {
                    typed(HexColor.normalize(text.toString()));
                }
            });

            view = new ScrollView(context);
            view.addView(content);
        }

        private static GradientDrawable trackOf(Context context) {
            GradientDrawable track = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0, 0});
            track.setCornerRadius(InstagramPreferenceStyle.dp(context, 7));
            track.setSize(-1, InstagramPreferenceStyle.dp(context, 14));
            return track;
        }

        private static SeekBar sliderOf(Context context, Drawable track, int max, String description) {
            SeekBar slider = new SeekBar(context);
            slider.setMax(max);
            slider.setProgressDrawable(track);
            slider.setContentDescription(description);
            Drawable thumb = slider.getThumb();
            // Changed on a copy, as the thumb drawable is shared with other sliders of the app.
            if (thumb != null) {
                thumb.mutate().setColorFilter(InstagramPreferenceStyle.primaryTextColor(), PorterDuff.Mode.SRC_IN);
            }
            return slider;
        }

        private static LinearLayout.LayoutParams sliderParams(Context context) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = InstagramPreferenceStyle.dp(context, 8);
            return params;
        }

        String color() {
            return HexColor.normalize(input.getText().toString());
        }

        void showInvalid() {
            input.setError(str("piko_color_invalid"));
        }

        private void showPicked() {
            String digits = String.format(Locale.ROOT, "%06X", Color.HSVToColor(hsv) & 0xFFFFFF);
            String color = alpha == 255 ? digits : String.format(Locale.ROOT, "%02X", alpha) + digits;
            writing = true;
            input.setText(color);
            input.setSelection(color.length());
            writing = false;
            paintSwatch(swatch, color);
            showPosition();
        }

        private void typed(String color) {
            // The digits written from the wheel or a slider are painted by whoever wrote them.
            if (writing) return;
            paintSwatch(swatch, color);
            if (color == null) return;
            alpha = alphaOf(color);
            Color.colorToHSV(HexColor.toArgb(color), hsv);
            showPosition();
        }

        private static int alphaOf(String color) {
            return color.length() == 8 ? Integer.parseInt(color.substring(0, 2), 16) : 255;
        }

        private void showPosition() {
            wheel.setColor(hsv[0], hsv[1], hsv[2]);
            brightness.setProgress(Math.round(hsv[2] * 100));
            // The slider runs from black to the color the wheel has picked at full brightness.
            brightnessTrack.setColors(new int[]{Color.BLACK, Color.HSVToColor(new float[]{hsv[0], hsv[1], 1f})});
            if (opacity != null) {
                opacity.setProgress(alpha);
                // The slider runs from the picked color left clear to the picked color solid.
                int picked = Color.HSVToColor(hsv);
                opacityTrack.setColors(new int[]{picked & 0xFFFFFF, picked});
            }
        }
    }
}
