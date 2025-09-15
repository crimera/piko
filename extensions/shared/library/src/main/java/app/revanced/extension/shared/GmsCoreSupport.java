package app.revanced.extension.shared;

import static app.revanced.extension.shared.StringRef.str;
import static app.revanced.extension.shared.requests.Route.Method.GET;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Pair;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import app.revanced.extension.shared.requests.Requester;
import app.revanced.extension.shared.requests.Route;

@SuppressWarnings("unused")
public class GmsCoreSupport {
    private static final String PACKAGE_NAME_YOUTUBE = "com.google.android.youtube";
    private static final String PACKAGE_NAME_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music";

    private static final String GMS_CORE_PACKAGE_NAME
            = getGmsCoreVendorGroupId() + ".android.gms";
    private static final Uri GMS_CORE_PROVIDER
            = Uri.parse("content://" + getGmsCoreVendorGroupId() + ".android.gsf.gservices/prefix");
    private static final String DONT_KILL_MY_APP_URL
            = "https://dontkillmyapp.com/";
    private static final Route DONT_KILL_MY_APP_MANUFACTURER_API
            = new Route(GET, "/api/v2/{manufacturer}.json");
    private static final String DONT_KILL_MY_APP_NAME_PARAMETER
            = "?app=MicroG";
    private static final String BUILD_MANUFACTURER
            = Build.MANUFACTURER.toLowerCase(Locale.ROOT).replace(" ", "-");

    /**
     * If a manufacturer specific page exists on DontKillMyApp.
     */
    @Nullable
    private static volatile Boolean DONT_KILL_MY_APP_MANUFACTURER_SUPPORTED;

    private static void open(String queryOrLink) {
        Logger.printInfo(() -> "Opening link: " + queryOrLink);

        Intent intent;
        try {
            // Check if queryOrLink is a valid URL.
            new URL(queryOrLink);

            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(queryOrLink));
        } catch (MalformedURLException e) {
            intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.putExtra(SearchManager.QUERY, queryOrLink);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Utils.getContext().startActivity(intent);

        // Gracefully exit, otherwise the broken app will continue to run.
        System.exit(0);
    }

    private static void showBatteryOptimizationDialog(Activity context,
                                                      String dialogMessageRef,
                                                      String positiveButtonTextRef,
                                                      DialogInterface.OnClickListener onPositiveClickListener) {
        // Use a delay to allow the activity to finish initializing.
        // Otherwise, if device is in dark mode the dialog is shown with wrong color scheme.
        Utils.runOnMainThreadDelayed(() -> {
            // Create the custom dialog.
            Pair<Dialog, LinearLayout> dialogPair = Utils.createCustomDialog(
                    context,
                    str("gms_core_dialog_title"), // Title.
                    str(dialogMessageRef),        // Message.
                    null,                         // No EditText.
                    str(positiveButtonTextRef),   // OK button text.
                    () -> onPositiveClickListener.onClick(null, 0), // Convert DialogInterface.OnClickListener to Runnable.
                    null,                         // No Cancel button action.
                    null,                         // No Neutral button text.
                    null,                         // No Neutral button action.
                    true                          // Dismiss dialog when onNeutralClick.
            );

            Dialog dialog = dialogPair.first;

            // Do not set cancelable to false, to allow using back button to skip the action,
            // just in case the battery change can never be satisfied.
            dialog.setCancelable(true);

            // Show the dialog
            Utils.showDialog(context, dialog);
        }, 100);
    }

    /**
     * Injection point.
     */
    public static void checkGmsCore(Activity context) {
        try {
            // Verify the user has not included GmsCore for a root installation.
            // GmsCore Support changes the package name, but with a mounted installation
            // all manifest changes are ignored and the original package name is used.
            String packageName = context.getPackageName();
            if (packageName.equals(PACKAGE_NAME_YOUTUBE) || packageName.equals(PACKAGE_NAME_YOUTUBE_MUSIC)) {
                Logger.printInfo(() -> "App is mounted with root, but GmsCore patch was included");
                // Cannot use localize text here, since the app will load
                // resources from the unpatched app and all patch strings are missing.
                Utils.showToastLong("The 'GmsCore support' patch breaks mount installations");

                // Do not exit. If the app exits before launch completes (and without
                // opening another activity), then on some devices such as Pixel phone Android 10
                // no toast will be shown and the app will continually relaunch
                // with the appearance of a hung app.
            }

            // Verify GmsCore is installed.
            try {
                PackageManager manager = context.getPackageManager();
                manager.getPackageInfo(GMS_CORE_PACKAGE_NAME, PackageManager.GET_ACTIVITIES);
            } catch (PackageManager.NameNotFoundException exception) {
                Logger.printInfo(() -> "GmsCore was not found");
                // Cannot show a dialog and must show a toast,
                // because on some installations the app crashes before a dialog can be displayed.
                Utils.showToastLong(str("gms_core_toast_not_installed_message"));
                open(getGmsCoreDownload());
                return;
            }

            // Check if GmsCore is whitelisted from battery optimizations.
            if (isAndroidAutomotive(context)) {
                // Ignore Android Automotive devices (Google built-in),
                // as there is no way to disable battery optimizations.
                Logger.printDebug(() -> "Device is Android Automotive");
            } else if (batteryOptimizationsEnabled(context)) {
                Logger.printInfo(() -> "GmsCore is not whitelisted from battery optimizations");

                showBatteryOptimizationDialog(context,
                        "gms_core_dialog_not_whitelisted_using_battery_optimizations_message",
                        "gms_core_dialog_continue_text",
                        (dialog, id) -> openGmsCoreDisableBatteryOptimizationsIntent(context));
                return;
            }

            // Check if GmsCore is currently running in the background.
            var client = context.getContentResolver().acquireContentProviderClient(GMS_CORE_PROVIDER);
            //noinspection TryFinallyCanBeTryWithResources
            try {
                if (client == null) {
                    Logger.printInfo(() -> "GmsCore is not running in the background");
                    checkIfDontKillMyAppSupportsManufacturer();

                    showBatteryOptimizationDialog(context,
                            "gms_core_dialog_not_whitelisted_not_allowed_in_background_message",
                            "gms_core_dialog_open_website_text",
                            (dialog, id) -> openDontKillMyApp());
                }
            } finally {
                if (client != null) client.close();
            }
        } catch (Exception ex) {
            Logger.printException(() -> "checkGmsCore failure", ex);
        }
    }

    @SuppressLint("BatteryLife") // Permission is part of GmsCore
    private static void openGmsCoreDisableBatteryOptimizationsIntent(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.fromParts("package", GMS_CORE_PACKAGE_NAME, null));
        activity.startActivityForResult(intent, 0);
    }

    private static void checkIfDontKillMyAppSupportsManufacturer() {
        Utils.runOnBackgroundThread(() -> {
            try {
                final long start = System.currentTimeMillis();
                HttpURLConnection connection = Requester.getConnectionFromRoute(
                        DONT_KILL_MY_APP_URL, DONT_KILL_MY_APP_MANUFACTURER_API, BUILD_MANUFACTURER);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                final boolean supported = connection.getResponseCode() == 200;
                Logger.printInfo(() -> "Manufacturer is " + (supported ? "" : "NOT ")
                        + "listed on DontKillMyApp: " + BUILD_MANUFACTURER
                        + " fetch took: " + (System.currentTimeMillis() - start) + "ms");
                DONT_KILL_MY_APP_MANUFACTURER_SUPPORTED = supported;
            } catch (Exception ex) {
                Logger.printInfo(() -> "Could not check if manufacturer is listed on DontKillMyApp: "
                        + BUILD_MANUFACTURER, ex);
                DONT_KILL_MY_APP_MANUFACTURER_SUPPORTED = null;
            }
        });
    }

    private static void openDontKillMyApp() {
        final Boolean manufacturerSupported = DONT_KILL_MY_APP_MANUFACTURER_SUPPORTED;

        String manufacturerPageToOpen;
        if (manufacturerSupported == null) {
            // Fetch has not completed yet. Only happens on extremely slow internet connections
            // and the user spends less than 1 second reading what's on screen.
            // Instead of waiting for the fetch (which may timeout),
            // open the website without a vendor.
            manufacturerPageToOpen = "";
        } else if (manufacturerSupported) {
            manufacturerPageToOpen = BUILD_MANUFACTURER;
        } else {
            // No manufacturer specific page exists. Open the general page.
            manufacturerPageToOpen = "general";
        }

        open(DONT_KILL_MY_APP_URL + manufacturerPageToOpen + DONT_KILL_MY_APP_NAME_PARAMETER);
    }

    /**
     * @return If GmsCore is not whitelisted from battery optimizations.
     */
    private static boolean batteryOptimizationsEnabled(Context context) {
        //noinspection ObsoleteSdkInt
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Android 5.0 does not have battery optimization settings.
            return false;
        }
        var powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return !powerManager.isIgnoringBatteryOptimizations(GMS_CORE_PACKAGE_NAME);
    }

    private static boolean isAndroidAutomotive(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private static String getGmsCoreDownload() {
        final var vendorGroupId = getGmsCoreVendorGroupId();
        //noinspection SwitchStatementWithTooFewBranches
        return switch (vendorGroupId) {
            case "app.revanced" -> "https://github.com/revanced/gmscore/releases/latest";
            default -> vendorGroupId + ".android.gms";
        };
    }

    // Modified by a patch. Do not touch.
    private static String getGmsCoreVendorGroupId() {
        return "app.revanced";
    }
}
