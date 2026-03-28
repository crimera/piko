/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.utils;

import android.os.Build;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import app.morphe.extension.instagram.constants.Strings;

public class Utils {

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

    public static boolean deleteRecursive(File file) {
        try {
            if (file == null || !file.exists()){
                toast(Strings.FAIL_NO_FILE);
                return false;
            }

            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            return file.delete();
        } catch (RuntimeException e) {
            logger(e);
        }
        return false;
    }
}