/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.postfilter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

import java.util.List;

public final class PostFilterAdapter extends BaseAdapter {
    public interface OnKeywordClickListener {
        void onClick(int position);
    }

    private final LayoutInflater inflater;
    private final List<String> keywords;
    private final OnKeywordClickListener clickListener;

    public PostFilterAdapter(
            Context context,
            List<String> keywords,
            OnKeywordClickListener clickListener) {
        this.inflater = LayoutInflater.from(context);
        this.keywords = keywords;
        this.clickListener = clickListener;
    }

    @Override
    public int getCount() {
        return keywords.size();
    }

    @Override
    public String getItem(int position) {
        return keywords.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView textView;
        if (convertView == null) {
            convertView = inflater.inflate(
                    ResourceUtils.getIdentifier(ResourceType.LAYOUT, "post_filter_item"),
                    parent,
                    false);
            textView = convertView.findViewById(
                    ResourceUtils.getIdentifier(ResourceType.ID, "post_filter_keyword"));
            convertView.setTag(textView);
        } else {
            textView = (TextView) convertView.getTag();
        }

        textView.setText(getItem(position));
        convertView.setOnClickListener(view -> clickListener.onClick(position));
        return convertView;
    }
}
