package app.morphe.extension.xlite.settings;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Window;

import androidx.appcompat.widget.Toolbar;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

@SuppressWarnings("deprecation")
public final class XLiteSettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySystemTheme();
        super.onCreate(savedInstanceState);
        Utils.setActivity(this);
        configureSystemBars();
        setContentView(ResourceUtils.getIdentifierOrThrow(
                this,
                ResourceType.LAYOUT,
                "preference_fragment_activity"
        ));
        configureToolbar();
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
        Toolbar toolbar = findViewById(ResourceUtils.getIdentifierOrThrow(
                this,
                ResourceType.ID,
                "toolbar"
        ));
        toolbar.setNavigationIcon(ResourceUtils.getIdentifierOrThrow(
                this,
                ResourceType.DRAWABLE,
                "ic_vector_arrow_left"
        ));
        toolbar.setTitle(StringRef.str("piko_xlite_settings_title"));
        toolbar.setNavigationOnClickListener(ignored -> onBackPressed());
    }

    private void configureSystemBars() {
        Window window = getWindow();
        View decorView = window.getDecorView();
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
