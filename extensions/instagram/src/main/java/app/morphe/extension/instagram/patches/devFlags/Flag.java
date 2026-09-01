/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.devFlags;

import org.json.JSONObject;
import app.morphe.extension.crimera.settings.StringSetting;


public class Flag {

    private String name;
    private String desc;
    private FlagType type;
    private StringSetting code;

    public Flag(JSONObject jsonObject) {
        try {
            this.name = jsonObject.optString("name");
            this.desc = jsonObject.optString("desc");
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
            this.code = new StringSetting(codeKey, FlagState.DEFAULT.toString());
        } catch (Exception e) {
        }
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public boolean isLongType() {
        return type == FlagType.LONG;
    }

    public StringSetting getCode() {
        return code;
    }
}