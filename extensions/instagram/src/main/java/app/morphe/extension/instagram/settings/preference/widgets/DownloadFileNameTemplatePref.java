/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.ZoneId;

import app.morphe.extension.instagram.patches.download.DownloadFileNameFormatter;

public final class DownloadFileNameTemplatePref extends EditTextPref {
    private TextView previewView;
    private TextWatcher previewWatcher;
    private FrameLayout editorContainer;
    private Button positiveButton;

    public DownloadFileNameTemplatePref(Context context) {
        super(context);
    }

    @Override
    protected View onCreateDialogView() {
        Context context = getContext();
        int horizontalPadding = InstagramPreferenceStyle.dp(context, 24);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(horizontalPadding, 0, horizontalPadding, 0);

        EditText editText = getEditText();
        ViewParent currentParent = editText.getParent();
        if (currentParent instanceof ViewGroup) {
            ((ViewGroup) currentParent).removeView(editText);
        }
        editText.setCursorVisible(true);
        editText.setSingleLine(true);
        editText.setHorizontallyScrolling(true);
        editText.setMinLines(1);
        editText.setMaxLines(1);
        editText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

        editorContainer = new FrameLayout(context);
        container.addView(editorContainer, matchWidthWrapHeight());

        previewView = new TextView(context);
        previewView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        previewView.setIncludeFontPadding(false);
        previewView.setPaddingRelative(
                editText.getPaddingStart(),
                0,
                editText.getPaddingEnd(),
                InstagramPreferenceStyle.dp(context, 4)
        );
        container.addView(previewView, matchWidthWrapHeight());

        container.addView(
                createSectionLabel(context, "piko_download_file_name_variables"),
                matchWidthWrapHeight()
        );

        HorizontalScrollView tokenScroller = new HorizontalScrollView(context);
        tokenScroller.setHorizontalScrollBarEnabled(false);
        tokenScroller.setHorizontalFadingEdgeEnabled(true);
        tokenScroller.setFadingEdgeLength(InstagramPreferenceStyle.dp(context, 24));
        tokenScroller.setFillViewport(false);
        tokenScroller.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout tokenRow = new LinearLayout(context);
        tokenRow.setOrientation(LinearLayout.HORIZONTAL);
        tokenRow.setPadding(0, 0, InstagramPreferenceStyle.dp(context, 12), 0);
        DownloadFileNameFormatter.Token[] tokens = DownloadFileNameFormatter.Token.values();
        for (DownloadFileNameFormatter.Token token : tokens) {
            String label = str("piko_download_file_name_token_" + token.key());
            TextView field = createInsertButton(context, label, token.placeholder());
            LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            int fieldMargin = InstagramPreferenceStyle.dp(context, 4);
            fieldParams.setMargins(fieldMargin, fieldMargin, fieldMargin, fieldMargin);
            tokenRow.addView(field, fieldParams);
        }
        tokenScroller.addView(tokenRow);
        LinearLayout.LayoutParams tokenScrollerParams = matchWidthWrapHeight();
        tokenScrollerParams.bottomMargin = InstagramPreferenceStyle.dp(context, 8);
        container.addView(tokenScroller, tokenScrollerParams);

        if (previewWatcher != null) {
            editText.removeTextChangedListener(previewWatcher);
        }
        previewWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                updatePreview(editable.toString());
            }
        };
        editText.addTextChangedListener(previewWatcher);
        updatePreview(editText.getText().toString());

        return container;
    }

    @Override
    protected void onAddEditTextToDialogView(View dialogView, EditText editText) {
        if (editorContainer == null) {
            super.onAddEditTextToDialogView(dialogView, editText);
            return;
        }
        editorContainer.addView(
                editText,
                0,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                )
        );
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setNeutralButton(str("piko_reset"), null);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        Dialog dialog = getDialog();
        if (!(dialog instanceof AlertDialog)) {
            return;
        }
        AlertDialog alertDialog = (AlertDialog) dialog;
        positionEditorAtEnd(getEditText());
        Button neutralButton = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (neutralButton != null) {
            neutralButton.setOnClickListener(view -> resetTemplate());
        }

        positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (positiveButton == null) {
            return;
        }
        updatePreview(getEditText().getText().toString());
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (previewWatcher != null) {
            getEditText().removeTextChangedListener(previewWatcher);
            previewWatcher = null;
        }
        previewView = null;
        editorContainer = null;
        positiveButton = null;
    }

    private TextView createSectionLabel(Context context, String resourceName) {
        TextView label = new TextView(context);
        label.setText(str(resourceName));
        label.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setPadding(0, 0, 0, InstagramPreferenceStyle.dp(context, 4));
        return label;
    }

    private TextView createInsertButton(
            Context context,
            String label,
            String component
    ) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(InstagramPreferenceStyle.dp(context, 44));
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setIncludeFontPadding(false);
        int horizontalPadding = InstagramPreferenceStyle.dp(context, 14);
        int verticalPadding = InstagramPreferenceStyle.dp(context, 7);
        button.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
        );
        button.setBackground(createButtonBackground(context));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(str("piko_download_file_name_insert_token", label));
        button.setOnClickListener(view -> insertComponent(component));
        applyButtonAccessibility(button);
        return button;
    }

    private void applyButtonAccessibility(TextView button) {
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                    View host,
                    AccessibilityNodeInfo info
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
    }

    private StateListDrawable createButtonBackground(Context context) {
        int outlineColor = withAlpha(InstagramPreferenceStyle.secondaryTextColor(), 0.7f);
        StateListDrawable background = new StateListDrawable();
        background.addState(
                new int[]{android.R.attr.state_pressed},
                roundedBackground(
                        context,
                        InstagramPreferenceStyle.pressedBackgroundColor(),
                        outlineColor,
                        8
                )
        );
        background.addState(
                new int[]{},
                roundedBackground(context, Color.TRANSPARENT, outlineColor, 8)
        );
        return background;
    }

    private GradientDrawable roundedBackground(
            Context context,
            int fillColor,
            int outlineColor,
            float radiusDp
    ) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(fillColor);
        if (outlineColor != Color.TRANSPARENT) {
            background.setStroke(InstagramPreferenceStyle.dp(context, 1), outlineColor);
        }
        background.setCornerRadius(InstagramPreferenceStyle.dp(context, radiusDp));
        return background;
    }

    private int withAlpha(int color, float alphaMultiplier) {
        return color & 0x00ffffff
                | Math.round(Color.alpha(color) * alphaMultiplier) << 24;
    }

    private void insertComponent(String component) {
        EditText editText = getEditText();
        Editable editable = editText.getText();
        int selectionStart = editText.getSelectionStart();
        int selectionEnd = editText.getSelectionEnd();
        if (selectionStart < 0 || selectionEnd < 0) {
            selectionStart = editable.length();
            selectionEnd = selectionStart;
        }
        int replaceStart = Math.min(selectionStart, selectionEnd);
        int replaceEnd = Math.max(selectionStart, selectionEnd);
        editable.replace(replaceStart, replaceEnd, component);
        editText.setSelection(replaceStart + component.length());
        editText.requestFocus();
    }

    private void resetTemplate() {
        EditText editText = getEditText();
        editText.setText(DownloadFileNameFormatter.DEFAULT_TEMPLATE);
        positionEditorAtEnd(editText);
        editText.requestFocus();
    }

    private void positionEditorAtEnd(EditText editText) {
        editText.setSelection(editText.length());
        editText.post(() -> {
            int end = editText.length();
            editText.setSelection(end);
            editText.bringPointIntoView(end);
        });
    }

    private void updatePreview(String template) {
        if (previewView == null) {
            return;
        }
        getEditText().setError(null);
        boolean empty = template.isEmpty();
        DownloadFileNameFormatter.Values previewValues =
                new DownloadFileNameFormatter.Values(
                        "username",
                        "123456",
                        "ABC123",
                        System.currentTimeMillis(),
                        "image",
                        0,
                        "1080x1440"
                );
        ZoneId zoneId = ZoneId.systemDefault();
        boolean valid = DownloadFileNameFormatter.isValidTemplate(
                template,
                previewValues,
                ".jpg",
                zoneId
        );
        if (positiveButton != null) {
            positiveButton.setEnabled(valid);
        }
        if (empty) {
            previewView.setText("");
            return;
        }
        if (!valid) {
            previewView.setText(str("piko_download_file_name_template_invalid"));
            previewView.setTextColor(errorTextColor(getContext()));
            return;
        }

        String example = DownloadFileNameFormatter.format(
                template,
                previewValues,
                ".jpg",
                zoneId
        );
        previewView.setText(example);
        previewView.setTextColor(InstagramPreferenceStyle.primaryTextColor());
    }

    private int errorTextColor(Context context) {
        TypedValue errorColor = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.colorError, errorColor, true)) {
            return errorColor.data;
        }
        return 0xffed4956;
    }

    private static LinearLayout.LayoutParams matchWidthWrapHeight() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }
}
