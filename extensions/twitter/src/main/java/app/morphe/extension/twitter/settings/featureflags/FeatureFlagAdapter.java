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
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

public class FeatureFlagAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<FeatureFlag> list;

    OnItemClickListener itemClickListener;
    OnItemCheckedChangeListener itemCheckedChangeListener;

    FeatureFlagAdapter(Context context, ArrayList<FeatureFlag> list) {
        inflater = LayoutInflater.from(context);

        this.list = list;
    }

    private static class ViewHolder {
        TextView textView;
        Switch enabled;
    }

    public void setItemClickListener(OnItemClickListener itemClickListener) {
        this.itemClickListener = itemClickListener;
    }

    public void setItemCheckedChangeListener(OnItemCheckedChangeListener itemCheckedChangeListener) {
        this.itemCheckedChangeListener = itemCheckedChangeListener;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public FeatureFlag getItem(int position) {
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
            convertView = inflater.inflate(ResourceUtils.getIdentifier(ResourceType.LAYOUT, "item_row"), null);

            holder.textView = convertView.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "textView"));
            holder.enabled = convertView.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "enabled"));

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.textView.setText(list.get(position).getName());
        holder.textView.setOnClickListener(view -> {
            itemClickListener.onClick(position);
        });

        holder.enabled.setChecked(list.get(position).getEnabled());
        holder.enabled.setOnCheckedChangeListener((compoundButton, b) -> {
            if (itemCheckedChangeListener!=null) itemCheckedChangeListener.onCheck(b, position);
        });

        return convertView;
    }

    public void filter(String charText) {
        List<FeatureFlag> original = new ArrayList<>(list);

        charText = charText.toLowerCase(Locale.getDefault());
        list.clear();
        if (charText.isEmpty()) {
            list.addAll(original);
        } else {
            for (FeatureFlag item : original) {
                if (item.getName().toLowerCase(Locale.getDefault()).contains(charText)) {
                    list.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }
}
interface OnItemClickListener {
    void onClick(int position);
}

interface OnItemCheckedChangeListener {
    void onCheck(boolean checked, int position);
}
