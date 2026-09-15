/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.featureflags;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

import java.util.ArrayList;
import java.util.List;

class FeatureFlagSearchAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final FeatureFlagCatalog catalog;
    private final List<String> list;

    FeatureFlagSearchAdapter(Context context, FeatureFlagCatalog catalog) {
        inflater = LayoutInflater.from(context);

        this.catalog = catalog;
        this.list = new ArrayList<>(catalog.search(""));
    }

    private static class ViewHolder {
        CheckBox textView;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public String getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean areAllItemsEnabled() { return false; }

    @Override
    public boolean isEnabled(int position) { return !catalog.isAdded(getItem(position)); }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = inflater.inflate(ResourceUtils.getIdentifier(ResourceType.LAYOUT, "search_item_row"), parent, false);

            holder.textView = convertView.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "searchItemText"));
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String name = getItem(position);
        boolean added = catalog.isAdded(name);
        holder.textView.setText(name);
        holder.textView.setChecked(added || catalog.isSelected(name));
        holder.textView.setEnabled(!added);
        convertView.setEnabled(!added);
        convertView.setAlpha(added ? 0.5f : 1f);

        return convertView;
    }

    public void filter(String charText) {
        list.clear();
        list.addAll(catalog.search(charText));
        notifyDataSetChanged();
    }
}
