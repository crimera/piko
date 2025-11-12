package app.revanced.extension.twitter.patches.customise.appIcon;

import android.app.Fragment;
import android.os.Bundle;
import android.view.*;
import android.widget.ListView;
import java.util.List;
import app.revanced.extension.twitter.settings.Settings;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.StringRef;

public class IconSelectorFragment extends Fragment {

    private AppIconManager iconManager;
    private List<RowItem> rows;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        iconManager = new AppIconManager(getActivity());

        View root = inflater.inflate(Utils.getResourceIdentifier("fragment_icon_selector", "layout"), container, false);
        ListView listView = root.findViewById(Utils.getResourceIdentifier("icon_listview", "id"));

        // Build all rows using the new builder class
        IconListBuilder builder = new IconListBuilder();
        rows = builder.buildRows();

        IconListAdapter adapter = new IconListAdapter(
                getActivity(),
                rows,
                iconManager.getSavedIcon(),
                componentName -> iconManager.applyIcon(componentName)
        );

        listView.setAdapter(adapter);
        return root;
    }
}
