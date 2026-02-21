package app.morphe.extension.twitter.patches.customise.appIcon;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

public class RowItem {
    public boolean isHeader;
    public String headerTitle;

    public int iconRes;
    public String iconLabel;
    public String componentName;

    public static RowItem header(String titleKey) {
        RowItem r = new RowItem();
        r.isHeader = true;
        r.headerTitle = StringRef.str(titleKey);
        return r;
    }

    public static RowItem icon(String labelKey, String iconId, String iconNum) {
        RowItem r = new RowItem();
        r.isHeader = false;
        r.iconRes = Utils.getResourceIdentifier(iconId, "mipmap");
        r.iconLabel = StringRef.str(labelKey);
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
        r.iconLabel+=" "+StringRef.str("piko_app_icon_piko_exclusive");
        return r;
    }
}
