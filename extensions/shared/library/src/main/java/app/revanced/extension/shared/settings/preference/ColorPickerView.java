package app.revanced.extension.shared.settings.preference;

import static app.revanced.extension.shared.Utils.dipToPixels;
import static app.revanced.extension.shared.settings.preference.ColorPickerPreference.getColorString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;

/**
 * A custom color picker view that allows the user to select a color using a hue slider and a saturation-value selector.
 * This implementation is density-independent and responsive across different screen sizes and DPIs.
 *
 * <p>
 * This view displays two main components for color selection:
 * <ul>
 *     <li><b>Hue Bar:</b> A horizontal bar at the bottom that allows the user to select the hue component of the color.
 *     <li><b>Saturation-Value Selector:</b> A rectangular area above the hue bar that allows the user to select the saturation and value (brightness)
 *     components of the color based on the selected hue.
 * </ul>
 *
 * <p>
 * The view uses {@link LinearGradient} and {@link ComposeShader} to create the color gradients for the hue bar and the
 * saturation-value selector. It also uses {@link Paint} to draw the selectors (draggable handles).
 *
 * <p>
 * The selected color can be retrieved using {@link #getColor()} and can be set using {@link #setColor(int)}.
 * An {@link OnColorChangedListener} can be registered to receive notifications when the selected color changes.
 */
public class ColorPickerView extends View {

    /**
     * Interface definition for a callback to be invoked when the selected color changes.
     */
    public interface OnColorChangedListener {
        /**
         * Called when the selected color has changed.
         *
         * Important: Callback color uses RGB format with zero alpha channel.
         *
         * @param color The new selected color.
         */
        void onColorChanged(@ColorInt int color);
    }

    /** Expanded touch area for the hue bar to increase the touch-sensitive area. */
    public static final float TOUCH_EXPANSION = dipToPixels(20f);

    private static final float MARGIN_BETWEEN_AREAS = dipToPixels(24);
    private static final float VIEW_PADDING = dipToPixels(16);
    private static final float HUE_BAR_HEIGHT = dipToPixels(12);
    private static final float HUE_CORNER_RADIUS = dipToPixels(6);
    private static final float SELECTOR_RADIUS = dipToPixels(12);
    private static final float SELECTOR_STROKE_WIDTH = 8;
    /**
     * Hue fill radius. Use slightly smaller radius for the selector handle fill,
     * otherwise the anti-aliasing causes the fill color to bleed past the selector outline.
     */
    private static final float SELECTOR_FILL_RADIUS = SELECTOR_RADIUS - SELECTOR_STROKE_WIDTH / 2;
    /** Thin dark outline stroke width for the selector rings. */
    private static final float SELECTOR_EDGE_STROKE_WIDTH = 1;
    public static final float SELECTOR_EDGE_RADIUS =
            SELECTOR_RADIUS + SELECTOR_STROKE_WIDTH / 2 + SELECTOR_EDGE_STROKE_WIDTH / 2;

    /** Selector outline inner color. */
    @ColorInt
    private static final int SELECTOR_OUTLINE_COLOR = Color.WHITE;

    /** Dark edge color for the selector rings. */
    @ColorInt
    private static final int SELECTOR_EDGE_COLOR = Color.parseColor("#CFCFCF");

    private static final int[] HUE_COLORS = new int[361];
    static {
        for (int i = 0; i < 361; i++) {
            HUE_COLORS[i] = Color.HSVToColor(new float[]{i, 1, 1});
        }
    }

    /** Hue bar. */
    private final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Saturation-value selector. */
    private final Paint saturationValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Draggable selector. */
    private final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    {
        selectorPaint.setStrokeWidth(SELECTOR_STROKE_WIDTH);
    }

    /** Bounds of the hue bar. */
    private final RectF hueRect = new RectF();
    /** Bounds of the saturation-value selector. */
    private final RectF saturationValueRect = new RectF();

    /** HSV color calculations to avoid allocations during drawing. */
    private final float[] hsvArray = {1, 1, 1};

    /** Current hue value (0-360). */
    private float hue = 0f;
    /** Current saturation value (0-1). */
    private float saturation = 1f;
    /** Current value (brightness) value (0-1). */
    private float value = 1f;

    /** The currently selected color in RGB format with no alpha channel. */
    @ColorInt
    private int selectedColor;

    private OnColorChangedListener colorChangedListener;

    /** Track if we're currently dragging the hue or saturation handle. */
    private boolean isDraggingHue;
    private boolean isDraggingSaturation;

    public ColorPickerView(Context context) {
        super(context);
    }

    public ColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ColorPickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final float DESIRED_ASPECT_RATIO = 0.8f; // height = width * 0.8

        final int minWidth = Utils.dipToPixels(250);
        final int minHeight = (int) (minWidth * DESIRED_ASPECT_RATIO) + (int) (HUE_BAR_HEIGHT + MARGIN_BETWEEN_AREAS);

        int width = resolveSize(minWidth, widthMeasureSpec);
        int height = resolveSize(minHeight, heightMeasureSpec);

        // Ensure minimum dimensions for usability.
        width = Math.max(width, minWidth);
        height = Math.max(height, minHeight);

        // Adjust height to maintain desired aspect ratio if possible.
        final int desiredHeight = (int) (width * DESIRED_ASPECT_RATIO) + (int) (HUE_BAR_HEIGHT + MARGIN_BETWEEN_AREAS);
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            height = desiredHeight;
        }

        setMeasuredDimension(width, height);
    }

    /**
     * Called when the size of the view changes.
     * This method calculates and sets the bounds of the hue bar and saturation-value selector.
     * It also creates the necessary shaders for the gradients.
     */
    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);

        // Calculate bounds with hue bar at the bottom.
        final float effectiveWidth = width - (2 * VIEW_PADDING);
        final float effectiveHeight = height - (2 * VIEW_PADDING) - HUE_BAR_HEIGHT - MARGIN_BETWEEN_AREAS;

        // Adjust rectangles to account for padding and density-independent dimensions.
        saturationValueRect.set(
                VIEW_PADDING,
                VIEW_PADDING,
                VIEW_PADDING + effectiveWidth,
                VIEW_PADDING + effectiveHeight
        );

        hueRect.set(
                VIEW_PADDING,
                height - VIEW_PADDING - HUE_BAR_HEIGHT,
                VIEW_PADDING + effectiveWidth,
                height - VIEW_PADDING
        );

        // Update the shaders.
        updateHueShader();
        updateSaturationValueShader();
    }

    /**
     * Updates the hue full spectrum (0-360 degrees).
     */
    private void updateHueShader() {
        LinearGradient hueShader = new LinearGradient(
                hueRect.left, hueRect.top,
                hueRect.right, hueRect.top,
                HUE_COLORS,
                null,
                Shader.TileMode.CLAMP
        );

        huePaint.setShader(hueShader);
    }

    /**
     * Updates the shader for the saturation-value selector based on the currently selected hue.
     * This method creates a combined shader that blends a saturation gradient with a value gradient.
     */
    private void updateSaturationValueShader() {
        // Create a saturation-value gradient based on the current hue.
        // Calculate the start color (white with the selected hue) for the saturation gradient.
        final int startColor = Color.HSVToColor(new float[]{hue, 0f, 1f});

        // Calculate the middle color (fully saturated color with the selected hue) for the saturation gradient.
        final int midColor = Color.HSVToColor(new float[]{hue, 1f, 1f});

        // Create a linear gradient for the saturation from startColor to midColor (horizontal).
        LinearGradient satShader = new LinearGradient(
                saturationValueRect.left, saturationValueRect.top,
                saturationValueRect.right, saturationValueRect.top,
                startColor,
                midColor,
                Shader.TileMode.CLAMP
        );

        // Create a linear gradient for the value (brightness) from white to black (vertical).
        //noinspection ExtractMethodRecommender
        LinearGradient valShader = new LinearGradient(
                saturationValueRect.left, saturationValueRect.top,
                saturationValueRect.left, saturationValueRect.bottom,
                Color.WHITE,
                Color.BLACK,
                Shader.TileMode.CLAMP
        );

        // Combine the saturation and value shaders using PorterDuff.Mode.MULTIPLY to create the final color.
        ComposeShader combinedShader = new ComposeShader(satShader, valShader, PorterDuff.Mode.MULTIPLY);

        // Set the combined shader for the saturation-value paint.
        saturationValuePaint.setShader(combinedShader);
    }

    /**
     * Draws the color picker view on the canvas.
     * This method draws the saturation-value selector, the hue bar with rounded corners,
     * and the draggable handles.
     *
     * @param canvas The canvas on which to draw.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        // Draw the saturation-value selector rectangle.
        canvas.drawRect(saturationValueRect, saturationValuePaint);

        // Draw the hue bar.
        canvas.drawRoundRect(hueRect, HUE_CORNER_RADIUS, HUE_CORNER_RADIUS, huePaint);

        final float hueSelectorX = hueRect.left + (hue / 360f) * hueRect.width();
        final float hueSelectorY = hueRect.centerY();

        final float satSelectorX = saturationValueRect.left + saturation * saturationValueRect.width();
        final float satSelectorY = saturationValueRect.top + (1 - value) * saturationValueRect.height();

        // Draw the saturation and hue selector handle filled with the selected color.
        hsvArray[0] = hue;
        final int hueHandleColor = Color.HSVToColor(0xFF, hsvArray);
        selectorPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        selectorPaint.setColor(hueHandleColor);
        canvas.drawCircle(hueSelectorX, hueSelectorY, SELECTOR_FILL_RADIUS, selectorPaint);

        selectorPaint.setColor(selectedColor | 0xFF000000);
        canvas.drawCircle(satSelectorX, satSelectorY, SELECTOR_FILL_RADIUS, selectorPaint);

        // Draw white outlines for the handles.
        selectorPaint.setColor(SELECTOR_OUTLINE_COLOR);
        selectorPaint.setStyle(Paint.Style.STROKE);
        selectorPaint.setStrokeWidth(SELECTOR_STROKE_WIDTH);
        canvas.drawCircle(hueSelectorX, hueSelectorY, SELECTOR_RADIUS, selectorPaint);
        canvas.drawCircle(satSelectorX, satSelectorY, SELECTOR_RADIUS, selectorPaint);

        // Draw thin dark outlines for the handles at the outer edge of the white outline.
        selectorPaint.setColor(SELECTOR_EDGE_COLOR);
        selectorPaint.setStrokeWidth(SELECTOR_EDGE_STROKE_WIDTH);
        canvas.drawCircle(hueSelectorX, hueSelectorY, SELECTOR_EDGE_RADIUS, selectorPaint);
        canvas.drawCircle(satSelectorX, satSelectorY, SELECTOR_EDGE_RADIUS, selectorPaint);
    }

    /**
     * Handles touch events on the view.
     * This method determines whether the touch event occurred within the hue bar or the saturation-value selector,
     * updates the corresponding values (hue, saturation, value), and invalidates the view to trigger a redraw.
     * <p>
     * In addition to testing if the touch is within the strict rectangles, an expanded hit area (by selectorRadius)
     * is used so that the draggable handles remain active even when half of the handle is outside the drawn bounds.
     *
     * @param event The motion event.
     * @return True if the event was handled, false otherwise.
     */
    @SuppressLint("ClickableViewAccessibility") // performClick is not overridden, but not needed in this case.
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            final float x = event.getX();
            final float y = event.getY();
            final int action = event.getAction();
            Logger.printDebug(() -> "onTouchEvent action: " + action + " x: " + x + " y: " + y);

            // Define touch expansion for the hue bar.
            RectF expandedHueRect = new RectF(
                    hueRect.left,
                    hueRect.top - TOUCH_EXPANSION,
                    hueRect.right,
                    hueRect.bottom + TOUCH_EXPANSION
            );

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    // Calculate current handle positions.
                    final float hueSelectorX = hueRect.left + (hue / 360f) * hueRect.width();
                    final float hueSelectorY = hueRect.centerY();

                    final float satSelectorX = saturationValueRect.left + saturation * saturationValueRect.width();
                    final float valSelectorY = saturationValueRect.top + (1 - value) * saturationValueRect.height();

                    // Create hit areas for both handles.
                    RectF hueHitRect = new RectF(
                            hueSelectorX - SELECTOR_RADIUS,
                            hueSelectorY - SELECTOR_RADIUS,
                            hueSelectorX + SELECTOR_RADIUS,
                            hueSelectorY + SELECTOR_RADIUS
                    );
                    RectF satValHitRect = new RectF(
                            satSelectorX - SELECTOR_RADIUS,
                            valSelectorY - SELECTOR_RADIUS,
                            satSelectorX + SELECTOR_RADIUS,
                            valSelectorY + SELECTOR_RADIUS
                    );

                    // Check if the touch started on a handle or within the expanded hue bar area.
                    if (hueHitRect.contains(x, y)) {
                        isDraggingHue = true;
                        updateHueFromTouch(x);
                    } else if (satValHitRect.contains(x, y)) {
                        isDraggingSaturation = true;
                        updateSaturationValueFromTouch(x, y);
                    } else if (expandedHueRect.contains(x, y)) {
                        // Handle touch within the expanded hue bar area.
                        isDraggingHue = true;
                        updateHueFromTouch(x);
                    } else if (saturationValueRect.contains(x, y)) {
                        isDraggingSaturation = true;
                        updateSaturationValueFromTouch(x, y);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    // Continue updating values even if touch moves outside the view.
                    if (isDraggingHue) {
                        updateHueFromTouch(x);
                    } else if (isDraggingSaturation) {
                        updateSaturationValueFromTouch(x, y);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDraggingHue = false;
                    isDraggingSaturation = false;
                    break;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "onTouchEvent failure", ex);
        }

        return true;
    }

    /**
     * Updates the hue value based on touch position, clamping to valid range.
     *
     * @param x The x-coordinate of the touch position.
     */
    private void updateHueFromTouch(float x) {
        // Clamp x to the hue rectangle bounds.
        final float clampedX = Utils.clamp(x, hueRect.left, hueRect.right);
        final float updatedHue = ((clampedX - hueRect.left) / hueRect.width()) * 360f;
        if (hue == updatedHue) {
            return;
        }

        hue = updatedHue;
        updateSaturationValueShader();
        updateSelectedColor();
    }

    /**
     * Updates saturation and value based on touch position, clamping to valid range.
     *
     * @param x The x-coordinate of the touch position.
     * @param y The y-coordinate of the touch position.
     */
    private void updateSaturationValueFromTouch(float x, float y) {
        // Clamp x and y to the saturation-value rectangle bounds.
        final float clampedX = Utils.clamp(x, saturationValueRect.left, saturationValueRect.right);
        final float clampedY = Utils.clamp(y, saturationValueRect.top, saturationValueRect.bottom);

        final float updatedSaturation = (clampedX - saturationValueRect.left) / saturationValueRect.width();
        final float updatedValue = 1 - ((clampedY - saturationValueRect.top) / saturationValueRect.height());

        if (saturation == updatedSaturation && value == updatedValue) {
            return;
        }
        saturation = updatedSaturation;
        value = updatedValue;
        updateSelectedColor();
    }

    /**
     * Updates the selected color and notifies listeners.
     */
    private void updateSelectedColor() {
        final int updatedColor = Color.HSVToColor(0, new float[]{hue, saturation, value});

        if (selectedColor != updatedColor) {
            selectedColor = updatedColor;

            if (colorChangedListener != null) {
                colorChangedListener.onColorChanged(updatedColor);
            }
        }

        // Must always redraw, otherwise if saturation is pure grey or black
        // then the hue slider cannot be changed.
        invalidate();
    }

    /**
     * Sets the currently selected color.
     *
     * @param color The color to set in either ARGB or RGB format.
     */
    public void setColor(@ColorInt int color) {
        color &= 0x00FFFFFF;
        if (selectedColor == color) {
            return;
        }

        // Update the selected color.
        selectedColor = color;
        Logger.printDebug(() -> "setColor: " + getColorString(selectedColor));

        // Convert the ARGB color to HSV values.
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);

        // Update the hue, saturation, and value.
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];

        // Update the saturation-value shader based on the new hue.
        updateSaturationValueShader();

        // Notify the listener if it's set.
        if (colorChangedListener != null) {
            colorChangedListener.onColorChanged(selectedColor);
        }

        // Invalidate the view to trigger a redraw.
        invalidate();
    }

    /**
     * Gets the currently selected color.
     *
     * @return The selected color in RGB format with no alpha channel.
     */
    @ColorInt
    public int getColor() {
        return selectedColor;
    }

    /**
     * Sets the listener to be notified when the selected color changes.
     *
     * @param listener The listener to set.
     */
    public void setOnColorChangedListener(OnColorChangedListener listener) {
        colorChangedListener = listener;
    }
}
