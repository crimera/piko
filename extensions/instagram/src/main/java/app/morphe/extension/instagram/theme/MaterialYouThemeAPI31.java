/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.instagram.theme;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@RequiresApi(31)
final class MaterialYouThemeAPI31 {
    private static final String MATERIAL_YOU_LIGHT_OVERLAY_ASSET =
            "piko/material_you_light.arsc";
    private static final String MATERIAL_YOU_DARK_OVERLAY_ASSET =
            "piko/material_you_dark.arsc";
    private static final String AMOLED_OVERLAY_ASSET = "piko/amoled.arsc";
    private static final String AMOLED_MATERIAL_YOU_OVERLAY_ASSET =
            "piko/amoled_material_you.arsc";
    private static final Map<Resources, OverlayMode> APPLIED_MODES = new WeakHashMap<>();
    private static final Map<OverlayMode, ResourcesProvider> PROVIDERS =
            new EnumMap<>(OverlayMode.class);
    private static final Map<OverlayMode, ResourcesLoader> LOADERS =
            new EnumMap<>(OverlayMode.class);

    private static Context applicationContext;

    private MaterialYouThemeAPI31() {
    }

    static synchronized void initialize(Context context) throws IOException {
        if (applicationContext != null) {
            return;
        }

        Context preparedContext = context.getApplicationContext();
        if (preparedContext == null) {
            preparedContext = context;
        }

        applicationContext = preparedContext;
        IOException failure = null;
        try {
            prepareOverlay(
                    preparedContext,
                    OverlayMode.MATERIAL_YOU_LIGHT,
                    MATERIAL_YOU_LIGHT_OVERLAY_ASSET
            );
            prepareOverlay(
                    preparedContext,
                    OverlayMode.MATERIAL_YOU_DARK,
                    MATERIAL_YOU_DARK_OVERLAY_ASSET
            );
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            prepareOverlay(preparedContext, OverlayMode.AMOLED, AMOLED_OVERLAY_ASSET);
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            }
        }
        try {
            prepareOverlay(
                    preparedContext,
                    OverlayMode.AMOLED_MATERIAL_YOU,
                    AMOLED_MATERIAL_YOU_OVERLAY_ASSET
            );
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            }
        }

        if (LOADERS.isEmpty()) {
            applicationContext = null;
            throw failure != null ? failure : new IOException("Unable to prepare theme resources");
        }
    }

    static synchronized boolean isReady(ThemeMode mode) {
        switch (mode) {
            case MATERIAL_YOU:
                return LOADERS.containsKey(OverlayMode.MATERIAL_YOU_LIGHT)
                        && LOADERS.containsKey(OverlayMode.MATERIAL_YOU_DARK);
            case AMOLED:
                return LOADERS.containsKey(OverlayMode.AMOLED);
            case AMOLED_MATERIAL_YOU:
                return LOADERS.containsKey(OverlayMode.AMOLED_MATERIAL_YOU);
            default:
                return false;
        }
    }

    static synchronized int getSystemNightMask() {
        int resourceNightMask = Resources.getSystem().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (applicationContext == null) {
            return resourceNightMask;
        }

        UiModeManager uiModeManager =
                applicationContext.getSystemService(UiModeManager.class);
        if (uiModeManager == null) {
            return resourceNightMask;
        }
        int managerMode = uiModeManager.getNightMode();
        return MaterialYouState.resolveSystemNightMask(
                managerMode,
                resourceNightMask
        );
    }

    static synchronized boolean setResourcesMode(
            Activity activity,
            ThemeMode mode,
            boolean instagramDark
    ) {
        OverlayMode requestedOverlay = overlayFor(mode, instagramDark);
        if (applicationContext == null
                || activity == null
                || Looper.myLooper() != Looper.getMainLooper()
                || (requestedOverlay != null && !LOADERS.containsKey(requestedOverlay))) {
            return false;
        }

        List<Resources> resources = distinctTargets(
                activity.getResources(),
                applicationContext.getResources()
        );

        List<Resources> changedResources = new ArrayList<>();
        Map<Resources, OverlayMode> previousModes = new WeakHashMap<>();
        try {
            for (Resources target : resources) {
                OverlayMode previousMode = APPLIED_MODES.get(target);
                previousModes.put(target, previousMode);
                changedResources.add(target);
                replaceLoader(target, requestedOverlay);
                if (requestedOverlay == null) {
                    APPLIED_MODES.remove(target);
                } else {
                    APPLIED_MODES.put(target, requestedOverlay);
                }
            }
            return true;
        } catch (Exception exception) {
            rollbackResources(changedResources, previousModes);
            return false;
        }
    }

    private static void prepareOverlay(Context context, OverlayMode mode, String assetName)
            throws IOException {
        ResourcesProvider provider = null;
        try {
            File overlayFile = new File(
                    context.getCodeCacheDir(),
                    overlayFileName(mode)
            );
            copyOverlay(context, assetName, overlayFile);

            try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                    overlayFile,
                    ParcelFileDescriptor.MODE_READ_ONLY
            )) {
                provider = ResourcesProvider.loadFromTable(descriptor, null);
            }

            ResourcesLoader loader = new ResourcesLoader();
            loader.addProvider(provider);
            PROVIDERS.put(mode, provider);
            LOADERS.put(mode, loader);
        } catch (Exception exception) {
            if (provider != null) {
                provider.close();
            }
            if (exception instanceof IOException) {
                throw (IOException) exception;
            }
            throw new IOException("Unable to prepare " + mode + " resources", exception);
        }
    }

    private static String overlayFileName(OverlayMode mode) {
        switch (mode) {
            case MATERIAL_YOU_LIGHT:
                return "piko-material-you-light.arsc";
            case MATERIAL_YOU_DARK:
                return "piko-material-you-dark.arsc";
            case AMOLED:
                return "piko-amoled.arsc";
            case AMOLED_MATERIAL_YOU:
                return "piko-amoled-material-you.arsc";
            default:
                throw new IllegalArgumentException("No overlay for " + mode);
        }
    }

    private static OverlayMode overlayFor(ThemeMode mode, boolean instagramDark) {
        switch (mode) {
            case MATERIAL_YOU:
                return instagramDark
                        ? OverlayMode.MATERIAL_YOU_DARK
                        : OverlayMode.MATERIAL_YOU_LIGHT;
            case AMOLED:
                return OverlayMode.AMOLED;
            case AMOLED_MATERIAL_YOU:
                return OverlayMode.AMOLED_MATERIAL_YOU;
            default:
                return null;
        }
    }

    private static void replaceLoader(Resources target, OverlayMode mode) {
        ResourcesLoader requestedLoader = mode == null ? null : LOADERS.get(mode);
        for (ResourcesLoader loader : LOADERS.values()) {
            target.removeLoaders(loader);
        }
        if (requestedLoader != null) {
            target.addLoaders(requestedLoader);
        }
    }

    private static List<Resources> distinctTargets(Resources primary, Resources secondary) {
        List<Resources> targets = new ArrayList<>(2);
        if (primary != null) {
            targets.add(primary);
        }
        if (secondary != null && secondary != primary) {
            targets.add(secondary);
        }
        return targets;
    }

    private static void copyOverlay(Context context, String assetName, File outputFile) throws IOException {
        try (InputStream input = context.getAssets().open(assetName);
             FileOutputStream output = new FileOutputStream(outputFile, false)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
    }

    private static void rollbackResources(
            List<Resources> changedResources,
            Map<Resources, OverlayMode> previousModes
    ) {
        for (int index = changedResources.size() - 1; index >= 0; index--) {
            Resources target = changedResources.get(index);
            OverlayMode previousMode = previousModes.get(target);
            try {
                replaceLoader(target, previousMode);
                if (previousMode == null) {
                    APPLIED_MODES.remove(target);
                } else {
                    APPLIED_MODES.put(target, previousMode);
                }
            } catch (Exception ignored) {
                // Keep the last successful state if Android rejects rollback.
            }
        }
    }

    private enum OverlayMode {
        MATERIAL_YOU_LIGHT,
        MATERIAL_YOU_DARK,
        AMOLED,
        AMOLED_MATERIAL_YOU
    }
}
