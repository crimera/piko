package app.morphe.extension.newx.misc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.morphe.extension.newx.settings.NewXCustomScreenFragment;
import app.morphe.extension.newx.settings.NewXSettingsActivity;
import app.morphe.extension.newx.settings.NewXSettingsUi;
import app.morphe.extension.newx.ui.ButtonView;
import app.morphe.extension.newx.ui.DialogView;
import app.morphe.extension.newx.ui.IconView;
import app.morphe.extension.newx.ui.ListItem;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

/**
 * Download options screen: folder choice and filename template in one place.
 *
 * <p>Every value edited here is an ordinary NewX settings node, so the settings backup covers the
 * chosen folders and the template without a second persistence path.
 *
 * <p>Rows are full-bleed {@link ListItem}s so the pressed ripple covers the whole width,
 * matching the media picker and the other custom screens.
 */
@SuppressWarnings("deprecation")
public final class DownloadOptionsFragment extends NewXCustomScreenFragment {
    private ListItem imagesRow;
    private ListItem videosRow;
    private ListItem filenameRow;
    private Theme.SettingsSnapshot themeSettings;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        Context context = requireContext();
        themeSettings = Theme.snapshot();

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NewXSettingsUi.backgroundColor(context));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        // No horizontal padding: ListItem rows are full-bleed so the ripple reaches
        // edge to edge. Headers and hints carry their own 24dp side padding.
        content.setPadding(0, Theme.dpToPx(context, 8f), 0, Theme.dpToPx(context, 32f));
        scroll.addView(content, new ViewGroup.LayoutParams(-1, -2));

        imagesRow = optionRow(
                context,
                StringRef.str("piko_newx_download_options_folder_images"),
                IconView.IconType.IMAGE,
                view -> openPicker(DownloadDestination.MediaKind.IMAGES)
        );
        content.addView(imagesRow, rowParams());

        videosRow = optionRow(
                context,
                StringRef.str("piko_newx_download_options_folder_videos"),
                IconView.IconType.VIDEO,
                view -> openPicker(DownloadDestination.MediaKind.VIDEOS)
        );
        content.addView(videosRow, rowParams());

        filenameRow = optionRow(
                context,
                StringRef.str("piko_newx_download_filename_title"),
                IconView.IconType.DOWNLOAD,
                view -> showFilenameEditor()
        );
        content.addView(filenameRow, rowParams());

        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();

        Activity activity = getActivity();
        if (activity instanceof NewXSettingsActivity settingsActivity) {
            settingsActivity.setPageTitle(StringRef.str("piko_newx_inline_download_options_title"));
        }
        refresh();
    }

    private Context requireContext() {
        Context context = getActivity();
        if (context == null) throw new IllegalStateException("Download options activity is missing");
        return context;
    }

    private void refresh() {
        if (imagesRow != null) {
            imagesRow.setSubtitle(folderSubtitle(imagesRow.getContext(),
                    DownloadDestination.MediaKind.IMAGES));
        }
        if (videosRow != null) {
            videosRow.setSubtitle(folderSubtitle(videosRow.getContext(),
                    DownloadDestination.MediaKind.VIDEOS));
        }
        if (filenameRow != null) {
            filenameRow.setSubtitle(DownloadFileName.preview(DownloadSettings.filenameTemplate()));
        }
    }

    private static CharSequence folderSubtitle(
            Context context,
            DownloadDestination.MediaKind kind
    ) {
        String path = DownloadDestination.displayPath(kind);
        if (path == null) {
            // Restore the display label from the saved URI when backups omit it.
            android.net.Uri tree = DownloadDestination.treeUri(kind);
            path = tree == null ? null : DownloadDestination.displayPathFor(tree);
        }
        if (path == null) {
            return StringRef.str("piko_newx_download_options_folder_not_set");
        }
        switch (DownloadDestination.destinationState(context, kind)) {
            case LIVE:
                // The temporary grant may stop working after a restart.
                return path + " \u2014 " + StringRef.str("piko_newx_download_options_folder_session");
            case UNUSABLE:
                return path + " \u2014 " + StringRef.str("piko_newx_download_options_folder_denied");
            default:
                return path;
        }
    }

    private void openPicker(DownloadDestination.MediaKind kind) {
        Activity activity = getActivity();
        if (activity == null) return;

        Intent intent = new Intent(activity, DownloadFolderPickerActivity.class)
                .putExtra(DownloadFolderPickerActivity.KIND_EXTRA, kind.name());
        activity.startActivity(intent);
    }

    private void showFilenameEditor() {
        Activity activity = getActivity();
        if (activity == null) return;

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Theme.dpToPx(activity, 24f), 0, Theme.dpToPx(activity, 24f), 0);

        EditText input = NewXSettingsUi.textInput(activity, null, InputType.TYPE_CLASS_TEXT);
        input.setText(DownloadSettings.filenameTemplate());
        input.setSelection(input.length());
        form.addView(input, new LinearLayout.LayoutParams(-1, -2));

        TextView preview = NewXSettingsUi.summaryText(activity);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
        previewParams.topMargin = Theme.dpToPx(activity, 12f);
        form.addView(preview, previewParams);

        HorizontalScrollView chips = new HorizontalScrollView(activity);
        chips.setHorizontalScrollBarEnabled(false);
        chips.addView(tokenChips(activity, input), new ViewGroup.LayoutParams(-2, -2));
        form.addView(chips, tokenRowParams(activity));

        DialogView dialog = new DialogView(activity)
                .setTitle(StringRef.str("piko_newx_download_filename_title"))
                .setScrollableBodyView(form);
        dialog.getDialog().setCanceledOnTouchOutside(true);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String value = editable == null ? "" : editable.toString();
                String error = DownloadFileName.validationError(value);
                preview.setText(error != null
                        ? error
                        : StringRef.str(
                                "piko_newx_download_options_filename_preview",
                                DownloadFileName.preview(value)
                        ));
            }
        });
        preview.setText(StringRef.str(
                "piko_newx_download_options_filename_preview",
                DownloadFileName.preview(input.getText().toString())
        ));

        ButtonView cancel = NewXSettingsUi.dialogButton(
                activity,
                StringRef.str("piko_newx_settings_cancel")
        );
        cancel.setOnClickListener(ignored -> dialog.dismiss());

        ButtonView reset = NewXSettingsUi.dialogButton(
                activity,
                StringRef.str("piko_newx_download_options_filename_reset")
        );
        reset.setOnClickListener(ignored -> {
            input.setText(DownloadFileName.DEFAULT_TEMPLATE);
            input.setSelection(input.length());
        });

        ButtonView save = NewXSettingsUi.dialogButton(
                activity,
                StringRef.str("piko_newx_settings_ok")
        );
        save.setOnClickListener(ignored -> {
            String value = input.getText().toString();
            String error = DownloadFileName.validationError(value);
            if (error != null) {
                Utils.showToastShort(error);
                return;
            }
            if (!DownloadSettings.filenameTemplate().equals(value)) {
                DownloadSettings.setString(DownloadSettings.FILENAME_TEMPLATE, value);
            }
            dialog.dismiss();
            refresh();
        });

        dialog.addButton(cancel).addButton(reset).addButton(save).show();
    }

    private static LinearLayout tokenChips(Activity activity, EditText input) {
        LinearLayout chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (String token : DownloadFileName.editorTokens()) {
            ButtonView chip = new ButtonView(
                    activity,
                    ButtonView.ButtonStyle.TONAL,
                    "{" + token + "}"
            );
            chip.setOnClickListener(ignored -> insertToken(input, token));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMarginEnd(Theme.dpToPx(activity, 8f));
            chips.addView(chip, params);
        }
        return chips;
    }

    /** Inserts the token at the selection. */
    private static void insertToken(EditText input, String token) {
        String text = input.getText().toString();
        int start = Math.max(input.getSelectionStart(), 0);
        int end = Math.max(input.getSelectionEnd(), 0);
        if (start > end) {
            int swap = start;
            start = end;
            end = swap;
        }
        start = Math.min(start, text.length());
        end = Math.min(end, text.length());

        String insertion = "{" + token + "}";
        input.setText(text.substring(0, start) + insertion + text.substring(end));
        input.setSelection(start + insertion.length());
    }

    private static LinearLayout.LayoutParams tokenRowParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Theme.dpToPx(context, 8f);
        return params;
    }

    private ListItem optionRow(
            Context context,
            CharSequence title,
            IconView.IconType icon,
            View.OnClickListener listener
    ) {
        Theme.SettingsSnapshot snapshot =
                themeSettings != null ? themeSettings : Theme.snapshot();
        ListItem row = new ListItem(context, snapshot);
        row.setTitle(title);
        row.setNavigationIcon(icon);
        row.setOnClickListener(listener);
        return row;
    }

    private static LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

}
