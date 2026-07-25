/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.postfilter;

import android.app.AlertDialog;
import android.app.Fragment;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.twitter.Utils;
import app.morphe.extension.twitter.patches.postfilter.PostFilterMatcher;
import app.morphe.extension.twitter.patches.postfilter.PostFilterPreferences;
import app.morphe.extension.twitter.settings.ActivityHook;
import app.morphe.extension.twitter.settings.Settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("deprecation")
public final class PostFilterFragment extends Fragment {
    private final List<String> keywords = new ArrayList<>();
    private PostFilterAdapter adapter;
    private TextView emptyView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keywords.addAll(PostFilterPreferences.getKeywords());
        sortKeywords();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ActivityHook.toolbar != null) {
            ActivityHook.toolbar.setTitle(ResourceUtils.getString("piko_title_post_filter"));
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(
                ResourceUtils.getIdentifier(ResourceType.LAYOUT, "post_filter_view"),
                container,
                false);

        Switch enabled = view.findViewById(
                ResourceUtils.getIdentifier(ResourceType.ID, "post_filter_enabled"));
        enabled.setChecked(Utils.getBooleanPref(Settings.POST_FILTER_ENABLED));
        enabled.setOnCheckedChangeListener((button, checked) -> {
            Utils.setBooleanPref(Settings.POST_FILTER_ENABLED.key, checked);
            PostFilterPreferences.invalidateCache();
        });

        emptyView = view.findViewById(
                ResourceUtils.getIdentifier(ResourceType.ID, "post_filter_empty"));
        ListView listView = view.findViewById(
                ResourceUtils.getIdentifier(ResourceType.ID, "post_filter_list"));
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, 200);

        adapter = new PostFilterAdapter(getContext(), keywords, this::showEditDialog);
        listView.setAdapter(adapter);
        updateEmptyState();

        FloatingActionButton addButton = view.findViewById(
                ResourceUtils.getIdentifier(ResourceType.ID, "post_filter_add"));
        addButton.setOnClickListener(button -> showKeywordDialog(-1));
        return view;
    }

    private void showEditDialog(int position) {
        if (position < 0 || position >= keywords.size()) return;
        showKeywordDialog(position);
    }

    private void showKeywordDialog(int position) {
        boolean editing = position >= 0;
        String original = editing ? keywords.get(position) : "";

        EditText input = new EditText(getContext());
        input.setHint(ResourceUtils.getString("piko_post_filter_hint"));
        input.setText(original);
        input.setSelection(input.length());
        input.setSingleLine(false);
        input.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(PostFilterPreferences.MAX_KEYWORD_LENGTH)
        });

        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        ViewGroup.MarginLayoutParams inputParams = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.setMargins(padding, 0, padding, 0);
        input.setLayoutParams(inputParams);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setTitle(ResourceUtils.getString(
                        editing ? "piko_post_filter_edit" : "piko_post_filter_add"))
                .setView(input)
                .setPositiveButton(ResourceUtils.getString("save"), null)
                .setNegativeButton(ResourceUtils.getString("cancel"), null);

        if (editing) {
            builder.setNeutralButton(ResourceUtils.getString("remove"), (dialog, which) -> {
                keywords.remove(position);
                saveAndRefresh();
            });
        }

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String value;
                    try {
                        value = PostFilterPreferences.validate(input.getText().toString());
                    } catch (IllegalArgumentException exception) {
                        input.setError(ResourceUtils.getString("piko_post_filter_blank_error"));
                        return;
                    }

                    if (isDuplicate(value, editing ? position : -1)) {
                        input.setError(ResourceUtils.getString("piko_post_filter_duplicate_error"));
                        return;
                    }

                    if (editing) {
                        keywords.set(position, value);
                    } else {
                        keywords.add(value);
                    }
                    saveAndRefresh();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private boolean isDuplicate(String candidate, int ignoredPosition) {
        String normalizedCandidate = PostFilterMatcher.normalize(candidate);
        for (int index = 0; index < keywords.size(); index++) {
            if (index == ignoredPosition) continue;
            if (PostFilterMatcher.normalize(keywords.get(index)).equals(normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    private void saveAndRefresh() {
        PostFilterPreferences.saveKeywords(new ArrayList<>(keywords));
        sortKeywords();
        if (adapter != null) adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void sortKeywords() {
        keywords.sort(Comparator.comparing(PostFilterMatcher::normalize));
    }

    private void updateEmptyState() {
        if (emptyView == null) return;
        emptyView.setVisibility(keywords.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
