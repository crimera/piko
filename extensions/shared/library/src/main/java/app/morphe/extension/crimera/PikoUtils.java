/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.crimera;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.util.TypedValue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

import app.morphe.extension.shared.Utils;

@SuppressWarnings("unused")
public class PikoUtils {
    private static final Context ctx = Utils.getContext();

    public static Context getContext() {
        return ctx;
    }

    // Credits to Morphe:
    // https://github.com/MorpheApp/morphe-patches/blob/d6a88edcfba71f9b630314c4c8b56347a10c8b2a/extensions/youtube/src/main/java/app/morphe/extension/youtube/settings/preference/ExternalDownloaderPreference.java#L128-L138
    public static boolean isAppInstalledAndEnabled(String packageName) {
        try {
            return ctx.getPackageManager().getApplicationInfo(packageName, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage(ctx.getPackageName());
        ctx.startActivity(intent);
    }

    public static void openDefaultLinks() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS);
        intent.setData(Uri.parse("package:" + ctx.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    public static boolean pikoWriteFile(String fileName,String data,boolean append){
        return pikoWriteFile(fileName,"Piko",data,append);
    }

    public static boolean pikoWriteFile(String fileName,String subFolder, String data,boolean append){
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File pikoDir = new File(downloadsDir, subFolder);

        if (!pikoDir.exists()) {
            pikoDir.mkdirs();
        }

        File outputFile = new File(pikoDir, fileName);
        return writeFile(outputFile,data.getBytes(),append);
    }

    public static boolean writeFile(File fileName, byte[] data, boolean append) {
        try {
            FileOutputStream outputStream = new FileOutputStream(fileName, append);
            outputStream.write(data);
            outputStream.close();
            return true;
        } catch (Exception e) {
            logger(e.toString());
        }
        return false;
    }

    public static String readFile(File fileName) {
        try {
            if (!fileName.exists())
                return null;

            StringBuilder content = new StringBuilder();
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(fileName));
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ignored) {
                    }
                }
            }
            return content.toString();
        } catch (Exception e) {
            logger(e.toString());
        }
        return null;
    }

    public static void toast(String msg) {
        app.morphe.extension.shared.Utils.showToastShort(msg);
    }

    public static void logger(Object e) {
        String logName = "piko";
        Log.d(logName, e +"\n");
        if (e instanceof Exception) {
            Exception ex = (Exception) e;
        StackTraceElement[] stackTraceElements = ex.getStackTrace();
            for (StackTraceElement element : stackTraceElements) {
                Log.d(logName, "Exception occurred at line " + element.getLineNumber() + " in " + element.getClassName()
                        + "." + element.getMethodName());
            }
        }
    }

    /**
     * Converts sp value to actual device pixels.
     *
     * @return The device pixel value.
     */
    public static int spToPixels(float sp) {
        return  (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                Resources.getSystem().getDisplayMetrics()
        );
    }

    public static void shareText(String txt) {
        Context context = getContext();
        final String appPackageName = context.getPackageName();
        Intent sendIntent = new Intent();
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, txt);
        sendIntent.setType("text/plain");
        context.startActivity(sendIntent);
    }
}
