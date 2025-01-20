package app.revanced.integrations.twitter;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.LinearLayout;
import androidx.annotation.RequiresApi;
import app.revanced.integrations.shared.StringRef;
import app.revanced.integrations.shared.settings.BooleanSetting;
import app.revanced.integrations.shared.settings.StringSetting;
import app.revanced.integrations.shared.settings.preference.SharedPrefCategory;
import app.revanced.integrations.twitter.settings.Settings;
import com.google.android.material.tabs.TabLayout$g;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@SuppressWarnings("unused")
public class Utils {
    @SuppressLint("StaticFieldLeak")
    private static final Context ctx = app.revanced.integrations.shared.Utils.getContext();
    private static final SharedPrefCategory sp = new SharedPrefCategory(Settings.SHARED_PREF_NAME);

    public static void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW,Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage(ctx.getPackageName());
        ctx.startActivity(intent);
    }

    private static void startActivity(Class cls) {
        Intent intent = new Intent(ctx, cls);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    public static void startActivityFromClassName(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            startActivity(clazz);
        } catch (Exception ex) {
            toast(ex.toString());
        }
    }

    public static void startUndoPostActivity() {
        String className = "com.twitter.feature.subscriptions.settings.undotweet.UndoTweetSettingsActivity";
        startActivityFromClassName(className);
    }

    public static void startAppIconNNavIconActivity() {
        String className = "com.twitter.feature.subscriptions.settings.extras.ExtrasSettingsActivity";
        startActivityFromClassName(className);
    }


    private static void startBookmarkActivity() {
        String className = "com.twitter.app.bookmarks.legacy.BookmarkActivity";
        startActivityFromClassName(className);
    }

    public static void startXSettings() {
        String className = "com.twitter.app.settings.SettingsRootCompatActivity";
        startActivityFromClassName(className);
    }

    //thanks to @Ouxyl
    public static boolean redirect(TabLayout$g g) {
        try {
            String tabName = g.c.toString();
            if (tabName == strRes("bookmarks_title")) {
                startBookmarkActivity();
                return true;
            }

        } catch (Exception e) {
            logger(e.toString());
        }
        return false;
    }

    public static Boolean setBooleanPerf(String key, Boolean val) {
        try {
            sp.saveBoolean(key, val);
            return true;
        } catch (Exception ex) {
            toast(ex.toString());
        }
        return false;
    }

    public static Boolean setStringPref(String key, String val) {
        try {
            sp.saveString(key, val);
            return true;
        } catch (Exception ex) {
            toast(ex.toString());
        }
        return false;
    }

    public static String getStringPref(StringSetting setting) {
        String value = sp.getString(setting.key, setting.defaultValue);
        if (value.isBlank()) {
            return setting.defaultValue;
        }
        return value;
    }

    public static String strRes(String tag) {
        try {
            return StringRef.str(tag);
        } catch (Exception e) {

            app.revanced.integrations.shared.Utils.showToastShort(tag + " not found");
        }
        return tag;
    }

    public static void showRestartAppDialog(Context context) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(context);

        LinearLayout ln = new LinearLayout(context);
        ln.setOrientation(LinearLayout.VERTICAL);

        dialog.setTitle(strRes("settings_restart"));
        dialog.setPositiveButton(strRes("ok"), (dialogInterface, i) -> {
            app.revanced.integrations.shared.Utils.restartApp(context);
        });
        dialog.setNegativeButton(strRes("cancel"), null);
        dialog.show();
    }

    public static void deleteSharedPrefAB(Context context, boolean flag) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(context);

        LinearLayout ln = new LinearLayout(context);
        ln.setOrientation(LinearLayout.VERTICAL);

        String content = flag ? "piko_title_feature_flags" : "notification_settings_preferences_category";

        dialog.setTitle(strRes("delete"));

        dialog.setMessage(strRes("delete") + " " + strRes(content) + " ?");
        dialog.setPositiveButton(strRes("ok"), (dialogInterface, i) -> {
            boolean success = false;
            if (flag) {
                sp.removeKey(Settings.MISC_FEATURE_FLAGS.key);
                success = true;
            } else {
                success = sp.clearAll();
            }
            if (success) {
                app.revanced.integrations.shared.Utils.restartApp(context);
            }
        });
        dialog.setNegativeButton(strRes("cancel"), null);
        dialog.show();
    }

    public static Boolean getBooleanPerf(BooleanSetting setting) {
        return sp.getBoolean(setting.key, setting.defaultValue);
    }

    public static String getAll(boolean no_flags) {
        JSONObject prefs = sp.getAll();
        if (no_flags) {
            prefs.remove(Settings.MISC_FEATURE_FLAGS.key);
            prefs.remove(Settings.MISC_FEATURE_FLAGS_SEARCH.key);
        }
        return prefs.toString();
    }

    public static Set<String> getSetPerf(String key, Set<String> defaultValue) {
        return sp.getSet(key, defaultValue);
    }

    public static Boolean setSetPerf(String key, Set<String> defaultValue) {
        try {
            sp.saveSet(key, defaultValue);
            return true;
        } catch (Exception ex) {
            toast(ex.toString());
        }
        return false;
    }

    public static boolean setAll(String jsonString) {
        boolean sts = false;
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = jsonObject.get(key);
                if (value instanceof Boolean) {
                    setBooleanPerf(key, (Boolean) value);
                } else if (value instanceof String) {
                    setStringPref(key, (String) value);
                } else if (value instanceof JSONArray) {
                    int index = 0;
                    Set<String> strings = new HashSet<>();

                    if (!(((JSONArray) value).get(0) instanceof String)) {
                        continue;
                    }

                    for (int i = 0; i < ((JSONArray) value).length(); i++) {
                        strings.add(((JSONArray) value).getString(i));
                    }

                    setSetPerf(key, strings);
                }
            }
            sts = true;
        } catch (Exception ex) {
            toast(ex.toString());
        }
        return sts;
    }

    public static String[] addPref(String[] prefs, String pref) {
        String[] bigger = Arrays.copyOf(prefs, prefs.length + 1);
        bigger[prefs.length] = pref;
        return bigger;
    }

    private static String getPath(String publicFolder, String subFolder, String filename) {
        return publicFolder + "/" + subFolder + "/" + filename;
    }


    private static void postDownload(String filename, File tempFile, File file, Intent intent, long downloadId, BroadcastReceiver broadcastReceiver) {
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (id == downloadId) {
            boolean result = tempFile.renameTo(file);
            if (!result) {
                toast("Failed to rename temp file");
            }

            toast(strRes("exo_download_completed") + ": " + filename);
            ctx.unregisterReceiver(broadcastReceiver);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public static void downloadFile(String url, String mediaName, String ext) {
        String filename = mediaName + "." + ext;
        boolean isPhoto = ext.equals("jpg");

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDescription("Downloading " + filename);
        request.setTitle(filename);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        String publicFolder = "Pictures";
        String subFolder = "Twitter";

        if (!isPhoto) {
            publicFolder = Pref.getPublicFolder();
            subFolder = Utils.getStringPref(Settings.VID_SUBFOLDER);
        }
        request.setDestinationInExternalPublicDir(publicFolder, subFolder + "/" + "temp_" + filename);

        File file = new File(Environment.getExternalStorageDirectory(), getPath(publicFolder, subFolder, filename));
        if (file.exists()) {
            toast(strRes("exo_download_completed") + ": " + filename);
            return;
        }

        DownloadManager manager = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = manager.enqueue(request);

        File tempFile = new File(
                Environment.getExternalStorageDirectory(),
                getPath(publicFolder, subFolder, "temp_" + filename)
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    postDownload(filename, tempFile, file, intent, downloadId, this);
                }
            }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
        } else {
            ctx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    postDownload(filename, tempFile, file, intent, downloadId, this);
                }
            }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    public static void toast(String msg) {
        app.revanced.integrations.shared.Utils.showToastShort(msg);
    }

    public static void logger(Object j) {
        Log.d("piko", j.toString());
    }


}
