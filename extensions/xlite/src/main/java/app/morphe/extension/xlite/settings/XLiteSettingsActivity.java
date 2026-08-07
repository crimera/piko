package app.morphe.extension.xlite.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.xlite.featureswitches.FeatureSwitchImportExport;
import app.morphe.extension.xlite.ui.Theme;

@SuppressWarnings("deprecation")
public final class XLiteSettingsActivity extends Activity {
    private Toolbar toolbar;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySystemTheme();
        super.onCreate(savedInstanceState);
        Utils.setActivity(this);
        setContentView(ResourceUtils.getIdentifierOrThrow(
                this,
                ResourceType.LAYOUT,
                "preference_fragment_activity"
        ));
        configureSystemBars();
        configureToolbar();
        applyCustomFontToToolbar();
        if (savedInstanceState != null) return;

        int containerId = ResourceUtils.getIdentifierOrThrow(
                this,
                ResourceType.ID,
                "fragment_container"
        );
        getFragmentManager()
                .beginTransaction()
                .replace(containerId, new XLiteSettingsFragment())
                .commit();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (FeatureSwitchImportExport.handleActivityResult(this, requestCode, resultCode, data)) {
            return;
        }
        SettingsBackupRestore.handleActivityResult(this, requestCode, resultCode, data);
    }

    private void applySystemTheme() {
        getTheme().applyStyle(style("Twitter"), true);
        getTheme().applyStyle(
                style(isSystemDark(this) ? "Twitter.LightsOut" : "Twitter.Standard"),
                true
        );
    }

    static Context createPreferenceContext(Context context) {
        int theme = isSystemDark(context)
                ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar;
        return new ContextThemeWrapper(context, theme);
    }

    private static boolean isSystemDark(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void configureToolbar() {
        toolbar = findViewById(ResourceUtils.getIdentifierOrThrow(
                this,
                ResourceType.ID,
                "toolbar"
        ));
        int contentColor = Theme.primaryText(this);
        Drawable navigationIcon = getDrawable(ResourceUtils.getIdentifierOrThrow(
                this,
                ResourceType.DRAWABLE,
                "ic_vector_arrow_left"
        )).mutate();
        navigationIcon.setTint(contentColor);
        toolbar.setNavigationIcon(navigationIcon);
        toolbar.setBackgroundColor(Theme.surfaceContainer(this));
        toolbar.setTitleTextColor(contentColor);
        toolbar.setTitle(StringRef.str("piko_xlite_settings_title"));
        toolbar.setNavigationOnClickListener(ignored -> onBackPressed());

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
        if (toolbar == null) return;
        toolbar.setTitle(title);
        applyCustomFontToToolbar();
    }

    private void applyCustomFontToToolbar() {
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTypeface(app.morphe.extension.xlite.misc.UpdateFont.customTypefaceOr(
                        ((TextView) child).getTypeface()
                ));
            }
        }
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
                isSystemDark(this) ? visibility & ~lightBarFlags : visibility | lightBarFlags
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
        return ResourceUtils.getIdentifierOrThrow(this, ResourceType.STYLE, resourceName);
    }
}
