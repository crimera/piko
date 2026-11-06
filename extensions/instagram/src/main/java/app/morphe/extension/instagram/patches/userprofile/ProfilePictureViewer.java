/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.userprofile;

import static app.morphe.extension.instagram.utils.IgStr.str;
import static app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle.dp;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.entity.InstagramButton;
import app.morphe.extension.instagram.entity.InstagramButtonStyleEnum;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.patches.download.DownloadUtils;
import app.morphe.extension.instagram.settings.ActivityHook;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

import com.instagram.igds.components.button.IgdsButton;

public class ProfilePictureViewer {

    private ProfilePictureViewer() {
    }

    public static void show(Context context, UserData userData, Callable<String> refreshUrl) {
        try {
            String imageUrl = userData.getProfilePictureUrl();
            String username = userData.getUsername();

            if (imageUrl == null || imageUrl.isEmpty()) {
                Utils.showToastShort(str("piko_fail_no_file"));
                return;
            }

            Context dialogContext = InstagramPreferenceStyle.dialogContext(context);
            Dialog dialog = new Dialog(dialogContext);
            Window window = dialog.getWindow();
            if (window != null) {
                window.requestFeature(Window.FEATURE_NO_TITLE);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);

            LinearLayout panel = new LinearLayout(dialogContext);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            int placeholderColor = InstagramPreferenceStyle.pressedBackgroundColor();

            GradientDrawable panelBackground = new GradientDrawable();
            panelBackground.setColor(InstagramPreferenceStyle.backgroundColor());
            panelBackground.setCornerRadius(dp(dialogContext, 18));
            panel.setBackground(panelBackground);
            panel.setPadding(0, 0, 0, dp(dialogContext, 12));
            panel.setClipToOutline(true);

            panel.addView(buildCloseRow(dialogContext, dialog));
            ScrollView scroll = new ScrollView(dialogContext);
            panel.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout content = new LinearLayout(dialogContext);
            content.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(content, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View openButton = buildActionButton(dialogContext, str("piko_open_image_externally"));
            View downloadButton = buildActionButton(dialogContext, str("piko_download_profile_picture"));
            content.addView(buildImageArea(dialog, imageUrl, placeholderColor, refreshUrl, resolvedUrl -> {
                openButton.setOnClickListener(v -> {
                    dialog.dismiss();
                    try {
                        ActivityHook.handleUrlIntent(false, resolvedUrl);
                    } catch (Exception e) {
                        Logger.printInfo(() -> "Profile picture external open: " + e.getClass().getSimpleName());
                        Utils.showToastShort(str("piko_fail_no_file"));
                    }
                });
                downloadButton.setOnClickListener(v -> {
                    try {
                        String filename = username + "_dp.jpg";
                        String subFolder = DownloadUtils.getSubfolderName(username);
                        DownloadUtils.downloadMediaUrl(context, resolvedUrl, subFolder, filename);
                    } catch (Exception e) {
                        Logger.printInfo(() -> "Profile picture download: " + e.getClass().getSimpleName());
                        Utils.showToastShort(str("piko_download_failed_media"));
                    }
                    dialog.dismiss();
                });
                openButton.setEnabled(true);
                downloadButton.setEnabled(true);
            }));
            content.addView(openButton);
            content.addView(downloadButton);

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
            Logger.printInfo(() -> "Profile picture dialog: " + e.getClass().getSimpleName());
            Utils.showToastShort(str("piko_fail_no_file"));
        }
    }

    private static View buildCloseRow(Context context, Dialog dialog) {
        FrameLayout row = new FrameLayout(context);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 44)));

        ImageButton close = new ImageButton(context);
        TypedValue background = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true)) {
            close.setBackgroundResource(background.resourceId);
        }
        int padding = dp(context, 10);
        close.setPadding(padding, padding, padding, padding);
        close.setContentDescription(str("piko_close"));
        UI.setThemedIcon(close, UI.DRAWABLE_CLEAR);

        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(context, 40), dp(context, 40));
        closeParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        closeParams.setMarginEnd(dp(context, 8));
        close.setLayoutParams(closeParams);
        close.setOnClickListener(v -> dialog.dismiss());

        row.addView(close);
        return row;
    }

    private static View buildImageArea(Dialog dialog, String imageUrl, int placeholderColor,
                                       Callable<String> refreshUrl, Consumer<String> onUrlResolved) {
        Context context = dialog.getContext();
        SquareFrameLayout frame = new SquareFrameLayout(context);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int sideMarginPx = dp(context, 12);
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

        loadImage(imageUrl, refreshUrl, () -> dialog.isShowing() && frame.isAttachedToWindow(), (bitmap, resolvedUrl) -> {
            progressBar.setVisibility(View.GONE);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                imageView.setVisibility(View.VISIBLE);
                onUrlResolved.accept(resolvedUrl);
            } else {
                Utils.showToastShort(str("piko_download_failed_media"));
            }
        });
        return frame;
    }

    private static View buildActionButton(Context context, String label) {
        InstagramButton button = new InstagramButton(context);
        button.setText(label);
        button.setStyle(InstagramButtonStyleEnum.SECONDARY);

        int marginPx = dp(context, 12);
        button.setMargins(marginPx, dp(context, 8), marginPx, 0);

        IgdsButton igdsButton = button.getIgdsButton();
        igdsButton.setEnabled(false);
        return igdsButton;
    }

    private static void loadImage(String imageUrl, Callable<String> refreshUrl, BooleanSupplier isShowing,
                                  BiConsumer<Bitmap, String> callback) {
        new Thread(() -> {
            String url = imageUrl;
            Bitmap bitmap = null;
            try {
                try {
                    bitmap = fetch(url);
                } catch (ExpiredSignature expired) {
                    url = refreshUrl.call();
                    if (url != null && !url.isEmpty() && !url.equals(imageUrl)) {
                        bitmap = fetch(url);
                    } else {
                        Logger.printInfo(() -> "Profile picture refresh returned no new HD URL");
                    }
                }
            } catch (Exception e) {
                Logger.printInfo(() -> "Profile picture load: " + e.getClass().getSimpleName());
            }
            Bitmap result = bitmap;
            String resolvedUrl = result == null ? null : url;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (isShowing.getAsBoolean()) callback.accept(result, resolvedUrl);
                else if (result != null) result.recycle();
            });
        }).start();
    }

    private static Bitmap fetch(String url) throws IOException {
        HttpURLConnection current = (HttpURLConnection) new URL(url).openConnection();
        try {
            current.setConnectTimeout(15000);
            current.setReadTimeout(15000);
            int status = current.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                Logger.printInfo(() -> "Profile picture image request: HTTP " + status);
                if (status == HttpURLConnection.HTTP_FORBIDDEN && isExpired(current)) throw new ExpiredSignature();
                return null;
            }
            try (InputStream input = current.getInputStream()) {
                Bitmap result = BitmapFactory.decodeStream(input);
                if (result == null) Logger.printInfo(() -> "Profile picture image decode failed");
                return result;
            }
        } finally {
            current.disconnect();
        }
    }

    private static boolean isExpired(HttpURLConnection current) throws IOException {
        String marker = "URL signature expired";
        if (marker.equalsIgnoreCase(current.getResponseMessage())) return true;
        try (InputStream error = current.getErrorStream()) {
            if (error == null) return false;
            byte[] bytes = new byte[256];
            int count = 0;
            while (count < bytes.length) {
                int read = error.read(bytes, count, bytes.length - count);
                if (read <= 0) break;
                count += read;
            }
            return marker.equalsIgnoreCase(new String(bytes, 0, count, StandardCharsets.UTF_8).trim());
        }
    }

    private static final class ExpiredSignature extends IOException {}


    private static class SquareFrameLayout extends FrameLayout {
        SquareFrameLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int height = resolveSize(MeasureSpec.getSize(widthMeasureSpec), heightMeasureSpec);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

}
