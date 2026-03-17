/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.os.Bundle;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.ViewGroup.LayoutParams;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.settings.SettingsFragment;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.constants.Strings;

@SuppressWarnings("deprecation")
public class ActivityHook {

    private static int getThemedColor(Context context, String attr) {
        int attrId = Utils.getResourceIdentifier(attr, "attr");
        TypedValue typedValue = new TypedValue();
        boolean resolved = context.getTheme().resolveAttribute(attrId, typedValue, true);
        return context.getColor(typedValue.resourceId);
    }

    public static void addPikoSettingsImageView(ViewGroup viewGroup) {
        Context context = viewGroup.getContext();
        ImageView imageView = new ImageView(context);
        int dimenPixelSize = Utils.getResourceDimensionPixelSize("account_discovery_bottom_gap");
        int dimenPixelSize2 = Utils.getResourceDimensionPixelSize("ab_test_media_thumbnail_preview_item_internal_padding");
        Drawable drawable = context.getDrawable(Utils.getResourceIdentifier("instagram_settings_outline_24", "drawable"));
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setPaddingRelative(dimenPixelSize,dimenPixelSize2,dimenPixelSize,dimenPixelSize2);
        imageView.setImageDrawable(drawable);
        imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(context, "igds_color_primary_icon"), PorterDuff.Mode.SRC_ATOP));
        imageView.setContentDescription(Strings.PIKO_SETTINGS);
        imageView.setClickable(true);
        imageView.setLongClickable(false);
        imageView.setFocusable(true);
        imageView.setFocusableInTouchMode(true);
        imageView.setImportantForAccessibility(1);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    startPikoActivity();
                }catch(Exception ex){
                    Logger.printException(() -> "Failed to launch settings: ", ex);
                }
            }
        });
        viewGroup.addView(imageView);
    }


    public static boolean hook(Activity act) {
        Intent intent = act.getIntent();
        if (!(intent.getBooleanExtra(Strings.PIKO, false))) return false;
        initialize(act);
        return true;
    }

    private static void initialize(Activity activity) {
        int fragmentId = View.generateViewId();
        FrameLayout fragment = new FrameLayout(activity);
        fragment.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        fragment.setId(fragmentId);

        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setFitsSystemWindows(true);
        linearLayout.setTransitionGroup(true);
        linearLayout.addView(fragment);
        linearLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        activity.setContentView(linearLayout);

        activity.getFragmentManager()
                .beginTransaction()
                .replace(fragmentId, new SettingsFragment())
                .commit();
    }

    public static void startPikoActivity() throws Exception {
        Bundle bundle = new Bundle();
        Context context = app.morphe.extension.shared.Utils.getContext();
        Intent intent = new Intent(context, Class.forName("com.instagram.mainactivity.LauncherActivity"));
        bundle.putBoolean(Strings.PIKO, true);
        intent.putExtras(bundle);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

}
