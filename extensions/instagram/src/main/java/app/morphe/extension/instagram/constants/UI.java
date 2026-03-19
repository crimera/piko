/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/

package app.morphe.extension.instagram.constants;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.ActivityHook;

public class UI {

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
                    ActivityHook. startPikoActivity();
                }catch(Exception ex){
                    Logger.printException(() -> "Failed to launch settings: ", ex);
                }
            }
        });
        viewGroup.addView(imageView);
    }

}