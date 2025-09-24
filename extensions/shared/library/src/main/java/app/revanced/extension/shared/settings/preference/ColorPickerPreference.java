package app.revanced.extension.shared.settings.preference;

import static app.revanced.extension.shared.StringRef.str;
import static app.revanced.extension.shared.Utils.getResourceIdentifier;
import static app.revanced.extension.shared.Utils.dipToPixels;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.*;

import androidx.annotation.ColorInt;

import java.util.Locale;
import java.util.regex.Pattern;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.settings.Setting;
import app.revanced.extension.shared.settings.StringSetting;

/**
 * A custom preference for selecting a color via a hexadecimal code or a color picker dialog.
 * Extends {@link EditTextPreference} to display a colored dot in the widget area,
 * reflecting the currently selected color. The dot is dimmed when the preference is disabled.
 */
@SuppressWarnings({"unused", "deprecation"})
public class ColorPickerPreference extends EditTextPreference {

    /**
     * Character to show the color appearance.
     */
    public static final String COLOR_DOT_STRING = "⬤";

    /**
     * Length of a valid color string of format #RRGGBB.
     */
    public static final int COLOR_STRING_LENGTH = 7;

    /**
     * Matches everything that is not a hex number/letter.
     */
    private static final Pattern PATTERN_NOT_HEX = Pattern.compile("[^0-9A-Fa-f]");

    /**
     * Alpha for dimming when the preference is disabled.
     */
    private static final float DISABLED_ALPHA = 0.5f; // 50%

    /**
     * View displaying a colored dot in the widget area.
     */
    private View widgetColorDot;

    /**
     * Current color in RGB format (without alpha).
     */
    @ColorInt
    private int currentColor;

    /**
     * Associated setting for storing the color value.
     */
    private StringSetting colorSetting;

    /**
     * Dialog TextWatcher for the EditText to monitor color input changes.
     */
    private TextWatcher colorTextWatcher;

    /**
     * Dialog TextView displaying a colored dot for the selected color preview in the dialog.
     */
    private TextView dialogColorPreview;

    /**
     * Dialog color picker view.
     */
    private ColorPickerView dialogColorPickerView;

    /**
     * Removes non valid hex characters, converts to all uppercase,
     * and adds # character to the start if not present.
     */
    public static String cleanupColorCodeString(String colorString) {
        // Remove non-hex chars, convert to uppercase, and ensure correct length
        String result = "#" + PATTERN_NOT_HEX.matcher(colorString)
                .replaceAll("").toUpperCase(Locale.ROOT);

        if (result.length() < COLOR_STRING_LENGTH) {
            return result;
        }

        return result.substring(0, COLOR_STRING_LENGTH);
    }

    /**
     * @param color RGB color, without an alpha channel.
     * @return #RRGGBB hex color string
     */
    public static String getColorString(@ColorInt int color) {
        String colorString = String.format("#%06X", color);
        if ((color & 0xFF000000) != 0) {
            // Likely a bug somewhere.
            Logger.printException(() -> "getColorString: color has alpha channel: " + colorString);
        }
        return colorString;
    }

    /**
     * Creates a Spanned object for a colored dot using SpannableString.
     *
     * @param color The RGB color (without alpha).
     * @return A Spanned object with the colored dot.
     */
    public static Spanned getColorDot(@ColorInt int color) {
        SpannableString spannable = new SpannableString(COLOR_DOT_STRING);
        spannable.setSpan(new ForegroundColorSpan(color | 0xFF000000), 0, COLOR_DOT_STRING.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new RelativeSizeSpan(1.5f), 0, 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    public ColorPickerPreference(Context context) {
        super(context);
        init();
    }

    public ColorPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * Initializes the preference by setting up the EditText, loading the color, and set the widget layout.
     */
    private void init() {
        colorSetting = (StringSetting) Setting.getSettingFromPath(getKey());
        if (colorSetting == null) {
            Logger.printException(() -> "Could not find color setting for: " + getKey());
        }

        EditText editText = getEditText();
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            editText.setAutofillHints((String) null);
        }

        // Set the widget layout to a custom layout containing the colored dot.
        setWidgetLayoutResource(getResourceIdentifier("revanced_color_dot_widget", "layout"));
    }

    /**
     * Sets the selected color and updates the UI and settings.
     *
     * @param colorString The color in hexadecimal format (e.g., "#RRGGBB").
     * @throws IllegalArgumentException If the color string is invalid.
     */
    @Override
    public final void setText(String colorString) {
        try {
            Logger.printDebug(() -> "setText: " + colorString);
            super.setText(colorString);

            currentColor = Color.parseColor(colorString) & 0x00FFFFFF;
            if (colorSetting != null) {
                colorSetting.save(getColorString(currentColor));
            }
            updateColorPreview();
            updateWidgetColorDot();
        } catch (IllegalArgumentException ex) {
            // This code is reached if the user pastes settings json with an invalid color
            // since this preference is updated with the new setting text.
            Logger.printDebug(() -> "Parse color error: " + colorString, ex);
            Utils.showToastShort(str("revanced_settings_color_invalid"));
            setText(colorSetting.resetToDefault());
        } catch (Exception ex) {
            Logger.printException(() -> "setText failure: " + colorString, ex);
        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        widgetColorDot = view.findViewById(getResourceIdentifier(
                "revanced_color_dot_widget", "id"));
        widgetColorDot.setBackgroundResource(getResourceIdentifier(
                "revanced_settings_circle_background", "drawable"));
        widgetColorDot.getBackground().setTint(currentColor | 0xFF000000);
        widgetColorDot.setAlpha(isEnabled() ? 1.0f : DISABLED_ALPHA);
    }

    /**
     * Updates the color preview TextView with a colored dot.
     */
    private void updateColorPreview() {
        if (dialogColorPreview != null) {
            dialogColorPreview.setText(getColorDot(currentColor));
        }
    }

    private void updateWidgetColorDot() {
        if (widgetColorDot != null) {
            widgetColorDot.getBackground().setTint(currentColor | 0xFF000000);
            widgetColorDot.setAlpha(isEnabled() ? 1.0f : DISABLED_ALPHA);
        }
    }

    /**
     * Creates a TextWatcher to monitor changes in the EditText for color input.
     *
     * @return A TextWatcher that updates the color preview on valid input.
     */
    private TextWatcher createColorTextWatcher(ColorPickerView colorPickerView) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable edit) {
                try {
                    String colorString = edit.toString();

                    String sanitizedColorString = cleanupColorCodeString(colorString);
                    if (!sanitizedColorString.equals(colorString)) {
                        edit.replace(0, colorString.length(), sanitizedColorString);
                        return;
                    }

                    if (sanitizedColorString.length() != COLOR_STRING_LENGTH) {
                        // User is still typing out the color.
                        return;
                    }

                    final int newColor = Color.parseColor(colorString);
                    if (currentColor != newColor) {
                        Logger.printDebug(() -> "afterTextChanged: " + sanitizedColorString);
                        currentColor = newColor;
                        updateColorPreview();
                        updateWidgetColorDot();
                        colorPickerView.setColor(newColor);
                    }
                } catch (Exception ex) {
                    // Should never be reached since input is validated before using.
                    Logger.printException(() -> "afterTextChanged failure", ex);
                }
            }
        };
    }

    /**
     * Creates a Dialog with a color preview and EditText for hex color input.
     */
    @Override
    protected void showDialog(Bundle state) {
        Context context = getContext();

        // Inflate color picker view.
        View colorPicker = LayoutInflater.from(context).inflate(
                getResourceIdentifier("revanced_color_picker", "layout"), null);
        dialogColorPickerView = colorPicker.findViewById(
                getResourceIdentifier("revanced_color_picker_view", "id"));
        dialogColorPickerView.setColor(currentColor);

        // Horizontal layout for preview and EditText.
        LinearLayout inputLayout = new LinearLayout(context);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);

        dialogColorPreview = new TextView(context);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        previewParams.setMargins(dipToPixels(15), 0, dipToPixels(10), 0); // text dot has its own indents so 15, instead 16.
        dialogColorPreview.setLayoutParams(previewParams);
        inputLayout.addView(dialogColorPreview);
        updateColorPreview();

        EditText editText = getEditText();
        ViewParent parent = editText.getParent();
        if (parent instanceof ViewGroup parentViewGroup) {
            parentViewGroup.removeView(editText);
        }
        editText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        String currentColorString = getColorString(currentColor);
        editText.setText(currentColorString);
        editText.setSelection(currentColorString.length());
        editText.setTypeface(Typeface.MONOSPACE);
        colorTextWatcher = createColorTextWatcher(dialogColorPickerView);
        editText.addTextChangedListener(colorTextWatcher);
        inputLayout.addView(editText);

        // Add a dummy view to take up remaining horizontal space,
        // otherwise it will show an oversize underlined text view.
        View paddingView = new View(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
        );
        paddingView.setLayoutParams(params);
        inputLayout.addView(paddingView);

        // Create content container for color picker and input layout.
        LinearLayout contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.addView(colorPicker);
        contentContainer.addView(inputLayout);

        // Create ScrollView to wrap the content container.
        ScrollView contentScrollView = new ScrollView(context);
        contentScrollView.setVerticalScrollBarEnabled(false); // Disable vertical scrollbar.
        contentScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER); // Disable overscroll effect.
        LinearLayout.LayoutParams scrollViewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        );
        contentScrollView.setLayoutParams(scrollViewParams);
        contentScrollView.addView(contentContainer);

        // Create custom dialog.
        final int originalColor = currentColor & 0x00FFFFFF;
        Pair<Dialog, LinearLayout> dialogPair = Utils.createCustomDialog(
                context,
                getTitle() != null ? getTitle().toString() : str("revanced_settings_color_picker_title"), // Title.
                null, // No message.
                null, // No EditText.
                null, // OK button text.
                () -> {
                    // OK button action.
                    try {
                        String colorString = editText.getText().toString();
                        if (colorString.length() != COLOR_STRING_LENGTH) {
                            Utils.showToastShort(str("revanced_settings_color_invalid"));
                            setText(getColorString(originalColor));
                            return;
                        }
                        setText(colorString);
                    } catch (Exception ex) {
                        // Should never happen due to a bad color string,
                        // since the text is validated and fixed while the user types.
                        Logger.printException(() -> "OK button failure", ex);
                    }
                },
                () -> {
                    // Cancel button action.
                    try {
                        // Restore the original color.
                        setText(getColorString(originalColor));
                    } catch (Exception ex) {
                        Logger.printException(() -> "Cancel button failure", ex);
                    }
                },
                str("revanced_settings_reset_color"), // Neutral button text.
                () -> {
                    // Neutral button action.
                    try {
                        final int defaultColor = Color.parseColor(colorSetting.defaultValue) & 0x00FFFFFF;
                        // Setting view color causes listener callback into this class.
                        dialogColorPickerView.setColor(defaultColor);
                    } catch (Exception ex) {
                        Logger.printException(() -> "Reset button failure", ex);
                    }
                },
                false // Do not dismiss dialog when onNeutralClick.
        );

        // Add the ScrollView to the dialog's main layout.
        LinearLayout dialogMainLayout = dialogPair.second;
        dialogMainLayout.addView(contentScrollView, dialogMainLayout.getChildCount() - 1);

        // Set up color picker listener with debouncing.
        // Add listener last to prevent callbacks from set calls above.
        dialogColorPickerView.setOnColorChangedListener(color -> {
            // Check if it actually changed, since this callback
            // can be caused by updates in afterTextChanged().
            if (currentColor == color) {
                return;
            }

            String updatedColorString = getColorString(color);
            Logger.printDebug(() -> "onColorChanged: " + updatedColorString);
            currentColor = color;
            editText.setText(updatedColorString);
            editText.setSelection(updatedColorString.length());

            updateColorPreview();
            updateWidgetColorDot();
        });

        // Configure and show the dialog.
        Dialog dialog = dialogPair.first;
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (colorTextWatcher != null) {
            getEditText().removeTextChangedListener(colorTextWatcher);
            colorTextWatcher = null;
        }

        dialogColorPreview = null;
        dialogColorPickerView = null;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        updateWidgetColorDot();
    }
}
