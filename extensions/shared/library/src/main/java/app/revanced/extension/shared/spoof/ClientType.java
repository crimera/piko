package app.revanced.extension.shared.spoof;

import android.os.Build;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.settings.BaseSettings;

public enum ClientType {
    // https://dumps.tadiphone.dev/dumps/oculus/eureka
    ANDROID_VR_NO_AUTH(
            28,
            "ANDROID_VR",
            "com.google.android.apps.youtube.vr.oculus",
            "Oculus",
            "Quest 3",
            "Android",
            "12",
            // Android 12.1
            "32",
            "SQ3A.220605.009.A1",
            "132.0.6808.3",
            "1.61.48",
            false,
            false,
            "Android VR No auth"
    ),
    // Chromecast with Google TV 4K.
    // https://dumps.tadiphone.dev/dumps/google/kirkwood
    ANDROID_UNPLUGGED(
            29,
            "ANDROID_UNPLUGGED",
            "com.google.android.apps.youtube.unplugged",
            "Google",
            "Google TV Streamer",
            "Android",
            "14",
            "34",
            "UTT3.240625.001.K5",
            "132.0.6808.3",
            "8.49.0",
            true,
            true,
            "Android TV"
    ),
    // Cannot play livestreams and lacks HDR, but can play videos with music and labeled "for children".
    // Google Pixel 9 Pro Fold
    // https://dumps.tadiphone.dev/dumps/google/barbet
    ANDROID_CREATOR(
            14,
            "ANDROID_CREATOR",
            "com.google.android.apps.youtube.creator",
            "Google",
            "Pixel 9 Pro Fold",
            "Android",
            "15",
            "35",
            "AP3A.241005.015.A2",
            "132.0.6779.0",
            "23.47.101",
            true,
            true,
            "Android Creator"
    ),
    IOS_UNPLUGGED(
            33,
            "IOS_UNPLUGGED",
            "com.google.ios.youtubeunplugged",
            "Apple",
            forceAVC()
                    // 11 Pro Max (last device with iOS 13)
                    ? "iPhone12,5"
                    // 15 Pro Max
                    : "iPhone16,2",
            "iOS",
            forceAVC()
                    // iOS 13 and earlier uses only AVC. 14+ adds VP9 and AV1.
                    ? "13.7.17H35"
                    : "18.2.22C152",
            null,
            null,
            null,
            // Version number should be a valid iOS release.
            // https://www.ipa4fun.com/history/152043/
            forceAVC()
                    // Some newer versions can also force AVC,
                    // but 6.45 is the last version that supports iOS 13.
                    ? "6.45"
                    : "8.49",
            true,
            true,
            forceAVC()
                    ? "iOS TV Force AVC"
                    : "iOS TV"
    ),
    ANDROID_VR_AUTH(
            ANDROID_VR_NO_AUTH.id,
            ANDROID_VR_NO_AUTH.clientName,
            ANDROID_VR_NO_AUTH.packageName,
            ANDROID_VR_NO_AUTH.deviceMake,
            ANDROID_VR_NO_AUTH.deviceModel,
            ANDROID_VR_NO_AUTH.osName,
            ANDROID_VR_NO_AUTH.osVersion,
            ANDROID_VR_NO_AUTH.androidSdkVersion,
            ANDROID_VR_NO_AUTH.buildId,
            ANDROID_VR_NO_AUTH.cronetVersion,
            ANDROID_VR_NO_AUTH.clientVersion,
            ANDROID_VR_NO_AUTH.requiresAuth,
            true,
            "Android VR Auth"
    );

    private static boolean forceAVC() {
        return BaseSettings.SPOOF_VIDEO_STREAMS_IOS_FORCE_AVC.get();
    }

    /**
     * YouTube
     * <a href="https://github.com/zerodytrash/YouTube-Internal-Clients?tab=readme-ov-file#clients">client type</a>
     */
    public final int id;

    public final String clientName;

    /**
     * App package name.
     */
    private final String packageName;

    /**
     * Player user-agent.
     */
    public final String userAgent;

    /**
     * Device model, equivalent to {@link Build#MANUFACTURER} (System property: ro.product.vendor.manufacturer)
     */
    public final String deviceMake;

    /**
     * Device model, equivalent to {@link Build#MODEL} (System property: ro.product.vendor.model)
     */
    public final String deviceModel;

    /**
     * Device OS name.
     */
    public final String osName;

    /**
     * Device OS version.
     */
    public final String osVersion;

    /**
     * Android SDK version, equivalent to {@link Build.VERSION#SDK} (System property: ro.build.version.sdk)
     * Field is null if not applicable.
     */
    @Nullable
    public final String androidSdkVersion;

    /**
     * Android build id, equivalent to {@link Build#ID}.
     * Field is null if not applicable.
     */
    @Nullable
    private final String buildId;

    /**
     * Cronet release version, as found in decompiled client apk.
     * Field is null if not applicable.
     */
    @Nullable
    private final String cronetVersion;

    /**
     * App version.
     */
    public final String clientVersion;

    /**
     * If this client requires authentication and does not work
     * if logged out or in incognito mode.
     */
    public final boolean requiresAuth;

    /**
     * If the client should use authentication if available.
     */
    public final boolean useAuth;

    /**
     * Friendly name displayed in stats for nerds.
     */
    public final String friendlyName;

    @SuppressWarnings("ConstantLocale")
    ClientType(int id,
               String clientName,
               String packageName,
               String deviceMake,
               String deviceModel,
               String osName,
               String osVersion,
               @Nullable String androidSdkVersion,
               @Nullable String buildId,
               @Nullable String cronetVersion,
               String clientVersion,
               boolean requiresAuth,
               boolean useAuth,
               String friendlyName) {
        this.id = id;
        this.clientName = clientName;
        this.packageName = packageName;
        this.deviceMake = deviceMake;
        this.deviceModel = deviceModel;
        this.osName = osName;
        this.osVersion = osVersion;
        this.androidSdkVersion = androidSdkVersion;
        this.buildId = buildId;
        this.cronetVersion = cronetVersion;
        this.clientVersion = clientVersion;
        this.requiresAuth = requiresAuth;
        this.useAuth = useAuth;
        this.friendlyName = friendlyName;

        Locale defaultLocale = Locale.getDefault();
        if (androidSdkVersion == null) {
            // Convert version from '18.2.22C152' into '18_2_22'
            String userAgentOsVersion = osVersion
                    .replaceAll("(\\d+\\.\\d+\\.\\d+).*", "$1")
                    .replace(".", "_");
            // https://github.com/mitmproxy/mitmproxy/issues/4836
            this.userAgent = String.format("%s/%s (%s; U; CPU iOS %s like Mac OS X; %s)",
                    packageName,
                    clientVersion,
                    deviceModel,
                    userAgentOsVersion,
                    defaultLocale
            );
        } else {
            this.userAgent = String.format("%s/%s (Linux; U; Android %s; %s; %s; Build/%s; Cronet/%s)",
                    packageName,
                    clientVersion,
                    osVersion,
                    defaultLocale,
                    deviceModel,
                    Objects.requireNonNull(buildId),
                    Objects.requireNonNull(cronetVersion)
            );
        }
        Logger.printDebug(() -> "userAgent: " + this.userAgent);
    }

}
