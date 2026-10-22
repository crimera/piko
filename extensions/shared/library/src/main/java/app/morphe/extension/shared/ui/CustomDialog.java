package app.morphe.extension.shared.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BaseSettings;

public class CustomDialog {
    private final Context context;
    private final Dialog dialog;
    private final LinearLayout mainLayout;

    public static int getDialogBackgroundColor() {
        return Utils.isDarkModeEnabled() ? 0xFF080808 : Color.WHITE;
    }

    public static int getAppForegroundColor() {
        return Utils.isDarkModeEnabled() ? Color.WHITE : Color.BLACK;
    }

    public static int getOkButtonBackgroundColor() {
        return Utils.isDarkModeEnabled() ? Color.WHITE : Color.BLACK;
    }

    public static int getCancelOrNeutralButtonBackgroundColor() {
        return Utils.isDarkModeEnabled() ? 0xFF282828 : 0xFFE0E0E0;
    }

    public static int getEditTextBackground() {
        return Utils.isDarkModeEnabled() ? 0xFF1C1C1C : 0xFFF2F2F2;
    }

    public static Pair<Dialog, LinearLayout> create(Context context, CharSequence title, CharSequence message,
                                                    @Nullable EditText editText, CharSequence okButtonText,
                                                    Runnable onOkClick, Runnable onCancelClick,
                                                    @Nullable CharSequence neutralButtonText,
                                                    @Nullable Runnable onNeutralClick,
                                                    boolean dismissDialogOnNeutralClick) {
        return create(context, title, message, editText, okButtonText, onOkClick, onCancelClick,
                neutralButtonText, onNeutralClick, dismissDialogOnNeutralClick, true);
    }

    public static Pair<Dialog, LinearLayout> create(Context context, CharSequence title, CharSequence message,
                                                    @Nullable EditText editText, CharSequence okButtonText,
                                                    Runnable onOkClick, Runnable onCancelClick,
                                                    @Nullable CharSequence neutralButtonText,
                                                    @Nullable Runnable onNeutralClick,
                                                    boolean dismissDialogOnNeutralClick,
                                                    boolean accentOkButton) {
        Logger.printDebug(() -> "Creating custom dialog with title: " + title);
        CustomDialog customDialog = new CustomDialog(context, title, message, editText,
                okButtonText, onOkClick, onCancelClick,
                neutralButtonText, onNeutralClick, dismissDialogOnNeutralClick, accentOkButton);
        return new Pair<>(customDialog.dialog, customDialog.mainLayout);
    }

    private CustomDialog(Context context, CharSequence title, CharSequence message, @Nullable EditText editText,
                         CharSequence okButtonText, Runnable onOkClick, Runnable onCancelClick,
                         @Nullable CharSequence neutralButtonText, @Nullable Runnable onNeutralClick,
                         boolean dismissDialogOnNeutralClick, boolean accentOkButton) {
        this.context = context;
        this.dialog = new Dialog(context);
        this.dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); // Remove default title bar.

        // Create main layout.
        mainLayout = createMainLayout();
        addTitle(title);
        addContent(message, editText);
        addButtons(okButtonText, onOkClick, onCancelClick, neutralButtonText,
                onNeutralClick, dismissDialogOnNeutralClick, accentOkButton);

        // Set dialog content and window attributes.
        dialog.setContentView(mainLayout);
        Window window = dialog.getWindow();
        if (window != null) {
            Utils.setDialogWindowParameters(window, Gravity.CENTER, 0, 90, false);
        }
    }

    private LinearLayout createMainLayout() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(Dim.dp24, Dim.dp16, Dim.dp24, Dim.dp24);

        // Set rounded rectangle background.
        ShapeDrawable background = new ShapeDrawable(new RoundRectShape(
                Dim.roundedCorners(28), null, null));
        // Dialog background.
        background.getPaint().setColor(getDialogBackgroundColor());
        layout.setBackground(background);

        return layout;
    }

    private void addTitle(CharSequence title) {
        if (TextUtils.isEmpty(title)) return;

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextSize(18);
        titleView.setTextColor(getAppForegroundColor());
        titleView.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, Dim.dp16);
        titleView.setLayoutParams(params);

        mainLayout.addView(titleView);
    }

    private void addContent(CharSequence message, @Nullable EditText editText) {
        if (editText != null) {
            // Check if the EditText has an existing parent and remove it.
            ViewGroup parent = (ViewGroup) editText.getParent();
            if (parent != null) parent.removeView(editText);

            // Add new EditText to the dialog.
            ShapeDrawable background = new ShapeDrawable(new RoundRectShape(
                    Dim.roundedCorners(10), null, null));
            background.getPaint().setColor(getEditTextBackground());
            editText.setBackground(background);
            editText.setPadding(Dim.dp12, Dim.dp8, Dim.dp12, Dim.dp8);
            editText.setTypeface(Typeface.DEFAULT);
            editText.setTextSize(15);
            editText.setSingleLine(true);
            editText.setGravity(Gravity.START);
            editText.setFocusable(true);
            editText.setFocusableInTouchMode(true);
            editText.setTextColor(getAppForegroundColor());

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Dim.dp36);
            editText.setLayoutParams(params);

            mainLayout.addView(editText);
        } else if (!TextUtils.isEmpty(message)) {
            TextView messageView = new TextView(context);
            messageView.setText(message);
            messageView.setTextSize(14);
            messageView.setTextColor(getAppForegroundColor());
            messageView.setGravity(Gravity.CENTER);

            // Enable link clicks if message contains clickable Spans.
            if (message instanceof Spanned) {
                messageView.setMovementMethod(LinkMovementMethod.getInstance());
            }

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            messageView.setLayoutParams(params);

            mainLayout.addView(messageView);
        }
    }

    private void addButtons(CharSequence okButtonText, Runnable onOkClick, Runnable onCancelClick,
                            CharSequence neutralButtonText, Runnable onNeutralClick,
                            boolean dismissDialogOnNeutralClick, boolean accentOkButton) {
        LinearLayout buttonContainer = new LinearLayout(context);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.setMargins(0, Dim.dp24, 0, 0);
        buttonContainer.setLayoutParams(containerParams);

        List<Button> buttons = new ArrayList<>();

        if (!TextUtils.isEmpty(neutralButtonText)) {
            Button neutralButton = createButton(context, dialog, neutralButtonText, onNeutralClick, false, dismissDialogOnNeutralClick);
            buttons.add(neutralButton);
        }

        if (onCancelClick != null) {
            String localizedCancelString = ResourceUtils.getSystemStringByLocale("cancel",
                    BaseSettings.MORPHE_LANGUAGE.get().getLocale());
            Button cancelButton = createButton(context, dialog, localizedCancelString,
                    onCancelClick, false, true);
            buttons.add(cancelButton);
        }

        if (onOkClick != null || okButtonText != null) {
            String localizedOkString = !TextUtils.isEmpty(okButtonText)
                    ? okButtonText.toString()
                    : ResourceUtils.getSystemStringByLocale("ok",
                    BaseSettings.MORPHE_LANGUAGE.get().getLocale());
            Button okButton = createButton(context, dialog, localizedOkString,
                    onOkClick, accentOkButton, true);
            buttons.add(okButton);
        }

        layoutButtons(buttonContainer, buttons);
        mainLayout.addView(buttonContainer);
    }

    public static Button createButton(Context context, @Nullable Dialog dialog,
                                      CharSequence text, @Nullable Runnable onClick,
                                      boolean isOkButton, boolean dismissDialog) {
        Button button = new Button(context, null, 0);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setGravity(Gravity.CENTER);
        // Set internal padding.
        button.setPadding(Dim.dp8, 0, Dim.dp8, 0);
        // Clear theme-imposed minimum width so text is never clipped with ellipsis.
        button.setMinWidth(0);
        button.setMinimumWidth(0);

        // Background color for OK button and Cancel/Neutral buttons.
        ShapeDrawable background = new ShapeDrawable(new RoundRectShape(
                Dim.roundedCorners(20), null, null));
        background.getPaint().setColor(isOkButton
                ? getOkButtonBackgroundColor()
                : getCancelOrNeutralButtonBackgroundColor());
        button.setBackground(background);

        button.setTextColor(Utils.isDarkModeEnabled()
                ? (isOkButton ? Color.BLACK : Color.WHITE)
                : (isOkButton ? Color.WHITE : Color.BLACK));

        ViewAnimations.applyPressEffect(button);

        button.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
            if (dismissDialog && dialog != null) dialog.dismiss();
        });

        return button;
    }

    private void layoutButtons(LinearLayout buttonContainer, List<Button> buttons) {
        if (buttons.isEmpty()) return;

        if (buttons.size() == 1) {
            layoutSingleButton(buttonContainer, buttons.get(0));
            return;
        }

        // Measure widths of all buttons.
        int totalWidth = 0;
        List<Integer> buttonWidths = new ArrayList<>();
        for (Button button : buttons) {
            button.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int width = button.getMeasuredWidth();
            buttonWidths.add(width);
            totalWidth += width;
        }

        // Add 8dp margins between buttons.
        totalWidth += Dim.dp8 * (buttons.size() - 1);

        // Check if buttons fit within 80% of screen width.
        int maxWidth = (int) (Dim.getScreenWidth() * 0.8);

        if (totalWidth <= maxWidth) {
            layoutButtonsInRow(buttonContainer, buttons, buttonWidths);
        } else {
            layoutButtonsInColumns(buttonContainer, buttons);
        }
    }

    private void layoutSingleButton(LinearLayout buttonContainer, Button button) {
        LinearLayout singleContainer = new LinearLayout(context);
        singleContainer.setOrientation(LinearLayout.HORIZONTAL);
        singleContainer.setGravity(Gravity.CENTER);

        ViewGroup parent = (ViewGroup) button.getParent();
        if (parent != null) parent.removeView(button);

        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Dim.dp36));
        singleContainer.addView(button);
        buttonContainer.addView(singleContainer);
    }

    private void layoutButtonsInRow(LinearLayout buttonContainer, List<Button> buttons, List<Integer> buttonWidths) {
        LinearLayout rowContainer = new LinearLayout(context);
        rowContainer.setOrientation(LinearLayout.HORIZONTAL);
        rowContainer.setGravity(Gravity.CENTER);
        rowContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Add all buttons with proportional weights and specific margins.
        for (int i = 0; i < buttons.size(); i++) {
            Button button = getButton(buttons, buttonWidths, i);
            rowContainer.addView(button);
        }

        buttonContainer.addView(rowContainer);
    }

    @NonNull
    private Button getButton(List<Button> buttons, List<Integer> buttonWidths, int i) {
        Button button = buttons.get(i);
        ViewGroup parent = (ViewGroup) button.getParent();
        if (parent != null) parent.removeView(button);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, Dim.dp36, buttonWidths.get(i));

        // Set margins based on button type and combination.
        if (buttons.size() == 2) {
            // Neutral + OK or Cancel + OK.
            params.setMargins(i == 0 ? 0 : Dim.dp4, 0, i == 0 ? Dim.dp4 : 0, 0);
        } else if (buttons.size() == 3) {
            // Neutral.
            // Cancel.
            // OK.
            params.setMargins(i == 0 ? 0 : Dim.dp4, 0, i == 2 ? 0 : Dim.dp4, 0);
        }

        button.setLayoutParams(params);
        return button;
    }

    private void layoutButtonsInColumns(LinearLayout buttonContainer, List<Button> buttons) {
        // Reorder: OK, Cancel, Neutral.
        List<Button> reorderedButtons = new ArrayList<>();
        if (buttons.size() == 3) {
            reorderedButtons.add(buttons.get(2)); // OK
            reorderedButtons.add(buttons.get(1)); // Cancel
            reorderedButtons.add(buttons.get(0)); // Neutral
        } else if (buttons.size() == 2) {
            reorderedButtons.add(buttons.get(1)); // OK or Cancel
            reorderedButtons.add(buttons.get(0)); // Neutral or Cancel
        }

        for (int i = 0; i < reorderedButtons.size(); i++) {
            Button button = reorderedButtons.get(i);
            LinearLayout singleContainer = new LinearLayout(context);
            singleContainer.setOrientation(LinearLayout.HORIZONTAL);
            singleContainer.setGravity(Gravity.CENTER);
            singleContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Dim.dp36));

            ViewGroup parent = (ViewGroup) button.getParent();
            if (parent != null) parent.removeView(button);

            button.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Dim.dp36));
            singleContainer.addView(button);
            buttonContainer.addView(singleContainer);

            // Add a spacer between the buttons (except the last one).
            if (i < reorderedButtons.size() - 1) {
                View spacer = new View(context);
                LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        Dim.dp8);
                spacer.setLayoutParams(spacerParams);
                buttonContainer.addView(spacer);
            }
        }
    }
}
