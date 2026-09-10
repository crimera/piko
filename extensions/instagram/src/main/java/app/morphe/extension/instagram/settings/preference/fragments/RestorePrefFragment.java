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
import java.io.FileOutputStream;
import java.io.InputStream;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public class RestorePrefFragment extends Fragment {

    private static final int READ_REQUEST_CODE = 42;

    private File destinationFile;
    private Context context;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = getActivity() != null ? getActivity() : Utils.getContext();
        Bundle args = getArguments();

        if (args != null) {
            if (args.containsKey("piko_import_dev_overrides")) {
                destinationFile = new File(context.getFilesDir() + "/mobileconfig", "mc_overrides.json");
            } else if (args.containsKey("piko_import_id_mapping")) {
                destinationFile = new File(context.getFilesDir() + "/mobileconfig", "id_name_mapping.json");
            } else if (args.containsKey("piko_import_pref")) {
                destinationFile = new File(context.getApplicationInfo().dataDir + "/shared_prefs", Constants.PIKO_SETTINGS + ".xml");
            }
        }
        if (savedInstanceState == null) {
            if (destinationFile != null) {
                requestFileForRestore();
            } else {
                toast(str("piko_import_fail"));
                finishHost();
            }
        }
    }

    public void requestFileForRestore() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, READ_REQUEST_CODE);
        } catch (Exception e) {
            Logger.printException(() -> "requestFileForRestore failure", e);
            toast(str("piko_import_fail"));
            finishHost();
        }
    }

    private void receiveFileForRestore(Context ctx, Uri uri) {
        try {
            File parent = destinationFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            InputStream in = ctx.getContentResolver().openInputStream(uri);
            FileOutputStream out = new FileOutputStream(destinationFile);

            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            in.close();
            out.close();
            toast(str("piko_import_success"));
            Utils.restartApp(ctx);

        } catch (Exception e) {
            toast(str("piko_import_fail"));
            Logger.printException(() -> "import failure", e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode == Activity.RESULT_CANCELED) {
            finishHost();
            return;
        }

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK && intent != null) {
            Uri uri = intent.getData();
            if (uri != null) {
                Context ctx = getActivity() != null ? getActivity() : context;
                receiveFileForRestore(ctx, uri);
            } else {
                toast(str("piko_fail_no_path"));
            }
        }
        finishHost();
    }

    private void finishHost() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private static void toast(String msg) {
        Utils.showToastShort(msg);
    }
}
