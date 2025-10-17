package app.revanced.extension.twitter.patches.customise.font;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileOutputStream;

import android.app.Fragment;
import app.revanced.extension.twitter.Utils;
import app.revanced.extension.shared.StringRef;
import app.revanced.extension.twitter.settings.Settings;

public class FontPickerFragment extends Fragment {
    private static final int PICK_FONT_FILE_REQUEST_CODE = 1;
    private static boolean isEmojiFont = false;

    public boolean isAFontFile(Uri uri){
        try{
            String path = uri.getPath();
            int cut = path.lastIndexOf('/');
            if (cut != -1) {
                String ext = path.substring(cut + 1).toLowerCase();
                return ext.endsWith(".ttf") || ext.endsWith(".otf");
            }
        }catch (Exception e){
            Utils.logger(e);
        }
        return false;
    }

    public boolean copyFont(Uri uri){
        try {
            Context context = app.revanced.extension.shared.Utils.getContext();
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            String filename = this.isEmojiFont ? UpdateFont.EMOJI_FONT_FILE_NAME : UpdateFont.FONT_FILE_NAME;
            File outFile = new File(context.getFilesDir(), filename);
            OutputStream outputStream = new FileOutputStream(outFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            inputStream.close();
            outputStream.close();
            return true;
        }
        catch (Exception e){
            Utils.logger(e);
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == PICK_FONT_FILE_REQUEST_CODE && resultCode == -1) {
            if (intent != null) {
                Uri uri = intent.getData();
                if (isAFontFile(uri)) {
                    boolean check = copyFont(uri);
                    if(check){
                        toast(StringRef.str("piko_pref_add_font_success"));
                        Utils.showRestartAppDialog(getActivity());
                    }else{
                        toast(StringRef.str("piko_pref_add_font_fail"));
                    }
                } else {
                    toast(StringRef.str("piko_pref_add_font_desc"));
                }
            }
        }
        getFragmentManager().popBackStack();
    }

    private static void toast(String msg){
        Utils.toast(msg);
    }

    @Override
    public void onCreate(@org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.isEmojiFont = getArguments().getBoolean("isEmojiFont", false);
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