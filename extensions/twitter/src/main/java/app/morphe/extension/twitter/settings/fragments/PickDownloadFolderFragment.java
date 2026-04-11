/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.morphe.extension.twitter.settings.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.twitter.Pref;
import app.morphe.extension.twitter.Utils;
import app.morphe.extension.twitter.patches.nativeFeatures.downloader.NativeDownloaderSafUtils;

public class PickDownloadFolderFragment extends Fragment {
    private static final int PICK_FOLDER_REQUEST_CODE = 43;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestFolder();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode != PICK_FOLDER_REQUEST_CODE) {
            return;
        }

        if (resultCode != Activity.RESULT_OK) {
            close();
            return;
        }

        Uri treeUri = intent != null ? intent.getData() : null;
        if (treeUri == null) {
            toast(StringRef.str("piko_pref_download_saf_pick_failed"));
            close();
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            toast(StringRef.str("piko_pref_download_saf_pick_failed"));
            close();
            return;
        }

        try {
            NativeDownloaderSafUtils.persistTreeUri(activity, treeUri, intent.getFlags());
            toast(StringRef.str("piko_pref_download_saf_pick_success", Pref.getNativeDownloaderSafFolderLabel()));
            Utils.showRestartAppDialog(activity);
        } catch (SecurityException ex) {
            app.morphe.extension.crimera.Utils.logger(ex);
            toast(StringRef.str("piko_pref_download_saf_permission_failed"));
        } catch (Exception ex) {
            app.morphe.extension.crimera.Utils.logger(ex);
            toast(StringRef.str("piko_pref_download_saf_pick_failed"));
        }

        close();
    }

    private void requestFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER_REQUEST_CODE);
    }

    private void close() {
        if (getFragmentManager() == null) {
            return;
        }
        getFragmentManager().popBackStack();
    }

    private void toast(String msg) {
        app.morphe.extension.crimera.Utils.toast(msg);
    }
}
