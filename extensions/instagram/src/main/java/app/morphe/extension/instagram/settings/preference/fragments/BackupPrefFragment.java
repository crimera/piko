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

import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.io.IOException;
import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.*;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.shared.Logger;

public class BackupPrefFragment extends Fragment {
    private File sourceFile;
    private Context context = Utils.getContext();

    private void startIntent(String fileName,String extension) {
        fileName = fileName+"_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date()) + extension;
        Intent intent = new Intent("android.intent.action.CREATE_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("application/json");
        intent.putExtra("android.intent.extra.TITLE", fileName);
        startActivityForResult(intent, 1);
    }

    private void copyFile(Uri uri) {
        try {
            FileInputStream fileInputStream = new FileInputStream(this.sourceFile);
            OutputStream outputStreamOpenOutputStream = context.getContentResolver().openOutputStream(uri);
            byte[] bArr = new byte[1024];
            while (true) {
                int i = fileInputStream.read(bArr);
                if (i <= 0) {
                    fileInputStream.close();
                    outputStreamOpenOutputStream.close();
                    toast(Strings.EXPORT_SUCCESS);
                    return;
                }
                outputStreamOpenOutputStream.write(bArr, 0, i);
            }
        } catch (IOException e) {
            toast(Strings.EXPORT_FAIL);
            Logger.printException(() -> "exporting failure", e);
        }
    }


    public void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        Uri uri = null;
        if (intent != null) {
            uri = intent.getData();
        }
        if (uri == null) {
            toast(Strings.EXPORT_FAIL_NO_PATH);

        }
        else if (i2 == -1) {
            copyFile(uri);
        }
        getActivity().finish();
    }

    private void toast(String msg){
        Utils.showToastShort(msg);
    }

    @Override
    public void onCreate(@org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();

        String filename = null;
        String ext = null;
        if(args != null ){
            if (args.containsKey(Strings.EXPORT_DEV_OVERRIDES)) {
                filename = "mc_overrides";
                ext = ".json";
                this.sourceFile = new File(context.getFilesDir() + "/mobileconfig", filename+ext);
            }
        }

        if(filename!=null && ext!=null){
            startIntent(filename,ext);
        }


    }
}