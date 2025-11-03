package app.revanced.extension.twitter.patches.customise.appIcon;

import java.util.List;

public class IconSection {
    public String header;
    public List<IconOption> icons;

    public IconSection(String header, List<IconOption> icons) {
        this.header = header;
        this.icons = icons;
    }
}
