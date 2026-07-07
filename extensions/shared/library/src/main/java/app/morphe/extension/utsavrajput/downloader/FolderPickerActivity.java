/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.downloader;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.crimera.constants.ExtensionStrings;

public class FolderPickerActivity extends AppCompatActivity {

    private static final int FOLDER_REQUEST_CODE = 43;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Direct launch of the system picker upon activity creation
        requestFolderPermission();
    }

    public void requestFolderPermission() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, FOLDER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FOLDER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                try {
                    // Persist access so the app can write here even after a reboot
                    getContentResolver().takePersistableUriPermission(treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    // Convert Uri to a physical path string
                    String folderPath = FilePathHelper.getPathFromUri(this, treeUri);

                    if (folderPath != null) {
                        StorageUtils.saveCustomPath(folderPath);
                        toast(ExtensionStrings.DOWNLOAD_SET_PATH_SUCCESS);

                        File testDir = new File(folderPath);
                        if (!StorageUtils.checkStoragePermissions()) {
                            StorageUtils.allowStorageAccess();
                        }
                    } else {
                        toast(ExtensionStrings.DOWNLOAD_SET_PATH_FAILED);
                    }
                } catch (Exception e) {
                    Logger.printException(() -> "setting path failure", e);
                }
            }
        }
        // Always finish the activity after the result is handled to return to the previous screen
        finish();
    }

    private void toast(String msg) {
        Utils.showToastShort(msg);
    }
}