/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.morphe.extension.twitter.patches.customise.appIcon;

import android.app.Fragment;
import android.os.Bundle;
import android.view.*;
import android.widget.ListView;
import java.util.List;

import app.morphe.extension.shared.Utils;

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
