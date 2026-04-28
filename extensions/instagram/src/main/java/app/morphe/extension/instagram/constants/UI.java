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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;

import app.morphe.extension.instagram.entity.InstagramButton;
import app.morphe.extension.instagram.entity.InstagramButtonStyleEnum;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.Dim;

public class UI {

    public static int getThemedColour() {
        Context context = Utils.getContext();
        TypedValue typedValue = new TypedValue();
        int attrId = ResourceUtils.getIdentifier(ResourceType.ATTR, "igds_color_primary_icon");
        boolean resolved = context.getTheme().resolveAttribute(attrId, typedValue, true);
        return context.getColor(typedValue.resourceId);
    }

    public static void setThemedIcon(ImageView imageView, String drawableAttr) {
        try {
            Drawable drawable = ResourceUtils.getDrawable(drawableAttr);
            imageView.setImageDrawable(drawable);
            imageView.setColorFilter(new PorterDuffColorFilter(getThemedColour(), PorterDuff.Mode.SRC_ATOP));

        } catch (Exception ex) {
            Logger.printException(() -> "Failed setThemedIcon: ", ex);
        }
    }

    public static void pikoSettingsButton(ViewGroup viewGroup) throws Exception {
        Context context = viewGroup.getContext();
        InstagramButton button = new InstagramButton(context);
        button.setText(Strings.PIKO_SETTINGS_TITLE);
        button.setStyle(InstagramButtonStyleEnum.SUPER_PRIMARY);
        button.setOnClickListener(ActivityHook::startPikoActivity);

        int marginPx = Dim.dp12;
        button.setMargins(marginPx, marginPx, marginPx, marginPx);

        viewGroup.addView(button.getIgdsButton());
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