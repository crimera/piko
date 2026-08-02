/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.devFlags;

import org.json.JSONObject;
import app.morphe.extension.crimera.settings.BooleanSetting;


public class Flag {

    private String name;
    private String desc;
    private BooleanSetting code;

    public Flag(JSONObject jsonObject) {
        try {
            this.name = jsonObject.optString("name");
            this.desc = jsonObject.optString("desc");
            String codeKey = jsonObject.optString("code");
            boolean defValue = jsonObject.optBoolean("defaultValue");
            this.code = new BooleanSetting(codeKey,defValue);
        } catch (Exception e) {
        }
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public BooleanSetting getCode() {
        return code;
    }
}