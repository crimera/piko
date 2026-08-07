/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.xlite.misc;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.CustomDialog;

@SuppressWarnings("deprecation")
public class FontPickerFragment extends Fragment {
    private static final int PICK_FONT_FILE_REQUEST_CODE = 1;

    private boolean isEmojiFont;
    private static final Context context = app.morphe.extension.shared.Utils.getContext();

    // Hail ChatGPT
    private boolean hasValidFontHeader(Uri uri) {
        try {
            InputStream inputStream = this.context.getContentResolver().openInputStream(uri);
            if (inputStream == null) return false;

            byte[] header = new byte[4];
            int read = inputStream.read(header);
            inputStream.close();

            if (read < 4) return false;

            String magic = new String(header, "ISO-8859-1");

            // Valid signatures
            if (magic.equals("OTTO")) return true;        // OpenType CFF
            if (magic.equals("true")) return true;        // Apple TrueType
            if (magic.equals("ttcf")) return true;        // TrueType Collection

            // 00 01 00 00 (TrueType)
            if ((header[0] == 0x00 &&
                    header[1] == 0x01 &&
                    header[2] == 0x00 &&
                    header[3] == 0x00)) {
                return true;
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return false;
    }

    private boolean copyFont(Uri uri) {
        try {
            String filename = this.isEmojiFont ? UpdateFont.EMOJI_FONT_FILE_NAME : UpdateFont.FONT_FILE_NAME;
            File outFile = new File(this.context.getFilesDir(), filename);
            InputStream inputStream = this.context.getContentResolver().openInputStream(uri);
            OutputStream outputStream = new FileOutputStream(outFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            inputStream.close();
            outputStream.close();
            return true;
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == PICK_FONT_FILE_REQUEST_CODE && resultCode == -1) {
            if (intent != null) {
                Uri uri = intent.getData();
                if (hasValidFontHeader(uri)) {
                    if (copyFont(uri)) {
                        toast(str("piko_xlite_font_added"));
                        promptForRestart();
                    } else {
                        toast(str("piko_xlite_font_add_failed"));
                    }
                } else {
                    toast(str("piko_xlite_font_invalid"));
                }
            }
        }
        getFragmentManager().popBackStack();
    }

    private static void toast(String msg) {
        PikoUtils.toast(msg);
    }

    private void promptForRestart() {
        Activity activity = getActivity();
        if (activity == null) return;
        Dialog dialog =
                CustomDialog.create(
                        activity,
                        str("piko_xlite_restart_title"),
                        str("piko_xlite_restart_summary"),
                        null,
                        str("piko_xlite_restart_now"),
                        () -> Utils.restartApp(activity),
                        () -> { },
                        null,
                        null,
                        true
                ).first;
        dialog.show();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.isEmojiFont = getArguments() != null && getArguments().getBoolean("isEmojiFont", false);
        requestFile();
    }

    public void requestFile() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        // Optionally specify MIME types if supported
        String[] mimeTypes = {"font/ttf", "application/x-font-ttf", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        startActivityForResult(intent, PICK_FONT_FILE_REQUEST_CODE);
    }
}