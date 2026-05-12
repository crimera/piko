/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */


package app.morphe.extension.instagram.patches.download;

import android.os.Handler;
import android.os.Looper;
import java.net.HttpURLConnection;
import java.io.File;
import android.content.Context;

import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.requests.Route;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.crimera.PikoUtils;

import static app.morphe.extension.shared.requests.Route.Method.GET;

public class DownloadMapping {

    public static void downloadMapping() {
        Context context = Utils.getContext();

        if (!Utils.isNetworkConnected()) {
            PikoUtils.toast(Strings.NO_INTERNET);
            return;
        }
        Utils.runOnBackgroundThread(() -> {
            String appVersionName = Utils.getAppVersionName();
            String mappings_filename = appVersionName + ".json";

            try {
                String url = "https://github.com/crimera/piko/raw/refs/heads/dev/docs/mappings/";

                Route route = new Route(GET, mappings_filename);
                HttpURLConnection connection = Requester.getConnectionFromRoute(url, route);
                String response = Requester.parseString(connection);

                File destinationFile = new File(context.getFilesDir() + "/mobileconfig", "id_name_mapping.json");
                PikoUtils.writeFile(destinationFile, response.getBytes(), false);

                new Handler(Looper.getMainLooper()).post(() -> {
                    PikoUtils.toast(Strings.DOWNLOADED_MEDIA + mappings_filename);
                    Utils.restartApp(context);
                });

            } catch (Exception e) {
                PikoUtils.logger(e);
                PikoUtils.toast(Strings.DOWNLOAD_FAILED_MEDIA + mappings_filename);
            }

        });

    }

}
