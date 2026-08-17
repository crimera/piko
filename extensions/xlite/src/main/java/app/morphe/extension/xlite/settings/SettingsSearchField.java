package app.morphe.extension.xlite.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.xlite.misc.UpdateFont;
import app.morphe.extension.xlite.ui.Theme;

/** The rounded search field used by the X-Lite settings root screen. */
final class SettingsSearchField extends LinearLayout {
    interface Listener {
        void onQueryChanged(String query);
    }

    private final EditText input;
    private final ImageView clearButton;
    private final TextView noResults;
    private final LinearLayout searchField;
    private final View trailingSpacer;
    private boolean suppressNotifications;
    private Listener listener;

    SettingsSearchField(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(4), dp(16), dp(4));
        setBackgroundColor(Theme.surfaceContainer(context));

        searchField = new LinearLayout(context);
        searchField.setOrientation(HORIZONTAL);
        searchField.setGravity(Gravity.CENTER);
        searchField.setPaddingRelative(dp(14), 0, dp(14), 0);
        searchField.setBackground(createSearchFieldBackground(context));
        searchField.setClickable(true);
        searchField.setFocusable(true);
        searchField.setOnClickListener(ignored -> focusInput());

        ImageView searchIcon = new ImageView(context);
        searchIcon.setScaleType(ImageView.ScaleType.CENTER);
        searchIcon.setColorFilter(Theme.secondaryText(context));
        int searchIconId = drawableId(context, "ic_vector_search_stroke");
        if (searchIconId != 0) {
            searchIcon.setImageResource(searchIconId);
        } else {
            searchIcon.setVisibility(GONE);
        }
        LayoutParams searchIconParams = new LayoutParams(dp(26), dp(26));
        searchIconParams.setMarginEnd(dp(8));
        searchField.addView(searchIcon, searchIconParams);

        input = new EditText(context);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setTextColor(Theme.primaryText(context));
        input.setHintTextColor(Theme.secondaryText(context));
        input.setHint(StringRef.str("piko_xlite_settings_search_hint"));
        input.setTypeface(UpdateFont.customTypefaceOr(input.getTypeface()));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(0, 0, 0, 0);
        input.setMinHeight(0);
        input.setMinWidth(0);
        input.setMinimumWidth(0);
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        input.setOnEditorActionListener((textView, actionId, event) -> {
            boolean enterKey = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || enterKey) {
                hideKeyboard(input);
                input.clearFocus();
                return true;
            }
            return false;
        });
        searchField.addView(input, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        trailingSpacer = new View(context);
        trailingSpacer.setVisibility(GONE);
        searchField.addView(trailingSpacer, new LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                0f
        ));

        clearButton = new ImageView(context);
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setColorFilter(Theme.primaryAccent(context));
        int clearIconId = drawableId(context, "ic_vector_close");
        clearButton.setImageResource(
                clearIconId == 0 ? android.R.drawable.ic_menu_close_clear_cancel : clearIconId
        );
        clearButton.setContentDescription(
                StringRef.str("piko_xlite_settings_search_clear")
        );
        clearButton.setClickable(true);
        clearButton.setFocusable(true);
        clearButton.setVisibility(GONE);
        clearButton.setOnClickListener(ignored -> {
            input.setText("");
            focusInput();
        });
        searchField.addView(clearButton, new LayoutParams(dp(40), dp(40)));

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSearchLayout(s.length() > 0);
                if (!suppressNotifications && listener != null) {
                    listener.onQueryChanged(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        addView(searchField, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
        ));

        noResults = XLiteSettingsUi.titleText(context);
        noResults.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        noResults.setTypeface(
                UpdateFont.customTypefaceOr(Typeface.DEFAULT),
                Typeface.BOLD
        );
        noResults.setTextColor(Theme.primaryText(context));
        noResults.setPadding(
                dp(24),
                dp(20),
                dp(24),
                dp(12)
        );
        noResults.setVisibility(GONE);
        addView(noResults, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        updateSearchLayout(false);
    }

    void setOnQueryChangedListener(Listener listener) {
        this.listener = listener;
    }

    String query() {
        return input.getText().toString();
    }

    void setQuery(String query) {
        suppressNotifications = true;
        input.setText(query == null ? "" : query);
        input.setSelection(input.length());
        suppressNotifications = false;
        updateSearchLayout(input.length() > 0);
    }

    void setNoResults(boolean visible, CharSequence text) {
        if (visible) noResults.setText(text);
        noResults.setVisibility(visible ? VISIBLE : GONE);
    }

    private void updateSearchLayout(boolean queryActive) {
        searchField.setGravity(
                queryActive
                        ? Gravity.CENTER_VERTICAL | Gravity.START
                        : Gravity.CENTER
        );
        trailingSpacer.setVisibility(queryActive ? VISIBLE : GONE);
        clearButton.setVisibility(queryActive ? VISIBLE : GONE);
        LinearLayout.LayoutParams spacerParams =
                (LinearLayout.LayoutParams) trailingSpacer.getLayoutParams();
        spacerParams.weight = queryActive ? 1f : 0f;
        trailingSpacer.setLayoutParams(spacerParams);
    }

    private void focusInput() {
        input.requestFocus();
        input.post(() -> {
            if (input.isAttachedToWindow()) showKeyboard(input);
        });
    }

    private StateListDrawable createSearchFieldBackground(Context context) {
        StateListDrawable background = new StateListDrawable();
        background.addState(
                new int[]{android.R.attr.state_pressed},
                createRoundedSearchFieldDrawable(
                        context,
                        Theme.blend(
                                Theme.surfaceContainerHigh(context),
                                Theme.primaryText(context),
                                0.08f
                        )
                )
        );
        background.addState(
                new int[]{android.R.attr.state_focused},
                createRoundedSearchFieldDrawable(
                        context,
                        Theme.surfaceVariant(context)
                )
        );
        background.addState(
                new int[]{},
                createRoundedSearchFieldDrawable(
                        context,
                        Theme.surfaceContainerHigh(context)
                )
        );
        return background;
    }

    private GradientDrawable createRoundedSearchFieldDrawable(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setStroke(dp(1), Theme.dividerColor(context));
        drawable.setCornerRadius(dp(20));
        return drawable;
    }

    private int drawableId(Context context, String name) {
        return context.getResources().getIdentifier(
                name,
                "drawable",
                context.getPackageName()
        );
    }

    private int dp(float value) {
        return Theme.dpToPx(getContext(), value);
    }

    private static void hideKeyboard(View view) {
        Object service = view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (service instanceof InputMethodManager inputMethodManager) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private static void showKeyboard(View view) {
        Object service = view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (service instanceof InputMethodManager inputMethodManager) {
            inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }
}
