package app.revanced.integrations.twitter.settings.featureflags;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import app.revanced.integrations.shared.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FeatureFlagAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<FeatureFlag> list;

    OnItemClickListener itemClickListener;
    OnItemCheckedChangeListener itemCheckedChangeListener;

    FeatureFlagAdapter(Context context, ArrayList<FeatureFlag> list) {
        inflater = LayoutInflater.from(context);

        this.list = list;
    }

    private class ViewHolder {
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
            convertView = inflater.inflate(Utils.getResourceIdentifier("item_row", "layout"), null);

            holder.textView = convertView.findViewById(Utils.getResourceIdentifier("textView", "id"));
            holder.enabled = convertView.findViewById(Utils.getResourceIdentifier("enabled", "id"));

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
