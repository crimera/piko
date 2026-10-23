/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;

public final class SettingsSearchController {
    public interface Listener {
        void onSearchStateChanged(State state);

        void onSearchChromeChanged(boolean inToolbar);
    }

    public static final class State {
        public final String query;
        public final boolean searchMode;
        public final List<SettingsSearchIndex.Result> results;
        public final boolean noResults;

        private State(
                String query,
                boolean searchMode,
                List<SettingsSearchIndex.Result> results,
                boolean noResults
        ) {
            this.query = query;
            this.searchMode = searchMode;
            this.results = Collections.unmodifiableList(new ArrayList<>(results));
            this.noResults = noResults;
        }
    }

    private final Context context;
    private SettingsSearchIndex index;
    private final Listener listener;
    private final LinearLayout searchContainer;
    private final EditText searchInput;
    private final View clearButton;

    private State state;
    private boolean searchMode;
    private boolean suppressTextChanges;
    private FrameLayout stableParent;

    public SettingsSearchController(Context context, SettingsSearchIndex index, Listener listener) {
        this.context = context;
        this.index = index;
        this.listener = listener;

        searchContainer = createSearchBar();
        View searchIcon = searchContainer.getChildAt(0);

        searchInput = new EditText(context);
        searchInput.setSingleLine(true);
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        searchInput.setHintTextColor(InstagramPreferenceStyle.secondaryTextColor());
        searchInput.setHint(str("piko_settings_search_hint"));
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        searchInput.setPaddingRelative(0, 0, 0, 0);
        searchInput.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        searchInput.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        searchInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        searchInput.setHighlightColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            GradientDrawable cursor = new GradientDrawable();
            cursor.setColor(InstagramPreferenceStyle.primaryTextColor());
            cursor.setSize(
                    InstagramPreferenceStyle.dp(context, 2),
                    InstagramPreferenceStyle.dp(context, 24)
            );
            searchInput.setTextCursorDrawable(cursor);
        }
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchContainer.addView(searchInput, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
        ));

        ImageView clearIcon = new ImageView(context);
        clearIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int clearHorizontalPadding = InstagramPreferenceStyle.dp(context, 14);
        int clearVerticalPadding = InstagramPreferenceStyle.dp(context, 16);
        clearIcon.setPadding(clearHorizontalPadding, clearVerticalPadding,
                clearHorizontalPadding, clearVerticalPadding);
        UI.setThemedIcon(clearIcon, UI.DRAWABLE_CLEAR, "igds_color_secondary_text");
        clearButton = clearIcon;
        clearButton.setContentDescription(str("piko_settings_search_clear"));
        clearButton.setClickable(true);
        clearButton.setFocusable(true);
        clearButton.setVisibility(View.GONE);
        searchContainer.addView(clearButton, new LinearLayout.LayoutParams(
                InstagramPreferenceStyle.dp(context, 44),
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                removeComposingBackgroundSpans(editable);
                if (suppressTextChanges) {
                    return;
                }
                updateQuery(editable.toString());
                notifyListener();
            }
        });
        searchInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus && !searchMode) {
                    enterSearchMode();
                }
            }
        });
        View.OnClickListener focusSearch = view -> {
            if (!searchMode) {
                enterSearchMode();
            } else {
                searchInput.requestFocus();
                showKeyboard();
            }
        };
        searchIcon.setOnClickListener(focusSearch);
        searchContainer.setOnClickListener(focusSearch);
        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, android.view.KeyEvent event) {
                if (actionId != EditorInfo.IME_ACTION_SEARCH) {
                    return false;
                }
                hideKeyboard();
                searchInput.clearFocus();
                return true;
            }
        });
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                searchInput.setText("");
            }
        });

        updateQuery("");
    }

    public void attach(FrameLayout stableParent) {
        if (stableParent == null || this.stableParent != null) {
            return;
        }
        this.stableParent = stableParent;
        stableParent.addView(searchContainer, layoutParams());
        searchContainer.setVisibility(searchMode ? View.VISIBLE : View.GONE);
    }

    public View createSearchEntryView() {
        FrameLayout entry = new FrameLayout(context);
        entry.setPaddingRelative(
                InstagramPreferenceStyle.dp(context, 16), 0,
                InstagramPreferenceStyle.dp(context, 16),
                InstagramPreferenceStyle.dp(context, 10)
        );
        entry.setContentDescription(str("piko_settings_search_hint"));
        entry.setFocusable(true);
        entry.setOnClickListener(view -> enterSearchMode());

        // Only the entry scrolls; the editable field keeps its parent and input connection.
        LinearLayout bar = createSearchBar();
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        TextView hint = new TextView(context);
        hint.setText(str("piko_settings_search_hint"));
        hint.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        hint.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        hint.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        hint.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        hint.setSingleLine(true);
        bar.addView(hint, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
        ));
        entry.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                InstagramPreferenceStyle.dp(context, 48)
        ));
        return entry;
    }

    private LinearLayout createSearchBar() {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(InstagramPreferenceStyle.searchBackgroundColor());
        background.setCornerRadius(InstagramPreferenceStyle.dp(context, 12));
        bar.setBackground(background);
        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int horizontalPadding = InstagramPreferenceStyle.dp(context, 11);
        int verticalPadding = InstagramPreferenceStyle.dp(context, 14);
        icon.setPadding(horizontalPadding, verticalPadding,
                horizontalPadding, verticalPadding);
        UI.setThemedIcon(icon, UI.DRAWABLE_SEARCH, "igds_color_secondary_text");
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.addView(icon, new LinearLayout.LayoutParams(
                InstagramPreferenceStyle.dp(context, 42),
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return bar;
    }

    private void enterSearchMode() {
        if (searchMode || stableParent == null) {
            return;
        }

        searchMode = true;
        rebuildState();
        notifyListener();
        searchContainer.setVisibility(View.VISIBLE);
        if (listener != null) {
            listener.onSearchChromeChanged(true);
        }
        searchInput.requestFocus();
        searchInput.setSelection(searchInput.length());
        showKeyboard();
    }

    public void exitSearchMode() {
        if (!searchMode) {
            return;
        }

        hideKeyboard();
        searchInput.clearFocus();
        suppressTextChanges = true;
        try {
            searchInput.setText("");
        } finally {
            suppressTextChanges = false;
        }
        searchMode = false;
        rebuildState();
        notifyListener();
        searchContainer.setVisibility(View.GONE);
        if (listener != null) {
            listener.onSearchChromeChanged(false);
        }
    }

    public void restore(String query, boolean searchMode) {
        String restoredQuery = query == null ? "" : query;
        suppressTextChanges = true;
        try {
            searchInput.setText(restoredQuery);
            searchInput.setSelection(searchInput.length());
        } finally {
            suppressTextChanges = false;
        }

        this.searchMode = searchMode;
        updateQuery(restoredQuery);
        searchContainer.setVisibility(searchMode ? View.VISIBLE : View.GONE);
        if (listener != null) {
            listener.onSearchChromeChanged(searchMode);
        }
        notifyListener();
        if (searchMode) {
            searchInput.requestFocus();
            searchInput.setSelection(searchInput.length());
            showKeyboard();
        }
    }

    public State state() {
        return state;
    }

    public void replaceIndex(SettingsSearchIndex index) {
        this.index = index;
        rebuildState();
        if (searchMode) {
            notifyListener();
        }
    }

    private void updateQuery(String query) {
        List<SettingsSearchIndex.Result> results = index.search(query);
        boolean noResults = searchMode
                && SettingsSearchMatcher.isSearchQueryReady(query)
                && results.isEmpty();
        state = new State(query, searchMode, results, noResults);
        clearButton.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void rebuildState() {
        updateQuery(searchInput.getText().toString());
    }

    private static void removeComposingBackgroundSpans(Editable editable) {
        if (editable == null || editable.length() == 0) {
            return;
        }
        BackgroundColorSpan[] spans = editable.getSpans(
                0,
                editable.length(),
                BackgroundColorSpan.class
        );
        for (BackgroundColorSpan span : spans) {
            editable.removeSpan(span);
        }
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onSearchStateChanged(state);
        }
    }

    private FrameLayout.LayoutParams layoutParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                InstagramPreferenceStyle.dp(context, 48)
        );
        params.gravity = Gravity.START | Gravity.TOP;
        params.setMarginStart(InstagramPreferenceStyle.dp(context, 51));
        params.setMarginEnd(InstagramPreferenceStyle.dp(context, 15));
        params.topMargin = InstagramPreferenceStyle.dp(context, 11);
        return params;
    }

    private void showKeyboard() {
        searchInput.post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (inputMethodManager != null) {
                    inputMethodManager.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });
    }

    private void hideKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }
}
