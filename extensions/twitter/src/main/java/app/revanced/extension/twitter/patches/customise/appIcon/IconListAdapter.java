package app.revanced.extension.twitter.patches.customise.appIcon;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import app.revanced.extension.shared.Utils;
import java.util.List;

public class IconListAdapter extends BaseAdapter {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ICON = 1;

    private final List<RowItem> rows;
    private final LayoutInflater inflater;
    private String selectedComponent;
    private final OnIconSelectListener listener;

    public interface OnIconSelectListener {
        void onSelected(String componentName);
    }

    public IconListAdapter(Context ctx, List<RowItem> rows, String selectedComponent, OnIconSelectListener listener) {
        this.rows = rows;
        this.inflater = LayoutInflater.from(ctx);
        this.selectedComponent = selectedComponent;
        this.listener = listener;
    }

    @Override
    public int getCount() { return rows.size(); }

    @Override
    public Object getItem(int i) { return rows.get(i); }

    @Override
    public long getItemId(int i) { return i; }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isHeader ? TYPE_HEADER : TYPE_ICON;
    }

    @Override
    public int getViewTypeCount() { return 2; }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        RowItem row = rows.get(pos);

        if (row.isHeader) {
            HeaderViewHolder hv;
            if (convertView == null) {
                convertView = inflater.inflate(Utils.getResourceIdentifier("section_header", "layout"), parent, false);
                hv = new HeaderViewHolder();
                hv.title = convertView.findViewById(Utils.getResourceIdentifier("section_header_text", "id"));
                convertView.setTag(hv);
            } else {
                hv = (HeaderViewHolder) convertView.getTag();
            }
            hv.title.setText(row.headerTitle);
            return convertView;
        }

        IconViewHolder iv;
        if (convertView == null) {

            convertView = inflater.inflate(Utils.getResourceIdentifier("icon_item", "layout"), parent, false);
            iv = new IconViewHolder();

            iv.icon = convertView.findViewById(Utils.getResourceIdentifier("icon_image", "id"));
            iv.label = convertView.findViewById(Utils.getResourceIdentifier("icon_name", "id"));
            iv.radio = convertView.findViewById(Utils.getResourceIdentifier("radio_button", "id"));
            convertView.setTag(iv);
        } else {
            iv = (IconViewHolder) convertView.getTag();
        }

        iv.icon.setImageResource(row.iconRes);
        iv.label.setText(row.iconLabel);
        iv.radio.setChecked(row.componentName.equals(selectedComponent));

        convertView.setOnClickListener(v -> {
            notifyDataSetChanged();
            listener.onSelected(row.componentName);
        });

        iv.radio.setOnClickListener(v -> {
            notifyDataSetChanged();
            listener.onSelected(row.componentName);
        });

        return convertView;
    }

    static class HeaderViewHolder {
        TextView title;
    }

    static class IconViewHolder {
        ImageView icon;
        TextView label;
        RadioButton radio;
    }
}
