package app.morphe.extension.instagram.utils;

import android.os.Build;
import android.os.Environment;
import android.util.Log;

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
}