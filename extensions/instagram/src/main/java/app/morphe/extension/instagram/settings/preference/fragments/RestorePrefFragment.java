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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import android.app.Fragment;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public class RestorePrefFragment extends Fragment {
    private static Context context = Utils.getContext();
    private static final int READ_REQUEST_CODE = 42;
    private File destinationFile;

    private void receiveFileForRestore(Context context, Uri uri) {
        try {

            InputStream in = context.getContentResolver().openInputStream(uri);
            FileOutputStream out = new FileOutputStream(this.destinationFile);

            byte[] buffer = new byte[4096];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            in.close();
            out.close();

            toast(Strings.IMPORT_SUCCESS);
            UI.restartDialogBox(context);

        } catch (Exception e) {
            toast(Strings.IMPORT_FAIL);
            Logger.printException(() -> "importing failure", e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == READ_REQUEST_CODE && resultCode == -1) {
            Uri uri = intent.getData();

            if (uri != null) {
                receiveFileForRestore(getActivity(),uri);
            } else {
                toast(Strings.FAIL_NO_PATH);
            }
        }
        getFragmentManager().popBackStack();
    }

    private static void toast(String msg){
        Utils.showToastShort(msg);
    }

    @Override
    public void onCreate(@org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();

        if(args != null){
            if (args.containsKey(Strings.IMPORT_DEV_OVERRIDES)) {
                this.destinationFile = new File(this.context.getFilesDir() + "/mobileconfig", "mc_overrides.json");
            } else if (args.containsKey(Strings.IMPORT_ID_MAPPING)) {
                this.destinationFile = new File(this.context.getFilesDir() + "/mobileconfig", "id_name_mapping.json");
            }

            if(this.destinationFile!=null){
                requestFileForRestore();
            }
        }
    }

    public void requestFileForRestore() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        startActivityForResult(intent, READ_REQUEST_CODE);
    }
}