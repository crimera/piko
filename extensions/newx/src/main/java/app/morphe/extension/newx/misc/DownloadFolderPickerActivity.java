package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

/**
 * Folder picker host for the NewX download destinations.
 *
 * <p>Kept local instead of reusing the shared {@code FolderPickerActivity} because that one is an
 * {@code AppCompatActivity} and the NewX targets ship no AppCompat runtime.
 */
public final class DownloadFolderPickerActivity extends Activity {
    public static final String KIND_EXTRA = "piko_newx_download_media_kind";

    private static final int PICK_TREE_REQUEST = 43;

    private DownloadDestination.MediaKind kind;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        kind = kindFrom(getIntent());
        if (kind == null) {
            // Avoid storing an unspecified pick as the images destination.
            NewXLogger.printException(
                    () -> "Download folder picker started without a media kind",
                    new IllegalArgumentException("Missing " + KIND_EXTRA)
            );
            finish();
            return;
        }

        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(pick, PICK_TREE_REQUEST);
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "No activity available for ACTION_OPEN_DOCUMENT_TREE", exception);
            Utils.showToastShort(StringRef.str("piko_newx_download_options_cancelled"));
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != PICK_TREE_REQUEST) return;

        Uri treeUri = data == null ? null : data.getData();
        if (resultCode != RESULT_OK || treeUri == null) {
            Utils.showToastShort(StringRef.str("piko_newx_download_options_cancelled"));
            finish();
            return;
        }

        boolean persisted = true;
        try {
            getContentResolver().takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (RuntimeException exception) {
            // Some providers hand out a tree they refuse to persist; the pick still works
            // until this process ends, so keep it and say what to expect.
            persisted = false;
            NewXLogger.printException(() -> "Could not persist NewX download folder permission", exception);
        }

        if (!persisted && !DownloadDestination.hasLiveTreeAccess(getContentResolver(), treeUri)) {
            // Nothing writable; keeping it would only replace a working folder with failures.
            DownloadDestination.captureDestination(this, kind, "folder-picked/unwritable");
            Utils.showToastShort(StringRef.str("piko_newx_download_options_folder_unwritable"));
            finish();
            return;
        }

        DownloadDestination.store(
                this,
                kind,
                treeUri,
                DownloadDestination.displayPathFor(treeUri)
        );
        DownloadDestination.captureDestination(this, kind, "folder-picked");
        Utils.showToastShort(StringRef.str(persisted
                ? "piko_newx_download_options_changed"
                : "piko_newx_download_options_folder_session"));
        finish();
    }

    @Nullable
    private static DownloadDestination.MediaKind kindFrom(@Nullable Intent intent) {
        if (intent == null) return null;

        String raw = intent.getStringExtra(KIND_EXTRA);
        if (raw == null) return null;

        try {
            return DownloadDestination.MediaKind.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
