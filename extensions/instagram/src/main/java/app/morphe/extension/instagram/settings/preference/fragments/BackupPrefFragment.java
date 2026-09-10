/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.fragments;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.shared.Logger;

public class BackupPrefFragment extends Fragment {

    private File sourceFile;
    private Context context;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = getActivity() != null ? getActivity() : PikoUtils.getContext();

        Bundle args = getArguments();

        String filename = null;
        String ext = null;

        if (args != null) {
            if (args.containsKey("piko_export_dev_overrides")) {
                filename = "mc_overrides";
                ext = ".json";
                sourceFile = new File(context.getFilesDir() + "/mobileconfig", filename + ext);
            } else if (args.containsKey("piko_export_pref")) {
                filename = Constants.PIKO_SETTINGS;
                ext = ".xml";
                sourceFile = new File(context.getApplicationInfo().dataDir + "/shared_prefs", filename + ext);
            }
        }

        if (savedInstanceState == null) {
            if (filename != null && ext != null && sourceFile != null) {
                if (sourceFile.exists()) {
                    startIntent(filename, ext);
                } else {
                    toast(str("piko_fail_no_file"));
                    finishHost();
                }
            } else {
                toast(str("piko_export_fail"));
                finishHost();
            }
        }
    }

    private void startIntent(String fileName, String extension) {
        try {
            fileName = fileName + "_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date()) + extension;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(".xml".equals(extension) ? "application/xml" : "application/json");
            intent.putExtra(Intent.EXTRA_TITLE, fileName);

            startActivityForResult(intent, 1);
        } catch (Exception e) {
            Logger.printException(() -> "startActivityForResult failure", e);
            toast(str("piko_export_fail"));
            finishHost();
        }
    }

    private void copyFile(Uri uri) {
        try {
            FileInputStream in = new FileInputStream(sourceFile);
            OutputStream out = context.getContentResolver().openOutputStream(uri);
            byte[] buffer = new byte[1024];

            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }

            in.close();
            out.close();

            toast(str("piko_export_success"));

        } catch (IOException e) {
            toast(str("piko_export_fail"));
            Logger.printException(() -> "export failure", e);
        }
    }

    @Override
    public void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);

        if (res == Activity.RESULT_CANCELED) {
            finishHost();
            return;
        }

        Uri uri = null;
        if (data != null) {
            uri = data.getData();
        }

        if (uri == null) {
            toast(str("piko_fail_no_path"));
        } else if (res == Activity.RESULT_OK) {
            copyFile(uri);
        }

        finishHost();
    }

    private void finishHost() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private void toast(String msg) {
        PikoUtils.toast(msg);
    }
}
