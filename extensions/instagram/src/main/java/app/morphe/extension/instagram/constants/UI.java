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
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
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
    public static final String DRAWABLE_HISTORY_ICON = "instagram_history_outline_24";
    public static final String DRAWABLE_CODE_ICON = "fb_ic_code_outline_24";
    public static final String DRAWABLE_FRAME_CROSSED_ICON = "fb_ic_frames_cross_outline_16";
    public static final String DRAWABLE_LINK_ICON = "fb_ic_link_outline_24";
    public static final String DRAWABLE_COLLECTIONS_ICON = "instagram_collections_pano_outline_24";
    public static final String DRAWABLE_EYE_STROKE_ICON = "design_ic_visibility_off";
    public static final String DRAWABLE_EYE_ICON = "design_ic_visibility";
    public static final String DRAWABLE_SHARE_TO_DIRECT = "gallery_share_to_direct_button";
    public static final String DRAWABLE_SHARE_TO_REEL = "gallery_share_to_reels_button";
    public static final String DRAWABLE_ARROW_BACK =
            "instagram_arrow_left_pano_outline_24";
    public static final String DRAWABLE_CHEVRON_RIGHT =
            "instagram_chevron_right_outline_16";
    public static final String DRAWABLE_CHEVRON_RIGHT_RTL =
            "instagram_chevron_right_outline_rtl_16";

    public static int getThemedColour(String attrName) {
        Context context = Utils.getContext();
        TypedValue typedValue = new TypedValue();
        int attrId = ResourceUtils.getAttrIdentifier(attrName);
        boolean resolved = context.getTheme().resolveAttribute(attrId, typedValue, true);
        return context.getColor(typedValue.resourceId);
    }

    public static boolean isDarkMode() {
        return Color.luminance(getThemedColour("igds_color_primary_background")) < 0.5;
    }

    public static void setThemedIcon(ImageView imageView, String drawableAttr) {
        setThemedIcon(imageView, drawableAttr, "igds_color_primary_icon");
    }

    public static void setThemedIcon(
            ImageView imageView,
            String drawableAttr,
            String colorAttr
    ) {
        try {
            Drawable drawable = ResourceUtils.getDrawable(drawableAttr);
            imageView.setImageDrawable(drawable);
            imageView.setColorFilter(new PorterDuffColorFilter(
                    getThemedColour(colorAttr),
                    PorterDuff.Mode.SRC_ATOP
            ));

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

    /**
     * Same as {@link #addImageViewToViewGroup}, but wraps the icon in a FrameLayout so an
     * optional small counter badge can be overlaid on its top-right corner — used for the
     * unseen-deleted-message count on the DM history icon. The wrapper keeps this safe to
     * use regardless of the actual parent ViewGroup type (LinearLayout, Toolbar, etc.), since the
     * overlay only ever happens inside the FrameLayout we control.
     *
     * @param badgeCount 0 (or less) hides the badge entirely; otherwise shows the count, capped
     *                   at "99+" so it never overflows the pill.
     */
    public static ImageView addImageViewWithBadge(
            ViewGroup viewGroup,
            String iconDrawable,
            Runnable action,
            int badgeCount,
            int badgeColor
    ) {
        try {
            if (viewGroup == null) {
                return null;
            }

            Context context = viewGroup.getContext();
            ImageView imageView = new ImageView(context);

            setThemedIcon(imageView, iconDrawable);
            imageView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            if (action != null) {
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            action.run();
                        } catch (Exception ex) {
                            Logger.printException(() -> "addImageViewWithBadge click failed: ", ex);
                        }
                    }
                });
            }
            int padding = Dim.dp16;
            imageView.setPadding(padding, padding, padding, padding);

            FrameLayout wrapper = new FrameLayout(context);
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            wrapper.addView(imageView);

            if (badgeCount > 0) {
                TextView badge = new TextView(context);
                badge.setTag("piko_unseen_deleted_badge");
                badge.setText((badgeCount > 99 ? "99" : String.valueOf(badgeCount)) + "+");
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                badge.setGravity(Gravity.CENTER);
                badge.setIncludeFontPadding(false);

                GradientDrawable pill = new GradientDrawable();
                pill.setShape(GradientDrawable.OVAL);
                pill.setColor(badgeColor);
                // No border — a hard stroke made it look heavy/detached from the icon. A flat
                // fill reads as a badge on its own without needing an outline.
                badge.setBackground(pill);

                // Fixed height keeps it circular for a single digit; horizontal padding lets it
                // stretch into a pill for "99+" without the text getting clipped.
                int badgeMinSize = Dim.dp8 + Dim.dp6;
                badge.setMinWidth(badgeMinSize);
                badge.setMinHeight(badgeMinSize);
                int horizontalPadding = Dim.dp2;
                badge.setPadding(horizontalPadding, 0, horizontalPadding, 0);

                FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, badgeMinSize);
                badgeParams.gravity = Gravity.END | Gravity.TOP;
                // The icon itself has Dim.dp16 padding around its visible glyph (for a bigger
                // touch target), so a small margin here sits near the wrapper's outer edge —
                // far from the icon you actually see. Compensating most of that padding puts the
                // badge right against the visible icon's corner instead.
                int badgeInset = Dim.dp16 - Dim.dp4;
                badgeParams.topMargin = badgeInset;
                badgeParams.rightMargin = badgeInset;
                badge.setLayoutParams(badgeParams);
                wrapper.addView(badge);
            }

            int count = viewGroup.getChildCount();
            int insertIndex = count - 1;
            if (insertIndex < 0) {
                insertIndex = 0;
            }

            viewGroup.addView(wrapper, insertIndex);
            return imageView;
        } catch (Exception e) {
            Logger.printException(() -> "Failed addImageViewWithBadge: ", e);
        }
        return null;
    }

    /** System Material You accent color (Android 12+), or the given fallback on older devices
     *  or if the dynamic color resource can't be resolved for any reason. */
    public static int resolveDynamicOrFallbackColor(Context context, int fallbackColor) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                return context.getColor(android.R.color.system_accent1_600);
            } catch (Exception ignored) {}
        }
        return fallbackColor;
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
                    // Doing like this because options are dynamic.
                    String selectedOption = options.get(which);

                    if (selectedOption.equals(str("piko_goto_piko_settings"))) {
                        PikoUtils.openUrl("instagram://profile",true);
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
