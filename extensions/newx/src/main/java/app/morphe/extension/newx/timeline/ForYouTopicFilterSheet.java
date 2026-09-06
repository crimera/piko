package app.morphe.extension.newx.timeline;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.newx.settings.NewXSettingsUi;
import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.newx.ui.BottomSheetView;
import app.morphe.extension.newx.ui.ButtonView;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.shared.Utils;

/** Displays the extension-owned topic selector for the For You tab. */
final class ForYouTopicFilterSheet {
    private ForYouTopicFilterSheet() {
    }

    static boolean show(ForYouTopicFilter.RefreshTarget refreshTarget) {
        Context context = Utils.getContext();
        Activity activity = NewXUtils.findUsableActivity(context);
        if (activity == null) return false;

        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread(() -> show(activity, refreshTarget));
        } else {
            show(activity, refreshTarget);
        }
        return true;
    }

    private static void show(
            Activity activity,
            ForYouTopicFilter.RefreshTarget refreshTarget
    ) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        try {
            List<ForYouTopicFilter.Topic> topics = ForYouTopicFilter.topicOptions();
            Set<String> storedSelection = ForYouTopicFilter.parseTopicIds(
                    ForYouTopicFilter.shared().selectedTopicIds.get()
            );
            LinkedHashSet<String> selected = new LinkedHashSet<>(storedSelection);

            BottomSheetView sheet = new BottomSheetView(activity);
            sheet.setTitle("For You topics");
            sheet.setSubtitle("Select topics to show in your For You timeline");

            LinearLayout body = new LinearLayout(activity);
            body.setOrientation(LinearLayout.VERTICAL);

            List<NewXSettingsUi.SwitchRow> rows = new ArrayList<>();
            ButtonView actionButton = new ButtonView(activity);
            for (ForYouTopicFilter.Topic topic : topics) {
                NewXSettingsUi.SwitchRow row = NewXSettingsUi.switchRow(
                        activity,
                        topic.getName(),
                        null,
                        selected.contains(topic.getId())
                );
                row.setOnCheckedChangeListener(checked -> {
                    if (checked) {
                        selected.add(topic.getId());
                    } else {
                        selected.remove(topic.getId());
                    }
                    saveSelection(selected);
                    updateActionButton(actionButton, selected.size());
                });
                body.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
                rows.add(row);
            }

            if (topics.isEmpty()) {
                TextView empty = NewXSettingsUi.summaryText(activity);
                empty.setText("Topic choices are not available yet. Try again after the For You timeline loads.");
                int padding = Theme.dpToPx(activity, 20f);
                empty.setPadding(padding, padding, padding, padding);
                body.addView(empty, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }

            sheet.setScrollableBodyView(body);

            updateActionButton(actionButton, selected.size());
            actionButton.setOnClickListener(ignored -> {
                if (selected.isEmpty()) {
                    selected.clear();
                    for (NewXSettingsUi.SwitchRow row : rows) {
                        if (row.isChecked()) row.setChecked(false, true);
                    }
                }
                saveSelection(selected);
                try {
                    refreshTarget.pikoRefreshForYouTopicFilter();
                } catch (RuntimeException exception) {
                    NewXLogger.printException(
                            () -> "Failed to refresh For You after topic selection",
                            exception
                    );
                }
                sheet.dismiss();
            });
            sheet.addButton(actionButton);
            sheet.show();
        } catch (RuntimeException exception) {
            NewXLogger.printException(() -> "Failed to open For You topic selector", exception);
        }
    }

    private static void updateActionButton(ButtonView button, int selectedCount) {
        if (selectedCount > 0) {
            button.setText(selectedCount == 1
                    ? "Snooze 1 topic"
                    : "Snooze " + selectedCount + " topics");
            button.setButtonStyle(ButtonView.ButtonStyle.FILLED);
        } else {
            button.setText("Reset");
            button.setButtonStyle(ButtonView.ButtonStyle.TONAL);
        }
    }

    private static void saveSelection(Set<String> selected) {
        ForYouTopicFilter.shared().selectedTopicIds.save(String.join(",", selected));
        ForYouTopicFilter.shared().enabled.save(!selected.isEmpty());
    }
}
