/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.crimera.downloader;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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
                    int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(treeUri,
                            flags);

                    StorageUtils.saveCustomTreeUri(treeUri);
                    StorageUtils.saveCustomPath(DocumentsContract.getTreeDocumentId(treeUri));
                    toast(ExtensionStrings.DOWNLOAD_SET_PATH_SUCCESS);
                } catch (Exception e) {
                    Logger.printException(() -> "setting path failure", e);
                    toast(ExtensionStrings.DOWNLOAD_SET_PATH_FAILED);
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
