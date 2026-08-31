/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.userprofile;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import app.morphe.extension.instagram.entity.InstagramButton;
import app.morphe.extension.instagram.entity.InstagramButtonStyleEnum;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

import com.instagram.igds.components.button.IgdsButton;

public class ProfilePictureViewer {

    private ProfilePictureViewer() {
    }

    public static void show(Context context, UserData userData) {
        try {
            String imageUrl = userData.getProfilePictureUrl();
            String username = userData.getUsername();

            if (imageUrl == null || imageUrl.isEmpty()) {
                Utils.showToastShort(str("piko_fail_no_file"));
                return;
            }

            Dialog dialog = new Dialog(context);
            Window window = dialog.getWindow();
            if (window != null) {
                window.requestFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);

            LinearLayout panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            boolean isDarkMode = Utils.isDarkModeEnabled();
            int panelColor = isDarkMode ? 0xFF121212 : 0xFFFFFFFF;
            int closeIconColor = isDarkMode ? Color.WHITE : Color.BLACK;
            int placeholderColor = isDarkMode ? 0xFF1C1C1E : 0xFFE8E8E8;

            ShapeDrawable panelBackground = new ShapeDrawable(new RoundRectShape(roundedCorners(dpToPx(context, 18)), null, null));
            panelBackground.getPaint().setColor(panelColor);
            panel.setBackground(panelBackground);
            panel.setPadding(0, 0, 0, dpToPx(context, 12));

            panel.addView(buildCloseRow(context, dialog, closeIconColor));
            panel.addView(buildImageArea(context, imageUrl, placeholderColor));
            panel.addView(buildActionButton(context, str("piko_open_image_externally"), () -> {
                dialog.dismiss();
                ActivityHook.handleUrlIntent(false, imageUrl);
            }));
            panel.addView(buildActionButton(context, str("piko_download_profile_picture"), () -> {
                try {
                    String filename = username + "_dp.jpg";
                    String subFolder = DownloadUtils.getSubfolderName(username);
                    DownloadUtils.downloadMediaUrl(context, imageUrl, subFolder, filename);
                } catch (Exception e) {
                    Logger.printException(() -> "Error downloading profile picture", e);
                    Utils.showToastShort(e.getMessage());
                }
                dialog.dismiss();
            }));

            dialog.setContentView(panel);

            if (window != null) {
                // Removes the default Dialog theme inset, otherwise a gap shows up
                // around the panel even with a transparent window background.
                window.getDecorView().setPadding(0, 0, 0, 0);

                DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                int width = (int) (metrics.widthPixels * 0.86f);
                window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setDimAmount(0.7f);
                WindowManager.LayoutParams params = window.getAttributes();
                params.gravity = Gravity.CENTER;
                window.setAttributes(params);
            }

            dialog.show();
        } catch (Exception e) {
            Logger.printException(() -> "Error at ProfilePictureViewer.show", e);
            Utils.showToastShort(e.getMessage());
        }
    }

    private static View buildCloseRow(Context context, Dialog dialog, int iconColor) {
        FrameLayout row = new FrameLayout(context);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 44)));

        TextView close = new TextView(context);
        close.setText("\u2715");
        close.setTextColor(iconColor);
        close.setTextSize(18);
        close.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dpToPx(context, 40), dpToPx(context, 40));
        closeParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        closeParams.setMarginStart(dpToPx(context, 8));
        close.setLayoutParams(closeParams);
        close.setOnClickListener(v -> dialog.dismiss());

        row.addView(close);
        return row;
    }

    private static View buildImageArea(Context context, String imageUrl, int placeholderColor) {
        SquareFrameLayout frame = new SquareFrameLayout(context);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int sideMarginPx = dpToPx(context, 12);
        frameParams.setMargins(sideMarginPx, 0, sideMarginPx, 0);
        frame.setLayoutParams(frameParams);
        frame.setBackgroundColor(placeholderColor);

        ProgressBar progressBar = new ProgressBar(context);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER;
        progressBar.setLayoutParams(progressParams);
        frame.addView(progressBar);

        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imageView.setVisibility(View.GONE);
        frame.addView(imageView);

        loadBitmapAsync(imageUrl, bitmap -> {
            progressBar.setVisibility(View.GONE);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                imageView.setVisibility(View.VISIBLE);
            } else {
                Utils.showToastShort(str("piko_download_failed_media"));
            }
        });

        return frame;
    }

    private static View buildActionButton(Context context, String label, Runnable onClick) {
        InstagramButton button = new InstagramButton(context);
        button.setText(label);
        button.setStyle(InstagramButtonStyleEnum.SECONDARY);
        button.setOnClickListener(onClick);

        int marginPx = dpToPx(context, 12);
        button.setMargins(marginPx, dpToPx(context, 8), marginPx, 0);

        IgdsButton igdsButton = button.getIgdsButton();
        return igdsButton;
    }

    private static class SquareFrameLayout extends FrameLayout {
        SquareFrameLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
        }
    }

    private interface BitmapCallback {
        void onLoaded(Bitmap bitmap);
    }

    private static void loadBitmapAsync(String imageUrl, BitmapCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                URL url = new URL(imageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.connect();
                try (InputStream input = connection.getInputStream()) {
                    bitmap = BitmapFactory.decodeStream(input);
                }
                connection.disconnect();
            } catch (Exception e) {
                Logger.printException(() -> "Failed to load profile picture", e);
            }
            Bitmap result = bitmap;
            mainHandler.post(() -> callback.onLoaded(result));
        }).start();
    }

    private static float[] roundedCorners(int radiusPx) {
        return new float[]{radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx};
    }

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }
}
