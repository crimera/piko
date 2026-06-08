/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.customise.appIcon;

import static app.morphe.extension.shared.StringRef.str;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

public class RowItem {
    public boolean isHeader;
    public String headerTitle;

    public int iconRes;
    public String iconLabel;
    public String componentName;

    public static RowItem header(String titleKey) {
        RowItem r = new RowItem();
        r.isHeader = true;
        r.headerTitle = str(titleKey);
        return r;
    }

    public static RowItem icon(String labelKey, String iconId, String iconNum) {
        RowItem r = new RowItem();
        r.isHeader = false;
        r.iconRes = ResourceUtils.getIdentifier(ResourceType.MIPMAP, iconId);
        r.iconLabel = str(labelKey);
        r.componentName = "app.morphe.extension.twitter.appicon"+iconNum;
        return r;
    }

    public static RowItem icon(String labelKey, String iconId) {
        RowItem r = icon(labelKey,iconId,"00");
        r.componentName = "com.twitter.android.StartActivity";
        return r;
    }

    public static RowItem exclusiveIcon(String labelKey, String iconId, String iconNum) {
        RowItem r = icon(labelKey,iconId,iconNum);
        r.iconLabel+=" "+str("piko_app_icon_piko_exclusive");
        return r;
    }
}
