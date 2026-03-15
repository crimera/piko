package app.morphe.extension.twitter.patches.logintoken;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

@SuppressWarnings("deprecation")
public class ImportLoginTokenFromTextDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        EditText editText = new EditText(getContext());
        editText.setHint("json");
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return new AlertDialog.Builder(getContext())
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (dialog, id) ->
                        ImportExportLoginTokenPatch.addAccount(getContext(), editText.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .create();

    }
}
