/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.patches.customise.appIcon;

import android.app.Fragment;
import android.os.Bundle;
import android.view.*;
import android.widget.ListView;
import java.util.List;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

public class IconSelectorFragment extends Fragment {

    private AppIconManager iconManager;
    private List<RowItem> rows;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        iconManager = new AppIconManager(getActivity());

        View root = inflater.inflate(ResourceUtils.getIdentifier(ResourceType.LAYOUT, "fragment_icon_selector"), container, false);
        ListView listView = root.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "icon_listview"));

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
