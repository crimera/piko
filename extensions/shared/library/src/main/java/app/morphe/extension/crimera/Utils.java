/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.morphe.extension.crimera;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.File;
import java.util.Set;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;

@SuppressWarnings("unused")
public class Utils {
    private static final Context ctx = app.morphe.extension.shared.Utils.getContext();

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
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File pikoDir = new File(downloadsDir, "Piko");

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

}
