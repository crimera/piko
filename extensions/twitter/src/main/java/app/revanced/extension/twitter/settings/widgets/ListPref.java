package app.revanced.extension.twitter.settings.widgets;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;
import app.revanced.extension.twitter.settings.Settings;
import app.revanced.extension.shared.Utils;
import android.preference.Preference;
import java.util.Locale;
import app.revanced.extension.twitter.patches.nativeFeatures.translator.Constants;
//import java.util.*;

public class ListPref extends ListPreference {
    private static Helper helper;

    public ListPref(Context context) {
        super(context);
        helper = new Helper(context);
        init();
    }

    public ListPref(Context context, AttributeSet attrs) {
        super(context, attrs);
        helper = new Helper(context);
        init();
    }

    public ListPref(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        helper = new Helper(context);
        init();
    }

    private void init() {
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String selectedValue = (String) newValue;
                helper.setValue(preference,newValue);
                return true;
            }
        });
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        super.onSetInitialValue(restoreValue, defaultValue);
        String key = getKey();

        CharSequence[] entries = new CharSequence[]{};
        CharSequence[] entriesValues = new CharSequence[]{};
        if (key == Settings.VID_PUBLIC_FOLDER.key) {
            entries =  Utils.getResourceStringArray("piko_array_public_folder");
            entriesValues = entries;
        }else if (key == Settings.CUSTOM_TIMELINE_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_timelinetabs");
            entriesValues = Utils.getResourceStringArray("piko_array_timelinetabs_val");
        }else if (key == Settings.VID_MEDIA_HANDLE.key) {
            entries = Utils.getResourceStringArray("piko_array_download_media_handle");
            entriesValues = Utils.getResourceStringArray("piko_array_download_media_handle_val");
        }else if (key == Settings.CUSTOM_INLINE_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_inlinetabs");
            entriesValues = Utils.getResourceStringArray("piko_array_inlinetabs_val");
        }else if (key == Settings.CUSTOM_DEF_REPLY_SORTING.key) {
            entries = Utils.getResourceStringArray("piko_array_reply_sorting");
            entriesValues = Utils.getResourceStringArray("piko_array_reply_sorting_val");
        }else if (key == Settings.NATIVE_TRANSLATOR_PROVIDERS.key) {
            entries = Utils.getResourceStringArray("piko_array_translators");
            entriesValues = Utils.getResourceStringArray("piko_array_translators_val");
        }else if (key == Settings.NATIVE_TRANSLATOR_LANG.key) {
            entries = Constants.displayNames;
            entriesValues = Constants.languageTags;
        }else if (key == Settings.VID_NATIVE_DOWNLOADER_FILENAME.key) {
            entries = Utils.getResourceStringArray("piko_array_native_download_filename");
            entriesValues = Utils.getResourceStringArray("piko_array_native_download_filename_val");
        }

        setEntries(entries);
        setEntryValues(entriesValues);
    }
}
