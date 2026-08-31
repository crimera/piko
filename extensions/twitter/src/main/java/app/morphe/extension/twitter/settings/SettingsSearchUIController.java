/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.BidiFormatter;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import java.lang.reflect.Proxy;
import java.util.Locale;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import static app.morphe.extension.shared.StringRef.str;

@SuppressLint("StaticFieldLeak")
public final class SettingsSearchUIController {
    private static Toolbar settingsSearchToolbar;
    private static Listener settingsSearchListener;
    private static final SettingsSearchSession settingsSearchSession = new SettingsSearchSession();
    private static View settingsSearchEntryBar;
    private static View settingsSearchContentView;
    private static LinearLayout settingsSearchEmptyState;
    private static TextView settingsSearchEmptyTitle;
    private static View toolbarSearchOverlay;
    private static EditText toolbarSearchInput;
    private static ImageView toolbarSearchClear;
    private static final SettingsSearchOwnerTracker<Activity> settingsSearchStateOwner =
            new SettingsSearchOwnerTracker<>();
    private static Object settingsSearchBackDispatcher;
    private static Object settingsSearchBackCallback;
    private static boolean settingsSearchBackCallbackRegistered;
    private static SettingsSearchSession.State pendingConfigurationState;
    private static final int TWITTER_BLUE = ResourceUtils.getColor("twitter_blue");

    private SettingsSearchUIController() {
    }

    public interface Listener {
        void onSettingsSearchQueryChanged(String query);
    }

    public static void setListener(Activity activity, Listener listener) {
        if (settingsSearchStateOwner.current() == activity) {
            settingsSearchListener = listener;
        }
    }

    public static void release(Activity activity) {
        settingsSearchStateOwner.clearIfOwnedBy(activity, () -> {
            pendingConfigurationState = activity != null && activity.isChangingConfigurations()
                    ? settingsSearchSession.snapshot()
                    : null;
            unregisterSettingsSearchBackCallback(activity);
            clearReferences();
        });
    }

    public static String query() {
        return settingsSearchSession.query();
    }

    public static void restoreToolbarTitle(Activity activity) {
        if (settingsSearchStateOwner.current() != activity
                || settingsSearchSession.isActive()
                || settingsSearchToolbar == null) {
            return;
        }
        settingsSearchToolbar.setTitle(str("piko_title_settings"));
        settingsSearchToolbar.setNavigationOnClickListener(view -> {
            Activity owner = settingsSearchStateOwner.current();
            if (owner != null) {
                owner.onBackPressed();
            }
        });
    }

    public static void install(Activity act, Toolbar toolbar, int fragmentContainerId) {
        View fragmentContainer = act.findViewById(fragmentContainerId);
        ViewParent toolbarParent = toolbar == null ? null : toolbar.getParent();
        ViewParent fragmentParent = fragmentContainer == null ? null : fragmentContainer.getParent();
        if (!(toolbarParent instanceof ViewGroup) || !(fragmentParent instanceof ViewGroup)) {
            return;
        }

        ViewGroup toolbarParentGroup = (ViewGroup) toolbarParent;
        ViewGroup parentGroup = (ViewGroup) fragmentParent;
        int toolbarIndex = toolbarParentGroup.indexOfChild(toolbar);
        int childIndex = parentGroup.indexOfChild(fragmentContainer);
        if (toolbarIndex < 0 || childIndex < 0) {
            return;
        }

        SettingsSearchSession.State configurationState = pendingConfigurationState;
        pendingConfigurationState = null;
        Activity previousActivity = settingsSearchStateOwner.replaceWith(act);
        unregisterSettingsSearchBackCallback(previousActivity);
        clearReferences();
        settingsSearchSession.restore(configurationState);
        wrapToolbarForSettingsSearch(act, toolbar, toolbarParentGroup, toolbarIndex);

        ViewGroup.LayoutParams originalParams = fragmentContainer.getLayoutParams();
        parentGroup.removeView(fragmentContainer);

        LinearLayout wrapper = new LinearLayout(act);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(SettingsSearchColors.current(act).settingsBackgroundColor);

        settingsSearchEntryBar = createSettingsSearchEntryBar(act);
        wrapper.addView(settingsSearchEntryBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout contentFrame = new FrameLayout(act);
        contentFrame.setBackgroundColor(SettingsSearchColors.current(act).settingsBackgroundColor);
        settingsSearchContentView = fragmentContainer;
        contentFrame.addView(fragmentContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        settingsSearchEmptyState = createSettingsSearchEmptyState(act);
        settingsSearchEmptyState.setVisibility(View.GONE);
        contentFrame.addView(settingsSearchEmptyState, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        wrapper.addView(contentFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        if (originalParams != null) {
            parentGroup.addView(wrapper, childIndex, originalParams);
        } else {
            parentGroup.addView(wrapper, childIndex, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }
        restoreInstalledSearchState(act);
    }

    private static void restoreInstalledSearchState(Activity activity) {
        if (!settingsSearchSession.isActive()
                || settingsSearchToolbar == null
                || toolbarSearchOverlay == null
                || toolbarSearchInput == null) {
            return;
        }

        if (settingsSearchEntryBar != null) {
            settingsSearchEntryBar.setVisibility(View.GONE);
        }
        settingsSearchToolbar.setTitle("");
        settingsSearchToolbar.setNavigationOnClickListener(view -> exitSettingsSearchMode(activity));
        toolbarSearchOverlay.setVisibility(View.VISIBLE);
        registerSettingsSearchBackCallback(activity);

        EditText searchInput = toolbarSearchInput;
        searchInput.setText(settingsSearchSession.query());
        searchInput.setSelection(searchInput.getText().length());
        searchInput.post(() -> {
            if (settingsSearchStateOwner.current() != activity
                    || toolbarSearchInput != searchInput
                    || !searchInput.isAttachedToWindow()) {
                return;
            }
            searchInput.requestFocus();
            showKeyboard(searchInput);
        });
    }

    private static void clearReferences() {
        Toolbar ownedToolbar = settingsSearchToolbar;
        settingsSearchSession.reset();
        settingsSearchListener = null;
        settingsSearchEntryBar = null;
        settingsSearchContentView = null;
        settingsSearchEmptyState = null;
        settingsSearchEmptyTitle = null;
        toolbarSearchOverlay = null;
        toolbarSearchInput = null;
        toolbarSearchClear = null;
        settingsSearchBackDispatcher = null;
        settingsSearchBackCallback = null;
        settingsSearchBackCallbackRegistered = false;
        ActivityHook.clearToolbarIfOwnedBy(ownedToolbar);
        settingsSearchToolbar = null;
    }

    private static void wrapToolbarForSettingsSearch(
            Activity activity,
            Toolbar toolbar,
            ViewGroup parentGroup,
            int toolbarIndex
    ) {
        settingsSearchToolbar = toolbar;
        ViewGroup.LayoutParams originalParams = toolbar.getLayoutParams();
        parentGroup.removeView(toolbar);

        FrameLayout toolbarFrame = new FrameLayout(activity);
        toolbarFrame.setBackgroundColor(SettingsSearchColors.current(activity).settingsBackgroundColor);
        toolbarFrame.addView(toolbar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        toolbarSearchOverlay = createToolbarSearchView(activity);
        toolbarSearchOverlay.setVisibility(View.GONE);
        toolbarFrame.addView(toolbarSearchOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        if (originalParams != null) {
            parentGroup.addView(toolbarFrame, toolbarIndex, originalParams);
        } else {
            parentGroup.addView(toolbarFrame, toolbarIndex, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
    }

    private static View createSettingsSearchEntryBar(Activity activity) {
        Context context = activity;
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        outer.setBackgroundColor(SettingsSearchColors.current(context).settingsBackgroundColor);

        LinearLayout searchField = new LinearLayout(context);
        searchField.setOrientation(LinearLayout.HORIZONTAL);
        searchField.setGravity(Gravity.CENTER);
        searchField.setPadding(dp(context, 14), 0, dp(context, 14), 0);

        searchField.setBackground(createSearchFieldBackground(context));

        int iconColor = SettingsSearchColors.current(context).searchHintColor;
        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(ResourceUtils.getIdentifier(ResourceType.DRAWABLE, "ic_vector_search_stroke"));
        searchIcon.setColorFilter(iconColor);
        searchIcon.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams searchIconParams = new LinearLayout.LayoutParams(dp(context, 26), dp(context, 26));
        searchIconParams.setMarginEnd(dp(context, 8));
        searchField.addView(searchIcon, searchIconParams);

        TextView label = new TextView(context);
        label.setSingleLine(true);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setTextColor(SettingsSearchColors.current(context).searchHintColor);
        label.setText(str("piko_settings_search_hint"));
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        searchField.addView(label, labelParams);
        searchField.setOnClickListener(view -> enterSettingsSearchMode(activity));
        outer.setOnClickListener(view -> enterSettingsSearchMode(activity));

        outer.addView(searchField, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 40)
        ));
        return outer;
    }

    private static LinearLayout createSettingsSearchEmptyState(Context context) {
        LinearLayout empty = new LinearLayout(context);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setBackgroundColor(SettingsSearchColors.current(context).settingsBackgroundColor);
        empty.setGravity(Gravity.START);
        empty.setPadding(dp(context, 24), dp(context, 32), dp(context, 24), 0);

        settingsSearchEmptyTitle = new TextView(context);
        settingsSearchEmptyTitle.setTextColor(SettingsSearchColors.current(context).searchTextColor);
        settingsSearchEmptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        settingsSearchEmptyTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        settingsSearchEmptyTitle.setLineSpacing(0, 1.04f);
        empty.addView(settingsSearchEmptyTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return empty;
    }

    public static boolean isActive() {
        return settingsSearchSession.isActive();
    }

    public static void setContentState(String query, boolean showResults, boolean showNoResults) {
        if (settingsSearchContentView != null) {
            settingsSearchContentView.setVisibility(showResults ? View.VISIBLE : View.GONE);
        }

        if (settingsSearchEmptyState != null) {
            settingsSearchEmptyState.setVisibility(showNoResults ? View.VISIBLE : View.GONE);
        }

        if (settingsSearchEmptyTitle != null && showNoResults) {
            settingsSearchEmptyTitle.setText(str("piko_settings_search_no_results",query));
        }
    }

    private static void enterSettingsSearchMode(Activity activity) {
        if (settingsSearchSession.isActive() || settingsSearchStateOwner.current() != activity) {
            return;
        }

        Toolbar searchToolbar = settingsSearchToolbar;
        if (searchToolbar == null || toolbarSearchOverlay == null || toolbarSearchInput == null) {
            return;
        }

        long searchSessionGeneration = settingsSearchSession.enter();
        if (settingsSearchEntryBar != null) {
            settingsSearchEntryBar.setVisibility(View.GONE);
        }
        searchToolbar.setTitle("");
        searchToolbar.setNavigationOnClickListener(view -> exitSettingsSearchMode(activity));
        toolbarSearchOverlay.setVisibility(View.VISIBLE);
        registerSettingsSearchBackCallback(activity);
        notifySettingsSearchChanged();
        EditText searchInput = toolbarSearchInput;
        searchInput.post(() -> {
            if (!settingsSearchSession.isCurrent(searchSessionGeneration)
                    || settingsSearchStateOwner.current() != activity
                    || toolbarSearchInput != searchInput
                    || !searchInput.isAttachedToWindow()) {
                return;
            }
            searchInput.requestFocus();
            showKeyboard(searchInput);
        });
    }

    private static void exitSettingsSearchMode(Activity activity) {
        if (settingsSearchStateOwner.current() != activity) {
            return;
        }
        if (!settingsSearchSession.isActive()) {
            activity.onBackPressed();
            return;
        }

        Toolbar searchToolbar = settingsSearchToolbar;
        hideKeyboard(toolbarSearchInput != null ? toolbarSearchInput : searchToolbar);
        unregisterSettingsSearchBackCallback(activity);
        boolean inputClearedByTextWatcher = toolbarSearchInput != null
                && toolbarSearchInput.getText().length() > 0;
        SettingsSearchSession.ExitAction exitAction = settingsSearchSession.exit(inputClearedByTextWatcher);
        if (inputClearedByTextWatcher) {
            toolbarSearchInput.setText("");
        }
        if (toolbarSearchClear != null) {
            toolbarSearchClear.setVisibility(View.GONE);
        }
        if (toolbarSearchOverlay != null) {
            toolbarSearchOverlay.setVisibility(View.GONE);
        }
        if (settingsSearchEntryBar != null) {
            settingsSearchEntryBar.setVisibility(View.VISIBLE);
        }
        if (searchToolbar != null) {
            searchToolbar.setTitle(str("piko_title_settings"));
            searchToolbar.setNavigationOnClickListener(view -> activity.onBackPressed());
        }
        setContentState("", true, false);
        if (exitAction == SettingsSearchSession.ExitAction.NOTIFY_DIRECTLY) {
            notifySettingsSearchChanged();
        }
    }

    public static void reset() {
        Activity activity = settingsSearchStateOwner.current();
        if (settingsSearchSession.isActive() && activity != null) {
            exitSettingsSearchMode(activity);
        }
    }

    public static boolean handleBackPressed(Activity activity) {
        Activity owner = settingsSearchStateOwner.current();
        if (!settingsSearchSession.isActive() || owner == null || (activity != null && activity != owner)) {
            return false;
        }

        exitSettingsSearchMode(owner);
        return true;
    }

    private static void registerSettingsSearchBackCallback(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || activity == null
                || settingsSearchBackCallbackRegistered) {
            return;
        }

        try {
            Class<?> callbackClass = Class.forName("android.window.OnBackInvokedCallback");
            Class<?> dispatcherClass = Class.forName("android.window.OnBackInvokedDispatcher");
            Object dispatcher = Activity.class.getMethod("getOnBackInvokedDispatcher").invoke(activity);
            Object callback = Proxy.newProxyInstance(
                    SettingsSearchUIController.class.getClassLoader(),
                    new Class<?>[]{callbackClass},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        int parameterCount = method.getParameterTypes().length;
                        if ("hashCode".equals(methodName) && parameterCount == 0) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(methodName) && parameterCount == 1) {
                            return proxy == (args == null ? null : args[0]);
                        }
                        if ("toString".equals(methodName) && parameterCount == 0) {
                            return "PikoSettingsSearchBackCallback";
                        }
                        if ("onBackInvoked".equals(methodName)
                                && settingsSearchStateOwner.current() == activity) {
                            exitSettingsSearchMode(activity);
                        }
                        return null;
                    }
            );
            int priorityDefault = dispatcherClass.getField("PRIORITY_DEFAULT").getInt(null);
            dispatcher.getClass()
                    .getMethod("registerOnBackInvokedCallback", int.class, callbackClass)
                    .invoke(dispatcher, priorityDefault, callback);
            settingsSearchBackDispatcher = dispatcher;
            settingsSearchBackCallback = callback;
            settingsSearchBackCallbackRegistered = true;
        } catch (Throwable throwable) {
            Logger.printException(() -> "settings search back callback registration failed", throwable);
            settingsSearchBackDispatcher = null;
            settingsSearchBackCallback = null;
            settingsSearchBackCallbackRegistered = false;
        }
    }

    private static void unregisterSettingsSearchBackCallback(Activity activity) {
        if (!settingsSearchBackCallbackRegistered
                || settingsSearchBackCallback == null) {
            return;
        }

        try {
            Class<?> callbackClass = Class.forName("android.window.OnBackInvokedCallback");
            Object dispatcher = settingsSearchBackDispatcher;
            if (dispatcher == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null) {
                dispatcher = Activity.class.getMethod("getOnBackInvokedDispatcher").invoke(activity);
            }
            if (dispatcher != null) {
                dispatcher.getClass()
                        .getMethod("unregisterOnBackInvokedCallback", callbackClass)
                        .invoke(dispatcher, settingsSearchBackCallback);
            }
        } catch (Throwable throwable) {
            Logger.printException(() -> "settings search back callback unregistration failed", throwable);
        }
        settingsSearchBackDispatcher = null;
        settingsSearchBackCallback = null;
        settingsSearchBackCallbackRegistered = false;
    }

    private static View createToolbarSearchView(Activity activity) {
        LinearLayout search = new LinearLayout(activity);
        search.setOrientation(LinearLayout.HORIZONTAL);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPaddingRelative(dp(activity, 72), 0, dp(activity, 4), 0);

        toolbarSearchInput = new EditText(activity);
        toolbarSearchInput.setSingleLine(true);
        toolbarSearchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        toolbarSearchInput.setTextColor(SettingsSearchColors.current(activity).searchTextColor);
        toolbarSearchInput.setHintTextColor(SettingsSearchColors.current(activity).searchHintColor);
        toolbarSearchInput.setHint(str("piko_settings_search_hint"));
        toolbarSearchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        toolbarSearchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        toolbarSearchInput.setBackgroundColor(Color.TRANSPARENT);
        toolbarSearchInput.setPadding(0, 0, 0, 0);
        toolbarSearchInput.setOnEditorActionListener((textView, actionId, event) -> {
            boolean enterKey = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || enterKey) {
                hideKeyboard(toolbarSearchInput);
                toolbarSearchInput.clearFocus();
                return true;
            }
            return false;
        });
        search.addView(toolbarSearchInput, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
        ));

        toolbarSearchClear = new ImageView(activity);
        toolbarSearchClear.setImageResource(
                ResourceUtils.getIdentifier(ResourceType.DRAWABLE, "ic_vector_close")
        );
        toolbarSearchClear.setColorFilter(TWITTER_BLUE);
        toolbarSearchClear.setScaleType(ImageView.ScaleType.CENTER);
        toolbarSearchClear.setContentDescription(str("piko_settings_search_clear"));
        toolbarSearchClear.setClickable(true);
        toolbarSearchClear.setFocusable(true);
        toolbarSearchClear.setVisibility(View.GONE);
        toolbarSearchClear.setOnClickListener(view -> toolbarSearchInput.setText(""));
        search.addView(toolbarSearchClear, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));

        toolbarSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                settingsSearchSession.updateQuery(s);
                if (toolbarSearchClear != null) {
                    toolbarSearchClear.setVisibility(
                            settingsSearchSession.query().length() > 0 ? View.VISIBLE : View.GONE
                    );
                }
                notifySettingsSearchChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        return search;
    }

    private static void notifySettingsSearchChanged() {
        if (settingsSearchListener != null) {
            settingsSearchListener.onSettingsSearchQueryChanged(settingsSearchSession.query());
        }
    }

    static boolean isLayoutRtl(Context context) {
        return context != null
                && context.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    private static Drawable createSearchFieldBackground(Context context) {
        SettingsSearchColors palette = SettingsSearchColors.current(context);
        StateListDrawable background = new StateListDrawable();
        background.addState(
                new int[]{android.R.attr.state_pressed},
                createRoundedSearchFieldDrawable(
                        context,
                        palette.searchFieldPressedColor,
                        palette.searchFieldBorderColor
                )
        );
        background.addState(
                new int[]{},
                createRoundedSearchFieldDrawable(
                        context,
                        palette.searchFieldBackgroundColor,
                        palette.searchFieldBorderColor
                )
        );
        return background;
    }

    private static GradientDrawable createRoundedSearchFieldDrawable(
            Context context,
            int color,
            int borderColor
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setStroke(dp(context, 1), borderColor);
        drawable.setCornerRadius(dp(context, 20));
        return drawable;
    }

    private static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    private static void hideKeyboard(View view) {
        if (view == null) {
            return;
        }
        Object service = view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (service instanceof InputMethodManager) {
            ((InputMethodManager) service).hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private static void showKeyboard(View view) {
        Object service = view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (service instanceof InputMethodManager) {
            ((InputMethodManager) service).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

}
