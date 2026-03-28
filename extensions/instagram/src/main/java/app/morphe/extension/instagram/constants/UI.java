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
import android.view.ViewParent;
import android.widget.ImageView;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.app.Activity;

import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import com.instagram.igds.components.button.IgdsButton;
import app.morphe.extension.instagram.entity.Entity;

public class UI {

    public static int getThemedColour() {
        Context context = Utils.getContext();
        TypedValue typedValue = new TypedValue();
        int attrId = Utils.getResourceIdentifier("igds_color_primary_icon", "attr");
        boolean resolved = context.getTheme().resolveAttribute(attrId, typedValue, true);
        return context.getColor(typedValue.resourceId);
    }

    public static void setThemedIcon(ImageView imageView, String drawableAttr) {
        try {
            Context context = Utils.getContext();
            Drawable drawable = context.getDrawable(Utils.getResourceIdentifier(drawableAttr, "drawable"));
            imageView.setImageDrawable(drawable);
            imageView.setColorFilter(new PorterDuffColorFilter(getThemedColour(), PorterDuff.Mode.SRC_ATOP));

        } catch (Exception ex) {
            Logger.printException(() -> "Failed setThemedIcon: ", ex);
        }
    }


    private static Object pikoSettingsButtonStyle() throws Exception {
        Entity e = new Entity();
        Class<?> styleClass = Class.forName("X.0X3");
        return e.getMethod(
                styleClass,
                "valueOf",
                "PRIMARY"
        );
    }

    private static void pikoSettingsButton(ViewGroup viewGroup) throws Exception {
        Context context = viewGroup.getContext();
        IgdsButton button = new IgdsButton(context);
        button.setText(Strings.PIKO_SETTINGS_TITLE);
        Object buttonStyle = pikoSettingsButtonStyle();
        // The function call for adding the button style to the button will be injected here from patches.
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ActivityHook.startPikoActivity();
                } catch (Exception ex) {
                    Logger.printException(() -> "Failed to launch settings: ", ex);
                }
            }
        });

        viewGroup.addView(button);
    }


    public static void addPikoSettingsButton(ViewGroup viewGroup, Object object) {
        try {
            Entity profileInfo = new Entity(object);
            Object profileRelatedDetailsObject = profileInfo.getField("A07");
            Entity profileRelatedDetails = new Entity(profileRelatedDetailsObject);
            Boolean isSelfProfile = (Boolean) profileRelatedDetails.getField("A0D");

            if (isSelfProfile) {
                pikoSettingsButton(viewGroup);
            }
        } catch (Exception e) {
            Logger.printException(() -> "Failed to add piko button: ", e);
        }

    }

    public static void restartDialogBox(Context context) {
        InstagramDialogBox dialog = new InstagramDialogBox(context);

        ArrayList<String> options = new ArrayList<>();
        options.add(Strings.OK);
        CharSequence[] items = options.toArray(new CharSequence[0]);

        dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                try {
                    // Doing like this because options are dynamic.
                    String selectedOption = options.get(which);

                    if (selectedOption.equals(Strings.OK)) {
                        Utils.restartApp(context);

                    }
                } catch (Exception e) {
                    Logger.printException(() -> "Error at downloadDialogBox", e);
                    Utils.showToastShort(e.getMessage());
                }
            }
        });


        dialog.setTitle(Strings.RESTART_APP);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        Dialog dlg = dialog.getDialog();
        dlg.show();
    }

}