package app.morphe.extension.twitter.patches.logintoken;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.os.Bundle;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.StringRef;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@SuppressWarnings("deprecation")
public class ImportLoginTokenDialogFragment extends DialogFragment {
    private static final int PICK_FILE_REQUEST_CODE = 31;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] items = {
                StringRef.str("piko_login_token_import_from_text"),
                StringRef.str("piko_login_token_import_from_file")
        };

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setItems(items, null)
                .create();

        // To prevent AlertDialog from closing automatically after pressing the "Import from file", set listener manually
        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0 -> { // Import from text
                    new ImportLoginTokenFromTextDialogFragment().show(getFragmentManager(), null);
                    dismiss();
                }
                case 1 -> { // Import from file
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    startActivityForResult(intent, PICK_FILE_REQUEST_CODE);
                }
            }
        });

        return dialog;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Import from file
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data == null || data.getData() == null) {
                StringRef.str("piko_pref_import_no_uri");
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(getContext().getContentResolver().openInputStream(data.getData())))) {
                StringBuilder sb = new StringBuilder();
                String readString;
                while ((readString = reader.readLine()) != null) {
                    sb.append(readString);
                }
                ImportExportLoginTokenPatch.addAccount(getContext(), sb.toString());
                dismiss();
            } catch (IOException e) {
                Logger.printException(() -> "IOException while reading imported file", e);
            }
        }
    }
}
