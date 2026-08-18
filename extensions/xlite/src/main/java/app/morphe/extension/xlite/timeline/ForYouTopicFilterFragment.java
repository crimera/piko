package app.morphe.extension.xlite.timeline;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.xlite.settings.XLiteSettingsActivity;
import app.morphe.extension.xlite.settings.XLiteSettingsUi;
import app.morphe.extension.xlite.ui.Theme;

@SuppressWarnings("deprecation")
public final class ForYouTopicFilterFragment extends Fragment {
    private final Runnable topicCatalogListener = this::onTopicCatalogChanged;
    private LinearLayout topicsContainer;
    private XLiteSettingsUi.SwitchRow masterSwitch;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        Context context = requireContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(XLiteSettingsUi.backgroundColor(context));

        masterSwitch = XLiteSettingsUi.switchRow(
                context,
                StringRef.str("piko_xlite_topic_filter_enabled_title"),
                StringRef.str("piko_xlite_topic_filter_enabled_summary"),
                ForYouTopicFilter.isEnabled()
        );
        masterSwitch.setOnCheckedChangeListener(this::setFilteringEnabled);
        root.addView(masterSwitch, new LinearLayout.LayoutParams(-1, -2));
        root.addView(XLiteSettingsUi.divider(context));

        ScrollView scroll = new ScrollView(context);
        topicsContainer = new LinearLayout(context);
        topicsContainer.setOrientation(LinearLayout.VERTICAL);
        topicsContainer.setPadding(0, Theme.dpToPx(context, 4f), 0, Theme.dpToPx(context, 24f));
        scroll.addView(topicsContainer, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        refreshTopics();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if (activity instanceof XLiteSettingsActivity settingsActivity) {
            settingsActivity.setPageTitle(StringRef.str("piko_xlite_topic_filtering_title"));
        }
        ForYouTopicFilter.addTopicCatalogListener(topicCatalogListener);
        refreshTopics();
    }

    @Override
    public void onPause() {
        ForYouTopicFilter.removeTopicCatalogListener(topicCatalogListener);
        super.onPause();
    }

    private void onTopicCatalogChanged() {
        LinearLayout container = topicsContainer;
        if (container == null) return;
        container.post(this::refreshTopics);
    }

    private void refreshTopics() {
        if (topicsContainer == null || masterSwitch == null) return;

        boolean enabled = ForYouTopicFilter.isEnabled();
        if (masterSwitch.isChecked() != enabled) {
            masterSwitch.setOnCheckedChangeListener(null);
            masterSwitch.setChecked(enabled, false);
            masterSwitch.setOnCheckedChangeListener(this::setFilteringEnabled);
        }

        topicsContainer.removeAllViews();
        List<ForYouTopicFilter.Topic> topics = ForYouTopicFilter.topicOptions();
        if (topics.isEmpty()) {
            TextView empty = XLiteSettingsUi.summaryText(requireContext());
            empty.setText(StringRef.str("piko_xlite_topic_filtering_empty"));
            int padding = Theme.dpToPx(requireContext(), 24f);
            empty.setPadding(padding, padding, padding, padding);
            topicsContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            topicsContainer.setAlpha(1f);
            return;
        }

        Set<String> selected = ForYouTopicFilter.selectedTopicIds();
        for (ForYouTopicFilter.Topic topic : topics) {
            XLiteSettingsUi.SwitchRow row = XLiteSettingsUi.switchRow(
                    requireContext(),
                    topic.getName(),
                    null,
                    selected.contains(topic.getId())
            );
            row.setEnabled(enabled);
            row.setOnCheckedChangeListener(checked -> setTopicSelected(topic.getId(), checked));
            topicsContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        topicsContainer.setAlpha(enabled ? 1f : 0.45f);
    }

    private void setFilteringEnabled(boolean enabled) {
        ForYouTopicFilter.setEnabled(enabled);
        refreshTopics();
    }

    private void setTopicSelected(String topicId, boolean selected) {
        LinkedHashSet<String> topicIds = new LinkedHashSet<>(ForYouTopicFilter.selectedTopicIds());
        if (selected) {
            topicIds.add(topicId);
        } else {
            topicIds.remove(topicId);
        }
        ForYouTopicFilter.setSelectedTopicIds(topicIds);
    }

    private Context requireContext() {
        Activity activity = getActivity();
        if (activity == null) throw new IllegalStateException("For You topic filter activity is missing");
        return activity;
    }
}
