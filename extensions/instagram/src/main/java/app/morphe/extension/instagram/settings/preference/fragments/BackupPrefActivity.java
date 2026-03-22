/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.morphe.extension.instagram.settings.preference.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Date;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.shared.Logger;

public class BackupPrefActivity extends AppCompatActivity {

    private File sourceFile;
    private Context context;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = Utils.getContext();

        Bundle args = getIntent().getExtras();

        String filename = null;
        String ext = null;

        if (args != null) {

            if (args.containsKey(Strings.EXPORT_DEV_OVERRIDES)) {
                filename = "mc_overrides";
                ext = ".json";
                sourceFile = new File( context.getFilesDir() + "/mobileconfig",filename + ext);
            }
        }

        if (filename != null && ext != null) {
            startIntent(filename, ext);
        } else {
            finish();
        }
    }


    private void startIntent(String fileName, String extension) {
        fileName = fileName + "_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date()) + extension;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE );
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        startActivityForResult(intent, 1);
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

            toast(Strings.EXPORT_SUCCESS);

        } catch (IOException e) {

            toast(Strings.EXPORT_FAIL);

            Logger.printException(() -> "export failure",e);
        }
    }


    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);

        Uri uri = null;

        if (data != null) {
            uri = data.getData();
        }

        if (uri == null) {
            toast(Strings.FAIL_NO_PATH);
        } else if (res == RESULT_OK) {
            copyFile(uri);
        }

        finish();
    }


    private void toast(String msg) {
        Utils.showToastShort(msg);
    }
}