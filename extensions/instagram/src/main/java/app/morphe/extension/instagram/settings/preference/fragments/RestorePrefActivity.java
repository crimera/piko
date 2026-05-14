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

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;

import android.content.Context;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public class RestorePrefActivity extends AppCompatActivity {

    private static Context context = Utils.getContext();

    private static final int READ_REQUEST_CODE = 42;

    private File destinationFile;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getIntent().getExtras();

        if (args != null) {
            if (args.containsKey(Strings.IMPORT_DEV_OVERRIDES)) {
                destinationFile =new File(context.getFilesDir()+ "/mobileconfig","mc_overrides.json");
            } else if (args.containsKey(Strings.IMPORT_ID_MAPPING)) {
                destinationFile = new File(context.getFilesDir()+ "/mobileconfig","id_name_mapping.json");
            } else if (args.containsKey(Strings.IMPORT_PIKO_PREF)) {
                destinationFile =  new File(context.getApplicationInfo().dataDir + "/shared_prefs",Strings.PIKO_SETTINGS+".xml");
            }
            if (destinationFile != null) {
                requestFileForRestore();
            } else {
                toast(Strings.EXPORT_FAIL);
                finish();
            }
        }
    }


    public void requestFileForRestore() {
        Intent intent =new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent,READ_REQUEST_CODE);
    }


    private void receiveFileForRestore(Context ctx, Uri uri) {
        try {
            InputStream in = ctx.getContentResolver().openInputStream(uri);

            FileOutputStream out = new FileOutputStream(destinationFile);

            byte[] buffer = new byte[4096];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            in.close();
            out.close();
            toast(Strings.IMPORT_SUCCESS);
            Utils.restartApp(ctx);

        } catch (Exception e) {
            toast(Strings.IMPORT_FAIL);
            Logger.printException(() -> "import failure", e);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == READ_REQUEST_CODE && resultCode == RESULT_OK) {
            Uri uri = intent.getData();

            if (uri != null) {
                receiveFileForRestore(this, uri);
            } else {
                toast(Strings.FAIL_NO_PATH);
            }
        }
        finish();
    }


    private static void toast(String msg) {
        Utils.showToastShort(msg);
    }
}