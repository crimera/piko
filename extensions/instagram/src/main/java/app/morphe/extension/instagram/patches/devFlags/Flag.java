/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.devFlags;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import org.json.JSONObject;
import app.morphe.extension.instagram.settings.StringSetting;
import app.morphe.extension.instagram.utils.IgStr;
import static app.morphe.extension.instagram.settings.RestartPolicy.REQUIRED;


public class Flag {

    private String name;
    private String desc;
    private FlagType type;
    private StringSetting code;
    private Category category = Category.OTHER;

    public enum Category {
        STORIES("stories"), REELS("reels"), FEED("feed"), MESSAGES("messages"),
        SEARCH("search"), PROFILE("profile"), QUALITY("quality"), OTHER("other");

        public final String id;

        Category(String id) {
            this.id = id;
        }

        public String titleKey() {
            return this == SEARCH ? "piko_navigation_tab_search" : "piko_rec_flags_category_" + id;
        }

        public static Category fromId(String id) {
            for (Category category : values()) {
                if (category.id.equals(id)) return category;
            }
            return OTHER;
        }
    }

    public Flag(JSONObject jsonObject) {
        try {
            this.name = jsonObject.optString("name");
            this.desc = jsonObject.optString("desc");
            this.category = Category.fromId(jsonObject.optString("category"));
            String rawType = jsonObject.optString("type", "bool");
            this.type = FlagType.BOOL;
            for (FlagType candidate : FlagType.values()) {
                if (candidate.toString().equals(rawType)) {
                    this.type = candidate;
                    break;
                }
            }
            String codeKey = jsonObject.optString("code");
            // No override by default -- both bool and long flags use FlagState.DEFAULT
            // as the "no override" sentinel, so dev-options and override-backup restores
            // (which don't touch this store) remain free to take effect unopposed.
            this.code = new StringSetting(codeKey, FlagState.DEFAULT.toString(), REQUIRED);
            localize(codeKey);
        } catch (Exception e) {
        }
    }

    private void localize(String codeKey) {
        try {
            String prefix;
            if (codeKey.contains(",")) {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(codeKey.getBytes(StandardCharsets.UTF_8));
                StringBuilder hash = new StringBuilder(32);
                for (int i = 0; i < 16; i++) {
                    hash.append(Character.forDigit((digest[i] & 0xff) >>> 4, 16));
                    hash.append(Character.forDigit(digest[i] & 0xf, 16));
                }
                prefix = "piko_rec_flag_group_" + hash;
            } else {
                prefix = "piko_rec_flag_" + codeKey.replace("::", "_");
            }
            Context context = IgStr.baseContext();
            Resources resources = context.getResources();
            int nameId = resources.getIdentifier(prefix + "_name", "string", context.getPackageName());
            int descId = resources.getIdentifier(prefix + "_desc", "string", context.getPackageName());
            if (nameId == 0 && descId == 0) return;

            Configuration configuration = new Configuration(resources.getConfiguration());
            configuration.setLocale(Locale.ENGLISH);
            Resources english = context.createConfigurationContext(configuration).getResources();
            name = localizedText(resources, english, nameId, name);
            desc = localizedText(resources, english, descId, desc);
        } catch (Exception ignored) {
            // A live JSON catalog can be newer than the translations bundled in the APK.
        }
    }

    private static String localizedText(Resources resources, Resources english, int id, String original) {
        // Keep newer JSON wording instead of applying a translation of an older description.
        return id != 0 && english.getString(id).equals(original) ? resources.getString(id) : original;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isModified() {
        String value = FlagsSharedPref.getStringPref(code);
        return value != null && !value.isEmpty() && !FlagState.DEFAULT.toString().equals(value);
    }

    public boolean isLongType() {
        return type == FlagType.LONG;
    }

    public StringSetting getCode() {
        return code;
    }
}
