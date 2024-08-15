package app.revanced.integrations.twitter.settings.featureflags;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import app.revanced.integrations.shared.Utils;

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

    private class ViewHolder {
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
            convertView = inflater.inflate(Utils.getResourceIdentifier("search_item_row", "layout"), null);

            holder.textView = convertView.findViewById(Utils.getResourceIdentifier("searchItemText", "id"));
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
