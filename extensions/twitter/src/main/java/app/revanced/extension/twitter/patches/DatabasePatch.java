package app.revanced.extension.twitter.patches;

import app.revanced.extension.twitter.Utils;
import android.content.Context;
import com.twitter.util.user.UserIdentifier;
import android.util.*;
import java.util.*;
import java.io.File;
import android.database.sqlite.SQLiteDatabase;
import android.app.AlertDialog;
import android.widget.LinearLayout;


public class DatabasePatch {
    private static Context ctx = app.revanced.extension.shared.Utils.getContext();
    private static final String[] listItems = app.revanced.extension.shared.Utils.getResourceStringArray("piko_array_ads_hooks");

    private static void logger(Object j){
        Log.d("piko", j.toString());
    }

    private static String getDBPath(){
        String dbName = UserIdentifier.getCurrent().getStringId()+"-66.db";
        return ctx.getDatabasePath(dbName).getAbsolutePath();
    }
    private static void showItemDialog(Context context,String result){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LinearLayout ln = new LinearLayout(context);
        ln.setOrientation(LinearLayout.VERTICAL);

        builder.setTitle(Utils.strRes("piko_pref_db_del_items"));
        builder.setMessage(result);
        builder.setNegativeButton(Utils.strRes("ok"), null);
        builder.show();
    }

    public static void showDialog(Context context){
        final boolean[] checkedItems = new boolean[listItems.length];
        final List<String> selectedItems = Arrays.asList(listItems);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        LinearLayout ln = new LinearLayout(context);
        ln.setOrientation(LinearLayout.VERTICAL);

        builder.setTitle(Utils.strRes("piko_pref_del_from_db"));
        builder.setMultiChoiceItems(listItems, checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
            String currentItem = selectedItems.get(which);
        });
        builder.setPositiveButton(Utils.strRes("ok"), (dialogInterface, i) -> {
            StringBuilder items = removeFromDB(checkedItems);
            if(items.length()!=0) showItemDialog(context,items.toString());
        });
        builder.setNegativeButton(Utils.strRes("cancel"), null);
        builder.show();
    }

    private static StringBuilder removeFromDB(boolean[] checkedItems){
        SQLiteDatabase database=null;
        StringBuilder result = new StringBuilder();

        try {
            String DATABASE_PATH = getDBPath();
            File f = new File(DATABASE_PATH);
            if (!f.exists() && f.isDirectory()) {
                Utils.toast(Utils.strRes("piko_pref_db_not_found"));
                return result;
            }
            database = SQLiteDatabase.openDatabase(DATABASE_PATH, null, SQLiteDatabase.OPEN_READWRITE);
            if (database != null && database.isOpen()) {
                for (int i = 0; i < checkedItems.length; i++) {
                    if (checkedItems[i]) {
                        String entry_id_str = "";
                        String entry_id_str2 = "";
                        switch (i) {
                            case 0: {
                                entry_id_str = "promoted%";
                                entry_id_str2 = "rtb%";
                                break;
                            }
                            case 1: {
                                entry_id_str = "who-to-follow%";
                                break;
                            }
                            case 2: {
                                entry_id_str = "who-to-subscribe%";
                                break;
                            }
                            case 3: {
                                entry_id_str = "community-to-join%";
                                break;
                            }
                            case 4: {
                                entry_id_str = "bookmarked%";
                                break;
                            }
                            case 5: {
                                entry_id_str = "pinned-tweets%";
                                break;
                            }
                            case 6: {
                                entry_id_str = "tweetdetailrelatedtweets%";
                                break;
                            }
                            case 7: {
                                entry_id_str = "messageprompt%";
                                break;
                            }
                            case 8: {
                                entry_id_str = "stories%";
                                break;
                            }
                        }
                        int deletedRows = 0;
                        if (entry_id_str != "") {
                            deletedRows = database.delete("timeline", "entity_id LIKE ?", new String[]{entry_id_str});                        }
                        if (entry_id_str2 != "") {
                            int deletedRows2 = database.delete("timeline", "entity_id LIKE ?", new String[]{entry_id_str2});
                            deletedRows+=deletedRows2;
                        }
                        result.append("• "+listItems[i]+" = "+String.valueOf(deletedRows)+"\n");
                    }
                }
            } else {
                Utils.toast(Utils.strRes("piko_pref_db_not_open"));
            }

        }
        catch (Exception e){
            logger(e.toString());
            Utils.toast(e.toString());
        }
        if (database != null && database.isOpen()) {
            database.close();
        }

        return result;
    }

    //classEnd
}