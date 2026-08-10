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
    private StringSetting code;

    public Flag(JSONObject jsonObject) {
        try {
            this.name = jsonObject.optString("name");
            this.desc = jsonObject.optString("desc");
            String codeKey = jsonObject.optString("code");
            this.code = new StringSetting(codeKey,FlagState.DEFAULT.toString());
        } catch (Exception e) {
        }
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public StringSetting getCode() {
        return code;
    }
}