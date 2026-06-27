/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.constants;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.util.ArrayList;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.settings.preference.fragments.FragmentHook;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.Dim;
import app.morphe.extension.crimera.constants.TooltipHelper;
import app.morphe.extension.instagram.entity.InstagramButton;
import app.morphe.extension.instagram.entity.InstagramButtonStyleEnum;

public class UI {

    public static final String DRAWABLE_DOWNLOAD_ICON = "instagram_download_outline_24";
    public static final String DRAWABLE_FB_DOWNLOAD_ICON = "fb_ic_download_filled_24";
    public static final String DRAWABLE_INFO_ICON = "instagram_info_outline_24";
    public static final String DRAWABLE_DEBUG_ICON = "instagram_app_instagram_pano_outline_24";
    public static final String DRAWABLE_BLUB_ICON = "instagram_bulb_outline_24";
    public static final String DRAWABLE_GEAR_ICON = "instagram_settings_pano_filled_24";
    public static final String DRAWABLE_SHEILD_ICON = "fb_ic_badge_admin_filled_32";
    public static final String DRAWABLE_SNAPCHAT_ICON = "fb_ic_app_snapchat_filled_16";
    public static final String DRAWABLE_STACK_ICON = "fb_ic_changed_beliefs_outline_24";
    public static final String DRAWABLE_CODE_ICON = "fb_ic_code_outline_24";
    public static final String DRAWABLE_FRAME_CROSSED_ICON = "fb_ic_frames_cross_outline_16";
    public static final String DRAWABLE_LINK_ICON = "fb_ic_link_outline_24";


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

    public static ImageView addImageViewToViewGroup(ViewGroup viewGroup, String iconDrawable, Runnable action) {
        try {
            if (viewGroup == null) {
                return null;
            }

            Context context = viewGroup.getContext();
            ImageView imageView = new ImageView(context);

            setThemedIcon(imageView, iconDrawable);
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            imageView.setLayoutParams(params);
            if(action!=null) {
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            action.run();
                        } catch (Exception ex) {
                            Logger.printException(() -> "addImageViewToViewGroup click failed: ", ex);
                        }
                    }
                });
            }
            int padding = Dim.dp16;
            imageView.setPadding(padding, padding, padding, padding);

            int count = viewGroup.getChildCount();
            int insertIndex = count - 1;
            if (insertIndex < 0) {
                insertIndex = 0;
            }

            viewGroup.addView(imageView, insertIndex);
            return imageView;
        } catch (Exception e) {
            Logger.printException(() -> "Failed addImageViewToViewGroup: ", e);
        }
        return null;
    }

    public static void pikoSettingsGear(ViewGroup viewGroup) {
        try {
            if (viewGroup == null) {
                return;
            }

            ImageView imageView = UI.addImageViewToViewGroup(viewGroup, UI.DRAWABLE_GEAR_ICON, FragmentHook::startSettings);
            if (imageView == null) {
                return;
            }

            Context context = viewGroup.getContext();
            boolean isFirstTime = Pref.firstTimePiko();
            if(isFirstTime) {
                TooltipHelper.showPersistentTooltip(context, imageView, str("piko_tap_here"));
                Pref.setFirstTimePiko(false);
            }
        } catch (Exception e) {
            Logger.printException(() -> "Failed pikoSettingsGear: ", e);
        }
    }

    public static void restartDialogBox(Context context) {
        InstagramDialogBox dialog = new InstagramDialogBox(context);

        ArrayList<String> options = new ArrayList<>();
        options.add(str("piko_ok"));
        CharSequence[] items = options.toArray(new CharSequence[0]);

        dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                try {
                    // Doing like this because options are dynamic.
                    String selectedOption = options.get(which);

                    if (selectedOption.equals(str("piko_ok"))) {
                        Utils.restartApp(context);

                    }
                } catch (Exception e) {
                    Logger.printException(() -> "Error at restartDialogBox", e);
                    Utils.showToastShort(e.getMessage());
                }
            }
        });


        dialog.setTitle(str("piko_restart_app"));
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        Dialog dlg = dialog.getDialog();
        dlg.show();
    }

    public static void welcomeDialogBox(Context context) {
        InstagramDialogBox dialog = new InstagramDialogBox(context);

        ArrayList<String> options = new ArrayList<>();
        options.add(str("piko_goto_piko_settings"));
        CharSequence[] items = options.toArray(new CharSequence[0]);

        dialog.addDialogMenuItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                try {
                    // If settings is placed on action bar, no need to redirect to profile.
                    if(!Pref.pikoSettingsOnActionBar()) {
                        // Doing like this because options are dynamic.
                        String selectedOption = options.get(which);

                        if (selectedOption.equals(str("piko_goto_piko_settings"))) {
                            PikoUtils.openUrl("instagram://profile");
                        }
                    }
                } catch (Exception e) {
                    Logger.printException(() -> "Error at welcomeDialogBox", e);
                    Utils.showToastShort(e.getMessage());
                }
            }
        });

        dialog.setTitle(str("piko_welcome_title"));
        dialog.setMessage(str("piko_welcome_message"));
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        Dialog dlg = dialog.getDialog();
        dlg.show();
    }

    public static void pikoSettingsButton(ViewGroup viewGroup) throws Exception {
        boolean isFirstTime = Pref.firstTimePiko();

        Context context = viewGroup.getContext();
        InstagramButton button = new InstagramButton(context);
        button.setText(str("piko_title_settings"));
        button.setStyle(InstagramButtonStyleEnum.SUPER_PRIMARY);
        button.setOnClickListener(FragmentHook::startSettings);

        int marginPx = Dim.dp12;
        button.setMargins(marginPx, marginPx, marginPx, marginPx);

        viewGroup.addView(button.getIgdsButton());
        if(isFirstTime){
            button.startPulseAnimation();
            Pref.setFirstTimePiko(false);
        }
    }
}