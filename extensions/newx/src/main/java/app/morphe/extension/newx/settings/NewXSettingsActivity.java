package app.morphe.extension.newx.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.newx.featureswitches.FeatureSwitchImportExport;
import app.morphe.extension.newx.misc.UpdateFont;
import app.morphe.extension.newx.theme.TwitterTheme;
import app.morphe.extension.newx.ui.Theme;

@SuppressWarnings("deprecation")
public final class NewXSettingsActivity extends Activity {
    static final int SETTINGS_CONTAINER_ID = 0x00f00001;

    private LinearLayout toolbar;
    private TextView toolbarTitle;
    private TextView patchVersionFooter;
    private Object backCallback;
    private boolean backCallbackRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySystemTheme();
        super.onCreate(savedInstanceState);
        Utils.setActivity(this);
        int containerId = createContentView();
        configureSystemBars();
        configureToolbar();
        applyCustomFontToToolbar();
        getFragmentManager().addOnBackStackChangedListener(this::onBackStackChanged);
        updateBackCallback();
        if (savedInstanceState != null) return;

        getFragmentManager()
                .beginTransaction()
                .replace(containerId, new NewXSettingsFragment())
                .commit();
    }

    @Override
    public void onBackPressed() {
        if (!isFinishing() && !isDestroyed()) {
            try {
                if (getFragmentManager().popBackStackImmediate()) {
                    return;
                }
            } catch (IllegalStateException ignored) {
                // Ignore if called after state saved
            }
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallbackRegistered) {
            Api33BackHelper.unregister(this, backCallback);
            backCallback = null;
            backCallbackRegistered = false;
        }
        super.onDestroy();
    }

    private void onBackStackChanged() {
        updateBackCallback();
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            setPageTitle(StringRef.str("piko_newx_settings_title"));
            return;
        }
        setPatchVersionFooterVisible(false);
    }

    private void updateBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        boolean shouldRegister = getFragmentManager().getBackStackEntryCount() > 0;
        if (shouldRegister && !backCallbackRegistered) {
            backCallback = Api33BackHelper.register(this, this::onBackPressed);
            backCallbackRegistered = true;
        } else if (!shouldRegister && backCallbackRegistered) {
            Api33BackHelper.unregister(this, backCallback);
            backCallback = null;
            backCallbackRegistered = false;
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static final class Api33BackHelper {
        @androidx.annotation.DoNotInline
        static Object register(Activity activity, Runnable onBack) {
            android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
            android.window.OnBackInvokedCallback callback = onBack::run;
            dispatcher.registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    callback
            );
            return callback;
        }

        @androidx.annotation.DoNotInline
        static void unregister(Activity activity, Object callback) {
            if (callback instanceof android.window.OnBackInvokedCallback backCallback) {
                activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (FeatureSwitchImportExport.handleActivityResult(this, requestCode, resultCode, data)) {
            return;
        }
        if (SettingsBackupRestore.handleActivityResult(this, requestCode, resultCode, data)) {
            return;
        }
        UpdateFont.handleActivityResult(this, requestCode, resultCode, data);
    }

    private void applySystemTheme() {
        int baseStyle = style("Twitter");
        if (baseStyle == 0) baseStyle = style("Theme.AppCompat.DayNight.NoActionBar");
        if (baseStyle != 0) getTheme().applyStyle(baseStyle, true);

        TwitterTheme appTheme = TwitterTheme.fromContext(this);
        int paletteStyle = style(appTheme.styleResourceName());
        if (paletteStyle != 0) getTheme().applyStyle(paletteStyle, true);
    }

    static Context createPreferenceContext(Context context) {
        int theme = Theme.isDark(context)
                ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar;
        return new ContextThemeWrapper(context, theme);
    }

    private int createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(
                toolbar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Theme.dpToPx(this, 56f)
                )
        );

        FrameLayout container = new FrameLayout(this);
        container.setId(SETTINGS_CONTAINER_ID);
        root.addView(
                container,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        patchVersionFooter = new TextView(this);
        patchVersionFooter.setText(StringRef.str(
                "piko_newx_patch_version",
                Utils.getPatchesReleaseVersion()
        ));
        patchVersionFooter.setTextColor(Theme.secondaryText(this));
        patchVersionFooter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        patchVersionFooter.setGravity(Gravity.CENTER);
        patchVersionFooter.setSingleLine(true);
        patchVersionFooter.setTypeface(app.morphe.extension.newx.misc.UpdateFont.customTypefaceOr(
                patchVersionFooter.getTypeface()
        ));
        patchVersionFooter.setPadding(
                0,
                Theme.dpToPx(this, 12f),
                0,
                Theme.dpToPx(this, 24f)
        );
        root.addView(
                patchVersionFooter,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        setContentView(root);
        return SETTINGS_CONTAINER_ID;
    }

    private void configureToolbar() {
        int contentColor = Theme.primaryText(this);
        Drawable navigationIcon = getDrawable(ResourceUtils.getIdentifierOrThrow(
                this,
                ResourceType.DRAWABLE,
                "ic_vector_arrow_left"
        )).mutate();
        navigationIcon.setTint(contentColor);

        ImageButton navigationButton = new ImageButton(this);
        navigationButton.setImageDrawable(navigationIcon);
        navigationButton.setBackgroundColor(Color.TRANSPARENT);
        navigationButton.setContentDescription("Back");
        navigationButton.setOnClickListener(ignored -> onBackPressed());
        toolbar.addView(
                navigationButton,
                new LinearLayout.LayoutParams(
                        Theme.dpToPx(this, 56f),
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        toolbarTitle = new TextView(this);
        toolbarTitle.setText(StringRef.str("piko_newx_settings_title"));
        toolbarTitle.setTextColor(contentColor);
        toolbarTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        toolbarTitle.setGravity(Gravity.CENTER_VERTICAL);
        toolbarTitle.setSingleLine(true);
        toolbar.addView(
                toolbarTitle,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                )
        );
        toolbar.setBackgroundColor(Theme.surfaceContainer(this));

        ViewGroup toolbarParent = (ViewGroup) toolbar.getParent();
        View divider = new View(this);
        divider.setBackgroundColor(Theme.dividerColor(this));
        toolbarParent.addView(
                divider,
                toolbarParent.indexOfChild(toolbar) + 1,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, Theme.dpToPx(this, 1f))
                )
        );
    }

    public void setPageTitle(CharSequence title) {
        if (toolbarTitle == null) return;
        toolbarTitle.setText(title);
        applyCustomFontToToolbar();
    }

    public void setPatchVersionFooterVisible(boolean visible) {
        if (patchVersionFooter == null) return;
        patchVersionFooter.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void applyCustomFontToToolbar() {
        if (toolbarTitle == null) return;
        toolbarTitle.setTypeface(app.morphe.extension.newx.misc.UpdateFont.customTypefaceOr(
                toolbarTitle.getTypeface()
        ));
    }

    private void configureSystemBars() {
        Window window = getWindow();
        int systemBarColor = Theme.surfaceContainer(this);
        View decorView = window.getDecorView();
        decorView.setBackgroundColor(systemBarColor);
        findViewById(android.R.id.content).setBackgroundColor(systemBarColor);
        if (Build.VERSION.SDK_INT >= 35) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        } else {
            window.setStatusBarColor(systemBarColor);
            window.setNavigationBarColor(systemBarColor);
        }
        int visibility = decorView.getSystemUiVisibility();
        int lightBarFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decorView.setSystemUiVisibility(
                Theme.isDark(this) ? visibility & ~lightBarFlags : visibility | lightBarFlags
        );
        if (Build.VERSION.SDK_INT < 35) return;

        decorView.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom()
            );
            return insets.consumeSystemWindowInsets();
        });
        decorView.requestApplyInsets();
    }

    private int style(String resourceName) {
        return getResources().getIdentifier(resourceName, "style", getPackageName());
    }
}
