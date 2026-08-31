/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.devFlags;

import android.content.Context;
import java.util.List;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;

import static app.morphe.extension.instagram.utils.IgStr.str;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.entity.DeveloperOptions;
import app.morphe.extension.instagram.entity.DeveloperOptionsItem;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.utils.InstaUtils;

public class RecommendedFlags {

    private static File getRecommendedFlagsFile() {
        Context context = PikoUtils.getContext();
        return new File(context.getFilesDir(), Constants.REC_FLAGS + ".json");
    }

    public static long getLastModified() {
        File file = getRecommendedFlagsFile();
        return file.exists() ? file.lastModified() : 0L;
    }

    private static JSONArray readFlagsFromFileDir(){
        try{
            File recFlagsFile = getRecommendedFlagsFile();
            if (!recFlagsFile.exists()) {
                PikoUtils.toast(str("piko_rec_flags_no_file"));
            } else {
                String data = PikoUtils.readFile(recFlagsFile);
                return new JSONArray(data);
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
            PikoUtils.toast(str("piko_rec_flags_read_error"));
        }
        return new JSONArray();
    }

    public static List<Flag> getFlags() {
        List<Flag> flags = new ArrayList();
        try {
            JSONArray arr = readFlagsFromFileDir();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                flags.add(new Flag(obj));
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
            PikoUtils.toast("getFlags failed");
        }
        return flags;
    }

    public static void downloadRecommendedFlagsFile(Runnable onComplete) {
        String filename = Constants.REC_FLAGS+".json";
        String host = Constants.PIKO_MAPPINGS_PATH;
        InstaUtils.downloadFile(host, filename, getRecommendedFlagsFile(), onComplete);
    }
}
