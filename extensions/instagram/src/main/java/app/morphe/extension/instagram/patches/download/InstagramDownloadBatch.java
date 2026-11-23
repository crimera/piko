/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.download;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.crimera.legacydownloader.DownloadRequest;
import app.morphe.extension.crimera.legacydownloader.MediaDownloader;

final class InstagramDownloadBatch {
    private static volatile String cachedUserAgent;

    static void enqueue(Context context, List<DownloadRequest> requests) {
        MediaDownloader.shared(context).enqueueBatch(requests, request -> {
            request.localSource = url -> openLocal(request, url);
            request.userAgent = InstagramDownloadBatch::userAgent;
            prepareLocal(request);
            return true;
        });
    }

    private static String userAgent(Context context) {
        if (cachedUserAgent != null) return cachedUserAgent;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
            DisplayManager displays = context.getSystemService(DisplayManager.class);
            Display display = displays == null ? null : displays.getDisplay(Display.DEFAULT_DISPLAY);
            if (display == null) return null;
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);
            String maker = Build.MANUFACTURER.equalsIgnoreCase(Build.BRAND)
                    ? Build.MANUFACTURER : Build.MANUFACTURER + "/" + Build.BRAND;
            cachedUserAgent = String.format(Locale.US,
                    "Instagram %s Android (%d/%s; %ddpi; %dx%d; %s; %s; %s; %s; %s; %d)",
                    info.versionName, Build.VERSION.SDK_INT, Build.VERSION.RELEASE,
                    metrics.densityDpi, metrics.widthPixels, metrics.heightPixels,
                    maker, Build.MODEL, Build.DEVICE, Build.HARDWARE, Locale.getDefault(), versionCode);
            return cachedUserAgent;
        } catch (Exception e) {
            PikoUtils.logger(e);
            return null;
        }
    }

    private static void prepareLocal(DownloadRequest request) {
        if (selectLocal(request, request.url, request.audioUrl)) return;
        for (int i = 0; i < request.sameResolutionFallbacks; i++) {
            DownloadRequest.Source alternative = request.fallbackSources.get(i);
            if (selectLocal(request, alternative.url, alternative.audioUrl)) return;
        }
        for (DownloadRequest.Source source : request.localAlternatives) {
            if (selectLocal(request, source.url, source.audioUrl)) return;
        }
    }

    private static boolean selectLocal(DownloadRequest request, String url, String audioUrl) {
        try {
            InputStream media = openLocal(request, url);
            if (media == null) return false;
            try {
                InputStream audio = audioUrl == null ? null : openLocal(request, audioUrl);
                if (audioUrl != null && audio == null) {
                    return false;
                }
                request.cachedSource = new DownloadRequest.Source(url, audioUrl);
                request.cachedMedia = media;
                request.cachedAudio = audio;
                return true;
            } finally {
                if (request.cachedMedia == null) media.close();
            }
        } catch (IOException ignored) {
            return false;
        }
    }

    private static InputStream openLocal(DownloadRequest request, String url) throws IOException {
        if (url == null || url.isEmpty()) return null;
        DownloadRequest.DashIndex index = request.dashIndex(url);
        if (index != null) return InstagramVideoCache.openTrack(url, index);
        InputStream image = InstagramImageCache.open(url);
        return image != null ? image : InstagramVideoCache.open(url);
    }
}
