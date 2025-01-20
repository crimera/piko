package app.revanced.integrations.twitter.settings.widgets;

import android.content.Context;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import app.revanced.integrations.shared.Utils;
import app.revanced.integrations.twitter.settings.Settings;


public class MultiSelectListPref extends MultiSelectListPreference {
    private static Helper helper;

    public MultiSelectListPref(Context context) {
        super(context);
        helper = new Helper(context);
        init();
    }
    
    public MultiSelectListPref(Context context, AttributeSet attrs) {
        super(context, attrs);
        helper = new Helper(context);
        init();
    }

    public MultiSelectListPref(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        helper = new Helper(context);
        init();
    }

    private void init() {
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                helper.setValue(preference,newValue);
                return true;
            }
        });
    }
    public void setInitialValue(String key) {
        CharSequence[] entries = new CharSequence[]{};
        CharSequence[] entriesValues = new CharSequence[]{};
        if (key == Settings.CUSTOM_PROFILE_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_profiletabs");
            entriesValues = new CharSequence[]{"tweets", "tweets_replies", "affiliated", "subs", "highlights", "articles", "media", "likes"};
        }else if (key == Settings.CUSTOM_SIDEBAR_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_sidebar");
            entriesValues = new CharSequence[]{"Profile","Premium", "Grok","DMs","Communities","Bookmarks","Lists","TopArticles","BirdwatchNotes","Spaces","PendingFollowers","Monetization","ProfessionalToolsGroup","MediaTransparency","Imprint","SettingsAndSupportGroup","Jobs"};
        }else if (key == Settings.CUSTOM_NAVBAR_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_navbar");
            entriesValues = new CharSequence[]{"HOME","GUIDE", "SPACES","COMMUNITIES","NOTIFICATIONS","CONNECT","COMMUNITY_NOTES","BOOKMARKS","DMS","GROK","MEDIA_TAB"};
        }else if (key == Settings.CUSTOM_INLINE_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_inlinetabs");
            entriesValues = new CharSequence[]{"Reply","Retweet", "Favorite","ViewCount","AddRemoveBookmarks", "TwitterShare"};
        }else if (key == Settings.CUSTOM_EXPLORE_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_exploretabs");
            entriesValues = Utils.getResourceStringArray("piko_array_exploretabs_val");
        }else if (key == Settings.CUSTOM_SEARCH_TYPE_AHEAD.key) {
            entries = Utils.getResourceStringArray("piko_array_search_type_ahead");
            entriesValues = Utils.getResourceStringArray("piko_array_search_type_ahead_val");
        }else if (key == Settings.CUSTOM_SEARCH_TABS.key) {
            entries = Utils.getResourceStringArray("piko_array_searchtabs");
            entriesValues = Utils.getResourceStringArray("piko_array_searchtabs_val");
        }
        setEntries(entries);
        setEntryValues(entriesValues);
    }
}
