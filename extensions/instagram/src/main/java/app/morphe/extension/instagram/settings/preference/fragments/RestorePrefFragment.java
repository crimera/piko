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
import android.util.Xml;

import android.app.Fragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.xmlpull.v1.XmlPullParser;

import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.patches.customise.font.FontStorage;
import app.morphe.extension.instagram.patches.dm.InboxLock;
import app.morphe.extension.instagram.patches.focusLock.FocusLock;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public class RestorePrefFragment extends Fragment {

    private static final int READ_REQUEST_CODE = 42;
    private static final long MAX_RESTORE_BYTES = 16L * 1024 * 1024;

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
                // An imported file can turn Inbox lock off, so it has to be confirmed first while the lock is up.
                if (args != null && args.containsKey("piko_import_pref") && getActivity() != null) {
                    InboxLock.confirmIfLocked(getActivity(), this::requestFileForRestore, this::finishHost);
                } else {
                    requestFileForRestore();
                }
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
        File tempFile = new File(destinationFile.getPath() + ".tmp");
        try {
            File parent = destinationFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            long total = 0;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                if (in == null) throw new IOException("Could not open " + uri);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_RESTORE_BYTES) throw new IOException("File is too large to restore");
                    out.write(buffer, 0, read);
                }
            }

            // Don't touch the current file unless the whole picked file is what we restore.
            if (!isValidRestoreFile(tempFile)) {
                tempFile.delete();
                toast(str("piko_import_fail"));
                return;
            }
            if (!tempFile.renameTo(destinationFile)) {
                throw new IOException("Could not replace " + destinationFile);
            }

            toast(str("piko_import_success"));
            Utils.restartApp(ctx);

        } catch (Exception e) {
            tempFile.delete();
            toast(str("piko_import_fail"));
            Logger.printException(() -> "import failure", e);
        }
    }

    private boolean isValidRestoreFile(File file) {
        try {
            if (destinationFile.getName().endsWith(".xml")) {
                try (InputStream in = new FileInputStream(file)) {
                    return isValidPreferences(in);
                }
            }
            byte[] bytes = new byte[(int) file.length()];
            try (InputStream in = new FileInputStream(file)) {
                int offset = 0;
                while (offset < bytes.length) {
                    int read = in.read(bytes, offset, bytes.length - offset);
                    if (read < 0) return false;
                    offset += read;
                }
            }
            JSONTokener tokener = new JSONTokener(new String(bytes, StandardCharsets.UTF_8));
            Object json = tokener.nextValue();
            if (tokener.nextClean() != 0) return false;
            if (destinationFile.getName().equals("id_name_mapping.json")) {
                if (!(json instanceof JSONArray) || ((JSONArray) json).length() == 0) return false;
                JSONArray array = (JSONArray) json;
                for (int i = 0; i < array.length(); i++) {
                    if (!(array.get(i) instanceof String) || ((String) array.get(i)).isEmpty()) return false;
                }
                return true;
            }
            return json instanceof JSONObject || json instanceof JSONArray;
        } catch (Exception e) {
            return false;
        }
    }

    /** Checks the whole SharedPreferences document: every entry, its type and its value. */
    private static boolean isValidPreferences(InputStream in) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(in, null);
        Set<String> names = new HashSet<>();
        String setName = null;
        boolean sawRoot = false;
        int depth = 0;
        boolean inPrimitive = false;
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == XmlPullParser.END_TAG) {
                if (depth == 2) inPrimitive = false;
                depth--;
                continue;
            }
            // Android's reader throws on any text inside a primitive entry.
            if (event == XmlPullParser.TEXT && inPrimitive) return false;
            if (event != XmlPullParser.START_TAG) continue;
            depth++;
            String tag = parser.getName();
            if (depth == 1) {
                if (sawRoot || !"map".equals(tag)) return false;
                sawRoot = true;
            } else if (depth == 2) {
                String name = parser.getAttributeValue(null, "name");
                if (name == null || name.isEmpty() || !names.add(name)) return false;
                String value = parser.getAttributeValue(null, "value");
                setName = null;
                switch (tag) {
                    case "boolean":
                        if (!"true".equals(value) && !"false".equals(value)) return false;
                        break;
                    case "int":
                        Integer.parseInt(value);
                        break;
                    case "long":
                        Long.parseLong(value);
                        break;
                    case "float":
                        if (!Float.isFinite(Float.parseFloat(value))) return false;
                        break;
                    case "string":
                        break;
                    case "set":
                        setName = name;
                        break;
                    default:
                        return false;
                }
                inPrimitive = !"string".equals(tag) && !"set".equals(tag);
            } else if (depth != 3 || setName == null || !"string".equals(tag)) {
                return false;
            }
        }
        return sawRoot && depth == 0;
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
