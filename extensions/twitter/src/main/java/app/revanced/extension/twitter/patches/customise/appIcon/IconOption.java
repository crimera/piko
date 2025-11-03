package app.revanced.extension.twitter.patches.customise.appIcon;

public class IconOption {
    public String name;
    public int iconResId;
    public String componentName;

    public IconOption(String name, int iconResId, String componentName) {
        this.name = name;
        this.iconResId = iconResId;
        this.componentName = componentName;
    }
}
