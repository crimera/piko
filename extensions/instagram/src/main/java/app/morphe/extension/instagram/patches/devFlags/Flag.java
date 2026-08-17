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
    private String type;
    private StringSetting code;

    public Flag(JSONObject jsonObject) {
        try {
            this.name = jsonObject.optString("name");
            this.desc = jsonObject.optString("desc");
            this.type = jsonObject.optString("type", "bool");
            String codeKey = jsonObject.optString("code");
            // Long-typed flags default to an empty override (rendered as a blank
            // numeric field) instead of the "default" sentinel bool flags use,
            // since showing the literal word "default" in a number box reads wrong.
            String defaultValue = isLongType() ? "" : FlagState.DEFAULT.toString();
            this.code = new StringSetting(codeKey, defaultValue);
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
        return "long".equals(type);
    }

    public StringSetting getCode() {
        return code;
    }
}