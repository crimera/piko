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

    public static boolean deleteRecursive(File file) {
        try {
            if (file == null || !file.exists()){
                app.morphe.extension.crimera.Utils.toast(Strings.FAIL_NO_FILE);
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
            app.morphe.extension.crimera.Utils.logger(e);
        }
        return false;
    }
}