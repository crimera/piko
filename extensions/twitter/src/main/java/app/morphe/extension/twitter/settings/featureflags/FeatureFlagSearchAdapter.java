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
import android.widget.TextView;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

class FeatureFlagSearchAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<String> original;
    private final List<String> list;

    FeatureFlagSearchAdapter(Context context, String[] list) {
        inflater = LayoutInflater.from(context);

        original = Arrays.asList(list);
        this.list = new ArrayList<>(Arrays.asList(list));
    }

    private static class ViewHolder {
        TextView textView;
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
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewHolder holder;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = inflater.inflate(ResourceUtils.getIdentifier(ResourceType.LAYOUT, "search_item_row"), null);

            holder.textView = convertView.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "searchItemText"));
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.textView.setText(list.get(position));

        return convertView;
    }

    public void filter(String charText) {
        charText = charText.toLowerCase(Locale.getDefault());
        list.clear();
        if (charText.isEmpty()) {
            list.addAll(original);
        } else {
            for (String item : original) {
                if (item.toLowerCase(Locale.getDefault()).contains(charText)) {
                    list.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }
}
