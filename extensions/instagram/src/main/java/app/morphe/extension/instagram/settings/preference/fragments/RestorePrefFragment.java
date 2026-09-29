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
import android.os.Handler;
import android.os.Looper;

import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.patches.customise.font.FontStorage;
import app.morphe.extension.instagram.patches.focusLock.FocusLock;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public class RestorePrefFragment extends Fragment {

    private static final int READ_REQUEST_CODE = 42;

    private static final String[] FONT_MIME_TYPES = {
            "font/ttf",
            "font/otf",
            "application/x-font-ttf",
            "application/octet-stream"
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private File destinationFile;
    private Context context;
    private boolean isFontImport;

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
                // Importing an older settings file would drop an active Focus Lock.
                if (FocusLock.isActive()) {
                    toast(str("piko_focus_lock_blocked_action"));
                    finishHost();
                    return;
                }
                destinationFile = new File(context.getApplicationInfo().dataDir + "/shared_prefs", Constants.PIKO_SETTINGS + ".xml");
            } else if (args.containsKey("piko_pref_add_font")) {
                // FontStorage decides where the font goes, and validates it first.
                isFontImport = true;
            }
        }
        if (savedInstanceState == null) {
            if (destinationFile != null || isFontImport) {
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
            if (isFontImport) {
                intent.putExtra(Intent.EXTRA_MIME_TYPES, FONT_MIME_TYPES);
            }
            startActivityForResult(intent, READ_REQUEST_CODE);
        } catch (Exception e) {
            Logger.printException(() -> "requestFileForRestore failure", e);
            toast(str("piko_import_fail"));
            finishHost();
        }
    }

    /**
     * Imports a picked font through {@link FontStorage}, off the main thread: a font can be several
     * megabytes and often comes from a cloud-backed provider, which could otherwise cause an ANR.
     */
    private void receiveFont(Context ctx, Uri uri) {
        new Thread(() -> {
            FontStorage.ImportResult result = FontStorage.importFrom(ctx, uri);

            mainHandler.post(() -> {
                switch (result) {
                    case ADDED:
                        toast(str("piko_pref_add_font_success"));
                        break;
                    case NOT_A_FONT:
                        toast(str("piko_pref_add_font_invalid"));
                        break;
                    case TOO_LARGE:
                        toast(str("piko_pref_add_font_too_large"));
                        break;
                    default:
                        toast(str("piko_pref_add_font_fail"));
                        break;
                }
                finishHost();
            });
        }).start();
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
            Context ctx = getActivity() != null ? getActivity() : context;
            if (uri == null) {
                toast(str("piko_fail_no_path"));
            } else if (isFontImport) {
                // Finishes itself once the font is imported off the main thread.
                receiveFont(ctx, uri);
                return;
            } else {
                receiveFileForRestore(ctx, uri);
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
