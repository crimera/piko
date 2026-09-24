/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.fragments;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;

import android.content.Context;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.patches.customise.font.FontStorage;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public class RestorePrefActivity extends AppCompatActivity {

    private static Context context = Utils.getContext();

    private static final int READ_REQUEST_CODE = 42;

    private static final String[] FONT_MIME_TYPES = {
            "font/ttf",
            "font/otf",
            "application/x-font-ttf",
            "application/octet-stream"
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private File destinationFile;

    private boolean isFontImport;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getIntent().getExtras();

        if (args != null) {
            if (args.containsKey("piko_import_dev_overrides")) {
                destinationFile =new File(context.getFilesDir()+ "/mobileconfig","mc_overrides.json");
            } else if (args.containsKey("piko_import_id_mapping")) {
                destinationFile = new File(context.getFilesDir()+ "/mobileconfig","id_name_mapping.json");
            } else if (args.containsKey("piko_import_pref")) {
                destinationFile =  new File(context.getApplicationInfo().dataDir + "/shared_prefs",Constants.PIKO_SETTINGS+".xml");
            } else if (args.containsKey("piko_pref_add_font")) {
                // A font keeps the name the user picked it by, so where it is written is only
                // known once they have picked it.
                isFontImport = true;
            }
            if (destinationFile != null || isFontImport) {
                requestFileForRestore();
            } else {
                toast(str("piko_export_fail"));
                finish();
            }
        }
    }


    public void requestFileForRestore() {
        Intent intent =new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        if (isFontImport) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, FONT_MIME_TYPES);
        }
        startActivityForResult(intent,READ_REQUEST_CODE);
    }


    /**
     * Copies a picked font into piko's own fonts directory. Reading the file, checking that it is
     * a font and cleaning up after a copy that went wrong all belong together, so the whole of it
     * lives in {@link FontStorage}.
     *
     * Run off the main thread: a font can be several megabytes and is often picked from a
     * cloud-backed provider whose reads carry real network latency, which would otherwise risk an
     * ANR right here.
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
                finish();
            });
        }).start();
    }


    /** Also run off the main thread, for the same reason as {@link #receiveFont}. */
    private void receiveFileForRestore(Context ctx, Uri uri) {
        new Thread(() -> {
            boolean success = false;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    try (FileOutputStream out = new FileOutputStream(destinationFile)) {
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                        success = true;
                    }
                }
            } catch (Exception e) {
                Logger.printException(() -> "import failure", e);
            }

            boolean restored = success;
            mainHandler.post(() -> {
                if (restored) {
                    toast(str("piko_import_success"));
                    Utils.restartApp(ctx);
                } else {
                    toast(str("piko_import_fail"));
                }
                finish();
            });
        }).start();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == READ_REQUEST_CODE && resultCode == RESULT_OK && intent != null) {
            Uri uri = intent.getData();

            if (uri == null) {
                toast(str("piko_fail_no_path"));
                finish();
            } else if (isFontImport) {
                receiveFont(this, uri);
            } else {
                receiveFileForRestore(this, uri);
            }
        } else {
            finish();
        }
    }


    private static void toast(String msg) {
        Utils.showToastShort(msg);
    }
}